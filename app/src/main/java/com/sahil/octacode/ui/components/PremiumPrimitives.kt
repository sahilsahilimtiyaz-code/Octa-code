package com.sahil.octacode.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp
import com.sahil.octacode.ui.motion.LocalMotionPolicy
import com.sahil.octacode.ui.theme.BubblePink
import com.sahil.octacode.ui.theme.DeepBlueBottom
import com.sahil.octacode.ui.theme.DeepPurpleTop
import com.sahil.octacode.ui.theme.ElectricPurple
import com.sahil.octacode.ui.theme.ElectricPurpleBright
import com.sahil.octacode.ui.theme.GlassBorder
import com.sahil.octacode.ui.theme.GlassShadow
import com.sahil.octacode.ui.theme.NeonBlue
import com.sahil.octacode.ui.theme.NeonBlueBright
import com.sahil.octacode.ui.theme.NeonTeal
import com.sahil.octacode.ui.theme.SpaceBlack
import com.sahil.octacode.ui.theme.TextSecondary

val OctaGradient: Brush
    @Composable get() = Brush.horizontalGradient(listOf(NeonBlue, ElectricPurple))

val OctaVerticalGradient: Brush
    @Composable get() = Brush.verticalGradient(listOf(NeonBlue, ElectricPurple))

val TealCyanGradient: Brush
    @Composable get() = Brush.horizontalGradient(listOf(NeonTeal, NeonBlue))

fun Modifier.glassSurface(
    shape: RoundedCornerShape = RoundedCornerShape(16.dp),
    alpha: Float = 0.05f,
    highlighted: Boolean = false,
): Modifier =
    shadow(
        elevation = if (highlighted) 12.dp else 8.dp,
        shape = shape,
        ambientColor = if (highlighted) NeonBlue.copy(alpha = 0.16f) else GlassShadow,
        spotColor = if (highlighted) ElectricPurple.copy(alpha = 0.12f) else GlassShadow,
    )
        .clip(shape)
        .background(Color.White.copy(alpha = alpha))
        .border(
            width = 1.dp,
            brush = if (highlighted) {
                Brush.linearGradient(
                    listOf(NeonBlue.copy(alpha = 0.65f), ElectricPurple.copy(alpha = 0.65f)),
                )
            } else {
                Brush.verticalGradient(
                    listOf(Color.White.copy(alpha = 0.25f), GlassBorder.copy(alpha = 0.05f)),
                )
            },
            shape = shape,
        )

@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = RoundedCornerShape(16.dp),
    highlighted: Boolean = false,
    strong: Boolean = false,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier.glassSurface(
            shape = shape,
            alpha = if (strong) 0.10f else 0.05f,
            highlighted = highlighted,
        ),
        content = content,
    )
}

@Composable
fun GradientHeaderText(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier,
        style = MaterialTheme.typography.headlineMedium.copy(
            brush = Brush.horizontalGradient(
                listOf(Color.White, NeonBlueBright, ElectricPurpleBright),
                startX = 0f,
                endX = Float.POSITIVE_INFINITY
            ),
        ),
    )
}

@Composable
fun SectionHeaderText(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier,
        style = MaterialTheme.typography.titleLarge.copy(
            brush = Brush.horizontalGradient(listOf(NeonBlue, ElectricPurple)),
        ),
    )
}

@Composable
fun StarFieldBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val motion = LocalMotionPolicy.current
    val stars = remember {
        List(85) { index ->
            Star(
                x = ((index * 37) % 100) / 100f,
                y = ((index * 53) % 100) / 100f,
                radius = 0.5f + ((index * 17) % 18) / 10f,
                baseAlpha = 0.15f + ((index * 11) % 30) / 100f,
                phase = ((index * 41) % 100) / 100f * (2f * Math.PI.toFloat()),
                speed = 0.5f + ((index * 7) % 12) / 10f,
            )
        }
    }
    val bubbles = remember {
        listOf(
            Bubble(0.12f, 0.22f, 34f, NeonBlue, 0.16f, 0f),
            Bubble(0.82f, 0.16f, 26f, BubblePink, 0.14f, 1.4f),
            Bubble(0.7f, 0.68f, 40f, ElectricPurple, 0.12f, 2.6f),
            Bubble(0.18f, 0.78f, 30f, NeonTeal, 0.13f, 3.8f),
            Bubble(0.9f, 0.52f, 20f, BubblePink, 0.15f, 5f),
            Bubble(0.45f, 0.4f, 22f, NeonBlue, 0.1f, 4.2f),
            Bubble(0.3f, 0.85f, 28f, ElectricPurpleBright, 0.11f, 1.1f),
            Bubble(0.6f, 0.1f, 25f, NeonTeal, 0.13f, 2.2f),
        )
    }
    val (animTime, animDrift) = if (motion.reducedMotion) {
        0f to 0.5f
    } else {
        val transition = rememberInfiniteTransition(label = "starfield")
        val time by transition.animateFloat(
            initialValue = 0f,
            targetValue = (2f * Math.PI.toFloat()),
            animationSpec = infiniteRepeatable(tween(12_000, easing = LinearEasing), RepeatMode.Restart),
            label = "starfield-time",
        )
        val drift by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(22_000, easing = LinearEasing), RepeatMode.Reverse),
            label = "starfield-drift",
        )
        time to drift
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(SpaceBlack),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(DeepPurpleTop, SpaceBlack, DeepBlueBottom),
                ),
            )
            stars.forEach { star ->
                val twinkle = if (motion.reducedMotion) {
                    star.baseAlpha
                } else {
                    star.baseAlpha * (0.55f + 0.45f * kotlin.math.sin(animTime * star.speed + star.phase))
                }
                drawCircle(
                    color = Color.White.copy(alpha = twinkle.coerceIn(0.05f, 1f)),
                    radius = star.radius,
                    center = Offset(size.width * star.x, size.height * star.y),
                )
            }
            bubbles.forEach { bubble ->
                val cx = size.width * bubble.x + kotlin.math.sin(animDrift * Math.PI.toFloat() + bubble.phase) * 35.dp.toPx()
                val cy = size.height * bubble.y + kotlin.math.cos(animDrift * Math.PI.toFloat() + bubble.phase) * 25.dp.toPx()
                val r = bubble.radius.dp.toPx()
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            bubble.color.copy(alpha = bubble.alpha * 1.6f),
                            bubble.color.copy(alpha = bubble.alpha * 0.5f),
                            Color.Transparent,
                        ),
                    ),
                    radius = r,
                    center = Offset(cx, cy),
                )
                drawCircle(
                    color = Color.White.copy(alpha = bubble.alpha * 0.9f),
                    radius = r * 0.22f,
                    center = Offset(cx - r * 0.25f, cy - r * 0.25f),
                )
            }
        }
        content()
    }
}

private data class Bubble(
    val x: Float,
    val y: Float,
    val radius: Float,
    val color: Color,
    val alpha: Float,
    val phase: Float,
)

@Composable
fun EmptyStateCard(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
) {
    GlassSurface(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                message,
                color = TextSecondary,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

private data class Star(
    val x: Float,
    val y: Float,
    val radius: Float,
    val baseAlpha: Float,
    val phase: Float,
    val speed: Float,
)

fun DrawScope.neonGradient(): Brush = Brush.linearGradient(listOf(NeonBlue, ElectricPurple))
