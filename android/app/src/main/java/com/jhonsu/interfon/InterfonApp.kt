package com.jhonsu.interfon

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context

class InterfonApp : Application() {

    override fun onCreate() {
        super.onCreate()
        appContext = this
        Bus.speaker.value = Prefs.speaker(this)
        Bus.agentName.value = Prefs.agentName(this)
        Bus.agentPhoto.value = Contact.load(this)
        Bus.servers.value = Servers.load(this)
        Lang.syncSystem(this)
        createChannels(Lang.localized(this))
    }

    companion object {
        /** Crea (o renombra, al cambiar de idioma) los canales de notificacion. */
        fun createChannels(ctx: Context) {
            val nm = ctx.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(
                NotificationChannel(CH_SERVICE, ctx.getString(R.string.channel_service),
                    NotificationManager.IMPORTANCE_MIN).apply { setShowBadge(false) })
            nm.createNotificationChannel(
                NotificationChannel(CH_CALLS, ctx.getString(R.string.channel_calls),
                    NotificationManager.IMPORTANCE_HIGH).apply {
                    description = ctx.getString(R.string.channel_calls_desc)
                })
            nm.createNotificationChannel(
                NotificationChannel(CH_MESSAGES, ctx.getString(R.string.channel_messages),
                    NotificationManager.IMPORTANCE_DEFAULT))
        }

        const val CH_SERVICE = "interfon_service"
        const val CH_CALLS = "interfon_calls"
        const val CH_MESSAGES = "interfon_messages"
        const val NOTIF_SERVICE_ID = 1
        const val NOTIF_CALL_ID = 2
        const val NOTIF_MESSAGE_ID = 3

        lateinit var appContext: android.content.Context
            private set
    }
}
