package com.sahil.octacode.ui.screens

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material.icons.outlined.TrackChanges
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
import com.sahil.octacode.ui.components.NeonSendButton
import com.sahil.octacode.ui.components.HeroPlanet
import com.sahil.octacode.ui.components.ThinkingOrb
import com.sahil.octacode.ui.theme.ElectricPurple
import com.sahil.octacode.ui.theme.NeonBlue
import com.sahil.octacode.ui.theme.NeonBlueBright
import com.sahil.octacode.ui.theme.OnDark
import com.sahil.octacode.ui.theme.OnDarkMuted
import com.sahil.octacode.ui.theme.WarningAmber
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/**
 * Agent workspace — reference layout with composer pinned to the bottom.
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
    // Set when send is tapped while the provider is not ready, so the caption
    // under the composer can escalate from a hint to the actual reason.
    var sendBlocked by remember { mutableStateOf(false) }
    var projectMenu by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
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
            missionNote = if (running == 0) {
                "A mission feed appears after a real runtime starts work. Nothing is running now."
            } else if (running == 1) {
                "1 mission active - open Workspace to inspect the pipeline."
            } else {
                "$running missions active - open Workspace to inspect the pipeline."
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
    val agentLabel = when {
        ready -> "Agent ready"
        status == null -> "Probing agent..."
        else -> "Agent unavailable"
    }
    val modelLabel = when {
        ready -> "${state.selectedProvider.title} ready"
        else -> "Model unavailable"
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // —— header + status + content (scrollable) ————————————————
        Column(
            modifier = Modifier
                .weight(1f, fill = true)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
        ) {
            Box(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                AgentTopBar(
                    onMenu = onOpenMenu,
                    onSearch = {},
                    onNotifications = {},
                    onProfile = onOpenProfile
                )
            }

            AgentStatusPill(
                projectLabel = "No project selected",
                agentLabel = agentLabel,
                agentReady = ready,
                onProjectClick = onOpenProjects,
                modifier = Modifier.padding(horizontal = 20.dp)
            )

            if (state.turns.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .padding(top = 8.dp)
                ) {
                    HeroPlanet(
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .size(280.dp)
                    )
                    AgentHeroCopy(
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .padding(start = 20.dp, end = 120.dp)
                    )
                }

                Column(
                    modifier = Modifier.padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    AccentInfoCard(
                        title = if (ready) "Agent ready" else "Agent unavailable",
                        message = if (ready) {
                            "Provider ${state.selectedProvider.title} is configured. Type a message to start a real streamed session."
                        } else {
                            "No provider or local agent runtime is connected. Configure Model & Provider from the menu before starting a session."
                        },
                        accent = ElectricPurple,
                        onClick = {
                            if (ready) {
                                scope.launch { engine.refreshProviderStatus() }
                            } else {
                                // Sending the user to Settings and then not taking
                                // them there is the same as saying nothing.
                                onOpenMenu()
                            }
                        },
                        icon = {
                            Icon(
                                Icons.Outlined.SmartToy,
                                contentDescription = null,
                                tint = ElectricPurple,
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
                }
                Spacer(modifier = Modifier.height(8.dp))
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 200.dp, max = 480.dp)
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(state.turns, key = { it.id }) { turn ->
                        ChatBubble(turn = turn)
                    }
                }
                if (state.busy && state.streamingText.isNullOrEmpty()) {
                    Box(modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) {
                        ThinkingOrb(label = "Waiting for provider...")
                    }
                }
            }

            state.lastError?.let { err ->
                Text(
                    text = err,
                    style = MaterialTheme.typography.bodySmall,
                    color = WarningAmber,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp)
                )
            }

            // A failed history write has to be said out loud. The chat keeps
            // running from memory, so without this the user would only find
            // out their conversation vanished the next time they open it.
            state.persistenceError?.let { reason ->
                Text(
                    text = "Not saved · $reason",
                    style = MaterialTheme.typography.bodySmall,
                    color = WarningAmber,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp)
                )
            }
        }

        // —— composer pinned to bottom (always visible) ————————————
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 10.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.White.copy(alpha = 0.05f),
                            Color.White.copy(alpha = 0.02f)
                        )
                    )
                )
                .border(
                    1.5.dp,
                    Brush.horizontalGradient(
                        listOf(
                            NeonBlue.copy(alpha = 0.90f),
                            NeonBlue.copy(alpha = 0.35f),
                            ElectricPurple.copy(alpha = 0.55f),
                            ElectricPurple.copy(alpha = 0.95f)
                        )
                    ),
                    RoundedCornerShape(20.dp)
                )
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(modifier = Modifier.weight(1.55f)) {
                    ComposerPill(
                        label = modelLabel,
                        leadingIcon = {
                            Icon(
                                Icons.Outlined.Layers,
                                contentDescription = null,
                                tint = NeonBlue,
                                modifier = Modifier.size(20.dp)
                            )
                        },
                        onClick = { providerMenu = true },
                        modifier = Modifier.fillMaxWidth()
                    )
                    DropdownMenu(
                        expanded = providerMenu,
                        onDismissRequest = { providerMenu = false }
                    ) {
                        ProviderId.entries.forEach { id ->
                            DropdownMenuItem(
                                text = {
                                    Text(if (id == state.selectedProvider) "• ${id.title}" else id.title)
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
                Box(modifier = Modifier.weight(1f)) {
                    ComposerPill(
                        label = "Project",
                        leadingIcon = {
                            Icon(
                                Icons.Outlined.Folder,
                                contentDescription = null,
                                tint = NeonBlue,
                                modifier = Modifier.size(20.dp)
                            )
                        },
                        onClick = { projectMenu = true },
                        modifier = Modifier.fillMaxWidth()
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
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(28.dp))
                    .background(
                        Brush.horizontalGradient(
                            listOf(Color.White.copy(alpha = 0.035f), NeonBlue.copy(alpha = 0.05f))
                        )
                    )
                    .border(
                        1.dp,
                        Brush.horizontalGradient(
                            listOf(
                                NeonBlue.copy(alpha = 0.50f),
                                Color.White.copy(alpha = 0.14f),
                                ElectricPurple.copy(alpha = 0.45f)
                            )
                        ),
                        RoundedCornerShape(28.dp)
                    )
                    .padding(start = 4.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clickable { }
                        .padding(8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.AttachFile,
                        contentDescription = "Attach",
                        tint = OnDarkMuted,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clickable { }
                        .padding(8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.Description,
                        contentDescription = "Files",
                        tint = OnDarkMuted,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clickable { }
                        .padding(8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.Mic,
                        contentDescription = "Voice",
                        tint = OnDarkMuted,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Box(
                    modifier = Modifier
                        .padding(horizontal = 2.dp)
                        .width(1.dp)
                        .height(26.dp)
                        .background(Color.White.copy(alpha = 0.18f))
                )
                Spacer(Modifier.width(6.dp))
                BasicTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    modifier = Modifier
                        .weight(1f)
                        .padding(vertical = 8.dp),
                    enabled = !state.busy,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        color = OnDark,
                        fontSize = 15.sp
                    ),
                    cursorBrush = Brush.linearGradient(listOf(NeonBlue, NeonBlueBright)),
                    singleLine = true,
                    decorationBox = { innerTextField ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            if (draft.isEmpty()) {
                                Text(
                                    text = "Type a message...",
                                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp),
                                    color = OnDarkMuted
                                )
                            }
                            innerTextField()
                        }
                    }
                )
                Spacer(Modifier.width(4.dp))
                NeonSendButton(
                    enabled = draft.isNotBlank() && ready && !state.busy,
                    busy = state.busy,
                    onClick = {
                        if (state.busy) {
                            engine.stop()
                        } else if (engine.send(draft)) {
                            draft = ""
                        }
                    },
                    onBlocked = { sendBlocked = true }
                )
            }

            // The send button cannot act right now, so say exactly why and where to
            // fix it. The old fixed "connect a provider" line never changed, which
            // made a config step look like the app simply not working.
            val reason = status?.reason ?: "No provider is configured"
            Text(
                text = when {
                    state.busy -> "Streaming from provider..."
                    ready -> "In-memory session - lost on process death"
                    sendBlocked -> "$reason - tap here to open Providers"
                    else -> "$reason. Tap to open Providers."
                },
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                color = if (sendBlocked) NeonBlue else OnDarkMuted,
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (!ready && !state.busy) {
                            Modifier.clickable {
                                sendBlocked = false
                                onOpenMenu()
                            }
                        } else {
                            Modifier
                        }
                    )
            )
        }
    }
}
