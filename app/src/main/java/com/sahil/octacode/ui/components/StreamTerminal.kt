package com.sahil.octacode.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.sahil.octacode.ui.theme.NeonGreen
import com.sahil.octacode.ui.theme.OnDarkMuted
import com.sahil.octacode.ui.theme.SurfaceDark

// M3b UI kit: monospace event / stream terminal with auto-scroll.
// M3d: safe scrollToItem (no animate crash on rapid events), bounded height.
@Composable
fun StreamTerminal(
    lines: List<String>,
    modifier: Modifier = Modifier,
    emptyHint: String = "No events yet — engine idle"
) {
    val listState = rememberLazyListState()
    LaunchedEffect(lines.size) {
        if (lines.isNotEmpty()) {
            try {
                listState.scrollToItem(lines.lastIndex)
            } catch (_: Exception) {
                // rapid inserts during fast phases — next frame will catch up
            }
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "STREAM",
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
            color = OnDarkMuted,
            modifier = Modifier.padding(bottom = 6.dp)
        )
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 120.dp, max = 280.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(SurfaceDark)
                .padding(10.dp)
        ) {
            if (lines.isEmpty()) {
                item {
                    Text(
                        text = emptyHint,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = OnDarkMuted
                    )
                }
            } else {
                items(lines) { line ->
                    Text(
                        text = line,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = NeonGreen,
                        modifier = Modifier.padding(vertical = 1.dp)
                    )
                }
            }
        }
    }
}
