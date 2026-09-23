package com.sahil.octacode.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sahil.octacode.core.capability.ProviderStatus
import com.sahil.octacode.core.provider.CapabilityBadge
import com.sahil.octacode.core.provider.ProviderId
import com.sahil.octacode.domain.chat.ChatEngine
import com.sahil.octacode.ui.components.BannerTone
import com.sahil.octacode.ui.components.CapabilityBadgeChip
import com.sahil.octacode.ui.components.ChatBubble
import com.sahil.octacode.ui.components.GlassPanel
import com.sahil.octacode.ui.components.StatusBanner
import com.sahil.octacode.ui.components.ThinkingOrb
import com.sahil.octacode.ui.theme.NeonBlue
import com.sahil.octacode.ui.theme.NeonGreen
import com.sahil.octacode.ui.theme.OnDarkMuted
import com.sahil.octacode.ui.theme.WarningAmber
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

// M4: live Agent chat — streams from M2 adapters via ChatEngine (no fake replies).
@Composable
fun ChatScreen(
    engine: ChatEngine = koinInject()
) {
    val state by engine.state.collectAsState()
    var draft by remember { mutableStateOf("") }
    var providerMenu by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    LaunchedEffect(Unit) {
        engine.refreshProviderStatus()
    }
    LaunchedEffect(state.turns.size, state.streamingText) {
        if (state.turns.isNotEmpty()) {
            listState.animateScrollToItem(state.turns.lastIndex)
        }
    }

    val status = state.providerStatus
    val ready = status is ProviderStatus.Ready

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        GlassPanel(title = "Agent Chat") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = state.selectedProvider.title,
                            style = MaterialTheme.typography.titleSmall,
                            color = NeonBlue
                        )
                        CapabilityBadgeChip(badge = badgeFor(state.selectedProvider))
                    }
                    val statusLine = when (val s = status) {
                        is ProviderStatus.Ready -> "Ready — ${s.reason}"
                        null -> "Probing…"
                        else -> "Unavailable — ${s.reason}"
                    }
                    Text(
                        text = statusLine,
                        style = MaterialTheme.typography.bodySmall,
                        color = when {
                            status is ProviderStatus.Ready -> NeonGreen
                            status == null -> OnDarkMuted
                            else -> WarningAmber
                        }
                    )
                }
                IconButton(onClick = { providerMenu = true }) {
                    Text(
                        text = "Change",
                        style = MaterialTheme.typography.labelMedium,
                        color = NeonBlue
                    )
                }
                DropdownMenu(
                    expanded = providerMenu,
                    onDismissRequest = { providerMenu = false }
                ) {
                    ProviderId.entries.forEach { id ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = if (id == state.selectedProvider) "✓ ${id.title}" else id.title
                                )
                            },
                            onClick = {
                                engine.selectProvider(id)
                                providerMenu = false
                            }
                        )
                    }
                }
                val refreshScope = androidx.compose.runtime.rememberCoroutineScope()
                IconButton(onClick = { refreshScope.launch { engine.refreshProviderStatus() } }) {
                    Icon(Icons.Filled.Refresh, contentDescription = "Refresh status", tint = NeonBlue)
                }
            }
        }

        state.lastError?.let { err ->
            StatusBanner(
                tone = BannerTone.Error,
                title = "Unavailable",
                message = err
            )
        }

        GlassPanel(modifier = Modifier.weight(1f)) {
            if (state.turns.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 160.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "No messages yet",
                        style = MaterialTheme.typography.bodyMedium,
                        color = OnDarkMuted
                    )
                    Text(
                        text = if (ready) {
                            "Type below to start a real streamed conversation."
                        } else {
                            "Configure a Ready provider in Settings to send messages."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = OnDarkMuted,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 200.dp, max = 420.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(state.turns, key = { it.id }) { turn ->
                        ChatBubble(turn = turn)
                    }
                }
                if (state.busy && state.streamingText.isNullOrEmpty()) {
                    ThinkingOrb(label = "Waiting for provider…")
                }
            }
        }

        GlassPanel(title = "Message") {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.busy,
                placeholder = {
                    Text(
                        text = if (ready) "Ask the agent…" else "Provider unavailable — configure in Settings",
                        color = OnDarkMuted
                    )
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = NeonBlue,
                    unfocusedBorderColor = OnDarkMuted,
                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                ),
                minLines = 2,
                maxLines = 5
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (state.busy) {
                    OutlinedButton(onClick = { engine.stop() }) {
                        Text("Stop", color = WarningAmber)
                    }
                } else {
                    Button(
                        onClick = {
                            if (engine.send(draft)) {
                                draft = ""
                            }
                        },
                        enabled = draft.isNotBlank() && ready
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null)
                        Text(" Send", modifier = Modifier.padding(start = 4.dp))
                    }
                }
                Text(
                    text = when {
                        state.busy -> "Streaming…"
                        !ready -> "Send disabled until provider is Ready"
                        else -> "In-memory session · lost on process death"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = OnDarkMuted,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

private fun badgeFor(id: ProviderId): CapabilityBadge = when (id) {
    ProviderId.OPENAI -> CapabilityBadge.API
    ProviderId.CUSTOM -> CapabilityBadge.REMOTE
    ProviderId.CLAUDE, ProviderId.GEMINI -> CapabilityBadge.UNAVAILABLE
}
