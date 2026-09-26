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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.sahil.octacode.core.capability.CapabilityRegistry
import com.sahil.octacode.core.capability.ProviderStatus
import com.sahil.octacode.core.provider.AiProvider
import com.sahil.octacode.core.provider.CapabilityBadge
import com.sahil.octacode.core.provider.ProviderId
import com.sahil.octacode.data.security.CredentialStore
import com.sahil.octacode.domain.model.FetchResult
import com.sahil.octacode.domain.model.FetchedModelsRepository
import com.sahil.octacode.ui.components.CapabilityBadgeChip
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/**
 * Which models this app is allowed to reach, and how they are proved reachable.
 *
 * Wires the M1 capability registry and the M2 credential store to real UI.
 * Nothing here is optimistic: a provider is "Ready" only after a probe says so.
 */
@Composable
fun SettingsProvidersScreen(
    onBack: () -> Unit,
    registry: CapabilityRegistry = koinInject(),
    credentials: CredentialStore = koinInject(),
    // Read from the DI graph rather than listed by hand: this screen and the
    // chat path must agree on which providers exist. Hand-writing the list
    // allowed a provider to be reachable in chat while its key field was
    // missing here — with no way for the user to supply the key at all.
    adapters: Map<ProviderId, AiProvider> = koinInject(),
    fetched: FetchedModelsRepository = koinInject(),
) {
    val scope = rememberCoroutineScope()
    var statuses by remember { mutableStateOf<Map<ProviderId, ProviderStatus>>(emptyMap()) }

    LaunchedEffect(Unit) { statuses = registry.refreshAll() }
    fun refresh() {
        scope.launch { statuses = registry.refreshAll() }
    }

    // Registered and key-bearing, in enum order. Custom endpoint is excluded
    // because it takes a URL and a model as well as a key, so it has its own
    // card below rather than a plain key field.
    val keyed = ProviderId.entries.filter { it != ProviderId.CUSTOM && it in adapters }
    // Declared but not wired: shown as unavailable rather than hidden, so the
    // absence is visible instead of looking like the app simply forgot them.
    val unimplemented = ProviderId.entries.filter { it != ProviderId.CUSTOM && it !in adapters }

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
            Text("Providers", style = MaterialTheme.typography.headlineMedium)
        }

        Text("Providers", style = MaterialTheme.typography.titleMedium)

        keyed.forEach { id ->
            ApiKeyCard(
                id = id,
                badge = CapabilityBadge.API,
                status = statuses[id],
                savedKey = credentials.getApiKey(id),
                onSaveKey = { credentials.setApiKey(id, it); refresh() },
                onClearKey = { credentials.clearApiKey(id); refresh() },
                // Suspends rather than returning a note itself: the card owns
                // the spinner and the scope, and asking it to format text as
                // well would put network work inside a composable.
                onFetchModels = { fetched.refresh(id) }
            )
        }
        CustomEndpointCard(credentials, statuses[ProviderId.CUSTOM], onChanged = { refresh() })
        unimplemented.forEach { id ->
            UnavailableCard(id, CapabilityBadge.UNAVAILABLE, statuses[id])
        }

        Text(
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
    savedKey: String?,
    onSaveKey: (String) -> Unit,
    onClearKey: () -> Unit,
    onFetchModels: suspend () -> FetchResult
) {
    var input by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    var fetching by remember { mutableStateOf(false) }
    var fetchNote by remember { mutableStateOf<String?>(null) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(id.title, style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.weight(1f))
                CapabilityBadgeChip(badge)
            }
            Text(statusText(status), style = MaterialTheme.typography.bodySmall)
            // Show enough of the stored key to recognise which one it is: an
            // OpenRouter key pasted into this box looks identical to an OpenAI
            // key until the request has already been rejected.
            if (!savedKey.isNullOrBlank()) {
                Text(
                    "Saved · starts with ${savedKey.take(8)}…",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                label = { Text(if (savedKey.isNullOrBlank()) "API key" else "Replace API key") },
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
            // Disabled rather than hidden when there is no key: statusText
            // above already says the key is missing, so this reads as waiting
            // on that rather than as a control that ignores taps.
            OutlinedButton(
                onClick = {
                    fetching = true
                    fetchNote = null
                    scope.launch {
                        val result = try {
                            onFetchModels()
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (t: Throwable) {
                            // refresh() is contracted not to throw; if something
                            // does, say so here rather than let a button press
                            // take the process down.
                            FetchResult(false, t.message ?: "The request failed.")
                        }
                        fetchNote = result.message
                        fetching = false
                    }
                },
                enabled = !fetching && !savedKey.isNullOrBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (fetching) "Fetching…" else "Fetch models")
            }
            // Success and failure are shown in the same place: a fetch that
            // quietly did nothing is indistinguishable from one that worked.
            fetchNote?.let { note ->
                Text(note, style = MaterialTheme.typography.bodySmall)
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
    // Open with whatever is already stored. A form that always starts blank
    // hides the one thing the user came here to check — which model is set.
    var url by remember { mutableStateOf(credentials.getCustomBaseUrl() ?: "") }
    var model by remember {
        mutableStateOf(credentials.getCustomModel().takeIf { it != "default" } ?: "")
    }
    var key by remember { mutableStateOf("") }
    val keySaved = credentials.hasApiKey(ProviderId.CUSTOM)
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
                placeholder = { Text("https://openrouter.ai/api/v1") },
                modifier = Modifier.fillMaxWidth(), singleLine = true
            )
            OutlinedTextField(
                value = model, onValueChange = { model = it },
                label = { Text("Model") },
                placeholder = { Text("openai/gpt-4o-mini") },
                supportingText = { Text("OpenRouter uses vendor/model ids. A blank model is rejected by the server.") },
                modifier = Modifier.fillMaxWidth(), singleLine = true
            )
            OutlinedTextField(
                value = key, onValueChange = { key = it },
                label = { Text(if (keySaved) "API key (saved — type to replace)" else "API key (optional)") },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(), singleLine = true
            )
            Button(onClick = {
                if (url.isNotBlank()) credentials.setCustomBaseUrl(url)
                if (model.isNotBlank()) credentials.setCustomModel(model)
                if (key.isNotBlank()) credentials.setApiKey(ProviderId.CUSTOM, key)
                // Only the secret is wiped; url and model stay on screen so the
                // user can see what they configured instead of guessing.
                key = ""
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

private fun statusText(s: ProviderStatus?): String = when (s) {
    null -> "Probing…"
    is ProviderStatus.Ready -> "Ready — ${s.reason}"
    is ProviderStatus.MissingKey -> "Unavailable — ${s.reason}"
    is ProviderStatus.Misconfigured -> "Unavailable — ${s.reason}"
    is ProviderStatus.Unavailable -> "Unavailable — ${s.reason}"
}
