package com.sahil.octacode.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sahil.octacode.ui.theme.NeonGreen
import com.sahil.octacode.ui.theme.OnDarkMuted
import com.sahil.octacode.ui.theme.SurfaceDark
import com.sahil.octacode.ui.theme.codeTextStyle

// M3d UI kit: monospace event / stream terminal with auto-scroll + copy.
@Composable
fun StreamTerminal(
    lines: List<String>,
    modifier: Modifier = Modifier,
    emptyHint: String = "No events yet — engine idle",
    // Optional because MissionDetailScreen shares this component and is happy
    // with the original cap; the terminal screen wants every spare pixel.
    maxHeight: Dp = 280.dp
) {
    val listState = rememberLazyListState()
    val clipboard = LocalClipboardManager.current
    LaunchedEffect(lines.size) {
        if (lines.isNotEmpty()) listState.animateScrollToItem(lines.lastIndex)
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "STREAM",
                style = codeTextStyle(MaterialTheme.typography.labelSmall),
                fontFamily = FontFamily.Monospace,
                color = OnDarkMuted,
                modifier = Modifier
                    .weight(1f)
                    .padding(bottom = 6.dp)
            )
            if (lines.isNotEmpty()) {
                TextButton(
                    onClick = { clipboard.setText(AnnotatedString(lines.joinToString("\n"))) },
                    modifier = Modifier.padding(bottom = 2.dp)
                ) {
                    Text(
                        text = "Copy",
                        style = codeTextStyle(MaterialTheme.typography.labelSmall),
                        fontFamily = FontFamily.Monospace,
                        color = NeonGreen
                    )
                }
            }
        }
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 120.dp, max = maxHeight)
                .clip(RoundedCornerShape(12.dp))
                .background(SurfaceDark)
                .padding(10.dp)
        ) {
            if (lines.isEmpty()) {
                item {
                    Text(
                        text = emptyHint,
                        style = codeTextStyle(MaterialTheme.typography.bodySmall),
                        fontFamily = FontFamily.Monospace,
                        color = OnDarkMuted
                    )
                }
            } else {
                items(lines) { line ->
                    Text(
                        text = line,
                        style = codeTextStyle(MaterialTheme.typography.bodySmall),
                        fontFamily = FontFamily.Monospace,
                        color = NeonGreen,
                        modifier = Modifier.padding(vertical = 1.dp)
                    )
                }
            }
        }
    }
}
