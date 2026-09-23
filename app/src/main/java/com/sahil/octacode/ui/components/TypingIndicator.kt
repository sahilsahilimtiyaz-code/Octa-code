package com.sahil.octacode.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.sahil.octacode.ui.motion.LocalMotionPolicy
import com.sahil.octacode.ui.theme.ElectricPurple
import com.sahil.octacode.ui.theme.NeonBlue
import kotlinx.coroutines.delay

@Composable
fun TypingIndicator(modifier: Modifier = Modifier) {
    val reducedMotion = LocalMotionPolicy.current.reducedMotion
    GlassSurface(
        modifier = modifier.wrapContentSize(),
        shape = RoundedCornerShape(24.dp),
        strong = true,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            repeat(3) { index ->
                val scale = remember { Animatable(if (reducedMotion) 0.65f else 0.3f) }
                if (!reducedMotion) {
                    LaunchedEffect(Unit) {
                        delay(index * 150L)
                        while (true) {
                            scale.animateTo(
                                1f,
                                spring(dampingRatio = 0.6f, stiffness = 300f),
                            )
                            scale.animateTo(
                                0.3f,
                                spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium),
                            )
                            delay(100L)
                        }
                    }
                }
                Box(
                    modifier = Modifier.width(6.dp).height(20.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Canvas(modifier = Modifier.matchParentSize()) {
                        val barHeight = size.height * scale.value
                        val barTop = (size.height - barHeight) / 2f

                        // Soft glow under bar
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(NeonBlue.copy(alpha = 0.35f), Color.Transparent),
                                center = Offset(size.width / 2f, size.height / 2f),
                                radius = size.height * 0.8f,
                            ),
                            radius = size.height * 0.8f,
                            center = Offset(size.width / 2f, size.height / 2f),
                        )

                        // Gradient bar
                        drawRoundRect(
                            brush = Brush.verticalGradient(listOf(NeonBlue, ElectricPurple)),
                            topLeft = Offset(0f, barTop),
                            size = Size(size.width, barHeight),
                            cornerRadius = CornerRadius(3.dp.toPx()),
                        )
                    }
                }
            }
        }
    }
}
