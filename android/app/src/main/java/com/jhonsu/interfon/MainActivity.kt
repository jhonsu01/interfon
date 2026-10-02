package com.jhonsu.interfon

import android.Manifest
import android.app.Activity
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jhonsu.interfon.ui.InterfonTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(Lang.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            InterfonTheme { AppRoot() }
        }
    }

    override fun onResume() {
        super.onResume()
        Bus.appInForeground.value = true
    }

    override fun onPause() {
        super.onPause()
        Bus.appInForeground.value = false
    }
}

private enum class Screen { HOME, WALKIE, SETTINGS }

private val Red = Color(0xFFEF4444)
private val Amber = Color(0xFFFBBF24)

@Composable
private fun AppRoot() {
    val ctx = LocalContext.current
    val incoming by Bus.incoming.collectAsStateWithLifecycle()
    val callActive by Bus.callActive.collectAsStateWithLifecycle()
    var screen by remember { mutableStateOf(Screen.HOME) }
    // Primer arranque: elegir idioma antes de nada
    var needLanguage by remember { mutableStateOf(Lang.chosen(ctx) == null) }

    // Permisos (mic + notificaciones). Al concederlos se vuelve a llamar al servicio
    // para que promueva el tipo FGS microphone, SIN reiniciar las conexiones.
    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        if (grants.values.any { it }) ConnectionService.start(ctx)
    }
    LaunchedEffect(Unit) { ConnectionService.start(ctx) }
    LaunchedEffect(needLanguage) {
        if (needLanguage) return@LaunchedEffect
        val wanted = buildList {
            add(Manifest.permission.RECORD_AUDIO)
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
        }
        permLauncher.launch(wanted.toTypedArray())
    }

    LaunchedEffect(Unit) {
        Bus.events.collect { Toast.makeText(ctx, it, Toast.LENGTH_SHORT).show() }
    }

    val announce by Bus.announce.collectAsStateWithLifecycle()

    // Atras desde Ajustes o Walkie vuelve a Inicio en lugar de cerrar la app
    BackHandler(enabled = screen != Screen.HOME) { screen = Screen.HOME }

    // Mientras habla el agente (o hay llamada/walkie activos) la pantalla no se apaga
    KeepScreenOn(active = incoming != null || callActive || announce != null ||
        screen == Screen.WALKIE)

    when {
        needLanguage -> LanguageScreen(onPicked = { needLanguage = false })

        incoming != null -> IncomingOverlay(
            call = incoming!!,
            onAccept = { CallController.answer() },
            onDecline = { CallController.decline() })

        callActive -> CallScreen()

        announce != null -> AnnounceScreen(onStop = { CallController.stopAnnouncement() })

        screen == Screen.WALKIE -> WalkieScreen(onBack = { screen = Screen.HOME })

        screen == Screen.SETTINGS -> SettingsScreen(onBack = { screen = Screen.HOME })

        else -> HomeScreen(
            onCall = {
                if (!CallController.startOutgoingCall()) {
                    Bus.events.tryEmit(ctx.getString(R.string.no_connection))
                }
            },
            onWalkie = { screen = Screen.WALKIE },
            onSettings = { screen = Screen.SETTINGS })
    }
}

// ============================================================
// Idioma (primer arranque)
// ============================================================

@Composable
private fun LanguageScreen(onPicked: () -> Unit) {
    val ctx = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(32.dp))
        Text("🌐", fontSize = 48.sp)
        Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.choose_language_title), fontSize = 24.sp,
            fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
        Text(stringResource(R.string.choose_language_sub), fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        Spacer(Modifier.height(24.dp))
        val current = Lang.current(ctx)
        Lang.OPTIONS.forEach { opt ->
            OutlinedButton(
                onClick = {
                    onPicked()
                    Lang.set(ctx as Activity, opt.tag)
                },
                modifier = Modifier.fillMaxWidth().height(54.dp).padding(vertical = 3.dp),
                shape = RoundedCornerShape(14.dp),
            ) {
                Text(opt.label, fontSize = 17.sp, modifier = Modifier.weight(1f))
                if (opt.tag == current) Icon(Icons.Filled.Check, null)
            }
        }
    }
}

// ============================================================
// Home
// ============================================================

@Composable
private fun HomeScreen(onCall: () -> Unit, onWalkie: () -> Unit, onSettings: () -> Unit) {
    val ctx = LocalContext.current
    val conn by Bus.connection.collectAsStateWithLifecycle()
    val discovering by Bus.discovering.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AgentAvatar(44.dp, 20.sp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.app_name), fontSize = 26.sp,
                    fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
                Text(stringResource(R.string.home_subtitle), fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onSettings) {
                Icon(Icons.Filled.Settings, stringResource(R.string.settings),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Spacer(Modifier.height(16.dp))
        ServerPanel()
        Spacer(Modifier.height(20.dp))

        if (conn != Bus.Conn.CONNECTED) {
            TextButton(
                onClick = { ConnectionService.discover(ctx) },
                enabled = !discovering,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(
                    if (discovering) R.string.searching_network else R.string.search_network))
            }
            Spacer(Modifier.height(10.dp))
        }

        Button(
            onClick = onCall,
            modifier = Modifier.fillMaxWidth().height(84.dp),
            shape = RoundedCornerShape(20.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary),
        ) {
            Icon(Icons.Filled.Phone, null, modifier = Modifier.size(30.dp))
            Spacer(Modifier.width(12.dp))
            Text(stringResource(R.string.call_agent), fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }

        Spacer(Modifier.height(14.dp))

        OutlinedButton(
            onClick = onWalkie,
            modifier = Modifier.fillMaxWidth().height(72.dp),
            shape = RoundedCornerShape(20.dp),
        ) {
            Text("🎙  " + stringResource(R.string.walkie), fontSize = 18.sp,
                fontWeight = FontWeight.Medium)
        }

        Spacer(Modifier.height(24.dp))
        Text(stringResource(R.string.conversation), fontSize = 13.sp,
            fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        TranscriptList(filterCtx = null)
    }
}

/**
 * Lista plegable de servidores. Plegada muestra solo el resumen y el contador
 * "conectados/total"; desplegada, cada servidor con su estado.
 */
@Composable
private fun ServerPanel() {
    val ctx = LocalContext.current
    val servers by Bus.servers.collectAsStateWithLifecycle()
    val connMap by Bus.serverConn.collectAsStateWithLifecycle()
    var expanded by remember { mutableStateOf(Prefs.serversExpanded(ctx)) }
    val arrow by animateFloatAsState(if (expanded) 180f else 0f, label = "arrow")

    val connected = servers.count { connMap[it.id] == Bus.Conn.CONNECTED }
    val connecting = servers.any { connMap[it.id] == Bus.Conn.CONNECTING }
    val primaryId = servers.firstOrNull { connMap[it.id] == Bus.Conn.CONNECTED }?.id

    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable {
                    expanded = !expanded
                    Prefs.setServersExpanded(ctx, expanded)
                }
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StatusDot(when {
                connected > 0 -> Bus.Conn.CONNECTED
                connecting -> Bus.Conn.CONNECTING
                else -> Bus.Conn.DISCONNECTED
            })
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.servers_title), fontSize = 15.sp,
                    fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
                Text(
                    if (servers.isEmpty()) stringResource(R.string.no_servers_short)
                    else stringResource(R.string.servers_summary, connected, servers.size),
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            // Contador visible siempre (sobre todo con la lista plegada)
            Box(
                Modifier
                    .background(
                        if (connected > 0) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceVariant,
                        RoundedCornerShape(12.dp))
                    .padding(horizontal = 10.dp, vertical = 3.dp),
            ) {
                Text("$connected/${servers.size}", fontSize = 13.sp, fontWeight = FontWeight.Bold,
                    color = if (connected > 0) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Filled.KeyboardArrowDown,
                stringResource(if (expanded) R.string.collapse else R.string.expand),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 6.dp).rotate(arrow))
        }

        AnimatedVisibility(visible = expanded) {
            Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp)) {
                if (servers.isEmpty()) {
                    Text(stringResource(R.string.no_servers), fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                servers.forEach { s ->
                    HorizontalDivider(color = MaterialTheme.colorScheme.background)
                    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        StatusDot(connMap[s.id] ?: Bus.Conn.DISCONNECTED, 9.dp)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(s.name, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(s.host, fontSize = 11.sp, fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (s.id == primaryId && servers.size > 1) {
                            Text(stringResource(R.string.primary_badge), fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold)
                        } else {
                            Text(connLabel(connMap[s.id]), fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun connLabel(c: Bus.Conn?): String = stringResource(when (c) {
    Bus.Conn.CONNECTED -> R.string.conn_connected
    Bus.Conn.CONNECTING -> R.string.conn_connecting
    else -> R.string.conn_disconnected
})

@Composable
private fun StatusDot(c: Bus.Conn, size: Dp = 12.dp) {
    Box(Modifier.size(size).background(
        when (c) {
            Bus.Conn.CONNECTED -> MaterialTheme.colorScheme.primary
            Bus.Conn.CONNECTING -> Amber
            Bus.Conn.DISCONNECTED -> Red
        }, CircleShape))
}

// ============================================================
// Llamada
// ============================================================

@Composable
private fun CallScreen() {
    val state by Bus.callState.collectAsStateWithLifecycle()
    val playing by Bus.playing.collectAsStateWithLifecycle()
    val speaker by Bus.speaker.collectAsStateWithLifecycle()
    val agentName by Bus.agentName.collectAsStateWithLifecycle()
    val voiceLevel by Bus.voiceLevel.collectAsStateWithLifecycle()
    val micLevel by Bus.micLevel.collectAsStateWithLifecycle()
    val seconds by produceState(0) { while (true) { delay(1000); value++ } }
    val serverName = remember { CallController.callServerName() }
    val multi = Bus.servers.value.size > 1

    val orbMode = when {
        playing -> com.jhonsu.interfon.ui.OrbMode.SPEAKING
        state == "thinking" -> com.jhonsu.interfon.ui.OrbMode.THINKING
        else -> com.jhonsu.interfon.ui.OrbMode.LISTENING
    }
    val orbLevel = if (playing) voiceLevel else micLevel

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(28.dp))
        Box(contentAlignment = Alignment.Center) {
            com.jhonsu.interfon.ui.VoiceOrb(
                mode = orbMode, level = orbLevel,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(190.dp))
            AgentAvatar(96.dp)
        }
        Spacer(Modifier.height(14.dp))
        Text(agentName, fontSize = 26.sp, fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground)
        if (multi && serverName != null) {
            Text(stringResource(R.string.via_server, serverName), fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(
            stringResource(when {
                playing -> R.string.speaking
                state == "thinking" -> R.string.thinking
                state == "speaking" -> R.string.speaking
                else -> R.string.listening
            }),
            fontSize = 15.sp, color = MaterialTheme.colorScheme.primary)
        Text("%02d:%02d".format(seconds / 60, seconds % 60), fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant)

        Spacer(Modifier.height(16.dp))
        Box(Modifier.fillMaxWidth().height(2.dp).background(MaterialTheme.colorScheme.surface))
        Spacer(Modifier.height(8.dp))

        TranscriptList(filterCtx = "call")

        Spacer(Modifier.height(14.dp))
        SpeakerToggle(speaker, 52.dp)
        Spacer(Modifier.height(10.dp))
        Button(
            onClick = { CallController.hangup() },
            modifier = Modifier.fillMaxWidth().height(64.dp),
            shape = RoundedCornerShape(20.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Red),
        ) {
            Icon(Icons.Filled.Phone, null, modifier = Modifier.size(26.dp), tint = Color.White)
            Spacer(Modifier.width(10.dp))
            Text(stringResource(R.string.hang_up), fontSize = 19.sp,
                fontWeight = FontWeight.Bold, color = Color.White)
        }
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun SpeakerToggle(speaker: Boolean, height: Dp) {
    OutlinedButton(
        onClick = { CallController.setSpeaker(!speaker) },
        modifier = Modifier.fillMaxWidth().height(height),
        shape = RoundedCornerShape(16.dp),
    ) {
        Text(if (speaker) "🎧  " + stringResource(R.string.earpiece)
            else "🔊  " + stringResource(R.string.speaker), fontSize = 15.sp)
    }
}

// ============================================================
// Walkie
// ============================================================

@Composable
private fun WalkieScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val busy by Bus.walkieBusy.collectAsStateWithLifecycle()
    val speaker by Bus.speaker.collectAsStateWithLifecycle()
    val playing by Bus.playing.collectAsStateWithLifecycle()
    val voiceLevel by Bus.voiceLevel.collectAsStateWithLifecycle()
    val micLevel by Bus.micLevel.collectAsStateWithLifecycle()
    var pressed by remember { mutableStateOf(false) }

    val orbMode = when {
        playing -> com.jhonsu.interfon.ui.OrbMode.SPEAKING
        busy -> com.jhonsu.interfon.ui.OrbMode.THINKING
        pressed -> com.jhonsu.interfon.ui.OrbMode.LISTENING
        else -> com.jhonsu.interfon.ui.OrbMode.IDLE
    }
    val orbLevel = if (playing) voiceLevel else micLevel

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            TextButton(onClick = onBack) { Text("‹ " + stringResource(R.string.back)) }
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.walkie), fontSize = 22.sp, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground)
        }

        Text(
            stringResource(when {
                pressed -> R.string.walkie_recording
                busy -> R.string.walkie_processing
                else -> R.string.walkie_hold
            }),
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
                        if (!ok) Bus.events.tryEmit(ctx.getString(R.string.no_mic))
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
            com.jhonsu.interfon.ui.VoiceOrb(
                mode = orbMode, level = orbLevel,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(210.dp))
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
        SpeakerToggle(speaker, 48.dp)

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
    val servers by Bus.servers.collectAsStateWithLifecycle()
    val connMap by Bus.serverConn.collectAsStateWithLifecycle()
    val discovering by Bus.discovering.collectAsStateWithLifecycle()
    val hasPhoto by Bus.agentPhoto.collectAsStateWithLifecycle()
    var nameField by remember { mutableStateOf(Bus.agentName.value) }
    // null = cerrado; ServerEntry con id vacio = alta nueva
    var editing by remember { mutableStateOf<ServerEntry?>(null) }
    var langMenu by remember { mutableStateOf(false) }
    val version = remember {
        runCatching {
            ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName
        }.getOrNull() ?: "?"
    }

    val pickerScope = rememberCoroutineScope()
    val photoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            pickerScope.launch(Dispatchers.IO) {
                val (ok, motivo) = Contact.save(ctx, uri)
                if (!ok) Bus.events.tryEmit(ctx.getString(R.string.photo_error, motivo ?: ""))
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("‹ " + stringResource(R.string.back)) }
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.settings), fontSize = 22.sp, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground)
        }

        // ---------- Servidores ----------
        Spacer(Modifier.height(16.dp))
        SectionLabel(stringResource(R.string.servers_title))
        Text(stringResource(R.string.servers_help), fontSize = 12.sp, lineHeight = 17.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))

        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(vertical = 4.dp)) {
                if (servers.isEmpty()) {
                    Text(stringResource(R.string.no_servers), fontSize = 13.sp,
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                servers.forEachIndexed { i, s ->
                    if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.background)
                    Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 4.dp, top = 6.dp,
                        bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        StatusDot(connMap[s.id] ?: Bus.Conn.DISCONNECTED, 9.dp)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text("${i + 1}. ${s.name}", fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(s.url, fontSize = 11.sp, fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        if (i > 0) {
                            IconButton(onClick = {
                                Servers.move(ctx, s.id, -1)
                            }) { Icon(Icons.Filled.KeyboardArrowUp, stringResource(R.string.move_up)) }
                        }
                        IconButton(onClick = { editing = s }) {
                            Icon(Icons.Filled.Edit, stringResource(R.string.edit))
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(
                onClick = { editing = ServerEntry("", "", "") },
                modifier = Modifier.weight(1f).height(50.dp),
            ) {
                Icon(Icons.Filled.Add, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.add_server), maxLines = 1)
            }
            OutlinedButton(
                onClick = { ConnectionService.discover(ctx) },
                enabled = !discovering,
                modifier = Modifier.weight(1f).height(50.dp),
            ) {
                Text(stringResource(if (discovering) R.string.searching_short
                    else R.string.search_short), maxLines = 1)
            }
        }

        // ---------- Idioma ----------
        Spacer(Modifier.height(28.dp))
        SectionLabel(stringResource(R.string.language))
        Box {
            val current = Lang.current(ctx)
            OutlinedButton(
                onClick = { langMenu = true },
                modifier = Modifier.fillMaxWidth().height(50.dp),
            ) {
                Text("🌐  " + (Lang.OPTIONS.firstOrNull { it.tag == current }?.label ?: current),
                    modifier = Modifier.weight(1f))
                Icon(Icons.Filled.KeyboardArrowDown, null)
            }
            DropdownMenu(expanded = langMenu, onDismissRequest = { langMenu = false }) {
                Lang.OPTIONS.forEach { opt ->
                    DropdownMenuItem(
                        text = { Text(opt.label) },
                        trailingIcon = { if (opt.tag == current) Icon(Icons.Filled.Check, null) },
                        onClick = {
                            langMenu = false
                            if (opt.tag != current) Lang.set(ctx as Activity, opt.tag)
                        })
                }
            }
        }

        // ---------- Contacto ----------
        Spacer(Modifier.height(28.dp))
        SectionLabel(stringResource(R.string.agent_contact))
        Row(verticalAlignment = Alignment.CenterVertically) {
            AgentAvatar(72.dp, 30.sp)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                OutlinedTextField(
                    value = nameField,
                    onValueChange = {
                        nameField = it
                        Prefs.setAgentName(ctx, it)
                        Bus.agentName.value = Prefs.agentName(ctx)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text(stringResource(R.string.name)) },
                )
                Spacer(Modifier.height(6.dp))
                Row {
                    TextButton(onClick = { photoPicker.launch("image/*") }) {
                        Text("📷 " + stringResource(
                            if (hasPhoto != null) R.string.change_photo else R.string.choose_photo))
                    }
                    if (hasPhoto != null) {
                        TextButton(onClick = { Contact.clear(ctx) }) {
                            Text(stringResource(R.string.remove))
                        }
                    }
                }
            }
        }
        if (hasPhoto == null) {
            Text(stringResource(R.string.photo_hint),
                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Spacer(Modifier.height(24.dp))
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("Interfon v$version", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Spacer(Modifier.height(6.dp))
                Text(
                    stringResource(R.string.about_text) + "\n\n" +
                        stringResource(R.string.servers_summary,
                            Bus.connectedIds().size, servers.size) + " · " + connLabel(conn),
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 19.sp,
                )
            }
        }
    }

    editing?.let { entry ->
        ServerDialog(entry = entry, onDismiss = { editing = null })
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, fontSize = 13.sp, fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 8.dp))
}

/** Alta / edicion de un servidor (nombre personalizado + URL). */
@Composable
private fun ServerDialog(entry: ServerEntry, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val isNew = entry.id.isEmpty()
    var name by remember { mutableStateOf(entry.name) }
    var url by remember { mutableStateOf(entry.url) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (isNew) R.string.add_server else R.string.edit_server)) },
        text = {
            Column {
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text(stringResource(R.string.name)) },
                    placeholder = { Text(stringResource(R.string.server_name_hint)) },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = url, onValueChange = { url = it; error = null },
                    label = { Text("URL / IP") },
                    placeholder = { Text("192.168.1.50:8765") },
                    singleLine = true, isError = error != null,
                    modifier = Modifier.fillMaxWidth())
                error?.let {
                    Text(it, color = Red, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                }
                if (!isNew) {
                    Spacer(Modifier.height(10.dp))
                    TextButton(
                        onClick = {
                            Servers.remove(ctx, entry.id)
                            ConnectionService.start(ctx)
                            onDismiss()
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = Red),
                    ) {
                        Icon(Icons.Filled.Delete, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.delete))
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val r = if (isNew) Servers.add(ctx, name, url)
                        else Servers.update(ctx, entry.id, name, url)
                when (r) {
                    Servers.Result.OK -> {
                        ConnectionService.start(ctx)   // sincroniza altas y cambios de URL
                        onDismiss()
                    }
                    Servers.Result.DUPLICATE -> error = ctx.getString(R.string.server_duplicate)
                    Servers.Result.INVALID -> error = ctx.getString(R.string.server_invalid)
                }
            }) { Text(stringResource(R.string.save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

// ============================================================
// Entrante (overlay dentro de la app)
// ============================================================

@Composable
private fun IncomingOverlay(call: Bus.IncomingCall, onAccept: () -> Unit, onDecline: () -> Unit) {
    val agentName by Bus.agentName.collectAsStateWithLifecycle()
    val serverName = Bus.serverName(call.serverId)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        AgentAvatar(100.dp, 40.sp)
        Spacer(Modifier.height(18.dp))
        Text(stringResource(R.string.call_from, agentName), fontSize = 24.sp,
            fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center)
        if (serverName != null) {
            Text(stringResource(R.string.via_server, serverName), fontSize = 13.sp,
                color = MaterialTheme.colorScheme.primary)
        }
        if (call.text.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(call.text, fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }
        Spacer(Modifier.height(36.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Button(onClick = onDecline, modifier = Modifier.height(60.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Red)) {
                Text(stringResource(R.string.decline), color = Color.White)
            }
            Button(onClick = onAccept, modifier = Modifier.height(60.dp)) {
                Text(stringResource(R.string.answer), fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ============================================================
// Utilidades de UI
// ============================================================

/** Evita que la pantalla entre en reposo mientras active sea true. */
@Composable
private fun KeepScreenOn(active: Boolean) {
    val view = androidx.compose.ui.platform.LocalView.current
    LaunchedEffect(active) { view.keepScreenOn = active }
}

// ============================================================
// Pantalla de anuncio (audio push con efecto de llamada entrante)
// ============================================================

@Composable
fun AnnounceScreen(onStop: () -> Unit) {
    val text by Bus.announce.collectAsStateWithLifecycle()
    val agentName by Bus.agentName.collectAsStateWithLifecycle()
    val voiceLevel by Bus.voiceLevel.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(contentAlignment = Alignment.Center) {
            com.jhonsu.interfon.ui.VoiceOrb(
                mode = com.jhonsu.interfon.ui.OrbMode.SPEAKING,
                level = voiceLevel,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(200.dp))
            AgentAvatar(104.dp)
        }
        Spacer(Modifier.height(18.dp))
        Text(stringResource(R.string.audio_from, agentName), fontSize = 24.sp,
            fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center)
        Spacer(Modifier.height(10.dp))
        Text(text ?: "", fontSize = 15.sp, textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.heightIn(max = 260.dp).verticalScroll(rememberScrollState()))
        Spacer(Modifier.height(30.dp))
        OutlinedButton(
            onClick = onStop,
            modifier = Modifier.fillMaxWidth().height(54.dp),
            shape = RoundedCornerShape(16.dp),
        ) { Text("⏹  " + stringResource(R.string.stop), fontSize = 16.sp) }
    }
}

// ============================================================
// Avatar del contacto (foto o inicial)
// ============================================================

@Composable
fun AgentAvatar(size: Dp, fontSize: TextUnit = 38.sp) {
    val name by Bus.agentName.collectAsStateWithLifecycle()
    val photo by Bus.agentPhoto.collectAsStateWithLifecycle()
    Box(
        Modifier
            .size(size)
            .background(MaterialTheme.colorScheme.primary, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        val bmp = photo
        if (bmp != null) {
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = name,
                modifier = Modifier.size(size).clip(CircleShape),
                contentScale = ContentScale.Crop,
            )
        } else {
            Text(name.take(1).uppercase(), fontSize = fontSize, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimary)
        }
    }
}

// ============================================================
// Transcripcion compartida
// ============================================================

@Composable
private fun ColumnScope.TranscriptList(filterCtx: String?) {
    val lines by Bus.lines.collectAsStateWithLifecycle()
    val agentName by Bus.agentName.collectAsStateWithLifecycle()
    val you = stringResource(R.string.you)
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
                    if (line.role == "user") you else agentName,
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
