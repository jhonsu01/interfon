package com.jhonsu.interfon

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import com.jhonsu.interfon.ui.InterfonTheme

/**
 * Pantalla de anuncio entrante (audio push del agente): se muestra sobre el
 * bloqueo como una llamada, con el orbe latiendo al ritmo de la voz real.
 */
class AnnounceActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }
        // Mientras suena el anuncio, la pantalla no entra en reposo
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent {
            InterfonTheme {
                AnnounceScreen(onStop = { CallController.stopAnnouncement() })
                // El anuncio se cierra solo cuando termina el audio
                LaunchedEffect(Unit) {
                    Bus.announce.collect { if (it == null) finish() }
                }
            }
        }
    }

    companion object {
        fun intent(ctx: Context): Intent =
            Intent(ctx, AnnounceActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
    }
}
