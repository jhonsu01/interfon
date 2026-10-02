package com.jhonsu.interfon

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** Cliente WebSocket contra el servidor Interfon, con reconexion automatica. */
class WsClient(
    private val httpUrl: String,
    private val onJson: (JSONObject) -> Unit,
    private val onBinary: (ByteArray) -> Unit,
    private val onConn: (Boolean) -> Unit,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val client = OkHttpClient.Builder()
        .pingInterval(20, TimeUnit.SECONDS)
        .connectTimeout(5, TimeUnit.SECONDS)
        .build()

    @Volatile private var webSocket: WebSocket? = null
    @Volatile private var closedByUser = false
    @Volatile private var attempts = 0

    fun connect() {
        closedByUser = false
        val wsUrl = httpUrl
            .replace(Regex("^http://"), "ws://")
            .replace(Regex("^https://"), "wss://")
            .trimEnd('/') + "/ws"
        val req = Request.Builder().url(wsUrl).build()
        webSocket = client.newWebSocket(req, object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, response: Response) {
                attempts = 0
                onConn(true)
                ws.send(JSONObject().put("type", "hello")
                    .put("app", "interfon").put("device", android.os.Build.MODEL).toString())
            }

            override fun onMessage(ws: WebSocket, text: String) {
                runCatching { onJson(JSONObject(text)) }
            }

            override fun onMessage(ws: WebSocket, bytes: okio.ByteString) {
                onBinary(bytes.toByteArray())
            }

            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                onConn(false)
                scheduleReconnect()
            }

            override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                onConn(false)
                if (!closedByUser) scheduleReconnect()
            }
        })
    }

    private fun scheduleReconnect() {
        if (closedByUser) return
        val wait = if (attempts < 5) 2000L else 10000L
        attempts++
        scope.launch {
            delay(wait)
            if (!closedByUser) connect()
        }
    }

    fun sendJson(json: JSONObject): Boolean = webSocket?.send(json.toString()) ?: false

    fun sendBytes(data: ByteArray): Boolean =
        webSocket?.send(okio.ByteString.of(*data)) ?: false

    fun close() {
        closedByUser = true
        webSocket?.close(1000, "app cerrada")
    }
}
