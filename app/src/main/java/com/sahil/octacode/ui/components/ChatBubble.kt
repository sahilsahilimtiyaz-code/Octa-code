package com.sahil.octacode.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.sahil.octacode.core.provider.ChatRole
import com.sahil.octacode.domain.chat.ChatSegment
import com.sahil.octacode.domain.chat.splitChatSegments
import com.sahil.octacode.ui.theme.NeonBlue
import com.sahil.octacode.ui.theme.OnDarkMuted
import com.sahil.octacode.ui.theme.SurfaceDark
import com.sahil.octacode.ui.theme.SurfaceVariantDark

// M4a chat bubble: user right / assistant left. Code fences render as
// mono blocks with language tag (plain mono — no fake syntax highlighting).
@Composable
fun ChatBubble(
    role: ChatRole,
    text: String,
    modifier: Modifier = Modifier
) {
    val isUser = role == ChatRole.USER
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .clip(RoundedCornerShape(14.dp))
                .background(SurfaceVariantDark)
                .border(
                    1.dp,
                    if (isUser) NeonBlue.copy(alpha = 0.55f)
                    else OnDarkMuted.copy(alpha = 0.35f),
                    RoundedCornerShape(14.dp)
                )
                .padding(12.dp)
        ) {
            val segments = remember(text) { splitChatSegments(text) }
            if (segments.isEmpty()) {
                Text(
                    text = if (text.isBlank()) "…" else text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            } else {
                segments.forEach { seg ->
                    when (seg) {
                        is ChatSegment.Text -> Text(
                            text = seg.text,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                        is ChatSegment.Code -> CodeBlock(
                            language = seg.language,
                            code = seg.code
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CodeBlock(
    language: String,
    code: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(SurfaceDark)
            .border(
                1.dp,
                NeonBlue.copy(alpha = 0.3f),
                RoundedCornerShape(10.dp)
            )
            .padding(10.dp)
    ) {
        Text(
            text = language.ifBlank { "code" },
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
            color = NeonBlue,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        Text(
            text = code.ifBlank { "(empty)" },
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
