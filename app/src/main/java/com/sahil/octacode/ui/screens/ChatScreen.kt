package com.sahil.octacode.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import com.sahil.octacode.domain.chat.agentCardText
import com.sahil.octacode.domain.chat.agentDotText
import com.sahil.octacode.domain.chat.composerHelperText
import com.sahil.octacode.domain.chat.historyWindow
import com.sahil.octacode.domain.chat.modelSlotLabel
import com.sahil.octacode.domain.mission.MissionEngine
import com.sahil.octacode.ui.components.AgentComposer
import com.sahil.octacode.ui.components.AgentHeader
import com.sahil.octacode.ui.components.BannerTone
import com.sahil.octacode.ui.components.ChatBubble
import com.sahil.octacode.ui.components.GlassPanel
import com.sahil.octacode.ui.components.SessionStatusBar
import com.sahil.octacode.ui.components.StatusBanner
import com.sahil.octacode.ui.components.ThinkingOrb
import com.sahil.octacode.ui.theme.GoldDeep
import com.sahil.octacode.ui.theme.GoldLight
import com.sahil.octacode.ui.theme.NeonBlue
import com.sahil.octacode.ui.theme.OnDark
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

// M4b reference-style chat: header · session strip · hero + status cards
// (empty) / bubbles (conversation) · composer. All states are real
// (registry + engine); nothing is faked.
@Composable
fun ChatScreen(
    onOpenSettings: () -> Unit,
    onOpenProjects: () -> Unit,
    onOpenMission: (String) -> Unit,
    onNewMission: () -> Unit,
    engine: MissionEngine = koinInject(),
    registry: CapabilityRegistry = koinInject(),
    providers: Map<ProviderId, AiProvider> = koinInject(),
    credentials: CredentialStore = koinInject()
) {
    val scope = rememberCoroutineScope()
    var messages by remember { mutableStateOf<List<UiChat>>(emptyList()) }
    var input by remember { mutableStateOf("") }
    var providerId by remember { mutableStateOf(ProviderId.OPENAI) }
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

    val st = statuses[providerId]
    val ready = st is ProviderStatus.Ready
    val card = agentCardText(providerId, st)
    val (dotText, dotReady) = agentDotText(st)
    val activeId = engineState.activeMissionId

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        AgentHeader(
            onOpenProjects = onOpenProjects,
            onNewMission = onNewMission,
            onOpenSettings = onOpenSettings
        )
        SessionStatusBar(
            agentText = dotText,
            agentReady = dotReady,
            onPickProject = onOpenProjects
        )

        if (messages.isEmpty()) {
            HeroBlock()
            StatusCard(
                title = card.title,
                body = card.body,
                ready = card.ready,
                accent = WarningAmber,
                onClick = onOpenSettings
            )
            if (activeId != null) {
                val live = "Phase ${engineState.currentPhase?.index ?: "—"} · " +
                    engineState.message.ifBlank { engineState.status.name } +
                    (engineState.percent?.let { " (~$it%)" } ?: "")
                StatusCard(
                    title = "Mission activity",
                    body = live,
                    ready = true,
                    accent = NeonBlue,
                    onClick = { onOpenMission(activeId) }
                )
            } else {
                StatusCard(
                    title = "Mission activity",
                    body = "A mission feed appears after a real runtime starts work. " +
                        "Nothing is running now.",
                    ready = false,
                    accent = NeonBlue,
                    onClick = onNewMission
                )
            }
        } else {
            if (activeId != null) {
                Text(
                    "Mission phase ${engineState.currentPhase?.index ?: "—"} · " +
                        engineState.message.ifBlank { engineState.status.name },
                    style = MaterialTheme.typography.labelSmall,
                    color = OnDarkMuted
                )
            }
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
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

        AgentComposer(
            input = input,
            onInput = { input = it },
            providerId = providerId,
            onSelectProvider = {
                providerId = it
                scope.launch { statuses = registry.refreshAll() }
            },
            modelLabel = modelSlotLabel(providerId, st),
            modelBadge = chatBadgeFor(providerId),
            onPickProject = onOpenProjects,
            sending = sending,
            canSend = !sending && input.isNotBlank() && ready,
            helperText = composerHelperText(st),
            onSend = ::send,
            onStop = { streamJob?.cancel() }
        )
    }
}

@Composable
private fun HeroBlock(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxWidth()) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(190.dp)
        ) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        NeonBlue.copy(alpha = 0.35f),
                        Color.Transparent
                    ),
                    center = Offset(size.width * 0.92f, size.height * 0.45f),
                    radius = size.width * 0.45f
                ),
                radius = size.width * 0.45f,
                center = Offset(size.width * 0.92f, size.height * 0.45f)
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        WarningAmber.copy(alpha = 0.18f),
                        Color.Transparent
                    ),
                    center = Offset(size.width * 0.7f, size.height * 0.7f),
                    radius = size.width * 0.35f
                ),
                radius = size.width * 0.35f,
                center = Offset(size.width * 0.7f, size.height * 0.7f)
            )
        }
        Column(Modifier.padding(vertical = 8.dp)) {
            Text(
                text = "YOUR CODING PARTNER",
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 2.sp),
                color = OnDarkMuted
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Build Better",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = OnDark
            )
            Text(
                text = "Together",
                style = MaterialTheme.typography.displaySmall.copy(
                    brush = Brush.horizontalGradient(listOf(GoldLight, GoldDeep))
                ),
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Your coding conversation and mission activity will appear here.",
                style = MaterialTheme.typography.bodyMedium,
                color = OnDarkMuted,
                modifier = Modifier.fillMaxWidth(0.75f)
            )
        }
    }
}

@Composable
private fun StatusCard(
    title: String,
    body: String,
    ready: Boolean,
    accent: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        GlassPanel(accent = accent) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.Transparent)
                        .border(1.dp, accent.copy(alpha = 0.6f), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = when {
                            ready && accent == NeonBlue -> Icons.Filled.GpsFixed
                            ready -> Icons.Filled.CheckCircle
                            else -> Icons.Filled.CloudOff
                        },
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        color = OnDark
                    )
                    Text(
                        text = body,
                        style = MaterialTheme.typography.bodySmall,
                        color = OnDarkMuted
                    )
                }
                Icon(
                    Icons.Filled.KeyboardArrowRight,
                    contentDescription = "Open",
                    tint = OnDarkMuted
                )
            }
        }
    }
}

private fun chatBadgeFor(id: ProviderId): CapabilityBadge = when (id) {
    ProviderId.OPENAI -> CapabilityBadge.API
    ProviderId.CUSTOM -> CapabilityBadge.REMOTE
    ProviderId.CLAUDE, ProviderId.GEMINI -> CapabilityBadge.UNAVAILABLE
}
