package com.sahil.octacode.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.sahil.octacode.domain.mission.DiffDecision
import com.sahil.octacode.domain.mission.MissionDiff
import com.sahil.octacode.ui.theme.NeonBlue
import com.sahil.octacode.ui.theme.NeonGreen
import com.sahil.octacode.ui.theme.NeonRed
import com.sahil.octacode.ui.theme.OnDarkMuted
import com.sahil.octacode.ui.theme.SurfaceVariantDark
import com.sahil.octacode.ui.theme.WarningAmber

// M3b UI kit: review card for a mission file diff.
@Composable
fun DiffCard(
    diff: MissionDiff,
    onAccept: (() -> Unit)? = null,
    onReject: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val decisionColor = when (diff.decision) {
        DiffDecision.PENDING -> WarningAmber
        DiffDecision.ACCEPTED -> NeonGreen
        DiffDecision.REJECTED -> NeonRed
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceVariantDark)
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    text = diff.path,
                    style = MaterialTheme.typography.titleSmall,
                    color = NeonBlue,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = diff.decision.name,
                    style = MaterialTheme.typography.labelMedium,
                    fontFamily = FontFamily.Monospace,
                    color = decisionColor
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = "phase ${diff.phase.index} · ${diff.phase.title}",
                style = MaterialTheme.typography.labelSmall,
                color = OnDarkMuted
            )
            Text(
                text = "before=${diff.beforeHash.take(12)}  after=${diff.afterHash.take(12)}",
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                color = OnDarkMuted
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = diff.patchText.ifBlank { "(empty patch)" },
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(6.dp)
            )
            if (diff.decision == DiffDecision.PENDING && onAccept != null && onReject != null) {
                Spacer(Modifier.height(8.dp))
                Row {
                    OutlinedButton(onClick = onAccept, modifier = Modifier.weight(1f)) {
                        Text("Accept", color = NeonGreen)
                    }
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = onReject, modifier = Modifier.weight(1f)) {
                        Text("Reject", color = NeonRed)
                    }
                }
            }
        }
    }
}
