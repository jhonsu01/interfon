package com.jhonsu.interfon.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Green = Color(0xFF4ADE80)
private val GreenSoft = Color(0xFF86EFAC)
private val Bg = Color(0xFF0B0F14)
private val Surface = Color(0xFF121821)
private val SurfaceHi = Color(0xFF1A2230)
private val Danger = Color(0xFFEF4444)

private val DarkScheme = darkColorScheme(
    primary = Green,
    onPrimary = Color(0xFF06220F),
    secondary = GreenSoft,
    onSecondary = Color(0xFF06220F),
    background = Bg,
    onBackground = Color(0xFFE6EDF3),
    surface = Surface,
    onSurface = Color(0xFFE6EDF3),
    surfaceVariant = SurfaceHi,
    onSurfaceVariant = Color(0xFF9FB0C0),
    error = Danger,
    onError = Color.White,
)

/** La app es siempre oscura, sin importar el tema del sistema. */
@Composable
fun InterfonTheme(content: @Composable () -> Unit) {
    @Suppress("UNUSED_EXPRESSION")
    isSystemInDarkTheme() // la app fuerza modo oscuro por decision de diseno
    MaterialTheme(colorScheme = DarkScheme, content = content)
}
