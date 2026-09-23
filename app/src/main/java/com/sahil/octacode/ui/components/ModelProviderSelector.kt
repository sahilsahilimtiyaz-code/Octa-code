package com.sahil.octacode.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.sahil.octacode.ui.state.ProviderOptionUi
import com.sahil.octacode.ui.theme.ElectricPurple
import com.sahil.octacode.ui.theme.NeonBlue
import com.sahil.octacode.ui.theme.TextSecondary

@Composable
fun ModelProviderSelector(
    providers: List<ProviderOptionUi>,
    onProviderSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        providers.forEach { provider ->
            ProviderRow(provider, onProviderSelected)
        }
    }
}

@Composable
private fun ProviderRow(provider: ProviderOptionUi, onProviderSelected: (String) -> Unit) {
    val rowModifier = Modifier
        .fillMaxWidth()
        .glassSurface(
            shape = RoundedCornerShape(16.dp),
            alpha = if (provider.selected) 0.10f else 0.05f,
            highlighted = provider.selected,
        )
        .then(
            if (provider.selected) {
                Modifier.border(
                    width = 1.5.dp,
                    brush = Brush.linearGradient(listOf(NeonBlue.copy(alpha = 0.7f), ElectricPurple.copy(alpha = 0.7f))),
                    shape = RoundedCornerShape(16.dp),
                )
            } else {
                Modifier
            },
        )
        .padding(12.dp)
    Row(modifier = rowModifier, verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(
                    Brush.linearGradient(
                        listOf(NeonBlue.copy(alpha = 0.25f), ElectricPurple.copy(alpha = 0.25f)),
                    ),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(provider.name.firstOrNull()?.toString() ?: "?", color = NeonBlue)
        }
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(provider.name, style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.width(6.dp))
                Text(
                    provider.badge,
                    color = ElectricPurple,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
            Text(
                provider.unavailableReason ?: provider.description,
                color = TextSecondary,
                style = MaterialTheme.typography.labelSmall,
            )
        }
        Switch(
            checked = provider.selected,
            enabled = provider.available && provider.configured,
            onCheckedChange = { if (it) onProviderSelected(provider.id) },
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = ElectricPurple.copy(alpha = 0.85f),
                checkedBorderColor = NeonBlue.copy(alpha = 0.7f),
                uncheckedThumbColor = TextSecondary,
                uncheckedTrackColor = Color.White.copy(alpha = 0.12f),
                uncheckedBorderColor = Color.Transparent,
            ),
        )
    }
}
