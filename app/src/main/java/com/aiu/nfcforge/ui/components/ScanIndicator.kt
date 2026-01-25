package com.aiu.nfcforge.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * ScanIndicator
 *
 * Animated "NFC scan" indicator:
 * - A center dot + 3 pulsing rings.
 * - Subtle, modern, and works well on a dark tech-style UI.
 */
@Composable
fun ScanIndicator(
    modifier: Modifier = Modifier,
    diameter: Dp = 180.dp,
    active: Boolean,
) {
    val transition = rememberInfiniteTransition(label = "scan")
    val t by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "scanProgress",
    )

    val primary = MaterialTheme.colorScheme.primary
    val tertiary = MaterialTheme.colorScheme.tertiary
    val outline = MaterialTheme.colorScheme.outline

    Box(modifier = modifier.size(diameter)) {
        Canvas(modifier = Modifier.size(diameter)) {
            // NOTE: inside Canvas, DrawScope exposes `size` (type Size). Our parameter is named `diameter`
            // to avoid shadowing the DrawScope property.
            val center = Offset(this.size.width / 2f, this.size.height / 2f)
            val maxR = this.size.minDimension / 2.2f

            // Center dot
            drawCircle(
                color = if (active) primary else outline,
                radius = maxR * 0.08f,
                center = center,
            )

            // Pulsing rings (three phases)
            val phases = listOf(0f, 0.33f, 0.66f)
            phases.forEach { phase ->
                val p = ((t + phase) % 1f)
                val r = (maxR * 0.15f) + (maxR * 0.85f * p)
                val alpha = if (active) (1f - p) * 0.55f else 0.18f
                drawCircle(
                    color = if (phase < 0.5f) primary.copy(alpha = alpha) else tertiary.copy(alpha = alpha),
                    radius = r,
                    center = center,
                    style = Stroke(width = maxR * 0.03f),
                )
            }
        }
    }
}



