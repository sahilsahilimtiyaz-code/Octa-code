package com.sahil.octacode.ui.demo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sahil.octacode.ui.components.GlassSurface
import com.sahil.octacode.ui.theme.NeonBlue
import com.sahil.octacode.ui.theme.TextSecondary

@Composable
fun DemoPreviewBanner(
    detail: String = "Sample content only · no real operation has run",
    modifier: Modifier = Modifier,
) {
    GlassSurface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        highlighted = true,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.Visibility, contentDescription = null, tint = NeonBlue)
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("DEMO PREVIEW · SIMULATED", color = NeonBlue, style = MaterialTheme.typography.labelLarge)
                Text(detail, color = TextSecondary, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}
