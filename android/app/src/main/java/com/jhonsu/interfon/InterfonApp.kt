package com.jhonsu.interfon

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager

class InterfonApp : Application() {

    override fun onCreate() {
        super.onCreate()
        appContext = this
        Bus.speaker.value = Prefs.speaker(this)
        Bus.agentName.value = Prefs.agentName(this)
        Bus.agentPhoto.value = Contact.load(this)
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CH_SERVICE, getString(R.string.channel_service),
                NotificationManager.IMPORTANCE_MIN).apply { setShowBadge(false) })
        nm.createNotificationChannel(
            NotificationChannel(CH_CALLS, getString(R.string.channel_calls),
                NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Llamadas entrantes del agente"
            })
        nm.createNotificationChannel(
            NotificationChannel(CH_MESSAGES, getString(R.string.channel_messages),
                NotificationManager.IMPORTANCE_DEFAULT))
    }

    companion object {
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
