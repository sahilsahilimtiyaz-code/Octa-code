package com.sahil.octacode.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.sahil.octacode.ui.motion.LocalMotionPolicy
import com.sahil.octacode.ui.state.MissionProgressUi
import com.sahil.octacode.ui.theme.BubblePink
import com.sahil.octacode.ui.theme.ElectricPurple
import com.sahil.octacode.ui.theme.NeonBlue
import com.sahil.octacode.ui.theme.TextSecondary
import kotlinx.coroutines.delay
import java.util.Locale

@Composable
fun MissionProgressCard(
    mission: MissionProgressUi,
    modifier: Modifier = Modifier,
) {
    val motion = LocalMotionPolicy.current
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(mission.startedAtEpochMillis) {
        if (mission.startedAtEpochMillis != null) {
            while (true) {
                now = System.currentTimeMillis()
                delay(1_000L)
            }
        }
    }
    val elapsed = mission.startedAtEpochMillis?.let { (now - it).coerceAtLeast(0L) }
    val animatedProgress by animateFloatAsState(
        targetValue = mission.progress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = if (motion.reducedMotion) 0 else 700),
        label = "mission-progress",
    )
    val transition = rememberInfiniteTransition(label = "mission-anim")
    val shimmerOffset = if (!motion.reducedMotion) {
        transition.animateFloat(
            initialValue = -0.3f,
            targetValue = 1.3f,
            animationSpec = infiniteRepeatable(tween(1_800, easing = LinearEasing), RepeatMode.Restart),
            label = "mission-shimmer",
        ).value
    } else {
        -1f
    }
    val iconPulse = if (!motion.reducedMotion) {
        transition.animateFloat(
            initialValue = 0.7f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(1_200, easing = LinearEasing), RepeatMode.Reverse),
            label = "mission-icon-pulse",
        ).value
    } else {
        0.85f
    }

    GlassSurface(modifier = modifier.fillMaxWidth(), highlighted = true) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                ) {
                    Canvas(Modifier.size(18.dp)) {
                        drawCircle(
                            Brush.radialGradient(
                                listOf(
                                    Color.White.copy(alpha = iconPulse),
                                    NeonBlue.copy(alpha = iconPulse * 0.8f),
                                    NeonBlue.copy(alpha = 0f),
                                ),
                            ),
                        )
                    }
                    Text("Mission in progress", style = MaterialTheme.typography.titleMedium)
                }
                GlassSurface(shape = RoundedCornerShape(50), strong = true) {
                    Text(
                        mission.status,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        color = TextSecondary,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
            Text(mission.currentStep, color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.White.copy(alpha = 0.1f)),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(animatedProgress)
                        .height(8.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            Brush.horizontalGradient(
                                listOf(NeonBlue, ElectricPurple, BubblePink),
                            ),
                        ),
                )
                if (animatedProgress > 0.02f) {
                    Canvas(Modifier.fillMaxWidth().height(8.dp)) {
                        val x = size.width * animatedProgress
                        // Glowing dot at tip (brighter, 12dp)
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(Color.White, NeonBlue, Color.Transparent),
                            ),
                            radius = 12.dp.toPx(),
                            center = Offset(x, size.height / 2f),
                        )
                        // Shimmer sweep
                        if (shimmerOffset in 0f..1f) {
                            val bandWidth = size.width * animatedProgress * 0.3f
                            val sx = size.width * animatedProgress * shimmerOffset - bandWidth
                            drawRect(
                                brush = Brush.linearGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        Color.White.copy(alpha = 0.18f),
                                        Color.Transparent,
                                    ),
                                    start = Offset(sx, 0f),
                                    end = Offset(sx + bandWidth, size.height),
                                ),
                                size = androidx.compose.ui.geometry.Size(size.width * animatedProgress, size.height),
                            )
                        }
                    }
                }
            }
            Text(
                elapsed?.let(::formatElapsed) ?: "Elapsed time unavailable",
                color = TextSecondary,
                style = MaterialTheme.typography.labelSmall,
            )
            if (motion.reducedMotion) {
                Text(
                    "Reduced motion enabled",
                    color = TextSecondary.copy(alpha = 0.6f),
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}

private fun formatElapsed(milliseconds: Long): String {
    val seconds = milliseconds / 1_000L
    return String.format(Locale.US, "Elapsed %02d:%02d", seconds / 60L, seconds % 60L)
}
