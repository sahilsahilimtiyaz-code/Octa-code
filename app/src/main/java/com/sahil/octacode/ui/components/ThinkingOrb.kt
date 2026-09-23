package com.sahil.octacode.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.sahil.octacode.ui.motion.LocalMotionPolicy
import com.sahil.octacode.ui.theme.ElectricPurple
import com.sahil.octacode.ui.theme.NeonBlue
import com.sahil.octacode.ui.theme.OnDarkMuted

// M3d UI kit: pulsing "thinking" orb while the engine is busy (reduced-motion safe).
@Composable
fun ThinkingOrb(
    label: String = "Working",
    modifier: Modifier = Modifier
) {
    val reducedMotion = LocalMotionPolicy.current.reducedMotion
    val transition = rememberInfiniteTransition(label = "orb")
    val pulse by if (!reducedMotion) {
        transition.animateFloat(
            initialValue = 0.85f,
            targetValue = 1.12f,
            animationSpec = infiniteRepeatable(
                animation = tween(900, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "pulse"
        )
    } else {
        androidx.compose.animation.core.animateFloatAsState(targetValue = 1f, label = "pulse-static")
    }
    val alpha by if (!reducedMotion) {
        transition.animateFloat(
            initialValue = 0.55f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(900, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "alpha"
        )
    } else {
        androidx.compose.animation.core.animateFloatAsState(targetValue = 0.9f, label = "alpha-static")
    }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .scale(if (reducedMotion) 1f else pulse)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            NeonBlue.copy(alpha = alpha),
                            ElectricPurple.copy(alpha = alpha * 0.85f),
                            Color.Transparent
                        )
                    )
                )
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = OnDarkMuted,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}
