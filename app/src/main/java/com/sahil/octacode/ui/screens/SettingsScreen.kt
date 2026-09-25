package com.sahil.octacode.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sahil.octacode.ui.components.SectionHeaderText

/**
 * The settings hub.
 *
 * It holds no controls of its own. Every row opens a page where the setting
 * actually lives, which is what makes the hub honest: there is no option here
 * that cannot be changed on the screen it leads to.
 *
 * Branding law: creator credits live HERE (and Splash) only — never in
 * Chat/Terminal.
 */
@Composable
fun SettingsScreen(
    onOpenAppearance: () -> Unit = {},
    onOpenProviders: () -> Unit = {},
    onOpenAutonomy: () -> Unit = {},
    onOpenRuntime: () -> Unit = {},
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium)

        SettingsNavRow(
            title = "Appearance",
            subtitle = "Text size, theme and syntax highlighting",
            onClick = onOpenAppearance,
        )
        SettingsNavRow(
            title = "Providers",
            subtitle = "API keys, endpoints and model access",
            onClick = onOpenProviders,
        )
        SettingsNavRow(
            title = "Autonomy",
            subtitle = "How much the agent may do unattended",
            onClick = onOpenAutonomy,
        )
        SettingsNavRow(
            title = "Runtime",
            subtitle = "Download and verify the coding runtime",
            onClick = onOpenRuntime,
        )

        SectionHeaderText("About")

        // Creator credits (Settings-only per branding law).
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("Crafted with ❤ by Sahil (Octavian)", style = MaterialTheme.typography.titleMedium)
                Text("sahilsahilimtiyaz@gmail.com", style = MaterialTheme.typography.bodySmall)
                Text("TikTok: @mog-octavian  •  YouTube: CHADFRAMED", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(4.dp))
                Text("Octa Code v1.0.0", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}
