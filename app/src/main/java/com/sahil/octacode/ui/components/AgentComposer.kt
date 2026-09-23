package com.sahil.octacode.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.unit.dp
import com.sahil.octacode.core.provider.CapabilityBadge
import com.sahil.octacode.core.provider.ProviderId
import com.sahil.octacode.ui.theme.DeepSpaceBlack
import com.sahil.octacode.ui.theme.NeonBlue
import com.sahil.octacode.ui.theme.OnDarkMuted
import com.sahil.octacode.ui.theme.WarningAmber

// M4b composer card: model slot (real statuses) │ project slot (real nav) ·
// attach/file/mic honestly disabled · round gold send gated by canSend.
@Composable
fun AgentComposer(
    input: String,
    onInput: (String) -> Unit,
    providerId: ProviderId,
    onSelectProvider: (ProviderId) -> Unit,
    modelLabel: String,
    modelBadge: CapabilityBadge,
    onPickProject: () -> Unit,
    sending: Boolean,
    canSend: Boolean,
    helperText: String,
    onSend: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier
) {
    var modelMenu by remember { mutableStateOf(false) }

    GlassPanel(accent = NeonBlue, modifier = modifier.imePadding()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { modelMenu = true }, modifier = Modifier.weight(1f)) {
                Icon(
                    Icons.Filled.Layers,
                    contentDescription = null,
                    tint = OnDarkMuted,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(text = modelLabel, color = OnDarkMuted)
                Icon(
                    Icons.Filled.KeyboardArrowDown,
                    contentDescription = "Pick model",
                    tint = OnDarkMuted,
                    modifier = Modifier.size(16.dp)
                )
            }
            DropdownMenu(expanded = modelMenu, onDismissRequest = { modelMenu = false }) {
                ProviderId.entries.forEach { id ->
                    DropdownMenuItem(
                        text = { Text(id.title) },
                        onClick = { modelMenu = false; onSelectProvider(id) }
                    )
                }
            }
            CapabilityBadgeChip(modelBadge)
            Box(
                modifier = Modifier
                    .padding(horizontal = 8.dp)
                    .size(width = 1.dp, height = 20.dp)
                    .background(OnDarkMuted.copy(alpha = 0.35f))
            )
            TextButton(onClick = onPickProject, modifier = Modifier.weight(1f)) {
                Icon(
                    Icons.Filled.Folder,
                    contentDescription = null,
                    tint = NeonBlue,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(text = "Project", color = OnDarkMuted)
                Icon(
                    Icons.Filled.KeyboardArrowDown,
                    contentDescription = "Open projects",
                    tint = OnDarkMuted,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = {}, enabled = false) {
                Icon(
                    Icons.Filled.AttachFile,
                    contentDescription = "Attach unavailable",
                    tint = OnDarkMuted.copy(alpha = 0.4f)
                )
            }
            IconButton(onClick = {}, enabled = false) {
                Icon(
                    Icons.Filled.Description,
                    contentDescription = "Files unavailable",
                    tint = OnDarkMuted.copy(alpha = 0.4f)
                )
            }
            IconButton(onClick = {}, enabled = false) {
                Icon(
                    Icons.Filled.Mic,
                    contentDescription = "Voice unavailable",
                    tint = OnDarkMuted.copy(alpha = 0.4f)
                )
            }
            Box(
                modifier = Modifier
                    .padding(horizontal = 4.dp)
                    .size(width = 1.dp, height = 24.dp)
                    .background(OnDarkMuted.copy(alpha = 0.35f))
            )
            OutlinedTextField(
                value = input,
                onValueChange = onInput,
                modifier = Modifier.weight(1f),
                placeholder = { Text("Type a message…") },
                maxLines = 4
            )
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Button(
                    onClick = onSend,
                    enabled = canSend,
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = WarningAmber,
                        contentColor = DeepSpaceBlack,
                        disabledContainerColor = OnDarkMuted.copy(alpha = 0.25f),
                        disabledContentColor = OnDarkMuted
                    ),
                    modifier = Modifier.size(48.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
                }
                if (sending) {
                    TextButton(onClick = onStop) {
                        Text("Stop", color = WarningAmber)
                    }
                }
            }
        }
        Text(
            text = helperText,
            style = MaterialTheme.typography.labelSmall,
            color = OnDarkMuted,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}
