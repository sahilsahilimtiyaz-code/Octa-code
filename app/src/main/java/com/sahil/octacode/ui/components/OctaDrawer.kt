package com.sahil.octacode.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.sahil.octacode.domain.chat.ChatEngine
import com.sahil.octacode.domain.chat.ChatRepository
import com.sahil.octacode.ui.theme.DeepSpaceBlack
import com.sahil.octacode.ui.theme.OnDark
import com.sahil.octacode.ui.theme.OnDarkMuted
import com.sahil.octacode.ui.theme.WarningAmber
import org.koin.compose.koinInject

/** How many conversations the drawer shows before pointing at the full list. */
private const val RECENT_LIMIT = 5

/**
 * The side drawer: Recent Chats, plus the two destinations the header's
 * menu button used to carry on its own.
 *
 * Recent Chats is live data, not a snapshot — it reads
 * [ChatRepository.sessions], the same flow Sessions settings renders, so the
 * two lists can never disagree about what exists.
 *
 * Tapping a row does the real thing: [ChatEngine.openSession] reloads that
 * conversation into the one engine the chat screen is already bound to, which
 * is the mechanism the engine was built around. That call refuses while a
 * stream is in flight and reports nothing when it does, so these rows are
 * inert and say why during a response rather than swallowing the tap — a
 * silently ignored row is the decorative affordance this app does not ship.
 *
 * Agents are deliberately absent. [com.sahil.octacode.core.agent.AgentDef]
 * is a model with no registry behind it yet, so there is nothing for a row
 * to open; a section advertising the category would offer an option that
 * cannot be taken.
 */
@Composable
fun OctaDrawer(
    onOpenSession: (String) -> Unit,
    onOpenProjects: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenManageChats: () -> Unit,
    modifier: Modifier = Modifier,
    chatRepository: ChatRepository = koinInject(),
    engine: ChatEngine = koinInject(),
) {
    val sessions by chatRepository.sessions.collectAsState(initial = emptyList())
    val engineState by engine.state.collectAsState()

    val recent = sessions.asSequence()
        .filter { it.isActive }
        .take(RECENT_LIMIT)
        .toList()

    ModalDrawerSheet(
        modifier = modifier,
        drawerContainerColor = DeepSpaceBlack,
        drawerContentColor = OnDark,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = "Octa Code",
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                style = MaterialTheme.typography.headlineSmall,
                color = OnDark,
            )
            Text(
                text = "Agent → Runtime → Model → Workspace",
                modifier = Modifier.padding(horizontal = 20.dp),
                style = MaterialTheme.typography.labelMedium,
                color = OnDarkMuted,
            )

            Spacer(Modifier.height(20.dp))

            SectionHeaderText(
                text = "Recent Chats",
                modifier = Modifier.padding(horizontal = 20.dp),
            )

            if (recent.isEmpty()) {
                Text(
                    // Two genuinely different situations, and only one of them
                    // is "you have nothing". Telling someone whose entire
                    // history is archived that there are no conversations yet
                    // would describe their own data wrongly.
                    text = if (sessions.isEmpty()) {
                        "No conversations yet. One begins the first time " +
                            "you send a message."
                    } else {
                        "No active conversations — the rest are archived."
                    },
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = OnDarkMuted,
                )
            } else {
                recent.forEach { session ->
                    DrawerRow(
                        title = session.title,
                        // Inert while a stream runs: openSession refuses then,
                        // and returns false without saying so.
                        enabled = !engineState.busy,
                        onClick = { onOpenSession(session.id) },
                    )
                }
            }

            // Shown whenever history exists — which is precisely the case the
            // block above cannot list. Every conversation archived leaves no
            // rows above, and the one screen that holds them still has to be
            // reachable from here.
            if (sessions.isNotEmpty()) {
                DrawerRow(
                    title = "Manage conversations",
                    enabled = true,
                    onClick = onOpenManageChats,
                )
            }

            // Only while rows above are actually gated; the note explains why
            // they are dim, so with none to dim it would explain nothing.
            if (engineState.busy && recent.isNotEmpty()) {
                Text(
                    text = "A response is still running, so a conversation " +
                        "cannot be swapped into the view. Open one when it " +
                        "finishes.",
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = WarningAmber,
                )
            }

            Spacer(Modifier.height(16.dp))

            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 20.dp),
                color = OnDarkMuted.copy(alpha = 0.25f),
            )

            Spacer(Modifier.height(8.dp))

            DrawerNavRow(
                title = "Projects",
                icon = Icons.Outlined.Folder,
                onClick = onOpenProjects,
            )
            DrawerNavRow(
                title = "Settings",
                icon = Icons.Outlined.Settings,
                onClick = onOpenSettings,
            )
        }
    }
}

/** One recent conversation. Disabled rows dim instead of pretending to work. */
@Composable
private fun DrawerRow(
    title: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = if (enabled) OnDark else OnDarkMuted,
            maxLines = 1,
        )
    }
}

/** A destination that exists — both of these route to a real screen. */
@Composable
private fun DrawerNavRow(
    title: String,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = OnDarkMuted,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            color = OnDark,
        )
    }
}
