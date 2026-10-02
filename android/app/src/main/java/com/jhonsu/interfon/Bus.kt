package com.jhonsu.interfon

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow

/** Estado compartido entre el servicio, el controlador de llamadas y la UI. */
object Bus {

    enum class Conn { DISCONNECTED, CONNECTING, CONNECTED }

    data class Line(val role: String, val text: String, val ctx: String,
                    val ts: Long = System.currentTimeMillis())

    data class IncomingCall(val callId: String, val from: String, val text: String)

    val connection = MutableStateFlow(Conn.DISCONNECTED)
    val serverUrl = MutableStateFlow("")

    /** Transcripcion de la conversacion (llamada y walkie). */
    val lines = MutableStateFlow<List<Line>>(emptyList())

    val callActive = MutableStateFlow(false)
    /** idle | listening | thinking | speaking */
    val callState = MutableStateFlow("idle")
    val incoming = MutableStateFlow<IncomingCall?>(null)

    /** True mientras se reproduce audio del agente (pausa el microfono). */
    val playing = MutableStateFlow(false)
    val walkieBusy = MutableStateFlow(false)

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
