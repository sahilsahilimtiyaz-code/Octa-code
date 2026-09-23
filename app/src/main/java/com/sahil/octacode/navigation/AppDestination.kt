package com.sahil.octacode.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.ui.graphics.vector.ImageVector

enum class AppDestination(
    val route: String,
    val label: String,
    val icon: ImageVector,
    val isWorkspace: Boolean = false,
) {
    AI("ai", "AI", Icons.Outlined.AutoAwesome, isWorkspace = true),
    CODE("code", "Code", Icons.Outlined.Code, isWorkspace = true),
    TERMINAL("terminal", "Terminal", Icons.Outlined.Terminal, isWorkspace = true),
    PROJECTS("projects", "Projects", Icons.Outlined.FolderOpen),
    MODEL_PROVIDER("model-provider", "Model & Provider", Icons.Outlined.AutoAwesome),
    SETTINGS("settings", "Settings", Icons.Outlined.Settings),
}
