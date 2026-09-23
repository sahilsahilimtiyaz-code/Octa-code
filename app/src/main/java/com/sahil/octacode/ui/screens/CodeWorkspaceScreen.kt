package com.sahil.octacode.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sahil.octacode.ui.components.EmptyStateCard
import com.sahil.octacode.ui.components.GlassSurface
import com.sahil.octacode.ui.components.ProjectOverviewDashboard
import com.sahil.octacode.ui.components.SectionHeaderText
import com.sahil.octacode.ui.demo.DemoPreviewBanner
import com.sahil.octacode.ui.demo.DemoSampleData
import com.sahil.octacode.ui.demo.LocalDemoPreview
import com.sahil.octacode.ui.state.ProjectFileUi
import com.sahil.octacode.ui.state.ProjectWorkspaceUi
import com.sahil.octacode.ui.state.UiContentSource
import com.sahil.octacode.ui.theme.NeonBlue
import com.sahil.octacode.ui.theme.TextSecondary

@Composable
fun CodeWorkspaceScreen() {
    val workspace = if (LocalDemoPreview.current.enabled) {
        DemoSampleData.project
    } else {
        ProjectWorkspaceUi(UiContentSource.UNAVAILABLE, name = null, metrics = null, files = emptyList())
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (workspace.source == UiContentSource.DEMO) {
            item { DemoPreviewBanner("Illustrative SmartNotes metrics and files · no workspace was opened") }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Project Overview", style = MaterialTheme.typography.headlineSmall)
                Text(
                    workspace.name ?: "Choose a project to see its code and activity.",
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        item { ProjectOverviewDashboard(workspace.metrics) }
        item { SectionHeaderText("Project files") }
        if (workspace.source == UiContentSource.DEMO) {
            item { DemoFilesCard(workspace.files) }
        } else {
            item {
                EmptyStateCard(
                    title = "Workspace unavailable",
                    message = "Import a project to browse files and open the editor. Project file access is not configured yet.",
                )
            }
        }
    }
}

@Composable
private fun DemoFilesCard(files: List<ProjectFileUi>) {
    GlassSurface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
        Column(modifier = Modifier.padding(vertical = 4.dp)) {
            files.forEachIndexed { index, file ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(
                        if (file.path.endsWith(".kt")) Icons.Outlined.Code else Icons.Outlined.Folder,
                        contentDescription = null,
                        tint = NeonBlue,
                    )
                    Column {
                        Text(file.path, style = MaterialTheme.typography.bodyMedium)
                        Text(file.description, color = TextSecondary, style = MaterialTheme.typography.labelSmall)
                    }
                }
                if (index < files.lastIndex) {
                    androidx.compose.material3.HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 14.dp),
                        color = TextSecondary.copy(alpha = 0.12f),
                    )
                }
            }
            Text(
                "Sample paths only · file opening is not available in Demo Preview.",
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                color = TextSecondary,
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}
