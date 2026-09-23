package com.sahil.octacode.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.outlined.AllInclusive
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.TrackChanges
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sahil.octacode.core.capability.ProviderStatus
import com.sahil.octacode.core.provider.ProviderId
import com.sahil.octacode.domain.chat.ChatEngine
import com.sahil.octacode.domain.mission.MissionRepository
import com.sahil.octacode.domain.mission.MissionStatus
import com.sahil.octacode.ui.components.AccentInfoCard
import com.sahil.octacode.ui.components.AgentHeroCopy
import com.sahil.octacode.ui.components.AgentStatusPill
import com.sahil.octacode.ui.components.AgentTopBar
import com.sahil.octacode.ui.components.ChatBubble
import com.sahil.octacode.ui.components.ComposerPill
import com.sahil.octacode.ui.components.GoldSendButton
import com.sahil.octacode.ui.components.HeroPlanet
import com.sahil.octacode.ui.components.ThinkingOrb
import com.sahil.octacode.ui.navigation.Routes
import com.sahil.octacode.ui.theme.Gold
import com.sahil.octacode.ui.theme.NeonBlue
import com.sahil.octacode.ui.theme.NeonGreen
import com.sahil.octacode.ui.theme.OnDark
import com.sahil.octacode.ui.theme.OnDarkMuted
import com.sahil.octacode.ui.theme.WarningAmber
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/**
 * M4 Agent workspace — pixel-aligned to the reference:
 * header · status pill · hero+planet · status cards · gold composer.
 * Streaming still comes from real ChatEngine / M2 adapters.
 */
@Composable
fun ChatScreen(
    onOpenMenu: () -> Unit = {},
    onOpenProfile: () -> Unit = {},
    onOpenProjects: () -> Unit = {},
    engine: ChatEngine = koinInject(),
    repository: MissionRepository = koinInject()
) {
    val state by engine.state.collectAsState()
    var draft by remember { mutableStateOf("") }
    var providerMenu by remember { mutableStateOf(false) }
    var projectMenu by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var runningMissions by remember { mutableStateOf(0) }
    var missionNote by remember {
        mutableStateOf("A mission feed appears after a real runtime starts work. Nothing is running now.")
    }

    LaunchedEffect(Unit) {
        engine.refreshProviderStatus()
    }
    LaunchedEffect(Unit) {
        repository.observeMissions().collect { list ->
            val running = list.count {
                it.status == MissionStatus.RUNNING || it.status == MissionStatus.PAUSED
            }
            runningMissions = running
            missionNote = if (running == 0) {
                "A mission feed appears after a real runtime starts work. Nothing is running now."
            } else if (running == 1) {
                "1 mission active - open Home to inspect the pipeline."
            } else {
                "$running missions active - open Home to inspect the pipeline."
            }
        }
    }
    LaunchedEffect(state.turns.size, state.streamingText) {
        if (state.turns.isNotEmpty()) {
            listState.animateScrollToItem(state.turns.lastIndex)
        }
    }

    val status = state.providerStatus
    val ready = status is ProviderStatus.Ready
    val agentReady = ready
    val agentLabel = when {
        ready -> "Agent ready"
        status == null -> "Probing agent…"
        else -> "Agent unavailable"
    }
    val modelLabel = when {
        ready -> "${state.selectedProvider.title} ready"
        else -> "Model unavailable"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        AgentTopBar(
            onMenu = onOpenMenu,
            onSearch = {},
            onNotifications = {},
            onProfile = onOpenProfile
        )

        AgentStatusPill(
            projectLabel = "No project selected",
            agentLabel = agentLabel,
            agentReady = agentReady,
            onProjectClick = onOpenProjects,
            modifier = Modifier.fillMaxWidth()
        )

        if (state.turns.isEmpty()) {
            // —— reference empty / hero state ————————————————————
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
            ) {
                HeroPlanet(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .size(230.dp)
                )
                AgentHeroCopy(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(end = 120.dp)
                )
            }

            AccentInfoCard(
                title = if (ready) "Agent ready" else "Agent unavailable",
                message = if (ready) {
                    "Provider ${state.selectedProvider.title} is configured. Type a message to start a real streamed session."
                } else {
                    "No provider or local agent runtime is connected. Configure Model & Provider from the menu before starting a session."
                },
                accent = Gold,
                onClick = { scope.launch { engine.refreshProviderStatus() } },
                icon = {
                    Icon(
                        Icons.Outlined.SmartToy,
                        contentDescription = null,
                        tint = Gold,
                        modifier = Modifier.size(28.dp)
                    )
                }
            )

            AccentInfoCard(
                title = "Mission activity",
                message = missionNote,
                accent = NeonBlue,
                onClick = onOpenProjects,
                icon = {
                    Icon(
                        Icons.Outlined.TrackChanges,
                        contentDescription = null,
                        tint = NeonBlue,
                        modifier = Modifier.size(28.dp)
                    )
                }
            )
        } else {
            // —— live conversation ———————————————————————————————
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
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

        state.lastError?.let { err ->
            Text(
                text = err,
                style = MaterialTheme.typography.bodySmall,
                color = WarningAmber,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }

        // —— composer (reference bottom panel) ————————————————————
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(
                    androidx.compose.ui.graphics.Brush.verticalGradient(
                        listOf(
                            Color.White.copy(alpha = 0.05f),
                            Color.White.copy(alpha = 0.025f)
                        )
                    )
                )
                .border(
                    1.2.dp,
                    androidx.compose.ui.graphics.Brush.horizontalGradient(
                        listOf(
                            Gold.copy(alpha = 0.75f),
                            Color.White.copy(alpha = 0.1f),
                            NeonBlue.copy(alpha = 0.55f)
                        )
                    ),
                    RoundedCornerShape(22.dp)
                )
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    ComposerPill(
                        label = modelLabel,
                        leadingIcon = {
                            Icon(
                                Icons.Outlined.AllInclusive,
                                contentDescription = null,
                                tint = if (ready) NeonBlue else OnDarkMuted,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        onClick = { providerMenu = true },
                        accent = if (ready) NeonBlue else Gold,
                        modifier = Modifier.fillMaxWidth()
                    )
                    DropdownMenu(
                        expanded = providerMenu,
                        onDismissRequest = { providerMenu = false }
                    ) {
                        ProviderId.entries.forEach { id ->
                            DropdownMenuItem(
                                text = {
                                    Text(if (id == state.selectedProvider) "✓ ${id.title}" else id.title)
                                },
                                onClick = {
                                    engine.selectProvider(id)
                                    providerMenu = false
                                    scope.launch { engine.refreshProviderStatus() }
                                }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("Refresh status") },
                            onClick = {
                                providerMenu = false
                                scope.launch { engine.refreshProviderStatus() }
                            }
                        )
                    }
                }
                ComposerPill(
                    label = "Project",
                    leadingIcon = {
                        Icon(
                            Icons.Outlined.Folder,
                            contentDescription = null,
                            tint = NeonBlue,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    onClick = { projectMenu = true },
                    accent = NeonBlue
                )
                DropdownMenu(
                    expanded = projectMenu,
                    onDismissRequest = { projectMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("No project selected") },
                        onClick = { projectMenu = false }
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(28.dp))
                    .background(Color.White.copy(alpha = 0.035f))
                    .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(28.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = {}, enabled = false) {
                    Icon(Icons.Filled.AttachFile, contentDescription = "Attach", tint = OnDarkMuted, modifier = Modifier.size(20.dp))
                }
                IconButton(onClick = {}, enabled = false) {
                    Icon(Icons.Filled.Description, contentDescription = "File", tint = OnDarkMuted, modifier = Modifier.size(20.dp))
                }
                IconButton(onClick = {}, enabled = false) {
                    Icon(Icons.Filled.Mic, contentDescription = "Voice", tint = OnDarkMuted, modifier = Modifier.size(20.dp))
                }
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(24.dp)
                        .background(Color.White.copy(alpha = 0.16f))
                )
                Spacer(Modifier.width(10.dp))
                androidx.compose.material3.OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    modifier = Modifier.weight(1f),
                    enabled = !state.busy,
                    placeholder = {
                        Text("Type a message...", color = OnDarkMuted, style = MaterialTheme.typography.bodyLarge)
                    },
                    colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent,
                        focusedTextColor = OnDark,
                        unfocusedTextColor = OnDark,
                        cursorColor = Gold
                    ),
                    singleLine = true
                )
                Spacer(Modifier.width(8.dp))
                GoldSendButton(
                    enabled = draft.isNotBlank() && ready && !state.busy,
                    busy = state.busy,
                    onClick = {
                        if (state.busy) {
                            engine.stop()
                        } else if (engine.send(draft)) {
                            draft = ""
                        }
                    }
                )
            }

            Text(
                text = when {
                    state.busy -> "Streaming from provider…"
                    ready -> "In-memory session · lost on process death"
                    else -> "Connect a provider or local runtime to send a message."
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (ready) OnDarkMuted else OnDarkMuted.copy(alpha = 0.9f)
            )
        }
    }
}
