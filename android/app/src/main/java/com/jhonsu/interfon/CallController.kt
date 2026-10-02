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

/** Orquesta llamadas y walkie contra el servidor. */
object CallController {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val playMutex = Mutex()

    private var ws: WsClient? = null
    private var onPushMessage: ((String) -> Unit)? = null

    /** Callback que el servicio registra para notificar audios push. */
    fun setPushMessageHandler(h: (String) -> Unit) {
        onPushMessage = h
    }

    fun attach(client: WsClient) {
        ws = client
    }

    // ---------- acciones del usuario ----------

    fun startOutgoingCall(): Boolean {
        val ok = ws?.sendJson(JSONObject().put("type", "call_outgoing")) ?: false
        if (ok) {
            Bus.callActive.value = true
            Bus.callState.value = "listening"
            startMic()
        }
        return ok
    }

    fun answer() {
        val call = Bus.incoming.value ?: return
        ws?.sendJson(JSONObject().put("type", "call_answer").put("call_id", call.callId))
        Bus.incoming.value = null
        Bus.callActive.value = true
        Bus.callState.value = "listening"
        startMic()
    }

    fun decline() {
        val call = Bus.incoming.value ?: return
        ws?.sendJson(JSONObject().put("type", "call_decline").put("call_id", call.callId))
        Bus.resetCall()
    }

    fun hangup() {
        ws?.sendJson(JSONObject().put("type", "hangup"))
        stopMic()
        Bus.resetCall()
    }

    // ---------- walkie ----------

    private val walkieBuffer = ByteArrayOutputStream()
    @Volatile private var walkieRecording = false

    fun startWalkie(): Boolean {
        if (walkieRecording) return true
        walkieBuffer.reset()
        beginAudioSession()
        val started = AudioEngine.startRecording { chunk ->
            if (walkieRecording) synchronized(walkieBuffer) { walkieBuffer.write(chunk) }
        }
        if (started) walkieRecording = true
        return started
    }

    fun stopWalkieAndSend() {
        if (!walkieRecording) return
        walkieRecording = false
        AudioEngine.stopRecording()
        val pcm = synchronized(walkieBuffer) { walkieBuffer.toByteArray() }
        if (pcm.size < 6400) { // < 0.2s: descartar toque accidental
            Bus.events.tryEmit("Clip demasiado corto")
            endAudioSession()
            return
        }
        Bus.walkieBusy.value = true
        ws?.sendJson(JSONObject().put("type", "walkie_tx"))
        ws?.sendBytes(AudioEngine.pcmToWav(pcm))
        // Seguro: nunca quedar "Procesando..." para siempre
        scope.launch {
            delay(60_000)
            if (Bus.walkieBusy.value) {
                Bus.walkieBusy.value = false
                Bus.events.tryEmit("El servidor no respondio (60s)")
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
            // Half-duplex: enviar solo mientras el servidor escucha y no reproducimos
            if (Bus.callActive.value && Bus.callState.value == "listening" &&
                !Bus.playing.value) {
                ws?.sendBytes(chunk)
            }
        }
        if (!ok) Bus.events.tryEmit("Sin permiso de microfono")
        micStarted = ok
    }

    private fun stopMic() {
        AudioEngine.stopRecording()
        micStarted = false
        endAudioSession()
    }

    // ---------- eventos del servidor ----------

    private var pendingAudioKind = ""
    private var pendingAudioText = ""

    fun onJson(msg: JSONObject) {
        when (msg.optString("type")) {
            "welcome" -> Unit

            "incoming_call" -> {
                Bus.incoming.value = Bus.IncomingCall(
                    msg.optString("call_id"),
                    msg.optString("from", "Agente"),
                    msg.optString("text"))
            }

            "call_started" -> {
                Bus.callActive.value = true
                if (Bus.callState.value == "idle") Bus.callState.value = "listening"
                startMic()
            }

            "call_state" -> Bus.callState.value = msg.optString("state")

            "transcript" -> Bus.addLine(
                msg.optString("role"), msg.optString("text"), msg.optString("ctx", "call"))

            "agent_audio" -> {
                pendingAudioKind = msg.optString("kind", "call")
                pendingAudioText = msg.optString("text")
            }

            "agent_audio_end" -> Unit

            "call_end" -> {
                stopMic()
                val reason = msg.optString("reason")
                Bus.resetCall()
                if (reason.isNotEmpty() && reason != "colgada por el usuario") {
                    Bus.events.tryEmit("Llamada finalizada: $reason")
                }
            }

            "walkie_done", "walkie_empty" -> {
                Bus.walkieBusy.value = false
                if (!walkieRecording) endAudioSession()
            }
        }
    }

    fun onBinary(data: ByteArray) {
        if (data.size < 4 || String(data, 0, 4) != "RIFF") return
        val kind = pendingAudioKind
        val text = pendingAudioText
        pendingAudioKind = ""
        pendingAudioText = ""
        Bus.playing.value = true
        val voice = kind != "message"
        scope.launch {
            try {
                // Cola: varios audios seguidos (p. ej. podcast) se reproducen en orden
                playMutex.withLock { AudioEngine.playWav(data, voice) }
            } finally {
                Bus.playing.value = false
            }
        }
        if (kind == "message") onPushMessage?.invoke(text)
    }
}
