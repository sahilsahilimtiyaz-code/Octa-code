package com.sahil.octacode.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import com.sahil.octacode.ui.motion.LocalMotionPolicy
import com.sahil.octacode.ui.state.ActivityStepState
import com.sahil.octacode.ui.state.ActivityStepUi
import com.sahil.octacode.ui.theme.BubblePink
import com.sahil.octacode.ui.theme.NeonBlue
import com.sahil.octacode.ui.theme.NeonGreen
import com.sahil.octacode.ui.theme.NeonTeal
import com.sahil.octacode.ui.theme.TextSecondary

@Composable
fun ActivityTimeline(
    steps: List<ActivityStepUi>,
    modifier: Modifier = Modifier,
) {
    GlassSurface(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SectionHeaderText("Generating progress")
            if (steps.isEmpty()) {
                Text(
                    "No mission activity is available yet.",
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                steps.forEachIndexed { index, step ->
                    TimelineStep(step, showConnector = index < steps.lastIndex)
                }
            }
        }
    }
}

@Composable
private fun TimelineStep(step: ActivityStepUi, showConnector: Boolean) {
    val reducedMotion = LocalMotionPolicy.current.reducedMotion
    val activeAlpha = if (!reducedMotion && step.state == ActivityStepState.ACTIVE) {
        val transition = rememberInfiniteTransition(label = "timeline-active")
        transition.animateFloat(
            initialValue = 0.45f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(850, easing = LinearEasing), RepeatMode.Reverse),
            label = "timeline-alpha",
        ).value
    } else {
        1f
    }
    val statusLabel = when (step.state) {
        ActivityStepState.COMPLETE -> "Completed"
        ActivityStepState.ACTIVE -> "Running"
        ActivityStepState.PENDING -> "Pending"
    }
    Row(verticalAlignment = Alignment.Top) {
        // Node column with connector line
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Canvas(Modifier.size(26.dp)) {
                val center = Offset(size.width / 2f, size.height / 2f)
                when (step.state) {
                    ActivityStepState.ACTIVE -> {
                        // Pulsing glow ring
                        drawCircle(color = NeonBlue.copy(alpha = 0.25f * activeAlpha), radius = 12.dp.toPx())
                        drawCircle(
                            brush = Brush.radialGradient(
                                listOf(Color.White.copy(alpha = 0.9f), NeonBlue),
                                center = center,
                            ),
                            radius = 9.dp.toPx(),
                            alpha = activeAlpha,
                        )
                    }
                    ActivityStepState.COMPLETE -> {
                        drawCircle(color = NeonTeal, radius = 8.dp.toPx())
                        // Checkmark
                        val s = size.minDimension
                        drawLine(
                            Color.Black.copy(alpha = 0.8f),
                            Offset(center.x - s * 0.15f, center.y),
                            Offset(center.x - s * 0.03f, center.y + s * 0.12f),
                            strokeWidth = 2.dp.toPx(),
                            cap = StrokeCap.Round,
                        )
                        drawLine(
                            Color.Black.copy(alpha = 0.8f),
                            Offset(center.x - s * 0.03f, center.y + s * 0.12f),
                            Offset(center.x + s * 0.18f, center.y - s * 0.12f),
                            strokeWidth = 2.dp.toPx(),
                            cap = StrokeCap.Round,
                        )
                    }
                    ActivityStepState.PENDING -> {
                        drawCircle(
                            color = TextSecondary.copy(alpha = 0.35f),
                            radius = 6.dp.toPx(),
                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5.dp.toPx()),
                        )
                    }
                }
            }
            if (showConnector) {
                Canvas(Modifier.width(2.dp).height(32.dp)) {
                    val isComplete = step.state == ActivityStepState.COMPLETE
                    val pathEffect = if (!isComplete) {
                        PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
                    } else {
                        null
                    }
                    drawLine(
                        brush = if (isComplete) {
                            Brush.verticalGradient(listOf(NeonTeal, NeonBlue))
                        } else {
                            Brush.verticalGradient(listOf(TextSecondary.copy(alpha = 0.3f), TextSecondary.copy(alpha = 0.15f)))
                        },
                        start = Offset(size.width / 2f, 0f),
                        end = Offset(size.width / 2f, size.height),
                        strokeWidth = 2.dp.toPx(),
                        pathEffect = pathEffect,
                        cap = StrokeCap.Round,
                    )
                }
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(step.label, style = MaterialTheme.typography.bodyMedium)
                Text(
                    statusLabel,
                    color = if (step.state == ActivityStepState.COMPLETE) NeonGreen else TextSecondary,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
            SegmentedProgress(
                progress = if (step.state == ActivityStepState.COMPLETE) 1f else step.progress.coerceIn(0f, 1f),
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp).height(8.dp),
            )
        }
    }
}

@Composable
private fun SegmentedProgress(progress: Float, modifier: Modifier = Modifier) {
    val segments = 4
    val colors = listOf(NeonBlue, NeonTeal, Color(0xFF7AA8FF), BubblePink)
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(segments) { index ->
            val segStart = index.toFloat() / segments
            val segEnd = (index + 1f) / segments
            val fill = ((progress - segStart) / (segEnd - segStart)).coerceIn(0f, 1f)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(8.dp)
                    .background(
                        brush = if (fill > 0f) {
                            Brush.horizontalGradient(
                                listOf(
                                    colors[index % colors.size],
                                    colors[(index + 1) % colors.size],
                                ),
                            )
                        } else {
                            Brush.horizontalGradient(
                                listOf(
                                    Color.White.copy(alpha = 0.1f),
                                    Color.White.copy(alpha = 0.1f),
                                ),
                            )
                        },
                        shape = RoundedCornerShape(6.dp),
                    ),
            ) {
                if (fill in 0.01f..0.99f) {
                    Canvas(Modifier.fillMaxWidth().height(8.dp)) {
                        drawRect(
                            color = Color.White.copy(alpha = 0.9f),
                            topLeft = Offset(size.width * fill - 1.dp.toPx(), 0f),
                            size = Size(2.dp.toPx(), size.height),
                        )
                    }
                }
            }
        }
    }
}
