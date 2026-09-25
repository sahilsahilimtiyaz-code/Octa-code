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
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sahil.octacode.core.settings.Settings
import com.sahil.octacode.data.settings.SettingsRepository
import com.sahil.octacode.ui.components.LockedRow
import com.sahil.octacode.ui.components.SectionHeaderText
import com.sahil.octacode.ui.theme.OnDarkMuted
import org.koin.compose.koinInject

/**
 * Server configuration, for a build that does not run a server.
 *
 * The two values are real: they are written, clamped to ports this app could
 * actually bind, and still there after a restart. What does not exist yet is a
 * process to hand them to, so both rows say that instead of presenting a switch
 * over a service that would not start when flipped.
 *
 * Keeping the fields now means the server milestone inherits whatever the user
 * already chose rather than asking them to configure it twice.
 */
@Composable
fun SettingsServerScreen(
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
            Text("Server", style = MaterialTheme.typography.headlineMedium)
        }

        Card(Modifier.fillMaxWidth()) {
            Column(
                Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    "No server runs in this build",
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = "There is nothing to start, reach or debug yet: the local " +
                        "server ships with the on-device runtime. This page therefore " +
                        "records what it will bind, and says plainly that it cannot be " +
                        "changed until there is a process to bind it to.",
                    style = MaterialTheme.typography.bodySmall,
                    color = OnDarkMuted,
                )
            }
        }

        SectionHeaderText("Configuration")

        LockedRow(
            title = "Enable local server",
            value = if (settings.serverEnabled) "On" else "Off",
            reason = "No server process exists to start, so turning this on would " +
                "change a value nothing reads.",
        )
        LockedRow(
            title = "Port",
            value = settings.serverPort.toString(),
            reason = "Nothing is listening yet, so a different port would be equally " +
                "silent. Values are kept between ${Settings.MIN_SERVER_PORT} and " +
                "${Settings.MAX_SERVER_PORT}.",
        )

        Text(
            "Both values are stored now and survive a restart, so the release that " +
                "adds the server starts from what you have already chosen.",
            style = MaterialTheme.typography.bodySmall,
            color = OnDarkMuted,
        )
    }
}
