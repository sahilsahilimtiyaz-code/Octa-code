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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.sahil.octacode.domain.mission.MissionPhase
import com.sahil.octacode.domain.mission.PhaseStatus
import com.sahil.octacode.ui.motion.LocalMotionPolicy
import com.sahil.octacode.ui.theme.NeonBlue
import com.sahil.octacode.ui.theme.NeonGreen
import com.sahil.octacode.ui.theme.NeonRed
import com.sahil.octacode.ui.theme.OnDark
import com.sahil.octacode.ui.theme.OnDarkMuted
import com.sahil.octacode.ui.theme.SurfaceVariantDark
import com.sahil.octacode.ui.theme.WarningAmber

// M3d UI kit: vertical 16-phase rail with honest status colors + RUNNING pulse.
@Composable
fun PhaseRail(
    currentPhase: MissionPhase?,
    statuses: Map<MissionPhase, PhaseStatus>,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        MissionPhase.ORDERED.forEach { phase ->
            val st = statuses[phase]
            val isCurrent = phase == currentPhase
            PhaseRailRow(
                phase = phase,
                status = st,
                isCurrent = isCurrent
            )
        }
    }
}

@Composable
private fun PhaseRailRow(
    phase: MissionPhase,
    status: PhaseStatus?,
    isCurrent: Boolean
) {
    val reducedMotion = LocalMotionPolicy.current.reducedMotion
    val isRunning = status == PhaseStatus.RUNNING || (isCurrent && status == PhaseStatus.RUNNING)
    val pulseAlpha by if (isRunning && !reducedMotion) {
        val transition = rememberInfiniteTransition(label = "phase-pulse")
        transition.animateFloat(
            initialValue = 0.45f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(700, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "phase-dot-alpha"
        )
    } else {
        androidx.compose.animation.core.animateFloatAsState(
            targetValue = 1f,
            label = "phase-dot-static"
        )
    }

    val dotColor: Color = when (status) {
        PhaseStatus.SUCCEEDED -> NeonGreen
        PhaseStatus.RUNNING -> NeonBlue.copy(alpha = pulseAlpha.coerceIn(0.45f, 1f))
        PhaseStatus.FAILED -> NeonRed
        PhaseStatus.CANCELLED -> NeonRed
        PhaseStatus.SKIPPED -> WarningAmber
        PhaseStatus.PENDING, null ->
            if (isCurrent) NeonBlue else SurfaceVariantDark
    }
    val titleColor = when {
        status == PhaseStatus.FAILED || status == PhaseStatus.CANCELLED -> NeonRed
        status == PhaseStatus.SUCCEEDED -> OnDark
        isCurrent || status == PhaseStatus.RUNNING -> NeonBlue
        else -> OnDarkMuted
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (isCurrent) SurfaceVariantDark.copy(alpha = 0.7f) else Color.Transparent)
            .padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(dotColor)
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = "%02d".format(phase.index),
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
            color = OnDarkMuted
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = phase.title,
            style = MaterialTheme.typography.bodySmall,
            color = titleColor,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = status?.name ?: if (isCurrent) "…" else "",
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
            color = titleColor
        )
    }
}
