package com.sahil.octacode.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.sahil.octacode.core.model.ModelDef
import com.sahil.octacode.core.model.ModelRow
import com.sahil.octacode.core.model.ModelSection
import com.sahil.octacode.core.model.ModelSectioning
import com.sahil.octacode.core.model.ModelUserState
import com.sahil.octacode.core.model.SectionKind
import com.sahil.octacode.core.provider.ProviderId
import com.sahil.octacode.ui.theme.NeonBlue
import com.sahil.octacode.ui.theme.OnDark
import com.sahil.octacode.ui.theme.OnDarkMuted
import com.sahil.octacode.ui.theme.SurfaceDark
import com.sahil.octacode.ui.theme.SurfaceVariantDark
import com.sahil.octacode.ui.theme.WarningAmber
import com.sahil.octacode.ui.theme.codeTextStyle

/**
 * The model picker: search over favorites, recents and provider groups, plus
 * the endpoints the catalog has no rows for.
 *
 * All grouping comes from [ModelSectioning.build] rather than being done here,
 * so the ordering rules stay in one tested place instead of being re-implemented
 * by whatever composable happens to draw them.
 *
 * Every row does what it says: a model row selects that model, a star toggles a
 * real favorite, and an endpoint with no adapter in this build is rendered as
 * [LockedRow] — no click handler, with the reason in words. The previous menu
 * offered those endpoints as if selecting them did something useful, when all
 * it could do was defer the failure to send time.
 *
 * @param availableProviders endpoints with a registered adapter. Anything else
 *   is shown locked rather than selectable.
 * @param busy a response is mid-stream. Selecting is refused by the engine
 *   while busy, so the rows are inert rather than clickable-into-nothing, and
 *   the sheet says why.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ModelSelectorSheet(
    selectedModelId: String?,
    selectedProvider: ProviderId,
    states: Map<String, ModelUserState>,
    /**
     * What the selector may offer: the bundled catalog plus whatever a
     * provider fetched for this key. Passed in rather than read straight from
     * `ModelCatalog` so the sheet cannot show one list while id resolution
     * reads another — a row here that the engine cannot find is a row that
     * does nothing when tapped.
     */
    models: List<ModelDef>,
    availableProviders: Set<ProviderId>,
    busy: Boolean = false,
    onSelectModel: (ModelDef) -> Unit,
    onSelectEndpoint: (ProviderId) -> Unit,
    onToggleFavorite: (ModelDef, Boolean) -> Unit,
    onRefreshStatus: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var query by remember { mutableStateOf("") }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // `models` is part of the key: a fetch that lands while the sheet is open
    // has to re-run this, or the list the user just asked for stays invisible
    // until they happen to type or pick something.
    val sections = remember(models, states, selectedModelId, query) {
        ModelSectioning.build(
            models = models,
            states = states,
            selectedModelId = selectedModelId,
            query = query,
        )
    }

    // Endpoints no model row covers: the custom endpoint, plus any adapter
    // with neither shipped nor fetched models. Models are grouped by their
    // provider above, so this list carries what could not appear there — and
    // a provider that has just fetched must leave it, or the sheet would offer
    // two ways to reach it, one of which arrives with no model chosen.
    val bareEndpoints = remember(availableProviders, models) {
        ProviderId.entries.filter { id ->
            models.none { it.adapter == id }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = SurfaceDark,
        contentColor = OnDark,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
        ) {
            SheetHeader(
                count = models.size,
                onClose = onDismiss,
            )

            if (busy) {
                Text(
                    text = "A response is streaming. Stop it before changing models.",
                    style = MaterialTheme.typography.bodySmall,
                    color = WarningAmber,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                )
            }

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                placeholder = { Text("Search name, id or provider") },
                leadingIcon = {
                    Icon(Icons.Outlined.Search, contentDescription = null, tint = OnDarkMuted)
                },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = NeonBlue,
                    unfocusedBorderColor = SurfaceVariantDark,
                    focusedTextColor = OnDark,
                    unfocusedTextColor = OnDark,
                    cursorColor = NeonBlue,
                ),
            )

            Spacer(Modifier.height(8.dp))

            if (sections.isEmpty()) {
                NoMatches(query = query, models = models)
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 440.dp)
                ) {
                    sections.forEach { section ->
                        stickyHeader(key = section.kind.name) {
                            SectionHeader(section)
                        }
                        items(
                            items = section.models,
                            key = { section.kind.name + it.model.id },
                        ) { row ->
                            ModelRowItem(
                                row = row,
                                enabled = !busy,
                                onClick = { onSelectModel(row.model) },
                                onToggleFavorite = { onToggleFavorite(row.model, it) },
                            )
                        }
                    }
                }
            }

            HorizontalDivider(color = SurfaceVariantDark)

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    "Endpoints",
                    style = MaterialTheme.typography.labelLarge,
                    color = OnDarkMuted,
                )

                bareEndpoints.forEach { id ->
                    if (id in availableProviders) {
                        EndpointRow(
                            id = id,
                            selected = id == selectedProvider,
                            enabled = !busy,
                            onClick = { onSelectEndpoint(id) },
                        )
                    } else {
                        LockedRow(
                            title = id.title,
                            value = if (id == selectedProvider) "Selected" else "Not selected",
                            reason = "No ${id.title} adapter is registered in this build.",
                        )
                    }
                }

                RefreshRow(onClick = onRefreshStatus)
            }
        }
    }
}

@Composable
private fun SheetHeader(count: Int, onClose: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("Model", style = MaterialTheme.typography.titleLarge, color = OnDark)
            Text(
                // "bundled" would now be untrue: some of these arrived from an
                // endpoint when a key was pressed.
                "$count available · grouped by provider",
                style = MaterialTheme.typography.bodySmall,
                color = OnDarkMuted,
            )
        }
        IconButton(onClick = onClose) {
            Icon(
                Icons.Outlined.Close,
                contentDescription = "Close model picker",
                tint = OnDarkMuted,
            )
        }
    }
}

@Composable
private fun SectionHeader(section: ModelSection) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // Opaque: a sticky header that shows rows scrolling under it is
            // unreadable, and the sheet has no surface of its own behind it.
            .background(SurfaceDark)
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val shortcut = section.kind == SectionKind.RECENT || section.kind == SectionKind.FAVORITE
        Text(
            section.title,
            style = MaterialTheme.typography.labelLarge,
            color = if (shortcut) NeonBlue else OnDarkMuted,
            modifier = Modifier.weight(1f),
        )
        if (section.kind == SectionKind.PROVIDER) {
            Text(
                section.models.size.toString(),
                style = MaterialTheme.typography.labelMedium,
                color = OnDarkMuted,
            )
        }
    }
}

@Composable
private fun ModelRowItem(
    row: ModelRow,
    enabled: Boolean,
    onClick: () -> Unit,
    onToggleFavorite: (Boolean) -> Unit,
) {
    val favorite = row.state.isFavorite
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(start = 20.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                row.model.displayName,
                style = MaterialTheme.typography.bodyLarge,
                color = OnDark,
            )
            Text(
                "${row.model.id} · ${row.model.provider}",
                style = codeTextStyle(MaterialTheme.typography.bodySmall),
                color = if (row.isSelected) NeonBlue else OnDarkMuted,
            )
        }
        IconButton(onClick = { onToggleFavorite(!favorite) }) {
            Icon(
                if (favorite) Icons.Filled.Star else Icons.Outlined.StarBorder,
                contentDescription = if (favorite) {
                    "Remove ${row.model.displayName} from favorites"
                } else {
                    "Add ${row.model.displayName} to favorites"
                },
                tint = if (favorite) WarningAmber else OnDarkMuted,
                modifier = Modifier.size(20.dp),
            )
        }
        if (row.isSelected) {
            Icon(
                Icons.Outlined.Check,
                contentDescription = "${row.model.displayName} is selected",
                tint = NeonBlue,
                modifier = Modifier.size(20.dp),
            )
        } else {
            // Keeps every row the same width, so the star does not shift as the
            // selection moves down the list.
            Spacer(Modifier.size(20.dp))
        }
    }
}

@Composable
private fun EndpointRow(
    id: ProviderId,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(id.title, style = MaterialTheme.typography.bodyLarge, color = OnDark)
            Text(
                if (id == ProviderId.CUSTOM) {
                    "Sends the model id configured on the endpoint"
                } else {
                    "No models in the catalog"
                },
                style = MaterialTheme.typography.bodySmall,
                color = OnDarkMuted,
            )
        }
        if (selected) {
            Icon(
                Icons.Outlined.Check,
                contentDescription = "$id is selected",
                tint = NeonBlue,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun RefreshRow(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Outlined.Refresh,
            contentDescription = null,
            tint = OnDarkMuted,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            "Refresh provider status",
            style = MaterialTheme.typography.bodyLarge,
            color = OnDarkMuted,
        )
    }
}

@Composable
private fun NoMatches(query: String, models: List<ModelDef>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = if (ModelSectioning.isEmptyAfterSearch(models, query)) {
                "No models match \u201C$query\u201D."
            } else {
                "There are no models to show yet."
            },
            style = MaterialTheme.typography.bodyLarge,
            color = OnDark,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Search covers display name, model id and provider. " +
                "${models.size} models available.",
            style = MaterialTheme.typography.bodySmall,
            color = OnDarkMuted,
            textAlign = TextAlign.Center,
        )
    }
}
