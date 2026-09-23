package com.sahil.octacode.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.sahil.octacode.ui.theme.ChatCardBottom
import com.sahil.octacode.ui.theme.ChatMuted
import com.sahil.octacode.ui.theme.DotGreen
import com.sahil.octacode.ui.theme.GoldMid
import com.sahil.octacode.ui.theme.OnDark
import com.sahil.octacode.ui.theme.WarningAmber

// M4b session strip: project slot (real nav to Projects) │ agent dot (real registry).
@Composable
fun SessionStatusBar(
    agentText: String,
    agentReady: Boolean,
    onPickProject: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(ChatCardBottom)
            .border(1.dp, ChatMuted.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onPickProject),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Filled.Folder,
                contentDescription = null,
                tint = GoldMid,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "No project selected",
                style = MaterialTheme.typography.bodyMedium,
                color = OnDark,
                modifier = Modifier.weight(1f, fill = false)
            )
            Icon(
                Icons.Filled.KeyboardArrowDown,
                contentDescription = "Open projects",
                tint = ChatMuted,
                modifier = Modifier.size(18.dp)
            )
        }
        Box(
            modifier = Modifier
                .padding(horizontal = 10.dp)
                .size(width = 1.dp, height = 20.dp)
                .background(ChatMuted.copy(alpha = 0.35f))
        )
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(if (agentReady) DotGreen else WarningAmber.copy(alpha = 0.85f))
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = agentText,
            style = MaterialTheme.typography.bodySmall,
            color = ChatMuted
        )
    }
}
