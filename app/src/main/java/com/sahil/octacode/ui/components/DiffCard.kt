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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
// M3d: collapsible patch + +/- line colors, keeps Accept/Reject wiring.
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
    var expanded by remember(diff.id) { mutableStateOf(false) }
    val rawLines = remember(diff.patchText) {
        diff.patchText.ifBlank { "(empty patch)" }.lines()
    }
    val visibleLines = if (expanded) rawLines.take(400) else rawLines.take(30)
    val hiddenCount = rawLines.size - visibleLines.size

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
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(6.dp),
                verticalArrangement = Arrangement.spacedBy(1.dp)
            ) {
                visibleLines.forEach { line ->
                    val color = when {
                        line.startsWith("+") && !line.startsWith("+++") -> NeonGreen
                        line.startsWith("-") && !line.startsWith("---") -> NeonRed
                        line.startsWith("@@") -> NeonBlue
                        else -> MaterialTheme.colorScheme.onSurface
                    }
                    Text(
                        text = line.ifBlank { " " },
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = color
                    )
                }
                if (hiddenCount > 0 || rawLines.size > 30) {
                    TextButton(onClick = { expanded = !expanded }) {
                        Text(
                            if (expanded) "Collapse"
                            else "Expand +$hiddenCount lines",
                            color = NeonBlue
                        )
                    }
                }
            }
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
