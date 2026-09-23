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
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.sahil.octacode.ui.components.StarFieldBackground
import com.sahil.octacode.ui.demo.LocalDemoPreview
import com.sahil.octacode.ui.demo.rememberDemoPreviewState
import com.sahil.octacode.ui.motion.LocalMotionPolicy
import com.sahil.octacode.ui.motion.rememberSystemMotionPolicy
import com.sahil.octacode.ui.navigation.OctaNavGraph
import com.sahil.octacode.ui.navigation.Routes
import com.sahil.octacode.ui.theme.ElectricPurple
import com.sahil.octacode.ui.theme.NeonBlue
import com.sahil.octacode.ui.theme.OctaCodeTheme
import com.sahil.octacode.ui.theme.TextSecondary

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
// M3d: premium starfield shell + motion/demo locals wired for the whole graph.
@Composable
fun OctaRoot() {
    val motionPolicy = rememberSystemMotionPolicy()
    val demoState = rememberDemoPreviewState()
    CompositionLocalProvider(
        LocalMotionPolicy provides motionPolicy,
        LocalDemoPreview provides demoState,
    ) {
        StarFieldBackground {
            OctaScaffold()
        }
    }
}

@Composable
private fun OctaScaffold() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    Scaffold(
        containerColor = Color.Transparent,
        bottomBar = {
            if (currentRoute != Routes.SPLASH && !Routes.isMissionRoute(currentRoute)) {
                NavigationBar(
                    containerColor = Color(0xF20A1425),
                    tonalElevation = 0.dp,
                ) {
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
                            label = { Text(dest.label) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = NeonBlue,
                                selectedTextColor = NeonBlue,
                                indicatorColor = ElectricPurple.copy(alpha = 0.18f),
                                unselectedIconColor = TextSecondary,
                                unselectedTextColor = TextSecondary,
                            )
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
