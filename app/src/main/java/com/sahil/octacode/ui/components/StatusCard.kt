package com.sahil.octacode.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.sahil.octacode.ui.motion.LocalMotionPolicy
import com.sahil.octacode.ui.state.StatusCardUi
import com.sahil.octacode.ui.state.StatusKind
import com.sahil.octacode.ui.theme.NeonAmber
import com.sahil.octacode.ui.theme.NeonBlue
import com.sahil.octacode.ui.theme.NeonGreen
import com.sahil.octacode.ui.theme.NeonRed
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun StatusCard(
    status: StatusCardUi,
    modifier: Modifier = Modifier,
) {
    val motion = LocalMotionPolicy.current
    val color = when (status.kind) {
        StatusKind.SUCCESS -> NeonGreen
        StatusKind.FAILURE -> NeonRed
        StatusKind.WARNING -> NeonAmber
        StatusKind.INFO -> NeonBlue
    }
    val transition = rememberInfiniteTransition(label = "status-anim")
    val pulse = if (!motion.reducedMotion) {
        transition.animateFloat(
            initialValue = 0.65f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(1_200, easing = LinearEasing), RepeatMode.Reverse),
            label = "status-alpha",
        ).value
    } else {
        0.85f
    }
    val shimmerProgress = if (!motion.reducedMotion && status.kind == StatusKind.SUCCESS) {
        transition.animateFloat(
            initialValue = -0.3f,
            targetValue = 1.3f,
            animationSpec = infiniteRepeatable(tween(1_800, easing = LinearEasing), RepeatMode.Restart),
            label = "status-shimmer",
        ).value
    } else {
        -1f
    }
    val offsetX = remember { Animatable(0f) }
    LaunchedEffect(status.kind, motion.reducedMotion, status.title, status.message) {
        if (status.kind == StatusKind.FAILURE && !motion.reducedMotion) {
            launch {
                offsetX.animateTo(4f, tween(55))
                offsetX.animateTo(-4f, tween(55))
                offsetX.animateTo(3f, tween(45))
                offsetX.animateTo(-3f, tween(45))
                offsetX.animateTo(0f, tween(45))
            }
        } else {
            offsetX.snapTo(0f)
        }
    }

    GlassSurface(
        modifier = modifier
            .fillMaxWidth()
            .offset { IntOffset(offsetX.value.roundToInt(), 0) },
        highlighted = status.kind == StatusKind.SUCCESS,
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            Canvas(Modifier.size(24.dp)) {
                // Outer glow
                if (status.kind == StatusKind.SUCCESS || status.kind == StatusKind.WARNING) {
                    drawCircle(color.copy(alpha = 0.3f * pulse), radius = size.minDimension * 0.8f)
                }
                // Main circle
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color.White.copy(alpha = 0.9f), color, color.copy(alpha = 0.6f)),
                    ),
                    radius = size.minDimension / 2f * if (status.kind == StatusKind.SUCCESS) pulse else 1f,
                    alpha = pulse,
                )
                // Checkmark for success
                if (status.kind == StatusKind.SUCCESS) {
                    val c = center
                    val s = size.minDimension
                    drawLine(
                        Color.White,
                        Offset(c.x - s * 0.18f, c.y),
                        Offset(c.x - s * 0.05f, c.y + s * 0.14f),
                        strokeWidth = 2.5.dp.toPx(),
                    )
                    drawLine(
                        Color.White,
                        Offset(c.x - s * 0.05f, c.y + s * 0.14f),
                        Offset(c.x + s * 0.2f, c.y - s * 0.14f),
                        strokeWidth = 2.5.dp.toPx(),
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(status.title, style = MaterialTheme.typography.titleMedium, color = color)
                Text(status.message, style = MaterialTheme.typography.bodyMedium)
            }
        }
        // Shimmer overlay for success
        if (shimmerProgress in 0f..1f) {
            Canvas(Modifier.fillMaxSize()) {
                val bandWidth = size.width * 0.3f
                val x = size.width * shimmerProgress - bandWidth
                drawRect(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.White.copy(alpha = 0.14f),
                            Color.Transparent,
                        ),
                        start = Offset(x, 0f),
                        end = Offset(x + bandWidth, size.height),
                    ),
                    size = size,
                )
            }
        }
    }
}
