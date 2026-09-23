package com.sahil.octacode.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.sahil.octacode.domain.chat.ChatAuthor
import com.sahil.octacode.domain.chat.ChatTurn
import com.sahil.octacode.ui.theme.ElectricPurple
import com.sahil.octacode.ui.theme.NeonBlue
import com.sahil.octacode.ui.theme.NeonGreen
import com.sahil.octacode.ui.theme.NeonRed
import com.sahil.octacode.ui.theme.OnDark
import com.sahil.octacode.ui.theme.OnDarkMuted

// M4 chat bubble: user vs agent alignment + honest error / streaming labels.
@Composable
fun ChatBubble(
    turn: ChatTurn,
    modifier: Modifier = Modifier
) {
    val isUser = turn.author == ChatAuthor.USER
    val label = when {
        turn.error == "stopped" -> "Stopped"
        turn.error != null -> "Failed"
        turn.streaming -> "Streaming…"
        isUser -> "You"
        else -> "Agent"
    }
    val labelColor: Color = when {
        turn.error != null && turn.error != "stopped" -> NeonRed
        turn.error == "stopped" -> OnDarkMuted
        turn.streaming -> NeonGreen
        isUser -> NeonBlue
        else -> ElectricPurple
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = labelColor,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        GlassSurface(
            modifier = Modifier.widthIn(max = 340.dp),
            highlighted = turn.streaming,
            strong = isUser
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                val placeholder = when {
                    turn.streaming && turn.text.isEmpty() -> "…"
                    turn.text.isEmpty() -> "— empty —"
                    else -> turn.text
                }
                Text(
                    text = placeholder,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (turn.error != null && turn.text.startsWith("—")) OnDarkMuted else OnDark
                )
                if (turn.error != null && turn.error != "stopped" && turn.text.isNotBlank() &&
                    !turn.text.startsWith("—")
                ) {
                    Text(
                        text = turn.error,
                        style = MaterialTheme.typography.labelSmall,
                        color = NeonRed,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
            }
        }
    }
}
