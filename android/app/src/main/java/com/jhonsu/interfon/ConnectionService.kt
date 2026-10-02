package com.jhonsu.interfon

import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Servicio en primer plano: mantiene la conexion con el servidor Interfon.
 *
 * Ciclo gestionado: intenta la URL guardada; si falla repetidamente, escanea
 * la red local en busca del servidor (ServerDiscovery) y actualiza la URL.
 */
class ConnectionService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var ws: WsClient? = null
    private var loopJob: Job? = null
    private var collectorJob: Job? = null

    @Volatile private var discoverOnNext = false
    @Volatile private var lastDiscoveryAt = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        startAsForeground(getString(R.string.notif_service_text))
        when (intent?.action) {
            ACTION_DISCOVER -> {
                discoverOnNext = true
                restartLoop()
            }
            ACTION_RECONNECT -> restartLoop()
            else -> if (loopJob == null) startLoop()
        }
        return START_STICKY
    }

    // ---------- ciclo de conexion ----------

    private fun startLoop() {
        ensureCollector()
        loopJob = scope.launch { connectionLoop() }
    }

    private fun restartLoop() {
        loopJob?.cancel()
        ws?.close()
        startLoop()
    }

    private suspend fun connectionLoop() {
        var failures = 0
        // La cancelacion del scope corta el bucle en delay()/await()
        while (true) {
            if (discoverOnNext) {
                discoverOnNext = false
                tryDiscover()
            }
            val url = Prefs.url(this@ConnectionService)
            Bus.serverUrl.value = url
            val connected = connectOnce(url)
            failures = if (connected) 0 else failures + 1
            if (!connected) {
                // Auto-descubrimiento: maximo una vez cada 15 s
                val now = System.currentTimeMillis()
                if (now - lastDiscoveryAt > 15000) {
                    tryDiscover()
                }
            }
            delay(when {
                connected -> 400
                failures > 6 -> 8000
                else -> 2500
            })
        }
    }

    /** Escanea la red; si encuentra el servidor, lo guarda como URL activa. */
    private suspend fun tryDiscover(): Boolean {
        lastDiscoveryAt = System.currentTimeMillis()
        Bus.connection.value = Bus.Conn.CONNECTING
        Bus.serverUrl.value = "buscando servidor en la red…"
        val found = ServerDiscovery.discover()
        return if (found != null) {
            Prefs.setUrl(this, found)
            Bus.serverUrl.value = found
            true
        } else {
            false
        }
    }

    /** Conecta a la URL y suspende hasta que la conexion caiga. */
    private suspend fun connectOnce(url: String): Boolean {
        Bus.connection.value = Bus.Conn.CONNECTING
        Bus.serverUrl.value = url
        val initial = CompletableDeferred<Boolean>()
        val ended = CompletableDeferred<Unit>()
        val client = WsClient(
            httpUrl = url,
            onJson = { CallController.onJson(it) },
            onBinary = { CallController.onBinary(it) },
            onConn = { ok ->
                Bus.connection.value = if (ok) Bus.Conn.CONNECTED else Bus.Conn.DISCONNECTED
                if (!ok) ended.complete(Unit)
            },
            autoRetry = false,
            onInitial = { ok -> initial.complete(ok) },
        )
        ws = client
        CallController.attach(client)
        client.connect()

        val ok = withTimeoutOrNull(10000) { initial.await() } == true
        if (!ok) {
            client.close()
            Bus.connection.value = Bus.Conn.DISCONNECTED
            return false
        }
        ended.await() // mantener hasta que se pierda la conexion
        return true
    }

    private fun ensureCollector() {
        if (collectorJob != null) return
        collectorJob = scope.launch {
            Bus.incoming.collectLatest { call ->
                if (call != null) showCallNotification(call)
                else dismissCallNotification()
            }
        }
    }

    // ---------- notificaciones / foreground ----------

    /** Tipos FGS segun permisos: microphone exige RECORD_AUDIO concedido (Android 14+). */
    private fun fgsTypes(): Int {
        var types = 0
        if (Build.VERSION.SDK_INT >= 29) {
            types = types or android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
        }
        if (Build.VERSION.SDK_INT >= 30 &&
            androidx.core.content.ContextCompat.checkSelfPermission(
                this, android.Manifest.permission.RECORD_AUDIO) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED) {
            types = types or android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        }
        return types
    }

    private fun startAsForeground(text: String) {
        val pi = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notif = NotificationCompat.Builder(this, InterfonApp.CH_SERVICE)
            .setSmallIcon(R.drawable.ic_notif)
            .setContentTitle(getString(R.string.notif_service_title))
            .setContentText(text)
            .setOngoing(true)
            .setContentIntent(pi)
            .build()
        try {
            ServiceCompat.startForeground(this, InterfonApp.NOTIF_SERVICE_ID, notif, fgsTypes())
        } catch (e: Exception) {
            runCatching {
                ServiceCompat.startForeground(
                    this, InterfonApp.NOTIF_SERVICE_ID, notif,
                    if (Build.VERSION.SDK_INT >= 29)
                        android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
                    else 0)
            }
        }
    }

    private fun showCallNotification(call: Bus.IncomingCall) {
        val fullScreen = PendingIntent.getActivity(
            this, 1, Intent(this, IncomingCallActivity::class.java).apply {
                putExtra("call_id", call.callId)
                putExtra("from", call.from)
                putExtra("text", call.text)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val notif = NotificationCompat.Builder(this, InterfonApp.CH_CALLS)
            .setSmallIcon(R.drawable.ic_notif)
            .setContentTitle("Llamada de ${call.from}")
            .setContentText(call.text.ifEmpty { "Telefono interno del agente" })
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setFullScreenIntent(fullScreen, true)
            .setOngoing(true)
            .setAutoCancel(true)
            .build()
        getSystemService(NotificationManager::class.java)
            .notify(InterfonApp.NOTIF_CALL_ID, notif)
    }

    private fun dismissCallNotification() {
        getSystemService(NotificationManager::class.java)
            .cancel(InterfonApp.NOTIF_CALL_ID)
    }

    override fun onDestroy() {
        scope.cancel()
        ws?.close()
        super.onDestroy()
    }

    companion object {
        const val ACTION_STOP = "stop"
        const val ACTION_RECONNECT = "reconnect"
        const val ACTION_DISCOVER = "discover"

        private fun intent(ctx: Context, action: String? = null): Intent =
            Intent(ctx, ConnectionService::class.java).apply { action?.let { setAction(it) } }

        fun start(ctx: Context) {
            ctx.startForegroundService(intent(ctx))
        }

        /** Reconectar con la URL guardada (tras guardar cambios en Ajustes). */
        fun restart(ctx: Context) {
            ctx.startForegroundService(intent(ctx, ACTION_RECONNECT))
        }

        /** Buscar el servidor en la red y conectar al que aparezca. */
        fun discover(ctx: Context) {
            ctx.startForegroundService(intent(ctx, ACTION_DISCOVER))
        }
    }
}
