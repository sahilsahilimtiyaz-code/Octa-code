package com.sahil.octacode.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.sahil.octacode.domain.mission.MissionPhase
import com.sahil.octacode.domain.mission.PhaseStatus
import com.sahil.octacode.ui.theme.NeonBlue
import com.sahil.octacode.ui.theme.NeonGreen
import com.sahil.octacode.ui.theme.NeonRed
import com.sahil.octacode.ui.theme.OnDark
import com.sahil.octacode.ui.theme.OnDarkMuted
import com.sahil.octacode.ui.theme.SurfaceVariantDark
import com.sahil.octacode.ui.theme.WarningAmber

// M3b UI kit: vertical 16-phase rail with honest status colors.
// M3d: current-row accent border + optional per-phase error line.
@Composable
fun PhaseRail(
    currentPhase: MissionPhase?,
    statuses: Map<MissionPhase, PhaseStatus>,
    errors: Map<MissionPhase, String?> = emptyMap(),
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
                isCurrent = isCurrent,
                error = errors[phase]
            )
        }
    }
}

@Composable
private fun PhaseRailRow(
    phase: MissionPhase,
    status: PhaseStatus?,
    isCurrent: Boolean,
    error: String? = null
) {
    val dotColor: Color = when (status) {
        PhaseStatus.SUCCEEDED -> NeonGreen
        PhaseStatus.RUNNING -> NeonBlue
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
            .then(
                if (isCurrent) Modifier.border(
                    1.dp,
                    NeonBlue.copy(alpha = 0.5f),
                    RoundedCornerShape(8.dp)
                ) else Modifier
            )
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
    if ((status == PhaseStatus.FAILED) && !error.isNullOrBlank()) {
        Text(
            text = error.take(180),
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
            color = NeonRed.copy(alpha = 0.9f),
            modifier = Modifier.padding(start = 28.dp, end = 6.dp, bottom = 2.dp)
        )
    }
}
