package com.sahil.octacode

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.sahil.octacode.ui.components.StarFieldBackground
import com.sahil.octacode.ui.demo.LocalDemoPreview
import com.sahil.octacode.ui.demo.rememberDemoPreviewState
import com.sahil.octacode.ui.motion.LocalMotionPolicy
import com.sahil.octacode.ui.motion.rememberSystemMotionPolicy
import com.sahil.octacode.ui.navigation.OctaNavGraph
import com.sahil.octacode.ui.navigation.Routes
import com.sahil.octacode.ui.theme.Gold
import com.sahil.octacode.ui.theme.GoldBright
import com.sahil.octacode.ui.theme.OctaCodeTheme
import com.sahil.octacode.ui.theme.OnDarkMuted

private data class BottomDest(val route: String, val label: String, val icon: ImageVector)

// Reference agent chrome: Workspace / Code / Terminal + Settings as overflow via chat menu.
// Kept full set for app function; active item uses gold pill like the mock.
private val BOTTOM_DESTS = listOf(
    BottomDest(Routes.HOME, "Workspace", Icons.Filled.Home),
    BottomDest(Routes.CHAT, "Agent", Icons.AutoMirrored.Filled.Chat),
    BottomDest(Routes.PROJECTS, "Code", Icons.Filled.Folder),
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
                AgentBottomBar(
                    currentRoute = currentRoute,
                    onSelect = { route ->
                        navController.navigate(route) {
                            popUpTo(Routes.HOME) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            OctaNavGraph(navController = navController)
        }
    }
}

/**
 * Glass bottom bar with gold active pill — matches the Agent reference mock.
 */
@Composable
private fun AgentBottomBar(
    currentRoute: String?,
    onSelect: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xF20A1425))
            .padding(horizontal = 8.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        BOTTOM_DESTS.forEach { dest ->
            val selected = currentRoute == dest.route
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .then(
                        if (selected) {
                            Modifier
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(
                                            Gold.copy(alpha = 0.22f),
                                            GoldBright.copy(alpha = 0.12f)
                                        )
                                    )
                                )
                                .border(
                                    1.dp,
                                    Brush.horizontalGradient(
                                        listOf(GoldBright.copy(alpha = 0.85f), Gold.copy(alpha = 0.35f))
                                    ),
                                    RoundedCornerShape(20.dp)
                                )
                        } else {
                            Modifier
                        }
                    )
                    .clickable { onSelect(dest.route) }
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Icon(
                    imageVector = dest.icon,
                    contentDescription = dest.label,
                    tint = if (selected) GoldBright else OnDarkMuted,
                    modifier = Modifier.size(22.dp)
                )
                Text(
                    text = dest.label,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                        fontSize = 11.sp
                    ),
                    color = if (selected) GoldBright else OnDarkMuted,
                    modifier = Modifier.padding(top = 4.dp)
                )
                if (selected) {
                    Box(
                        modifier = Modifier
                            .padding(top = 4.dp)
                            .size(width = 18.dp, height = 2.dp)
                            .background(GoldBright, RoundedCornerShape(1.dp))
                    )
                }
            }
        }
    }
}
