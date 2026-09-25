package com.sahil.octacode.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sahil.octacode.core.capability.AutonomyLevel
import com.sahil.octacode.core.capability.CapabilityRegistry
import org.koin.compose.koinInject

/**
 * How far the agent may act before it has to ask.
 *
 * The picker writes straight through the registry, so the level on screen is
 * the level the engine will use on the next decision — not a stored preference
 * waiting for a restart.
 */
@Composable
fun SettingsAutonomyScreen(
    onBack: () -> Unit,
    registry: CapabilityRegistry = koinInject(),
) {
    val autonomy by registry.autonomy.collectAsState()

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
            Text("Autonomy", style = MaterialTheme.typography.headlineMedium)
        }

        AutonomyPicker(autonomy) { registry.setAutonomy(it) }

        Text(
            "Dangerous actions require approval at ASK level (default).",
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun AutonomyPicker(current: AutonomyLevel, onPick: (AutonomyLevel) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("Current: ${current.name}", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = { open = true }) { Text("Change autonomy") }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                AutonomyLevel.entries.forEach { level ->
                    DropdownMenuItem(
                        text = { Text(level.name) },
                        onClick = { onPick(level); open = false }
                    )
                }
            }
        }
    }
}
