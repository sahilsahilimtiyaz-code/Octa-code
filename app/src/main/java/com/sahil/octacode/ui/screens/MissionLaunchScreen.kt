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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
import com.sahil.octacode.core.capability.CapabilityRegistry
import com.sahil.octacode.core.capability.ProjectTypeDetector
import com.sahil.octacode.core.capability.ProviderStatus
import com.sahil.octacode.core.provider.ProviderId
import com.sahil.octacode.domain.mission.MissionEngine
import com.sahil.octacode.ui.components.GlassPanel
import com.sahil.octacode.ui.components.StatusBanner
import com.sahil.octacode.ui.components.BannerTone
import com.sahil.octacode.ui.theme.NeonBlue
import com.sahil.octacode.ui.theme.NeonGreen
import com.sahil.octacode.ui.theme.NeonRed
import com.sahil.octacode.ui.theme.OnDarkMuted
import com.sahil.octacode.ui.theme.WarningAmber
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import java.io.File

// M3b MissionLaunch: real inputs → engine.launch → navigate to detail.
@Composable
fun MissionLaunchScreen(
    onBack: () -> Unit,
    onStarted: (String) -> Unit,
    engine: MissionEngine = koinInject(),
    registry: CapabilityRegistry = koinInject()
) {
    val scope = rememberCoroutineScope()
    var goal by remember { mutableStateOf("") }
    var path by remember { mutableStateOf("") }
    var providerId by remember { mutableStateOf(ProviderId.OPENAI) }
    var providerMenu by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var statuses by remember { mutableStateOf<Map<ProviderId, ProviderStatus>>(emptyMap()) }
    val autonomy by registry.autonomy.collectAsState()

    androidx.compose.runtime.LaunchedEffect(Unit) {
        statuses = registry.refreshAll()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Text("New Mission", style = MaterialTheme.typography.headlineSmall)
        }

        error?.let {
            StatusBanner(BannerTone.Error, "Cannot start", it)
        }

        GlassPanel(title = "Goal", accent = NeonBlue) {
            OutlinedTextField(
                value = goal,
                onValueChange = { goal = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("What should the agent do?") },
                minLines = 2
            )
        }

        GlassPanel(title = "Project path", accent = NeonBlue) {
            OutlinedTextField(
                value = path,
                onValueChange = { path = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("/absolute/path/to/project") },
                singleLine = true
            )
            Spacer(Modifier.height(6.dp))
            val detected = remember(path) {
                val f = File(path)
                if (path.isNotBlank() && f.exists()) ProjectTypeDetector.detect(f).name
                else if (path.isBlank()) "—"
                else "path missing"
            }
            Text(
                "Detected: $detected",
                style = MaterialTheme.typography.labelMedium,
                color = if (detected == "path missing") WarningAmber else OnDarkMuted
            )
        }

        GlassPanel(title = "Provider", accent = NeonGreen) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = { providerMenu = true }, modifier = Modifier.weight(1f)) {
                    Text(providerId.title)
                }
                DropdownMenu(expanded = providerMenu, onDismissRequest = { providerMenu = false }) {
                    ProviderId.entries.forEach { id ->
                        DropdownMenuItem(
                            text = { Text(id.title) },
                            onClick = {
                                providerId = id
                                providerMenu = false
                            }
                        )
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            val st = statuses[providerId]
            val line = when (st) {
                null -> "Probing…"
                is ProviderStatus.Ready -> "Ready — ${st.reason}"
                is ProviderStatus.MissingKey -> "Unavailable — ${st.reason}"
                is ProviderStatus.Misconfigured -> "Unavailable — ${st.reason}"
                is ProviderStatus.Unavailable -> "Unavailable — ${st.reason}"
            }
            val tone = if (st is ProviderStatus.Ready) NeonGreen else WarningAmber
            Text(line, style = MaterialTheme.typography.bodySmall, color = tone)
        }

        GlassPanel(title = "Autonomy", accent = WarningAmber) {
            Text(
                "Current: ${autonomy.name} (change in Settings)",
                style = MaterialTheme.typography.bodySmall,
                color = OnDarkMuted
            )
            Text(
                "USER_CHECKPOINT approval is gated by this level.",
                style = MaterialTheme.typography.labelSmall,
                color = OnDarkMuted
            )
        }

        Button(
            onClick = {
                error = null
                val g = goal.trim()
                val p = path.trim()
                when {
                    g.isEmpty() -> {
                        error = "Goal must not be empty."
                        return@Button
                    }
                    p.isEmpty() -> {
                        error = "Project path must not be empty."
                        return@Button
                    }
                    !File(p).exists() -> {
                        error = "Project path does not exist: $p"
                        return@Button
                    }
                    else -> {
                        busy = true
                        scope.launch {
                            try {
                                val id = engine.launch(g, p, providerId)
                                onStarted(id)
                            } catch (t: Throwable) {
                                error = t.message ?: t::class.java.simpleName
                            } finally {
                                busy = false
                            }
                        }
                    }
                }
            },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (busy) "Starting…" else "Start mission (16 phases)")
        }

        Text(
            "Runs Understand → … → Complete. Implement needs a Ready provider; format/test/build skip honestly when tools are missing.",
            style = MaterialTheme.typography.labelSmall,
            color = OnDarkMuted
        )
    }
}
