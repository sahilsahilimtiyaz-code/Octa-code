package com.sahil.octacode.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sahil.octacode.core.provider.CapabilityBadge
import com.sahil.octacode.ui.theme.NeonBlue
import com.sahil.octacode.ui.theme.NeonGreen
import com.sahil.octacode.ui.theme.NeonRed
import com.sahil.octacode.ui.theme.ElectricPurple

// Honest badge chip: API blue, REMOTE purple, LOCAL green, UNAVAILABLE red.
@Composable
fun CapabilityBadgeChip(badge: CapabilityBadge, modifier: Modifier = Modifier) {
    val color = when (badge) {
        CapabilityBadge.API -> NeonBlue
        CapabilityBadge.REMOTE -> ElectricPurple
        CapabilityBadge.LOCAL -> NeonGreen
        CapabilityBadge.UNAVAILABLE -> NeonRed
    }
    AssistChip(
        onClick = {},
        label = { Text(badge.label) },
        modifier = modifier.padding(end = 4.dp),
        colors = AssistChipDefaults.assistChipColors(labelColor = color)
    )
}
