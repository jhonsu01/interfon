package com.jhonsu.interfon

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.concurrent.ConcurrentHashMap

/**
 * Orquesta llamadas y walkie contra varios servidores a la vez.
 *
 * - Cualquier servidor puede llamar o enviar audios (se reciben de todos).
 * - Una llamada queda ligada al servidor que la origino ([callServer]).
 * - Lo saliente (llamar, walkie) va al primer servidor conectado en orden de
 *   prioridad; si el envio falla se prueba el siguiente (failover).
 */
object CallController {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val playMutex = Mutex()

    private val clients = ConcurrentHashMap<String, WsClient>()
    @Volatile private var callServer: String? = null
    @Volatile private var walkieServer: String? = null
    private var onPushMessage: ((String) -> Unit)? = null

    /** Callback que el servicio registra para notificar audios push. */
    fun setPushMessageHandler(h: (String) -> Unit) {
        onPushMessage = h
    }

    fun attach(serverId: String, client: WsClient) {
        clients[serverId] = client
    }

    /** El servidor se desconecto: cerrar lo que dependiera de el. */
    fun detach(serverId: String, client: WsClient) {
        // Si ya lo reemplazo una conexion nueva del mismo servidor, no tocar nada
        if (!clients.remove(serverId, client)) return
        pending.remove(serverId)
        if (Bus.incoming.value?.serverId == serverId) Bus.incoming.value = null
        if (callServer == serverId && Bus.callActive.value) {
            stopMic()
            Bus.resetCall()
            callServer = null
            Bus.events.tryEmit(Lang.str(R.string.call_lost, Bus.serverName(serverId) ?: ""))
        }
        if (walkieServer == serverId && Bus.walkieBusy.value) {
            Bus.walkieBusy.value = false
            walkieServer = null
        }
    }

    private fun send(serverId: String?, json: JSONObject): Boolean =
        serverId?.let { clients[it]?.sendJson(json) } ?: false

    /** Envia al primer servidor conectado que acepte el mensaje; devuelve su id. */
    private fun sendToFirst(json: JSONObject): String? =
        Bus.connectedIds().firstOrNull { clients[it]?.sendJson(json) == true }

    // ---------- acciones del usuario ----------

    fun startOutgoingCall(): Boolean {
        val id = sendToFirst(JSONObject().put("type", "call_outgoing")) ?: return false
        callServer = id
        Bus.callActive.value = true
        Bus.callState.value = "listening"
        startMic()
        return true
    }

    fun answer() {
        val call = Bus.incoming.value ?: return
        send(call.serverId, JSONObject().put("type", "call_answer").put("call_id", call.callId))
        callServer = call.serverId
        Bus.incoming.value = null
        Bus.callActive.value = true
        Bus.callState.value = "listening"
        startMic()
    }

    fun decline() {
        val call = Bus.incoming.value ?: return
        send(call.serverId, JSONObject().put("type", "call_decline").put("call_id", call.callId))
        Bus.resetCall()
    }

    fun hangup() {
        send(callServer, JSONObject().put("type", "hangup"))
        callServer = null
        stopMic()
        Bus.resetCall()
    }

    /** Nombre del servidor de la llamada en curso (para mostrarlo en pantalla). */
    fun callServerName(): String? = Bus.serverName(callServer)

    // ---------- walkie ----------

    private val walkieBuffer = ByteArrayOutputStream()
    @Volatile private var walkieRecording = false

    fun startWalkie(): Boolean {
        if (walkieRecording) return true
        walkieBuffer.reset()
        beginAudioSession()
        val started = AudioEngine.startRecording { chunk ->
            Bus.micLevel.value = AudioEngine.rmsLevel(chunk)
            if (walkieRecording) synchronized(walkieBuffer) { walkieBuffer.write(chunk) }
        }
        if (started) walkieRecording = true
        return started
    }

    fun stopWalkieAndSend() {
        if (!walkieRecording) return
        walkieRecording = false
        AudioEngine.stopRecording()
        Bus.micLevel.value = 0f
        val pcm = synchronized(walkieBuffer) { walkieBuffer.toByteArray() }
        if (pcm.size < 6400) { // < 0.2s: descartar toque accidental
            Bus.events.tryEmit(Lang.str(R.string.clip_too_short))
            endAudioSession()
            return
        }
        val id = sendToFirst(JSONObject().put("type", "walkie_tx"))
        if (id == null) {
            Bus.events.tryEmit(Lang.str(R.string.no_connection))
            endAudioSession()
            return
        }
        walkieServer = id
        Bus.walkieBusy.value = true
        clients[id]?.sendBytes(AudioEngine.pcmToWav(pcm))
        // Seguro: nunca quedar "Procesando..." para siempre
        scope.launch {
            delay(60_000)
            if (Bus.walkieBusy.value) {
                Bus.walkieBusy.value = false
                Bus.events.tryEmit(Lang.str(R.string.server_no_reply))
            }
        }
    }

    // ---------- microfono en llamada ----------

    @Volatile private var micStarted = false

    /** Alterna auricular (llamada normal) / altavoz. */
    fun setSpeaker(on: Boolean) {
        Bus.speaker.value = on
        val ctx = InterfonApp.appContext
        Prefs.setSpeaker(ctx, on)
        CallAudio.apply(ctx, on)
    }

    private fun beginAudioSession() {
        val ctx = InterfonApp.appContext
        CallAudio.setInCall(ctx, true)
        CallAudio.apply(ctx, Bus.speaker.value)
    }

    private fun endAudioSession() {
        runCatching { CallAudio.setInCall(InterfonApp.appContext, false) }
    }

    private fun startMic() {
        if (micStarted) return
        beginAudioSession()
        val ok = AudioEngine.startRecording { chunk ->
            Bus.micLevel.value = AudioEngine.rmsLevel(chunk)
            // Half-duplex: enviar solo mientras el servidor escucha y no reproducimos
            if (Bus.callActive.value && Bus.callState.value == "listening" &&
                !Bus.playing.value) {
                callServer?.let { clients[it]?.sendBytes(chunk) }
            }
        }
        if (!ok) Bus.events.tryEmit(Lang.str(R.string.no_mic))
        micStarted = ok
    }

    private fun stopMic() {
        AudioEngine.stopRecording()
        Bus.micLevel.value = 0f
        micStarted = false
        endAudioSession()
    }

    // ---------- eventos del servidor ----------

    /** Por servidor: tipo y texto del proximo binario (JSON y audio llegan por el mismo socket). */
    private val pending = ConcurrentHashMap<String, Pair<String, String>>()

    fun onJson(serverId: String, msg: JSONObject) {
        val isCallServer = callServer == serverId
        when (msg.optString("type")) {
            "welcome" -> Unit

            "incoming_call" -> {
                val busy = Bus.callActive.value ||
                    (Bus.incoming.value != null && Bus.incoming.value?.serverId != serverId)
                if (busy) {
                    // Ya hay una llamada con otro servidor: rechazar como ocupado
                    send(serverId, JSONObject().put("type", "call_decline")
                        .put("call_id", msg.optString("call_id")))
                } else {
                    Bus.incoming.value = Bus.IncomingCall(
                        msg.optString("call_id"),
                        msg.optString("from", Lang.str(R.string.agent_default)),
                        msg.optString("text"),
                        serverId)
                }
            }

            "call_started" -> if (callServer == null || isCallServer) {
                callServer = serverId
                Bus.callActive.value = true
                if (Bus.callState.value == "idle") Bus.callState.value = "listening"
                startMic()
            }

            "call_state" -> if (isCallServer) Bus.callState.value = msg.optString("state")

            "transcript" -> Bus.addLine(
                msg.optString("role"), msg.optString("text"), msg.optString("ctx", "call"))

            "agent_audio" -> pending[serverId] =
                msg.optString("kind", "call") to msg.optString("text")

            // Mensaje push del servidor: el binario que sigue es un anuncio
            "agent_message" -> pending[serverId] = "message" to msg.optString("text")

            "agent_audio_end" -> Unit

            "call_end" -> {
                if (Bus.incoming.value?.serverId == serverId) Bus.incoming.value = null
                if (isCallServer) {
                    stopMic()
                    callServer = null
                    val reason = msg.optString("reason")
                    Bus.resetCall()
                    if (reason.isNotEmpty() && reason != "colgada por el usuario") {
                        Bus.events.tryEmit(Lang.str(R.string.call_ended, reason))
                    }
                }
            }

            "walkie_done", "walkie_empty" -> if (walkieServer == null || walkieServer == serverId) {
                walkieServer = null
                Bus.walkieBusy.value = false
                if (!walkieRecording) endAudioSession()
            }
        }
    }

    fun onBinary(serverId: String, data: ByteArray) {
        if (data.size < 4 || String(data, 0, 4) != "RIFF") return
        val (kind, text) = pending.remove(serverId) ?: ("" to "")
        val isAnnounce = kind == "message"
        if (isAnnounce) Bus.announce.value = text
        Bus.playing.value = true
        scope.launch {
            try {
                if (isAnnounce && !Bus.callActive.value) {
                    // Anuncio estilo llamada: canal de comunicacion, altavoz, orbe visible
                    val ctx = InterfonApp.appContext
                    runCatching {
                        CallAudio.setInCall(ctx, true)
                        CallAudio.apply(ctx, true)
                    }
                    try {
                        playMutex.withLock { AudioEngine.playWav(data, true) }
                    } finally {
                        runCatching {
                            CallAudio.apply(ctx, Bus.speaker.value)
                            CallAudio.setInCall(ctx, false)
                        }
                    }
                } else {
                    // Cola: varios audios seguidos (p. ej. podcast) se reproducen en orden
                    playMutex.withLock { AudioEngine.playWav(data, !isAnnounce) }
                }
            } finally {
                Bus.playing.value = false
                if (isAnnounce) Bus.announce.value = null
            }
        }
        if (isAnnounce) onPushMessage?.invoke(text)
    }

    /** Corta el anuncio en curso (boton Detener). */
    fun stopAnnouncement() {
        AudioEngine.stopPlayback()
        Bus.announce.value = null
    }
}
