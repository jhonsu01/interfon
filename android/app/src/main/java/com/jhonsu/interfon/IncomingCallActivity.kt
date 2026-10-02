package com.jhonsu.interfon

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.jhonsu.interfon.ui.InterfonTheme

/** Pantalla de llamada entrante: suena, vibra y brilla sobre el bloqueo. */
class IncomingCallActivity : ComponentActivity() {

    private var player: MediaPlayer? = null
    private var vibrator: Vibrator? = null

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(Lang.wrap(newBase))
    }

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
        // Mientras timbra, la pantalla no entra en reposo
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val callId = intent.getStringExtra("call_id") ?: ""
        val from = intent.getStringExtra("from") ?: getString(R.string.agent_default)
        val text = intent.getStringExtra("text") ?: ""
        val serverName = Bus.serverName(Bus.incoming.value?.serverId)

        startRing()

        setContent {
            InterfonTheme {
                IncomingScreen(
                    from = from,
                    text = text,
                    serverName = serverName,
                    onAccept = {
                        stopRing()
                        CallController.answer()
                        startActivity(Intent(this, MainActivity::class.java))
                        finish()
                    },
                    onDecline = {
                        stopRing()
                        CallController.decline()
                        finish()
                    },
                )
            }
        }
        if (callId.isEmpty()) finish()
    }

    private fun startRing() {
        runCatching {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            player = MediaPlayer().apply {
                setDataSource(this@IncomingCallActivity, uri)
                setAudioAttributes(AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build())
                isLooping = true
                prepare()
                start()
            }
        }
        vibrator = ContextCompat.getSystemService(this, Vibrator::class.java)
        val pattern = longArrayOf(0, 600, 400)
        vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
    }

    private fun stopRing() {
        runCatching { player?.stop(); player?.release() }
        player = null
        vibrator?.cancel()
    }

    override fun onDestroy() {
        stopRing()
        super.onDestroy()
    }

    companion object {
        fun intent(ctx: Context, callId: String, from: String, text: String): Intent =
            Intent(ctx, IncomingCallActivity::class.java)
                .putExtra("call_id", callId)
                .putExtra("from", from)
                .putExtra("text", text)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}

@Composable
private fun IncomingScreen(from: String, text: String, serverName: String?,
                           onAccept: () -> Unit, onDecline: () -> Unit) {
    val agentName = Bus.agentName.value
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        AgentAvatar(110.dp, 44.sp)
        Spacer(Modifier.height(20.dp))
        Text(stringResource(R.string.incoming_call), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
        Spacer(Modifier.height(6.dp))
        Text(agentName, fontSize = 30.sp, fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground)
        if (serverName != null) {
            Text(stringResource(R.string.via_server, serverName),
                color = MaterialTheme.colorScheme.primary, fontSize = 13.sp)
        }
        if (text.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 16.sp)
        }
        Spacer(Modifier.height(48.dp))
        Button(
            onClick = onAccept,
            modifier = Modifier.fillMaxWidth().height(64.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
        ) {
            Text(stringResource(R.string.answer), fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(12.dp))
        TextButton(
            onClick = onDecline,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0x33EF4444),
                contentColor = Color(0xFFEF4444)),
        ) {
            Text(stringResource(R.string.decline), fontSize = 18.sp)
        }
    }
}
