package com.jhonsu.interfon.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

enum class OrbMode { IDLE, LISTENING, THINKING, SPEAKING }

/**
 * Orbe animado estilo Siri: blobs de luz que orbitan y pulsan.
 * - SPEAKING: la energia sigue la envolvente real de la voz del agente (level).
 * - LISTENING: respira suave y sigue el nivel del microfono (level).
 * - THINKING: remolino lento y constante.
 * - IDLE: brillo tenue.
 */
@Composable
fun VoiceOrb(mode: OrbMode, level: Float, tint: Color, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "orb")
    val fast = transition.animateFloat(
        initialValue = 0f, targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(3400, easing = LinearEasing)), label = "fast")
    val slow = transition.animateFloat(
        initialValue = 0f, targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(7900, easing = LinearEasing)), label = "slow")
    val eased = 0.15f + 0.85f * level

    Canvas(modifier) {
        val c = center
        val base = size.minDimension / 2f
        val energy = when (mode) {
            OrbMode.SPEAKING -> 0.40f + 0.60f * eased
            OrbMode.THINKING -> 0.48f + 0.14f * sin(slow.value)
            OrbMode.LISTENING -> 0.30f + 0.34f * eased + 0.06f * sin(slow.value * 1.4f)
            OrbMode.IDLE -> 0.20f
        }

        // Bloques orbitando
        val blobs = 5
        for (i in 0 until blobs) {
            val k = i.toFloat() / blobs
            val ph = fast.value + k * (2 * PI).toFloat()
            val orbit = base * (0.30f + 0.17f * sin(slow.value * (0.7f + k * 0.45f)))
            val off = Offset(cos(ph) * orbit, sin(ph * 1.13f + k) * orbit)
            val blobR = base * (0.52f + 0.42f * energy) * (0.86f + 0.14f * sin(ph * 2f))
            drawBlob(c + off, blobR, tint, alpha = (0.16f + 0.34f * energy))
        }
        // Halo central
        drawBlob(c, base * (0.72f + 0.20f * energy), tint, alpha = 0.12f + 0.26f * energy)
    }
}

private fun DrawScope.drawBlob(center: Offset, radius: Float, tint: Color, alpha: Float) {
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(tint.copy(alpha = alpha), tint.copy(alpha = alpha * 0.35f),
                Color.Transparent),
            center = center, radius = radius),
        radius = radius, center = center)
}
