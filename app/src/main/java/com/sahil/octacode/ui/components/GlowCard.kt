package com.sahil.octacode.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sahil.octacode.ui.theme.ChatCardBottom
import com.sahil.octacode.ui.theme.ChatCardTop
import com.sahil.octacode.ui.theme.ElectricPurple
import com.sahil.octacode.ui.theme.GoldBright
import com.sahil.octacode.ui.theme.GoldDeep
import com.sahil.octacode.ui.theme.IceBlue
import com.sahil.octacode.ui.theme.NeonBlue
import com.sahil.octacode.ui.theme.TealGlow

// M4b reference card: navy vertical-gradient fill + glowing horizontal
// gradient edge (bright center, fading sides) plus an always-on rainbow
// comet that circulates the border (pure UI animation, no state implied).
@Composable
fun GlowCard(
    edge: List<Color>,
    modifier: Modifier = Modifier,
    contentPadding: Dp = 16.dp,
    animated: Boolean = true,
    sweep: List<Color> = rainbowSweep(),
    content: @Composable ColumnScope.() -> Unit
) {
    Box(modifier = modifier.circulatingGlow(enabled = animated, sweep = sweep)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(
                    Brush.verticalGradient(listOf(ChatCardTop, ChatCardBottom))
                )
                .border(
                    1.dp,
                    Brush.horizontalGradient(edge),
                    RoundedCornerShape(18.dp)
                )
                .padding(contentPadding)
        ) {
            content()
        }
    }
}

/** Rainbow comet tail: ice blue → violet → blue → green → gold. */
fun rainbowSweep(): List<Color> = listOf(
    Color.Transparent,
    IceBlue,
    ElectricPurple,
    NeonBlue,
    TealGlow,
    GoldBright,
    Color.Transparent
)

/** Gold edge for the agent card (bright champagne center). */
fun goldEdge(): List<Color> = listOf(
    Color.Transparent,
    GoldDeep.copy(alpha = 0.55f),
    GoldBright,
    GoldDeep.copy(alpha = 0.55f),
    Color.Transparent
)

/** Teal-blue edge for mission/composer cards. */
fun tealEdge(): List<Color> = listOf(
    Color.Transparent,
    TealGlow.copy(alpha = 0.45f),
    IceBlue,
    TealGlow.copy(alpha = 0.45f),
    Color.Transparent
)

@Composable
private fun Modifier.circulatingGlow(
    enabled: Boolean,
    sweep: List<Color>
): Modifier {
    if (!enabled) return this
    return composedGlow(sweep)
}

@Composable
private fun Modifier.composedGlow(sweep: List<Color>): Modifier {
    val transition = rememberInfiniteTransition(label = "glow")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(3500, easing = LinearEasing)
        ),
        label = "sweep"
    )
    return drawWithCache {
        val corner = 18.dp.toPx()
        val stroke = 2.dp.toPx()
        val brush = Brush.sweepGradient(sweep)
        // Fixed ring mask: gradient rotates underneath, shape never swings.
        val outer = Path().apply {
            addRoundRect(
                androidx.compose.ui.geometry.RoundRect(
                    0f, 0f, size.width, size.height, corner, corner
                )
            )
        }
        val innerR = (corner - stroke).coerceAtLeast(0f)
        val inner = Path().apply {
            addRoundRect(
                androidx.compose.ui.geometry.RoundRect(
                    stroke, stroke,
                    size.width - stroke, size.height - stroke,
                    innerR, innerR
                )
            )
        }
        onDrawWithContent {
            drawContent()
            clipPath(outer, clipOp = ClipOp.Intersect) {
                clipPath(inner, clipOp = ClipOp.Difference) {
                    rotate(angle) {
                        drawRect(brush = brush)
                    }
                }
            }
        }
    }
}
