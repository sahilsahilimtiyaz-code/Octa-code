package com.sahil.octacode.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import com.sahil.octacode.core.provider.ProviderId
import com.sahil.octacode.ui.theme.ChatMuted
import com.sahil.octacode.ui.theme.DeepSpaceBlack
import com.sahil.octacode.ui.theme.GoldBright
import com.sahil.octacode.ui.theme.GoldDeep
import com.sahil.octacode.ui.theme.IceBlue
import com.sahil.octacode.ui.theme.NeonBlue

// M4b composer card: inset model/project pills (real states) ·
// attach/file/mic honestly disabled · gold-gradient round send gated by canSend.
@Composable
fun AgentComposer(
    input: String,
    onInput: (String) -> Unit,
    providerId: ProviderId,
    onSelectProvider: (ProviderId) -> Unit,
    modelLabel: String,
    onPickProject: () -> Unit,
    sending: Boolean,
    canSend: Boolean,
    helperText: String,
    onSend: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier
) {
    var modelMenu by remember { mutableStateOf(false) }

    GlowCard(edge = tealEdge(), modifier = modifier.imePadding()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(DeepSpaceBlack.copy(alpha = 0.45f))
                    .border(1.dp, ChatMuted.copy(alpha = 0.25f), RoundedCornerShape(10.dp))
                    .clickable { modelMenu = true }
                    .padding(horizontal = 10.dp, vertical = 8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.Layers,
                        contentDescription = null,
                        tint = IceBlue,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = modelLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = ChatMuted,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        Icons.Filled.KeyboardArrowDown,
                        contentDescription = "Pick model",
                        tint = ChatMuted,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            DropdownMenu(expanded = modelMenu, onDismissRequest = { modelMenu = false }) {
                ProviderId.entries.forEach { id ->
                    DropdownMenuItem(
                        text = { Text(id.title) },
                        onClick = { modelMenu = false; onSelectProvider(id) }
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(DeepSpaceBlack.copy(alpha = 0.45f))
                    .border(1.dp, ChatMuted.copy(alpha = 0.25f), RoundedCornerShape(10.dp))
                    .clickable(onClick = onPickProject)
                    .padding(horizontal = 10.dp, vertical = 8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.Folder,
                        contentDescription = null,
                        tint = NeonBlue,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "Project",
                        style = MaterialTheme.typography.bodySmall,
                        color = ChatMuted,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        Icons.Filled.KeyboardArrowDown,
                        contentDescription = "Open projects",
                        tint = ChatMuted,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = {}, enabled = false) {
                Icon(
                    Icons.Filled.AttachFile,
                    contentDescription = "Attach unavailable",
                    tint = ChatMuted.copy(alpha = 0.45f)
                )
            }
            IconButton(onClick = {}, enabled = false) {
                Icon(
                    Icons.Filled.Description,
                    contentDescription = "Files unavailable",
                    tint = ChatMuted.copy(alpha = 0.45f)
                )
            }
            IconButton(onClick = {}, enabled = false) {
                Icon(
                    Icons.Filled.Mic,
                    contentDescription = "Voice unavailable",
                    tint = ChatMuted.copy(alpha = 0.45f)
                )
            }
            Box(
                modifier = Modifier
                    .padding(horizontal = 4.dp)
                    .size(width = 1.dp, height = 24.dp)
                    .background(ChatMuted.copy(alpha = 0.35f))
            )
            OutlinedTextField(
                value = input,
                onValueChange = onInput,
                modifier = Modifier.weight(1f),
                placeholder = { Text("Type a message…", color = ChatMuted) },
                maxLines = 4
            )
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (canSend) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(listOf(GoldBright, GoldDeep))
                            )
                            .clickable(onClick = onSend),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send",
                            tint = DeepSpaceBlack,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                } else {
                    Button(
                        onClick = {},
                        enabled = false,
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(
                            disabledContainerColor = ChatMuted.copy(alpha = 0.22f),
                            disabledContentColor = ChatMuted.copy(alpha = 0.6f)
                        ),
                        modifier = Modifier.size(48.dp),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send unavailable",
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
                if (sending) {
                    TextButton(onClick = onStop) {
                        Text("Stop", color = GoldDeep)
                    }
                }
            }
        }
        Text(
            text = helperText,
            style = MaterialTheme.typography.labelSmall,
            color = ChatMuted,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}
