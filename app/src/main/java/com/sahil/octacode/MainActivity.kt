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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Layers
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
import com.sahil.octacode.ui.theme.NeonBlue
import com.sahil.octacode.ui.theme.NeonBlueBright
import com.sahil.octacode.ui.theme.OctaCodeTheme
import com.sahil.octacode.ui.theme.OnDark
import com.sahil.octacode.ui.theme.OnDarkMuted

/** Reference bottom bar: Workspace / Code / Terminal (Settings via header menu). */
private data class BottomDest(val route: String, val label: String, val icon: ImageVector)

private val BOTTOM_DESTS = listOf(
    BottomDest(Routes.HOME, "Workspace", Icons.Filled.Code),
    BottomDest(Routes.PROJECTS, "Code", Icons.Filled.Layers),
    BottomDest(Routes.TERMINAL, "Terminal", Icons.Filled.Terminal)
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
 * Glass bottom bar with gold active pill on the icon + label underline — reference mock.
 * Chat (Agent) highlights Workspace, matching the mock while Chat is open.
 */
@Composable
private fun AgentBottomBar(
    currentRoute: String?,
    onSelect: (String) -> Unit
) {
    val activeRoute = when (currentRoute) {
        Routes.CHAT -> Routes.HOME
        else -> currentRoute
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xF20A1425))
            .padding(horizontal = 12.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.Top
    ) {
        BOTTOM_DESTS.forEach { dest ->
            val selected = activeRoute == dest.route
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clip(RoundedCornerShape(18.dp))
                    .clickable { onSelect(dest.route) }
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .then(
                            if (selected) {
                                Modifier
                                    // Solid tint rather than a gradient sweep: a
                                    // control reads as a control, not as a light
                                    // source, and selection is still unmissable.
                                    .background(NeonBlue.copy(alpha = 0.12f))
                                    .border(
                                        1.5.dp,
                                        NeonBlue.copy(alpha = 0.55f),
                                        RoundedCornerShape(16.dp)
                                    )
                            } else {
                                Modifier
                            }
                        )
                        .padding(horizontal = 28.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = dest.icon,
                        contentDescription = dest.label,
                        tint = if (selected) NeonBlueBright else OnDarkMuted,
                        modifier = Modifier.size(26.dp)
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = dest.label,
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                        fontSize = 13.sp
                    ),
                    color = if (selected) OnDark else OnDarkMuted
                )
                Spacer(Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .width(if (selected) 48.dp else 0.dp)
                        .height(if (selected) 2.5.dp else 0.dp)
                        .background(
                            if (selected) NeonBlueBright else Color.Transparent,
                            RoundedCornerShape(2.dp)
                        )
                )
            }
        }
    }
}
