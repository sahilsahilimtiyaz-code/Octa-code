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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
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

private val BubbleShape = RoundedCornerShape(18.dp)

// M4 chat bubble: agent gets an avatar and glass, the user gets the neon
// gradient outline — the same language as the rest of the app's chrome.
// Error / streaming labels stay honest rather than decorative.
@Composable
fun ChatBubble(
    turn: ChatTurn,
    modifier: Modifier = Modifier
) {
    val isUser = turn.author == ChatAuthor.USER
    val failed = turn.error != null && turn.error != "stopped"
    val label = when {
        turn.error == "stopped" -> "Stopped"
        failed -> "Failed"
        turn.streaming -> "Streaming…"
        isUser -> "You"
        else -> "Octa"
    }
    val labelColor: Color = when {
        failed -> NeonRed
        turn.error == "stopped" -> OnDarkMuted
        turn.streaming -> NeonGreen
        isUser -> NeonBlue
        else -> ElectricPurple
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Top,
    ) {
        if (!isUser) {
            AgentAvatar()
            Spacer(Modifier.size(8.dp))
        }

        Column(horizontalAlignment = if (isUser) Alignment.End else Alignment.Start) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = labelColor,
                modifier = Modifier.padding(bottom = 4.dp),
            )
            if (isUser) {
                UserBubble(turn = turn)
            } else {
                GlassSurface(
                    modifier = Modifier.widthIn(max = 300.dp),
                    highlighted = turn.streaming,
                    shape = BubbleShape,
                ) {
                    BubbleText(turn = turn, modifier = Modifier.align(Alignment.CenterStart))
                }
            }
        }
    }
}

// Outgoing: the gradient outline that marks "you wrote this". Distinct from
// incoming by border AND fill, not by screen edge alone.
@Composable
private fun UserBubble(turn: ChatTurn) {
    Box(
        modifier = Modifier
            .widthIn(max = 300.dp)
            .clip(BubbleShape)
            // Flat tint and a single border colour. The blue-to-pink sweep
            // across every outgoing message made the user's own text the most
            // decorated thing on screen, competing with the response below it.
            .background(NeonBlue.copy(alpha = 0.14f))
            .border(
                width = 1.5.dp,
                color = NeonBlue.copy(alpha = 0.55f),
                shape = BubbleShape,
            ),
    ) {
        BubbleText(turn = turn, modifier = Modifier.align(Alignment.Center))
    }
}

@Composable
private fun BubbleText(turn: ChatTurn, modifier: Modifier = Modifier) {
    Column(modifier = modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
        val placeholder = when {
            turn.streaming && turn.text.isEmpty() -> "…"
            turn.text.isEmpty() -> "— empty —"
            else -> turn.text
        }
        Text(
            text = placeholder,
            style = MaterialTheme.typography.bodyMedium,
            color = if (turn.error != null && turn.text.startsWith("—")) OnDarkMuted else OnDark,
        )
        // Deliberately NOT gated on the text. A failure that happens before a
        // single token arrives sets text to "— request failed —", and the old
        // `!turn.text.startsWith("—")` guard hid the reason on exactly that
        // path: the label said Failed, the body said request failed, and
        // nothing anywhere said why. The bubble is the durable record of the
        // error — lastError is cleared by the next send, this is not.
        if (turn.error != null && turn.error != "stopped") {
            Text(
                text = turn.error,
                style = MaterialTheme.typography.labelSmall,
                color = NeonRed,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

@Composable
private fun AgentAvatar() {
    Box(
        modifier = Modifier
            .size(30.dp)
            .clip(CircleShape)
            .background(Brush.linearGradient(listOf(NeonBlue, ElectricPurple))),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Outlined.SmartToy,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(17.dp),
        )
    }
}
