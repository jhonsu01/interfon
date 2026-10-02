package com.jhonsu.interfon

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/** Estado compartido entre el servicio, el controlador de llamadas y la UI. */
object Bus {

    enum class Conn { DISCONNECTED, CONNECTING, CONNECTED }

    data class Line(val role: String, val text: String, val ctx: String,
                    val ts: Long = System.currentTimeMillis())

    /** Llamada entrante; serverId indica que servidor la origino. */
    data class IncomingCall(val callId: String, val from: String, val text: String,
                            val serverId: String = "")

    /** Estado combinado: CONNECTED si al menos un servidor lo esta. */
    val connection = MutableStateFlow(Conn.DISCONNECTED)

    /** Servidores configurados (orden = prioridad) y estado de cada uno por id. */
    val servers = MutableStateFlow<List<ServerEntry>>(emptyList())
    val serverConn = MutableStateFlow<Map<String, Conn>>(emptyMap())
    val discovering = MutableStateFlow(false)

    fun setServerConn(id: String, c: Conn?) {
        // Un socket que cierra tarde no debe resucitar un servidor ya eliminado
        if (c != null && servers.value.none { it.id == id }) return
        serverConn.update { if (c == null) it - id else it + (id to c) }
        android.util.Log.d("Interfon", "server $id -> $c")
        val all = serverConn.value.values
        connection.value = when {
            Conn.CONNECTED in all -> Conn.CONNECTED
            Conn.CONNECTING in all || discovering.value -> Conn.CONNECTING
            else -> Conn.DISCONNECTED
        }
    }

    /** Servidores conectados, en orden de prioridad. */
    fun connectedIds(): List<String> =
        servers.value.map { it.id }.filter { serverConn.value[it] == Conn.CONNECTED }

    fun serverName(id: String?): String? = servers.value.firstOrNull { it.id == id }?.name

    /** Transcripcion de la conversacion (llamada y walkie). */
    val lines = MutableStateFlow<List<Line>>(emptyList())

    val callActive = MutableStateFlow(false)
    /** idle | listening | thinking | speaking */
    val callState = MutableStateFlow("idle")
    val incoming = MutableStateFlow<IncomingCall?>(null)

    /** True mientras se reproduce audio del agente (pausa el microfono). */
    val playing = MutableStateFlow(false)
    val walkieBusy = MutableStateFlow(false)

    /** Niveles de voz 0..1 para la animacion del orbe (envolvente real del audio). */
    val voiceLevel = MutableStateFlow(0f)   // voz del agente (reproduccion)
    val micLevel = MutableStateFlow(0f)     // voz del usuario (grabacion)

    /** Anuncio en curso (audio push): texto del mensaje mientras suena. */
    val announce = MutableStateFlow<String?>(null)
    val appInForeground = MutableStateFlow(false)

    /** Ruta de audio: false = auricular (llamada normal), true = altavoz. */
    val speaker = MutableStateFlow(false)

    /** Contacto del agente: nombre y foto personalizados por el usuario. */
    val agentName = MutableStateFlow("ZCode")
    val agentPhoto = MutableStateFlow<android.graphics.Bitmap?>(null)

    /** Avisos breves para la UI (toasts). */
    val events = MutableSharedFlow<String>(extraBufferCapacity = 16)

    fun addLine(role: String, text: String, ctx: String) {
        lines.value = lines.value + Line(role, text, ctx)
    }

    fun resetCall() {
        callActive.value = false
        callState.value = "idle"
        incoming.value = null
    }
}
