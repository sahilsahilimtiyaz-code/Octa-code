package com.sahil.octacode.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sahil.octacode.core.settings.SendBehavior
import com.sahil.octacode.data.settings.SettingsRepository
import com.sahil.octacode.ui.components.LockedRow
import com.sahil.octacode.ui.components.SectionHeaderText
import com.sahil.octacode.ui.theme.OnDarkMuted
import org.koin.compose.koinInject

/**
 * Chat behaviour: what happens while a response streams, and what would be
 * shown if there were something to show.
 *
 * Two of these three settings persisted while no screen anywhere displayed
 * them, which is the failure this app's own settings model warns about in its
 * header — a value written to disk and shown nowhere is not a setting, it is a
 * fossil. They are surfaced now, and locked with the reason each cannot yet be
 * operated, rather than omitted or offered as controls that change nothing.
 */
@Composable
fun SettingsChatScreen(
    onBack: () -> Unit,
    repository: SettingsRepository = koinInject(),
) {
    val settings by repository.settings.collectAsState()

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
            Text("Chat", style = MaterialTheme.typography.headlineMedium)
        }

        SectionHeaderText("Sending")

        EnumSettingCard(
            title = "While a response is streaming",
            current = settings.sendBehavior,
            options = SendBehavior.entries,
            label = { it.label },
            onPick = { next ->
                repository.update { it.copy(sendBehavior = next) }
            },
            // States what the choice actually does, because "queue while
            // streaming" means nothing until it says when the message goes.
            subtitle = when (settings.sendBehavior) {
                SendBehavior.IMMEDIATELY -> "The composer is read-only until the response finishes."
                SendBehavior.QUEUE -> "What you send during a response is held, then sent when it finishes."
            },
        )

        SectionHeaderText("Display")

        LockedRow(
            title = "Tool call detail",
            value = settings.toolCallDetail.label,
            reason = "Chat cannot request tools yet, so there are no tool calls to show " +
                "compactly, normally or in detail.",
        )
        LockedRow(
            title = "Auto-expand reasoning",
            value = if (settings.autoExpandReasoning) "On" else "Off",
            reason = "No adapter in this build streams reasoning back, so there is nothing " +
                "on screen to expand.",
        )

        Text(
            "These values are stored now and survive a restart. Each says what is missing " +
                "before it can be changed.",
            style = MaterialTheme.typography.bodySmall,
            color = OnDarkMuted,
        )
    }
}
