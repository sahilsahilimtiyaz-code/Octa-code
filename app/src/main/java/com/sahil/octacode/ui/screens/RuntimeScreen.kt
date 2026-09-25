package com.sahil.octacode.ui.screens

import android.os.Build
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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.sahil.octacode.core.runtime.ProvisionerState
import com.sahil.octacode.core.runtime.RuntimeGroup
import com.sahil.octacode.core.runtime.RuntimeManifest
import com.sahil.octacode.core.runtime.RuntimeProvisioner
import com.sahil.octacode.ui.components.BannerTone
import com.sahil.octacode.ui.components.GlassPanel
import com.sahil.octacode.ui.components.StatusBanner
import org.koin.compose.koinInject

/**
 * R1: fetch the coding runtime from pinned mirrors and prove every byte.
 *
 * The screen is deliberately honest about what this milestone does and does not
 * do: artifacts are downloaded, checksum-verified and stored — unpacking them
 * into a working shell is the exec runtime (R2/R3), and this screen says so
 * rather than implying tools already work.
 */
@Composable
fun RuntimeScreen(
    onBack: () -> Unit,
    provisioner: RuntimeProvisioner = koinInject(),
    manifest: RuntimeManifest = koinInject()
) {
    val state by provisioner.state.collectAsState()
    val supportedAbi = remember(manifest) {
        Build.SUPPORTED_ABIS.any { it.equals(manifest.abi, ignoreCase = true) }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("Back") }
            Spacer(Modifier.width(4.dp))
            Text("Runtime", style = MaterialTheme.typography.headlineMedium)
        }

        if (!supportedAbi) {
            StatusBanner(
                BannerTone.Error,
                "Unavailable on this device",
                "This device reports ${Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown ABI"} · " +
                    "the pinned runtime is ${manifest.abi} only. Nothing will be downloaded."
            )
        }

        state.lastError?.let { error ->
            if (error.groupId == null) {
                StatusBanner(
                    if (error.retryable) BannerTone.Warning else BannerTone.Error,
                    "Runtime",
                    error.reason
                )
            }
        }

        GlassPanel(title = "Pinned source") {
            InfoLine("Mirror", manifest.baseUrl)
            InfoLine("Index", manifest.index)
            InfoLine("Architecture", "${manifest.arch} · ${manifest.abi}")
            InfoLine("Pinned", "${manifest.packageCount} artifacts · ${formatBytes(manifest.unionBytes)}")
            InfoLine("Manifest", "generated ${manifest.generated} · schema v${manifest.schema}")
            InfoLine("Stored", "${state.fetched.size} verified · ${formatBytes(state.totalFetchedBytes())}")
            Spacer(Modifier.height(6.dp))
            Text(
                "Every artifact is matched against a SHA-256 pinned inside the APK before it is " +
                    "accepted. A mismatch is deleted and reported — never trusted.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (supportedAbi) {
            manifest.groups.forEach { group ->
                GroupCard(
                    group = group,
                    state = state,
                    onInstall = { provisioner.install(group.id) },
                    onStop = { provisioner.cancel() },
                    onDismiss = { provisioner.dismissError() }
                )
            }
        }

        GlassPanel(title = "What happens next") {
            Text(
                "R1 stores verified artifacts only. Unpacking them into a working shell (bash, " +
                    "node, python) and wiring them into the mission pipeline lands with the exec " +
                    "runtime — until then no tool is reported as available.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (state.fetched.isNotEmpty()) {
            HorizontalDivider()
            OutlinedButton(
                onClick = { provisioner.clearAll() },
                enabled = !state.busy,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Delete all downloaded runtime artifacts (${formatBytes(state.totalFetchedBytes())})")
            }
        }
    }
}

@Composable
private fun GroupCard(
    group: RuntimeGroup,
    state: ProvisionerState,
    onInstall: () -> Unit,
    onStop: () -> Unit,
    onDismiss: () -> Unit
) {
    val complete = state.isComplete(group)
    val fetchedCount = state.fetchedCount(group)
    val pendingBytes = state.pendingBytes(group)
    val active = state.activeGroupId == group.id
    val progress = if (active) state.progress else null
    val error = state.lastError?.takeIf { it.groupId == group.id }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(group.title, style = MaterialTheme.typography.titleSmall)
                if (group.optional) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "optional",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.weight(1f))
                Text(
                    "$fetchedCount/${group.items.size}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Text(
                group.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (progress != null) {
                LinearProgressIndicator(
                    progress = { progress.groupFraction },
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    "Item ${progress.itemIndex} of ${progress.itemCount} · ${progress.itemId} · " +
                        "${formatBytes(progress.itemBytesDone)} / ${formatBytes(progress.itemBytesTotal)}",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    if (complete) "All ${group.items.size} artifacts verified"
                    else "${formatBytes(pendingBytes)} to download · ${group.items.size - fetchedCount} remaining",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (complete) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            error?.let {
                StatusBanner(
                    BannerTone.Error,
                    if (it.retryable) "Stopped — retry available" else "Failed",
                    it.reason
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                when {
                    active -> Button(onClick = onStop, modifier = Modifier.weight(1f)) {
                        Text("Stop")
                    }
                    complete -> Button(
                        onClick = onInstall,
                        enabled = !state.busy,
                        modifier = Modifier.weight(1f)
                    ) { Text("Re-verify (offline)") }
                    else -> Button(
                        onClick = onInstall,
                        enabled = !state.busy,
                        modifier = Modifier.weight(1f)
                    ) { Text("Install (${formatBytes(pendingBytes)})") }
                }
                if (error != null && !state.busy) {
                    TextButton(onClick = onDismiss) { Text("Dismiss") }
                }
            }
        }
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.weight(1.4f),
            maxLines = 2
        )
    }
}

internal fun formatBytes(bytes: Long): String = when {
    bytes >= 1_000_000_000L -> "%.1f GB".format(bytes / 1_000_000_000.0)
    bytes >= 1_000_000L -> "%.1f MB".format(bytes / 1_000_000.0)
    bytes >= 1_000L -> "%.1f kB".format(bytes / 1_000.0)
    else -> "$bytes B"
}
