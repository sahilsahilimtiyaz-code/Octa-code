package com.sahil.octacode.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.sahil.octacode.core.capability.AutonomyLevel
import com.sahil.octacode.core.capability.CapabilityRegistry
import com.sahil.octacode.core.capability.ProviderStatus
import com.sahil.octacode.core.provider.CapabilityBadge
import com.sahil.octacode.core.provider.ProviderId
import com.sahil.octacode.data.security.CredentialStore
import com.sahil.octacode.ui.components.CapabilityBadgeChip
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

// Settings wires M1 registry + M2 credential store to real UI (no orphan code).
// Branding law: creator credits live HERE (and Splash) only — never in Chat/Terminal.
@Composable
fun SettingsScreen(
    registry: CapabilityRegistry = koinInject(),
    credentials: CredentialStore = koinInject(),
    onOpenRuntime: () -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    var statuses by remember { mutableStateOf<Map<ProviderId, ProviderStatus>>(emptyMap()) }
    val autonomy by registry.autonomy.collectAsState()

    LaunchedEffect(Unit) { statuses = registry.refreshAll() }
    fun refresh() {
        scope.launch { statuses = registry.refreshAll() }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium)

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

        Text("Providers", style = MaterialTheme.typography.titleMedium)

        ApiKeyCard(
            id = ProviderId.OPENAI,
            badge = CapabilityBadge.API,
            status = statuses[ProviderId.OPENAI],
            onSaveKey = { credentials.setApiKey(ProviderId.OPENAI, it); refresh() },
            onClearKey = { credentials.clearApiKey(ProviderId.OPENAI); refresh() }
        )
        CustomEndpointCard(credentials, statuses[ProviderId.CUSTOM], onChanged = { refresh() })
        UnavailableCard(ProviderId.CLAUDE, CapabilityBadge.UNAVAILABLE, statuses[ProviderId.CLAUDE])
        UnavailableCard(ProviderId.GEMINI, CapabilityBadge.UNAVAILABLE, statuses[ProviderId.GEMINI])

        Text("Autonomy", style = MaterialTheme.typography.titleMedium)
        AutonomyPicker(autonomy) { registry.setAutonomy(it) }

        Text("Runtime", style = MaterialTheme.typography.titleMedium)
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Coding runtime", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Fetch bash, Node.js, Python, PRoot and dev tools from pinned mirrors. " +
                        "Each artifact is checked against a SHA-256 that ships inside the APK.",
                    style = MaterialTheme.typography.bodySmall
                )
                OutlinedButton(onClick = onOpenRuntime, modifier = Modifier.fillMaxWidth()) {
                    Text("Open runtime manager")
                }
            }
        }

        Text(
            "Dangerous actions require approval at ASK level (default). " +
                "Provider status is probed, never assumed.",
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun ApiKeyCard(
    id: ProviderId,
    badge: CapabilityBadge,
    status: ProviderStatus?,
    onSaveKey: (String) -> Unit,
    onClearKey: () -> Unit
) {
    var input by remember { mutableStateOf("") }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(id.title, style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.weight(1f))
                CapabilityBadgeChip(badge)
            }
            Text(statusText(status), style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                label = { Text("API key") },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { if (input.isNotBlank()) { onSaveKey(input); input = "" } }) {
                    Text("Save")
                }
                OutlinedButton(onClick = onClearKey) { Text("Clear") }
            }
        }
    }
}

@Composable
private fun CustomEndpointCard(
    credentials: CredentialStore,
    status: ProviderStatus?,
    onChanged: () -> Unit
) {
    var url by remember { mutableStateOf("") }
    var model by remember { mutableStateOf("") }
    var key by remember { mutableStateOf("") }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Custom endpoint", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.weight(1f))
                CapabilityBadgeChip(CapabilityBadge.REMOTE)
            }
            Text(statusText(status), style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(
                value = url, onValueChange = { url = it },
                label = { Text("Base URL (https://…)") },
                modifier = Modifier.fillMaxWidth(), singleLine = true
            )
            OutlinedTextField(
                value = model, onValueChange = { model = it },
                label = { Text("Model") },
                modifier = Modifier.fillMaxWidth(), singleLine = true
            )
            OutlinedTextField(
                value = key, onValueChange = { key = it },
                label = { Text("API key (optional)") },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(), singleLine = true
            )
            Button(onClick = {
                if (url.isNotBlank()) credentials.setCustomBaseUrl(url)
                if (model.isNotBlank()) credentials.setCustomModel(model)
                if (key.isNotBlank()) credentials.setApiKey(ProviderId.CUSTOM, key)
                url = ""; model = ""; key = ""
                onChanged()
            }) { Text("Save endpoint") }
        }
    }
}

@Composable
private fun UnavailableCard(id: ProviderId, badge: CapabilityBadge, status: ProviderStatus?) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(id.title, style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.weight(1f))
                CapabilityBadgeChip(badge)
            }
            Text(statusText(status), style = MaterialTheme.typography.bodySmall)
        }
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

private fun statusText(s: ProviderStatus?): String = when (s) {
    null -> "Probing…"
    is ProviderStatus.Ready -> "Ready — ${s.reason}"
    is ProviderStatus.MissingKey -> "Unavailable — ${s.reason}"
    is ProviderStatus.Misconfigured -> "Unavailable — ${s.reason}"
    is ProviderStatus.Unavailable -> "Unavailable — ${s.reason}"
}
