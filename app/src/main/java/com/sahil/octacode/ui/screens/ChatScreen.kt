package com.sahil.octacode.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import com.sahil.octacode.core.capability.CapabilityRegistry
import com.sahil.octacode.core.capability.ProviderStatus
import com.sahil.octacode.core.provider.AiProvider
import com.sahil.octacode.core.provider.CapabilityBadge
import com.sahil.octacode.core.provider.ChatMessage
import com.sahil.octacode.core.provider.ChatRequest
import com.sahil.octacode.core.provider.ChatRole
import com.sahil.octacode.core.provider.ProviderId
import com.sahil.octacode.data.security.CredentialStore
import com.sahil.octacode.data.security.SecretRedactor
import com.sahil.octacode.domain.chat.historyWindow
import com.sahil.octacode.domain.mission.MissionEngine
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

private data class UiChat(val role: ChatRole, val text: String)

private const val OPENAI_CHAT_MODEL = "gpt-4o-mini"
private const val CHAT_SYSTEM =
    "You are Octa Code, a concise mobile coding assistant. " +
        "Answer directly. Put code in ``` fences with a language tag."

// M4a agent chat: real streaming via M2 adapters, no fake activity.
// Unconfigured providers show Unavailable with reason + Settings hint.
@Composable
fun ChatScreen(
    engine: MissionEngine = koinInject(),
    registry: CapabilityRegistry = koinInject(),
    providers: Map<ProviderId, AiProvider> = koinInject(),
    credentials: CredentialStore = koinInject()
) {
    val scope = rememberCoroutineScope()
    var messages by remember { mutableStateOf<List<UiChat>>(emptyList()) }
    var input by remember { mutableStateOf("") }
    var providerId by remember { mutableStateOf(ProviderId.OPENAI) }
    var providerMenu by remember { mutableStateOf(false) }
    var statuses by remember { mutableStateOf<Map<ProviderId, ProviderStatus>>(emptyMap()) }
    var error by remember { mutableStateOf<String?>(null) }
    var sending by remember { mutableStateOf(false) }
    var streamJob by remember { mutableStateOf<Job?>(null) }
    val engineState by engine.state.collectAsState()
    val listState = rememberLazyListState()

    LaunchedEffect(Unit) {
        statuses = registry.refreshAll()
    }
    LaunchedEffect(messages.size, messages.lastOrNull()?.text?.length) {
        if (messages.isNotEmpty()) {
            try {
                listState.scrollToItem(messages.lastIndex)
            } catch (_: Exception) {
                // fast deltas — next frame catches up
            }
        }
    }

    fun send() {
        val prompt = input.trim()
        if (prompt.isEmpty() || sending) return
        error = null
        sending = true
        input = ""
        streamJob = scope.launch {
            val status = registry.providerStatus(providerId)
            if (status !is ProviderStatus.Ready) {
                error = "Unavailable — ${status.reason}. Configure it in Settings."
                sending = false
                return@launch
            }
            val adapter = providers[providerId]
            if (adapter == null) {
                error = "Unavailable — no adapter registered for ${providerId.title}."
                sending = false
                return@launch
            }
            val model = if (providerId == ProviderId.OPENAI) {
                OPENAI_CHAT_MODEL
            } else {
                credentials.getCustomModel()
            }
            val history = historyWindow(
                messages.map { ChatMessage(it.role, it.text) } +
                    ChatMessage(ChatRole.USER, prompt)
            )
            messages = messages + UiChat(ChatRole.USER, prompt) + UiChat(ChatRole.ASSISTANT, "")
            try {
                adapter.chatStream(
                    ChatRequest(
                        messages = listOf(ChatMessage(ChatRole.SYSTEM, CHAT_SYSTEM)) + history,
                        model = model,
                        maxTokens = 2048,
                        temperature = 0.2
                    )
                ).collect { chunk ->
                    if (chunk.delta.isNotEmpty()) {
                        val last = messages.last()
                        messages = messages.dropLast(1) + last.copy(text = last.text + chunk.delta)
                    }
                }
                if (messages.lastOrNull()?.text.isNullOrBlank()) {
                    error = "Empty reply — provider returned no text (honest, nothing invented)."
                    messages = messages.dropLast(1)
                }
            } catch (c: CancellationException) {
                val last = messages.lastOrNull()
                if (last != null && last.role == ChatRole.ASSISTANT) {
                    val suffix = if (last.text.isBlank()) "(stopped before any text)" else " (stopped)"
                    messages = messages.dropLast(1) + last.copy(text = last.text + suffix)
                }
                throw c
            } catch (t: Throwable) {
                error = SecretRedactor.redact(t.message ?: t::class.java.simpleName).take(300)
                if (messages.lastOrNull()?.text.isNullOrBlank()) {
                    messages = messages.dropLast(1)
                }
            } finally {
                sending = false
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("Agent", style = MaterialTheme.typography.headlineSmall)

        if (engineState.activeMissionId != null) {
            GlassPanel(title = "Active mission", accent = NeonBlue) {
                Text(
                    "Phase ${engineState.currentPhase?.index ?: "—"} · ${engineState.message.ifBlank { engineState.status.name }}",
                    style = MaterialTheme.typography.bodySmall,
                    color = OnDarkMuted
                )
                engineState.percent?.let { p ->
                    Text(
                        "~$p%",
                        style = MaterialTheme.typography.labelSmall,
                        color = NeonGreen
                    )
                }
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = { providerMenu = true }, modifier = Modifier.weight(1f)) {
                Text(providerId.title)
            }
            Spacer(Modifier.width(8.dp))
            CapabilityBadgeChip(chatBadgeFor(providerId))
            DropdownMenu(expanded = providerMenu, onDismissRequest = { providerMenu = false }) {
                ProviderId.entries.forEach { id ->
                    DropdownMenuItem(
                        text = { Text(id.title) },
                        onClick = {
                            providerId = id
                            providerMenu = false
                            scope.launch { statuses = registry.refreshAll() }
                        }
                    )
                }
            }
        }
        val st = statuses[providerId]
        Text(
            when (st) {
                null -> "Probing…"
                is ProviderStatus.Ready -> "Ready — ${st.reason}"
                is ProviderStatus.MissingKey -> "Unavailable — ${st.reason}. Set it in Settings."
                is ProviderStatus.Misconfigured -> "Unavailable — ${st.reason}"
                is ProviderStatus.Unavailable -> "Unavailable — ${st.reason}"
            },
            style = MaterialTheme.typography.bodySmall,
            color = if (st is ProviderStatus.Ready) NeonGreen else WarningAmber
        )

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (messages.isEmpty()) {
                item {
                    Text(
                        "Ask anything. Replies stream from the selected provider — nothing is faked.",
                        style = MaterialTheme.typography.bodySmall,
                        color = OnDarkMuted
                    )
                }
            } else {
                itemsIndexed(messages) { _, m ->
                    ChatBubble(role = m.role, text = m.text)
                }
                if (sending && messages.lastOrNull()?.text.isNullOrBlank()) {
                    item {
                        ThinkingOrb(label = "Streaming")
                    }
                }
            }
        }

        error?.let {
            StatusBanner(BannerTone.Error, "Chat failed", it)
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.imePadding()
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                label = { Text("Message the agent…") },
                singleLine = false,
                maxLines = 4
            )
            Spacer(Modifier.width(8.dp))
            Column {
                Button(onClick = ::send, enabled = !sending && input.isNotBlank()) {
                    Text("Send")
                }
                if (sending) {
                    Spacer(Modifier.height(6.dp))
                    OutlinedButton(onClick = { streamJob?.cancel() }) {
                        Text("Stop")
                    }
                }
            }
        }
    }
}

private fun chatBadgeFor(id: ProviderId): CapabilityBadge = when (id) {
    ProviderId.OPENAI -> CapabilityBadge.API
    ProviderId.CUSTOM -> CapabilityBadge.REMOTE
    ProviderId.CLAUDE, ProviderId.GEMINI -> CapabilityBadge.UNAVAILABLE
}
