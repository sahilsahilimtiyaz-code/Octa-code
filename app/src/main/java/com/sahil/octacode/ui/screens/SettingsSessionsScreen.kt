package com.sahil.octacode.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sahil.octacode.core.settings.Settings
import com.sahil.octacode.data.settings.SettingsRepository
import com.sahil.octacode.domain.chat.ChatRepository
import com.sahil.octacode.domain.chat.ChatSession
import com.sahil.octacode.domain.chat.RetentionOutcome
import com.sahil.octacode.domain.chat.RetentionReport
import com.sahil.octacode.ui.components.BannerTone
import com.sahil.octacode.ui.components.EmptyStateCard
import com.sahil.octacode.ui.components.SectionHeaderText
import com.sahil.octacode.ui.components.StatusBanner
import com.sahil.octacode.ui.theme.OnDarkMuted
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import kotlin.math.roundToInt

/**
 * Retention rules, and the conversations they act on — on one screen.
 *
 * The lists are the point. Four settings ("auto archive", "archive after",
 * "limit active chats", "maximum") decide when a conversation leaves the
 * active list, and a rule that moves things without showing where they went
 * is indistinguishable from data loss. So every archived conversation is
 * listed here with a restore, and whatever the startup pass did is reported
 * above it rather than noted in a log nobody reads.
 */
@Composable
fun SettingsSessionsScreen(
    onBack: () -> Unit,
    settingsRepository: SettingsRepository = koinInject(),
    chatRepository: ChatRepository = koinInject(),
    report: RetentionReport = koinInject(),
) {
    val settings by settingsRepository.settings.collectAsState()
    val sessions by chatRepository.sessions.collectAsState(initial = emptyList())
    val outcome by report.outcome.collectAsState()
    val scope = rememberCoroutineScope()
    val now = System.currentTimeMillis()

    val active = sessions.filter { it.isActive }
    val archived = sessions.filter { it.archivedAt != null }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("Back") }
            Spacer(Modifier.width(4.dp))
            Text("Sessions", style = MaterialTheme.typography.headlineMedium)
        }

        when (val o = outcome) {
            is RetentionOutcome.Failed -> ReportBanner(
                tone = BannerTone.Error,
                title = "Retention did not run",
                message = "Auto archive could not run this launch: ${o.reason}. " +
                    "No conversation was moved.",
                onDismiss = { report.clear() },
            )
            is RetentionOutcome.Archived ->
                if (o.sessions.isNotEmpty()) {
                    ReportBanner(
                        tone = BannerTone.Warning,
                        title = "Archived ${o.sessions.size} conversation" +
                            if (o.sessions.size == 1) "" else "s",
                        message = "Idle past your window at launch. They are listed under " +
                            "Archived below — restore any of them from there.",
                        onDismiss = { report.clear() },
                    )
                }
            else -> Unit
        }

        SectionHeaderText("Retention")

        ToggleCard(
            title = "Auto archive",
            description = "Move conversations idle longer than the window below out of the " +
                "active list. Runs at launch.",
            checked = settings.autoArchive,
            onCheckedChange = { enabled ->
                settingsRepository.update { it.copy(autoArchive = enabled) }
            },
        )

        ArchiveAfterCard(
            selectedDays = settings.archiveAfterDays,
            note = if (settings.autoArchive) {
                "A conversation with no activity for this long moves to Archived."
            } else {
                "Only applies while auto archive is on."
            },
            onSelect = { days ->
                settingsRepository.update { it.copy(archiveAfterDays = days) }
            },
        )

        ToggleCard(
            title = "Limit active chats",
            description = "Keep at most the number below in the active list. The oldest move " +
                "to Archived, newest are kept.",
            checked = settings.limitActiveChats,
            onCheckedChange = { enabled ->
                settingsRepository.update { it.copy(limitActiveChats = enabled) }
            },
        )

        SettingSlider(
            title = "Maximum active chats",
            description = if (settings.limitActiveChats) {
                null
            } else {
                "Only applies while limit active chats is on."
            },
            display = "${settings.maxActiveChats}",
            value = settings.maxActiveChats.toFloat(),
            valueRange = Settings.MIN_ACTIVE_CHATS.toFloat()..Settings.MAX_ACTIVE_CHATS.toFloat(),
            // One step per whole chat between the endpoints, which the range
            // spans exactly: (200 - 1) intervals minus the two counted already.
            steps = Settings.MAX_ACTIVE_CHATS - Settings.MIN_ACTIVE_CHATS - 1,
            onChange = { value ->
                settingsRepository.update { s ->
                    s.copy(maxActiveChats = value.roundToInt())
                }
            },
        )

        SectionHeaderText("Active (${active.size})")
        if (active.isEmpty()) {
            EmptyStateCard(
                title = "No active conversations",
                message = "Chats you start will be listed here.",
            )
        } else {
            active.forEach { session ->
                SessionCard(
                    session = session,
                    now = now,
                    onArchive = {
                        scope.launch {
                            chatRepository.setArchived(session.id, System.currentTimeMillis())
                        }
                    },
                )
            }
        }

        SectionHeaderText("Archived (${archived.size})")
        if (archived.isEmpty()) {
            EmptyStateCard(
                title = "Nothing archived",
                message = "Conversations moved out by a rule or by hand land here, and can be " +
                    "restored from here.",
            )
        } else {
            archived.forEach { session ->
                SessionCard(
                    session = session,
                    now = now,
                    onRestore = {
                        scope.launch { chatRepository.setArchived(session.id, null) }
                    },
                    onDelete = {
                        scope.launch { chatRepository.deleteSession(session.id) }
                    },
                )
            }
        }
    }
}

/**
 * What the startup pass did, stated where it can be acted on.
 *
 * Dismissible for this process only — the archived conversations are not
 * undone by dismissing, and a failed pass will report itself again next launch.
 */
@Composable
private fun ReportBanner(
    tone: BannerTone,
    title: String,
    message: String,
    onDismiss: () -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        StatusBanner(tone, title, message)
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(onClick = onDismiss) { Text("Dismiss") }
        }
    }
}

@Composable
private fun ArchiveAfterCard(
    selectedDays: Int,
    note: String,
    onSelect: (Int) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Archive after", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.weight(1f))
                Text(
                    "$selectedDays days",
                    style = MaterialTheme.typography.titleSmall,
                    color = OnDarkMuted,
                )
            }
            Text(note, style = MaterialTheme.typography.bodySmall, color = OnDarkMuted)
            OutlinedButton(onClick = { open = true }) { Text("Change window") }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                Settings.ARCHIVE_CHOICES.forEach { days ->
                    DropdownMenuItem(
                        text = { Text("$days days") },
                        onClick = { onSelect(days); open = false },
                        trailingIcon = {
                            if (days == selectedDays) {
                                Icon(
                                    imageVector = Icons.Outlined.Check,
                                    contentDescription = "Current selection",
                                    tint = OnDarkMuted,
                                )
                            }
                        },
                    )
                }
            }
        }
    }
}

/**
 * One stored conversation with the action the list it is in allows.
 *
 * Delete is two deliberate presses: it removes the turns with the session and
 * there is no undo, so it must not be reachable by a mis-tap next to Restore.
 */
@Composable
private fun SessionCard(
    session: ChatSession,
    now: Long,
    onArchive: (() -> Unit)? = null,
    onRestore: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
) {
    var confirmingDelete by remember(session.id) { mutableStateOf(false) }

    Card(Modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(session.title, style = MaterialTheme.typography.titleSmall)
            Text(
                buildString {
                    append("Last used ")
                    append(relativeTime(now, session.lastMessageAt))
                    session.archivedAt?.let {
                        append(" · archived ")
                        append(relativeTime(now, it))
                    }
                },
                style = MaterialTheme.typography.bodySmall,
                color = OnDarkMuted,
            )
            if (onArchive != null || onRestore != null || onDelete != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    onArchive?.let {
                        OutlinedButton(onClick = it) { Text("Archive") }
                    }
                    onRestore?.let {
                        OutlinedButton(onClick = it) { Text("Restore") }
                    }
                    onDelete?.let { delete ->
                        if (confirmingDelete) {
                            Button(onClick = { confirmingDelete = false; delete() }) {
                                Text("Tap again to delete")
                            }
                        } else {
                            OutlinedButton(onClick = { confirmingDelete = true }) {
                                Text("Delete")
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Ages in the coarsest unit that is still true — never "0 days ago".
 *
 * Internal rather than private so it can be pinned directly: relative times
 * are pure arithmetic with four boundaries, and getting one wrong is visible
 * on every session card at once.
 */
internal fun relativeTime(now: Long, then: Long): String {
    val minutes = (now - then).coerceAtLeast(0L) / 60_000L
    return when {
        minutes < 1L -> "just now"
        minutes < 60L -> "${minutes}m ago"
        minutes < 1_440L -> "${minutes / 60L}h ago"
        else -> "${minutes / 1_440L}d ago"
    }
}
