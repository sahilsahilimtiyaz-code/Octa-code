package com.sahil.octacode.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sahil.octacode.core.workspace.Workspace
import com.sahil.octacode.data.workspace.WorkspaceStore
import com.sahil.octacode.ui.components.SectionHeaderText
import com.sahil.octacode.ui.theme.NeonAmber
import com.sahil.octacode.ui.theme.NeonGreen
import com.sahil.octacode.ui.theme.OnDark
import com.sahil.octacode.ui.theme.OnDarkMuted
import com.sahil.octacode.ui.theme.WarningAmber
import org.koin.compose.koinInject

/**
 * The folders this app is allowed to work in.
 *
 * Picking goes through ACTION_OPEN_DOCUMENT_TREE rather than a path field,
 * because a typed path is a string the app has no right to read — the system
 * grant is the only thing that makes a folder ours to open, and it is the
 * same grant [com.sahil.octacode.data.workspace.WorkspaceStore] re-checks on
 * every load.
 *
 * Two things are said out loud on this screen because they are easy to get
 * wrong: that access can be withdrawn from outside the app, and what
 * selecting a folder actually does. Nothing here reaches into the agent's
 * execution — that arrives with the runtime — so the claim is limited to
 * what is true now: the choice is recorded in the conversations started
 * while it is in effect.
 */
@Composable
fun WorkspacesScreen(
    onBack: () -> Unit,
    store: WorkspaceStore = koinInject(),
) {
    val workspaces by store.workspaces.collectAsState()
    val activeId by store.activeId.collectAsState()

    // Android can refuse the grant outright, and the folder would then be
    // listed but unreadable. That has to be said rather than left to be
    // discovered the first time anyone opens it.
    var grantWarning by remember { mutableStateOf<String?>(null) }

    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        grantWarning = if (uri == null) {
            null
        } else if (store.add(uri)) {
            null
        } else {
            "The folder was added, but Android did not keep the permission " +
                "for it. It is listed below and will stay unreadable until " +
                "you pick it again."
        }
    }

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
            Text("Workspaces", style = MaterialTheme.typography.headlineMedium)
        }

        Card(Modifier.fillMaxWidth()) {
            Column(
                Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    "Folders this app may work in",
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = "A workspace is a folder you choose through Android's own " +
                        "picker, which is what grants the access in the first place — " +
                        "typing a path would produce a string with no permission " +
                        "behind it. Choosing one below records it in every " +
                        "conversation started while it is selected.",
                    style = MaterialTheme.typography.bodySmall,
                    color = OnDarkMuted,
                )
            }
        }

        grantWarning?.let { message ->
            Card(Modifier.fillMaxWidth()) {
                Text(
                    text = message,
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = WarningAmber,
                )
            }
        }

        OutlinedButton(
            onClick = { picker.launch(null) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Choose a folder")
        }

        if (workspaces.isEmpty()) {
            Text(
                text = "No folders chosen yet. The picker above opens Android's " +
                    "folder browser; whichever tree you confirm becomes a " +
                    "workspace listed here.",
                style = MaterialTheme.typography.bodySmall,
                color = OnDarkMuted,
            )
        } else {
            SectionHeaderText("Folders")

            // Most recently worked in first — the ordering the selection just
            // wrote, so the folder you used last is the one you can see.
            val ordered = workspaces.sortedByDescending { it.lastUsedAt ?: 0L }
            ordered.forEach { workspace ->
                WorkspaceRow(
                    workspace = workspace,
                    selected = workspace.id == activeId,
                    onSelect = {
                        // Tapping the selected row drops the choice rather
                        // than doing nothing: a selection you cannot undo
                        // would keep naming a folder after you meant to stop.
                        if (workspace.id == activeId) {
                            store.clearActive()
                        } else {
                            store.setActive(workspace.id)
                        }
                    },
                    onToggleFavorite = {
                        store.setFavorite(workspace.id, !workspace.isFavorite)
                    },
                    onRemove = { store.remove(workspace.id) },
                )
            }

            Text(
                text = "Removing a folder also gives back the access Android " +
                    "granted for it. A folder whose permission was withdrawn " +
                    "outside this app stays listed, marked, until you pick it again.",
                style = MaterialTheme.typography.bodySmall,
                color = OnDarkMuted,
            )
        }
    }
}

/** One folder: tap to choose it or stop choosing it, plus star and remove. */
@Composable
private fun WorkspaceRow(
    workspace: Workspace,
    selected: Boolean,
    onSelect: () -> Unit,
    onToggleFavorite: () -> Unit,
    onRemove: () -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onSelect)
                .padding(start = 16.dp, end = 4.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = if (selected) Icons.Outlined.Check else Icons.Outlined.Folder,
                contentDescription = null,
                tint = if (selected) NeonGreen else OnDarkMuted,
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.width(12.dp))

            Column(Modifier.weight(1f)) {
                Text(
                    text = workspace.displayName,
                    style = MaterialTheme.typography.bodyLarge,
                    color = OnDark,
                    maxLines = 1,
                )
                Text(
                    text = locationFor(workspace.uri),
                    style = MaterialTheme.typography.bodySmall,
                    color = OnDarkMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = if (selected) {
                        "Working here — tap to stop using it"
                    } else {
                        "Tap to work in this folder"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (selected) NeonGreen else OnDarkMuted,
                )
                if (!workspace.persisted) {
                    Text(
                        text = "Android withdrew access to this folder. Choose " +
                            "it again to restore it.",
                        style = MaterialTheme.typography.bodySmall,
                        color = WarningAmber,
                    )
                }
            }

            IconButton(onClick = onToggleFavorite) {
                Icon(
                    imageVector = if (workspace.isFavorite) {
                        Icons.Filled.Star
                    } else {
                        Icons.Outlined.StarBorder
                    },
                    contentDescription = if (workspace.isFavorite) {
                        "Remove ${workspace.displayName} from favourites"
                    } else {
                        "Add ${workspace.displayName} to favourites"
                    },
                    tint = if (workspace.isFavorite) NeonAmber else OnDarkMuted,
                )
            }
            IconButton(onClick = onRemove) {
                Icon(
                    // Delete rather than a close cross: this forgets the
                    // folder and gives back Android's grant for it, which is
                    // a removal, not a dismissal of something on screen.
                    imageVector = Icons.Outlined.Delete,
                    contentDescription = "Remove ${workspace.displayName}",
                    tint = OnDarkMuted,
                )
            }
        }
    }
}

/**
 * The folder's own address, shortened to what identifies it.
 *
 * Tree URIs are addressed like `.../tree/primary:Documents`; everything up to
 * that marker is the document provider's own plumbing, and the percent
 * encoding is there for URI syntax rather than for anyone reading it.
 * Providers that do not follow the shape keep their raw tail instead of
 * being blanked out.
 */
private fun locationFor(uri: String): String {
    val tail = uri.substringAfterLast("/tree/", uri.substringAfterLast('/'))
    return tail
        .replace("%3A", ":")
        .replace("%2F", "/")
        .ifBlank { uri }
}
