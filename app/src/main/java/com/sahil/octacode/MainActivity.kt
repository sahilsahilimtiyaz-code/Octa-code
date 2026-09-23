package com.sahil.octacode

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.sahil.octacode.ui.navigation.OctaNavGraph
import com.sahil.octacode.ui.navigation.Routes
import com.sahil.octacode.ui.theme.OctaCodeTheme

private data class BottomDest(val route: String, val label: String, val icon: ImageVector)

private val BOTTOM_DESTS = listOf(
    BottomDest(Routes.HOME, "Home", Icons.Filled.Home),
    BottomDest(Routes.CHAT, "Agent", Icons.AutoMirrored.Filled.Chat),
    BottomDest(Routes.PROJECTS, "Projects", Icons.Filled.Folder),
    BottomDest(Routes.TERMINAL, "Terminal", Icons.Filled.Terminal),
    BottomDest(Routes.SETTINGS, "Settings", Icons.Filled.Settings)
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            OctaCodeTheme {
                OctaRoot()
            }
        }
    }
}

// Named OctaRoot (not OctaApp) — OctaApp is the Application class.
@Composable
fun OctaRoot() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    Scaffold(
        bottomBar = {
            if (currentRoute != Routes.SPLASH && !Routes.isMissionRoute(currentRoute)) {
                NavigationBar {
                    val currentDestination = navBackStackEntry?.destination
                    BOTTOM_DESTS.forEach { dest ->
                        val selected = currentDestination?.hierarchy?.any { it.route == dest.route } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(dest.route) {
                                    popUpTo(Routes.HOME) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(dest.icon, contentDescription = dest.label) },
                            label = { Text(dest.label) }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        androidx.compose.foundation.layout.Box(modifier = Modifier.padding(innerPadding)) {
            OctaNavGraph(navController = navController)
        }
    }
}
