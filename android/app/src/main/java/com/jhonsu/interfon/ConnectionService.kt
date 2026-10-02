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
import java.util.concurrent.ConcurrentHashMap

/**
 * Servicio en primer plano: mantiene la conexion con TODOS los servidores Interfon.
 *
 * Cada servidor tiene su propio ciclo de conexion (independiente: si uno cae, los
 * demas siguen). Si ninguno esta conectado, escanea la red local
 * (ServerDiscovery) y agrega a la lista los servidores nuevos que encuentre.
 */
class ConnectionService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Ciclo activo por servidor (id -> URL con la que se lanzo y su Job). */
    private val loops = HashMap<String, Pair<String, Job>>()
    private val clients = ConcurrentHashMap<String, WsClient>()
    private var supervisorJob: Job? = null
    private var collectorJob: Job? = null

    @Volatile private var lastDiscoveryAt = 0L

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(Lang.wrap(base))
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        startAsForeground(getString(R.string.notif_service_text))
        ensureCollector()
        when (intent?.action) {
            ACTION_DISCOVER -> {
                syncLoops(restartAll = false)
                scope.launch { tryDiscover(manual = true) }
            }
            ACTION_RECONNECT -> syncLoops(restartAll = true)
            else -> syncLoops(restartAll = false)
        }
        if (supervisorJob == null) supervisorJob = scope.launch { supervisor() }
        return START_STICKY
    }

    // ---------- ciclos de conexion ----------

    /** Alinea los ciclos activos con la lista guardada (altas, bajas y cambios de URL). */
    @Synchronized
    private fun syncLoops(restartAll: Boolean) {
        val list = Servers.load(this)
        Bus.servers.value = list
        val ids = list.map { it.id }.toSet()
        for (id in loops.keys.toList()) {
            val url = loops.getValue(id).first
            val entry = list.firstOrNull { it.id == id }
            if (entry == null || entry.url != url || restartAll) {
                loops.remove(id)?.second?.cancel()
                clients.remove(id)?.close()
                if (entry == null) Bus.setServerConn(id, null)
            }
        }
        Bus.serverConn.value.keys.filterNot { it in ids }.forEach { Bus.setServerConn(it, null) }
        for (entry in list) {
            if (entry.id !in loops) {
                loops[entry.id] = entry.url to scope.launch { serverLoop(entry) }
            }
        }
    }

    private suspend fun serverLoop(entry: ServerEntry) {
        var failures = 0
        // La cancelacion del Job corta el bucle en delay()/await()
        while (true) {
            val connected = connectOnce(entry)
            failures = if (connected) 0 else failures + 1
            delay(when {
                connected -> 400
                failures > 6 -> 8000
                else -> 2500
            })
        }
    }

    /** Si no hay ningun servidor conectado, busca en la red (como maximo cada 20 s). */
    private suspend fun supervisor() {
        while (true) {
            delay(5000)
            val none = Bus.connectedIds().isEmpty()
            if (none && Prefs.autoDiscovery(this) &&
                System.currentTimeMillis() - lastDiscoveryAt > 20000) {
                tryDiscover(manual = false)
            }
        }
    }

    /** Escanea la red y agrega los servidores nuevos a la lista. */
    private suspend fun tryDiscover(manual: Boolean) {
        if (Bus.discovering.value) return
        lastDiscoveryAt = System.currentTimeMillis()
        Bus.discovering.value = true
        try {
            val found = ServerDiscovery.discoverAll()
            val added = Servers.addDiscovered(this, found)
            if (added > 0) {
                syncLoops(restartAll = false)
                Bus.events.tryEmit(Lang.str(R.string.servers_found, added))
            } else if (manual) {
                Bus.events.tryEmit(Lang.str(R.string.no_servers_found))
            }
        } finally {
            Bus.discovering.value = false
        }
    }

    /** Conecta a un servidor y suspende hasta que la conexion caiga. */
    private suspend fun connectOnce(entry: ServerEntry): Boolean {
        Bus.setServerConn(entry.id, Bus.Conn.CONNECTING)
        val initial = CompletableDeferred<Boolean>()
        val ended = CompletableDeferred<Unit>()
        // Tras descartar este cliente, sus callbacks tardios no deben pisar el estado
        // ni los mensajes de la conexion que lo reemplace.
        var alive = true
        val client = WsClient(
            httpUrl = entry.url,
            onJson = { if (alive) CallController.onJson(entry.id, it) },
            onBinary = { if (alive) CallController.onBinary(entry.id, it) },
            onConn = { ok ->
                if (alive) {
                    Bus.setServerConn(entry.id,
                        if (ok) Bus.Conn.CONNECTED else Bus.Conn.DISCONNECTED)
                }
                if (!ok) ended.complete(Unit)
            },
            autoRetry = false,
            onInitial = { ok -> initial.complete(ok) },
        )
        clients.put(entry.id, client)?.close()
        CallController.attach(entry.id, client)
        try {
            client.connect()
            val ok = withTimeoutOrNull(10000) { initial.await() } == true
            if (!ok) {
                client.close()
                Bus.setServerConn(entry.id, Bus.Conn.DISCONNECTED)
                return false
            }
            ended.await() // mantener hasta que se pierda la conexion
            return true
        } finally {
            alive = false
            client.close()
            CallController.detach(entry.id, client)
            clients.remove(entry.id, client)
        }
    }

    private fun ensureCollector() {
        if (collectorJob != null) return
        collectorJob = scope.launch {
            Bus.incoming.collectLatest { call ->
                if (call != null) showCallNotification(call)
                else dismissCallNotification()
            }
        }
        scope.launch {
            Bus.announce.collectLatest { ann ->
                if (ann == null) {
                    getSystemService(NotificationManager::class.java)
                        .cancel(InterfonApp.NOTIF_MESSAGE_ID)
                }
            }
        }
        // Texto de la notificacion fija: cuantos servidores hay conectados
        scope.launch {
            Bus.serverConn.collectLatest { conn ->
                val total = Bus.servers.value.size
                val ok = conn.values.count { it == Bus.Conn.CONNECTED }
                val loc = Lang.localized(this@ConnectionService)
                startAsForeground(
                    if (ok > 0) loc.getString(R.string.notif_service_connected, ok, total)
                    else loc.getString(R.string.notif_service_text))
            }
        }
        CallController.setPushMessageHandler { _ ->
            if (!Bus.appInForeground.value) showAnnounceNotification()
        }
    }

    /** Audio push: efecto de llamada entrante (pantalla completa si esta bloqueado). */
    private fun showAnnounceNotification() {
        val fullScreen = PendingIntent.getActivity(
            this, 3, AnnounceActivity.intent(this),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notif = NotificationCompat.Builder(this, InterfonApp.CH_MESSAGES)
            .setSmallIcon(R.drawable.ic_notif)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.notif_announce_text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setFullScreenIntent(fullScreen, true)
            .setContentIntent(fullScreen)
            .setOngoing(true)
            .build()
        getSystemService(NotificationManager::class.java)
            .notify(InterfonApp.NOTIF_MESSAGE_ID, notif)
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
            .setContentTitle(getString(R.string.call_from, call.from))
            .setContentText(call.text.ifEmpty {
                Bus.serverName(call.serverId) ?: getString(R.string.home_subtitle) })
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
        clients.values.forEach { it.close() }
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

        /** Reconectar con la lista guardada (tras cambios en Ajustes). */
        fun restart(ctx: Context) {
            ctx.startForegroundService(intent(ctx, ACTION_RECONNECT))
        }

        /** Buscar servidores en la red y agregar los nuevos. */
        fun discover(ctx: Context) {
            ctx.startForegroundService(intent(ctx, ACTION_DISCOVER))
        }
    }
}
