package com.jhonsu.interfon

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jhonsu.interfon.ui.InterfonTheme
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            InterfonTheme { AppRoot() }
        }
    }
}

private enum class Screen { HOME, WALKIE, SETTINGS }

@Composable
private fun AppRoot() {
    val ctx = LocalContext.current
    val incoming by Bus.incoming.collectAsStateWithLifecycle()
    val callActive by Bus.callActive.collectAsStateWithLifecycle()
    var screen by remember { mutableStateOf(Screen.HOME) }

    // Permisos al arrancar (mic + notificaciones); al concederlos se reinicia el
    // servicio para que promote el tipo FGS microphone.
    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        if (grants.values.any { it }) ConnectionService.start(ctx)
    }
    LaunchedEffect(Unit) {
        ConnectionService.start(ctx)
        val wanted = buildList {
            add(Manifest.permission.RECORD_AUDIO)
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
        }
        permLauncher.launch(wanted.toTypedArray())
    }

    LaunchedEffect(Unit) {
        Bus.events.collect { Toast.makeText(ctx, it, Toast.LENGTH_SHORT).show() }
    }

    when {
        incoming != null -> IncomingOverlay(
            call = incoming!!,
            onAccept = { CallController.answer() },
            onDecline = { CallController.decline() })

        callActive -> CallScreen()

        screen == Screen.WALKIE -> WalkieScreen(onBack = { screen = Screen.HOME })

        screen == Screen.SETTINGS -> SettingsScreen(onBack = { screen = Screen.HOME })

        else -> HomeScreen(
            onCall = { if (!CallController.startOutgoingCall()) {
                Bus.events.tryEmit("Sin conexion con el servidor")
            } },
            onWalkie = { screen = Screen.WALKIE },
            onSettings = { screen = Screen.SETTINGS })
    }
}

// ============================================================
// Home
// ============================================================

@Composable
private fun HomeScreen(onCall: () -> Unit, onWalkie: () -> Unit, onSettings: () -> Unit) {
    val conn by Bus.connection.collectAsStateWithLifecycle()
    val url by Bus.serverUrl.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Interfon", fontSize = 26.sp, fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground)
                Text("Telefono interno del agente", fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onSettings) {
                Icon(Icons.Filled.Settings, "Ajustes",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Spacer(Modifier.height(16.dp))

        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(12.dp).background(
                    when (conn) {
                        Bus.Conn.CONNECTED -> MaterialTheme.colorScheme.primary
                        Bus.Conn.CONNECTING -> Color(0xFFFBBF24)
                        Bus.Conn.DISCONNECTED -> Color(0xFFEF4444)
                    }, CircleShape))
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        when (conn) {
                            Bus.Conn.CONNECTED -> "Conectado al servidor"
                            Bus.Conn.CONNECTING -> "Conectando…"
                            Bus.Conn.DISCONNECTED -> "Sin conexion"
                        },
                        fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface)
                    Text(url, fontSize = 12.sp, fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        Spacer(Modifier.height(28.dp))

        Button(
            onClick = onCall,
            modifier = Modifier.fillMaxWidth().height(84.dp),
            shape = RoundedCornerShape(20.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary),
        ) {
            Icon(Icons.Filled.Phone, null, modifier = Modifier.size(30.dp))
            Spacer(Modifier.width(12.dp))
            Text("Llamar al agente", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }

        Spacer(Modifier.height(14.dp))

        OutlinedButton(
            onClick = onWalkie,
            modifier = Modifier.fillMaxWidth().height(72.dp),
            shape = RoundedCornerShape(20.dp),
        ) {
            Text("🎙  Walkie-Talkie", fontSize = 18.sp, fontWeight = FontWeight.Medium)
        }

        Spacer(Modifier.height(24.dp))
        Text("Conversacion", fontSize = 13.sp, fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        TranscriptList(filterCtx = null)
    }
}

// ============================================================
// Llamada
// ============================================================

@Composable
private fun CallScreen() {
    val state by Bus.callState.collectAsStateWithLifecycle()
    val playing by Bus.playing.collectAsStateWithLifecycle()
    val seconds by produceState(0) { while (true) { delay(1000); value++ } }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(28.dp))
        Box(Modifier.size(96.dp).background(MaterialTheme.colorScheme.primary, CircleShape),
            contentAlignment = Alignment.Center) {
            Text("Z", fontSize = 38.sp, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimary)
        }
        Spacer(Modifier.height(14.dp))
        Text("ZCode", fontSize = 26.sp, fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground)
        Text(
            when {
                playing -> "Hablando…"
                state == "thinking" -> "Pensando…"
                state == "speaking" -> "Hablando…"
                else -> "Escuchando…"
            },
            fontSize = 15.sp, color = MaterialTheme.colorScheme.primary)
        Text("%02d:%02d".format(seconds / 60, seconds % 60), fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant)

        Spacer(Modifier.height(16.dp))
        Box(Modifier.fillMaxWidth().height(2.dp).background(MaterialTheme.colorScheme.surface))
        Spacer(Modifier.height(8.dp))

        TranscriptList(filterCtx = "call")

        Spacer(Modifier.height(14.dp))
        Button(
            onClick = { CallController.hangup() },
            modifier = Modifier.fillMaxWidth().height(64.dp),
            shape = RoundedCornerShape(20.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
        ) {
            Icon(Icons.Filled.Phone, null, modifier = Modifier.size(26.dp),
                tint = Color.White)
            Spacer(Modifier.width(10.dp))
            Text("Colgar", fontSize = 19.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }
        Spacer(Modifier.height(20.dp))
    }
}

// ============================================================
// Walkie
// ============================================================

@Composable
private fun WalkieScreen(onBack: () -> Unit) {
    val busy by Bus.walkieBusy.collectAsStateWithLifecycle()
    var pressed by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            TextButton(onClick = onBack) { Text("‹ Volver") }
            Spacer(Modifier.width(8.dp))
            Text("Walkie-Talkie", fontSize = 22.sp, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground)
        }

        Text(
            if (pressed) "Grabando… suelta para enviar"
            else if (busy) "Procesando…"
            else "Mantén presionado para hablar",
            fontSize = 15.sp,
            color = if (pressed) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 10.dp),
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(10.dp))
        Box(
            modifier = Modifier
                .size(210.dp)
                .background(Color.Transparent, CircleShape)
                .pointerInput(Unit) {
                    detectTapGestures(onPress = {
                        pressed = true
                        val ok = CallController.startWalkie()
                        if (!ok) Bus.events.tryEmit("Sin permiso de microfono")
                        try {
                            awaitRelease()
                        } finally {
                            pressed = false
                            CallController.stopWalkieAndSend()
                        }
                    })
                },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .size(if (pressed) 210.dp else 190.dp)
                    .background(
                        if (pressed) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surface,
                        CircleShape),
            )
            Text(
                "🎙",
                fontSize = 64.sp,
                color = if (pressed) MaterialTheme.colorScheme.onPrimary
                else MaterialTheme.colorScheme.primary,
            )
        }

        Spacer(Modifier.height(18.dp))
        if (busy) CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)

        Spacer(Modifier.height(12.dp))
        TranscriptList(filterCtx = "walkie")
    }
}

// ============================================================
// Ajustes
// ============================================================

@Composable
private fun SettingsScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val conn by Bus.connection.collectAsStateWithLifecycle()
    var url by remember { mutableStateOf(Bus.serverUrl.value) }
    val version = remember {
        runCatching {
            ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName
        }.getOrNull() ?: "?"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("‹ Volver") }
            Spacer(Modifier.width(8.dp))
            Text("Ajustes", fontSize = 22.sp, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground)
        }

        Spacer(Modifier.height(16.dp))
        Text("Servidor (PC con el agente)", fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = url,
            onValueChange = { url = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text("http://192.168.1.50:8765") },
        )
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = {
                Prefs.setUrl(ctx, url)
                Bus.events.tryEmit("Guardado. Reconectando…")
                ConnectionService.start(ctx) // el servicio ya corre; re-arrancar reconecta
            },
            modifier = Modifier.fillMaxWidth().height(54.dp),
        ) { Text("Guardar y reconectar") }

        Spacer(Modifier.height(24.dp))
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("Interfon v$version", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Spacer(Modifier.height(6.dp))
                Text(
                    "Telefono interno para hablar con el agente de tu PC.\n\n" +
                        "1. Arranca el servidor en el PC (server/serve.py).\n" +
                        "2. Esta app se conecta por WiFi a la IP del PC.\n" +
                        "3. El agente puede llamarte o enviarte audios; tu puedes llamarlo o usar el walkie.\n\n" +
                        "Estado actual: " + conn.name,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 19.sp,
                )
            }
        }
    }
}

// ============================================================
// Entrante (overlay dentro de la app)
// ============================================================

@Composable
private fun IncomingOverlay(call: Bus.IncomingCall, onAccept: () -> Unit, onDecline: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(Modifier.size(100.dp).background(MaterialTheme.colorScheme.primary, CircleShape),
            contentAlignment = Alignment.Center) {
            Text(call.from.take(1).uppercase(), fontSize = 40.sp, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimary)
        }
        Spacer(Modifier.height(18.dp))
        Text("Llamada de ${call.from}", fontSize = 24.sp, fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground)
        if (call.text.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(call.text, fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }
        Spacer(Modifier.height(36.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Button(onClick = onDecline, modifier = Modifier.height(60.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))) {
                Text("Rechazar", color = Color.White)
            }
            Button(onClick = onAccept, modifier = Modifier.height(60.dp)) {
                Text("Responder", fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ============================================================
// Transcripcion compartida
// ============================================================

@Composable
private fun ColumnScope.TranscriptList(filterCtx: String?) {
    val lines by Bus.lines.collectAsStateWithLifecycle()
    val list = remember(lines, filterCtx) {
        if (filterCtx == null) lines else lines.filter { it.ctx == filterCtx }
    }
    val listState = rememberLazyListState()
    LaunchedEffect(list.size) {
        if (list.isNotEmpty()) listState.animateScrollToItem(list.lastIndex)
    }
    LazyColumn(state = listState, modifier = Modifier.fillMaxWidth().weight(1f)) {
        items(list, key = { it.ts.toString() + it.text.hashCode() }) { line ->
            Column(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                Text(
                    if (line.role == "user") "Tú" else "ZCode",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (line.role == "user") MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.primary,
                )
                Text(line.text, fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(start = 4.dp))
            }
        }
    }
}
