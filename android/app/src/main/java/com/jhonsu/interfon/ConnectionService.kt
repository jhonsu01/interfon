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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/** Servicio en primer plano: mantiene el WebSocket vivo para recibir llamadas. */
class ConnectionService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var ws: WsClient? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        startAsForeground(getString(R.string.notif_service_text))
        if (ws == null) connect()
        return START_STICKY
    }

    private fun connect() {
        Bus.serverUrl.value = Prefs.url(this)
        ws = WsClient(
            httpUrl = Prefs.url(this),
            onJson = { CallController.onJson(it) },
            onBinary = { CallController.onBinary(it) },
            onConn = { ok ->
                Bus.connection.value = if (ok) Bus.Conn.CONNECTED else Bus.Conn.DISCONNECTED
            },
        ).also { client ->
            CallController.attach(client)
            client.connect()
        }

        scope.launch {
            Bus.incoming.collectLatest { call ->
                if (call != null) showCallNotification(call)
                else dismissCallNotification()
            }
        }
    }

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
            // Ultimo recurso: iniciar solo como connectedDevice (nunca microfono sin permiso)
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

        fun start(ctx: Context) {
            ctx.startForegroundService(Intent(ctx, ConnectionService::class.java))
        }
    }
}
