package com.jhonsu.interfon

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build

/**
 * Enrutamiento de audio de llamadas Interfon:
 * auricular (como una llamada telefonica normal) o altavoz.
 */
object CallAudio {

    /** Aplica la ruta de salida para audio de comunicacion. */
    fun apply(ctx: Context, speaker: Boolean) {
        val am = ctx.getSystemService(AudioManager::class.java) ?: return
        if (Build.VERSION.SDK_INT >= 31) {
            val wanted = if (speaker) AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
                         else AudioDeviceInfo.TYPE_BUILTIN_EARPIECE
            val dev = am.availableCommunicationDevices.firstOrNull { it.type == wanted }
                // dispositivos sin auricular (tablets): fallback a altavoz
                ?: am.availableCommunicationDevices.firstOrNull {
                    it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
            if (dev != null) am.setCommunicationDevice(dev)
        } else {
            @Suppress("DEPRECATION")
            am.isSpeakerphoneOn = speaker
        }
    }

    /** Entra/sale del modo comunicacion (volumen y AEC de llamada). */
    fun setInCall(ctx: Context, inCall: Boolean) {
        val am = ctx.getSystemService(AudioManager::class.java) ?: return
        am.mode = if (inCall) AudioManager.MODE_IN_COMMUNICATION
                  else AudioManager.MODE_NORMAL
    }
}
