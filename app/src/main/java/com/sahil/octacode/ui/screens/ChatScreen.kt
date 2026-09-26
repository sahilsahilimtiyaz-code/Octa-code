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
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material.icons.outlined.TrackChanges
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.sahil.octacode.core.model.ModelCatalog
import com.sahil.octacode.core.provider.AiProvider
import com.sahil.octacode.core.provider.ProviderId
import com.sahil.octacode.core.settings.SendBehavior
import com.sahil.octacode.data.settings.SettingsRepository
import com.sahil.octacode.data.workspace.WorkspaceStore
import com.sahil.octacode.domain.chat.ChatEngine
import com.sahil.octacode.domain.mission.MissionRepository
import com.sahil.octacode.domain.mission.MissionStatus
import com.sahil.octacode.domain.model.FetchedModelsRepository
import com.sahil.octacode.domain.model.ModelUserStateRepository
import com.sahil.octacode.ui.components.AccentInfoCard
import com.sahil.octacode.ui.components.AgentHeroCopy
import com.sahil.octacode.ui.components.AgentStatusPill
import com.sahil.octacode.ui.components.AgentTopBar
import com.sahil.octacode.ui.components.ChatBubble
import com.sahil.octacode.ui.components.ComposerPill
import com.sahil.octacode.ui.components.ModelSelectorSheet
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
    /**
     * Where the folder pill and the composer's folder chip lead — both are
     * promises to choose where work happens, and only Workspaces keeps them.
     */
    onOpenWorkspaces: () -> Unit = {},
    engine: ChatEngine = koinInject(),
    repository: MissionRepository = koinInject(),
    modelState: ModelUserStateRepository = koinInject(),
    /** Which endpoints have a registered adapter, so the sheet can say so. */
    adapters: Map<ProviderId, AiProvider> = koinInject(),
    settingsRepository: SettingsRepository = koinInject(),
    workspaceStore: WorkspaceStore = koinInject(),
    /**
     * Fetched model lists. Read here so the sheet, the header label and the
     * engine's own id resolution can all be handed the same merged list — three
     * views of "what is selectable" that disagree would show a name the send
     * path has never heard of.
     */
    fetchedModels: FetchedModelsRepository = koinInject(),
) {
    val state by engine.state.collectAsState()
    val modelStates by modelState.states.collectAsState()
    val settings by settingsRepository.settings.collectAsState()
    val folderList by workspaceStore.workspaces.collectAsState()
    val activeFolderId by workspaceStore.activeId.collectAsState()
    val fetched by fetchedModels.models.collectAsState()
    /**
     * Read from the id rather than held, so a folder renamed or removed here
     * cannot leave the pill naming something that no longer exists.
     */
    val activeFolder = folderList.firstOrNull { it.id == activeFolderId }?.displayName
    /**
     * Queue mode is what decides whether the composer can be used mid-response
     * at all — under IMMEDIATELY it stays read-only, as it always has.
     */
    val queueMode = settings.sendBehavior == SendBehavior.QUEUE
    var draft by remember { mutableStateOf("") }
    var modelSheet by remember { mutableStateOf(false) }
    // Set when send is tapped while the provider is not ready, so the caption
    // under the composer can escalate from a hint to the actual reason.
    var sendBlocked by remember { mutableStateOf(false) }
    var projectMenu by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var missionNote by remember {
        mutableStateOf("Nothing is running — a mission feed appears once work starts.")
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
    val agentLabel = when {
        ready -> "Agent ready"
        status == null -> "Probing agent..."
        else -> "Agent unavailable"
    }
    // Readiness already lives in agentLabel above; this pill answers the
    // different question — which model is next to go out.
    //
    // Merged rather than read from ModelCatalog.bundled: a model a provider
    // fetched has no bundled entry, so resolving it to null would drop it from
    // this header while the engine still had it selected and was going to send
    // it.
    val models = remember(fetched) { ModelCatalog.mergedWith(fetched) }
    val pickedModel = state.selectedModelId?.let { id -> models.firstOrNull { it.id == id } }
    val modelLabel = pickedModel?.displayName ?: state.selectedProvider.title

    if (modelSheet) {
        ModelSelectorSheet(
            selectedModelId = state.selectedModelId,
            selectedProvider = state.selectedProvider,
            states = modelStates,
            models = models,
            availableProviders = adapters.keys,
            busy = state.busy,
            // Both close and re-probe: selectModel/selectProvider drop the
            // probe, and leaving the user on "Probing..." with no way to
            // resolve it would be the half-finished action this app avoids.
            onSelectModel = { model ->
                engine.selectModel(model)
                modelSheet = false
                scope.launch { engine.refreshProviderStatus() }
            },
            onSelectEndpoint = { id ->
                engine.selectProvider(id)
                modelSheet = false
                scope.launch { engine.refreshProviderStatus() }
            },
            onToggleFavorite = { model, favorite -> modelState.setFavorite(model.id, favorite) },
            onRefreshStatus = {
                modelSheet = false
                scope.launch { engine.refreshProviderStatus() }
            },
            onDismiss = { modelSheet = false },
        )
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
                // What the pill actually reports: where work happens. The old
                // "No project selected" pointed at an index this build does
                // not have; a folder is the thing that has always been
                // selectable here, and it is now a real destination.
                projectLabel = activeFolder ?: "No folder selected",
                agentLabel = agentLabel,
                agentReady = ready,
                onProjectClick = onOpenWorkspaces,
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
                            "${state.selectedProvider.title} is configured. Type a message to start."
                        } else {
                            "No provider connected. Add one in Settings → Providers."
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
                // A failed send writes its reason into the bubble now, and that
                // bubble stays after the next send clears lastError — printing
                // the identical sentence twice at the same moment is only noise.
                // This line therefore keeps the errors that live nowhere else:
                // provider unavailable, no adapter, conversation gone.
                if (state.turns.lastOrNull()?.error != err) {
                    Text(
                        text = err,
                        style = MaterialTheme.typography.bodySmall,
                        color = WarningAmber,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp)
                    )
                }
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
                // Flat surface colour, neutral border: the controls sitting
                // inside it carry the affordance, so the container should not
                // out-shout what it contains.
                .background(Color.White.copy(alpha = 0.04f))
                .border(
                    1.5.dp,
                    Color.White.copy(alpha = 0.10f),
                    RoundedCornerShape(20.dp)
                )
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
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
                        onClick = { modelSheet = true },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Box(modifier = Modifier.weight(1f)) {
                    ComposerPill(
                        label = activeFolder ?: "Folder",
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
                        // Every entry does the real thing: choosing one marks
                        // it as the folder new conversations record, and the
                        // last row leaves for the screen that can add more.
                        folderList.forEach { folder ->
                            val chosen = folder.id == activeFolderId
                            DropdownMenuItem(
                                text = { Text(if (chosen) "${folder.displayName} ✓" else folder.displayName) },
                                onClick = {
                                    if (chosen) {
                                        workspaceStore.clearActive()
                                    } else {
                                        workspaceStore.setActive(folder.id)
                                    }
                                    projectMenu = false
                                }
                            )
                        }
                        if (folderList.isEmpty()) {
                            // Disabled, not merely inert: a menu item that
                            // looks tappable and only closes the menu is the
                            // silent no-op this app does not ship. Dimmed, it
                            // reads as the statement it is.
                            DropdownMenuItem(
                                text = { Text("No folder chosen yet") },
                                onClick = { projectMenu = false },
                                enabled = false
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("Choose a folder…") },
                            onClick = {
                                projectMenu = false
                                onOpenWorkspaces()
                            }
                        )
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(28.dp))
                    .background(Color.White.copy(alpha = 0.04f))
                    // The mid-stop of the sweep it replaces: an input field
                    // should look like a field, not like a lit edge.
                    .border(
                        1.dp,
                        Color.White.copy(alpha = 0.14f),
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
                    // Read-only mid-response unless the user chose to queue:
                    // under IMMEDIATELY a usable composer would invite a send
                    // the button then refuses without saying so.
                    enabled = !state.busy || queueMode,
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
                // Under QUEUE the round button keeps meaning "send", so stop
                // needs a place of its own: one control meaning send half the
                // time and stop the other half is how a stream gets abandoned
                // by someone reaching for send.
                if (state.busy && queueMode) {
                    IconButton(onClick = { engine.stop() }) {
                        Icon(
                            Icons.Filled.Stop,
                            contentDescription = "Stop response",
                            tint = OnDarkMuted,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
                NeonSendButton(
                    enabled = draft.isNotBlank() && ready && (!state.busy || queueMode),
                    busy = state.busy && !queueMode,
                    onClick = {
                        when {
                            state.busy && queueMode ->
                                if (engine.send(draft, queueIfBusy = true)) draft = ""
                            state.busy -> engine.stop()
                            else -> if (engine.send(draft)) draft = ""
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
                    state.queuedText != null -> "Queued - sends when this response finishes"
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
