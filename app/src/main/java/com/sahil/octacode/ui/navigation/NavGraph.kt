package com.sahil.octacode.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.sahil.octacode.ui.screens.ChatScreen
import com.sahil.octacode.ui.screens.HomeScreen
import com.sahil.octacode.ui.screens.MissionDetailScreen
import com.sahil.octacode.ui.screens.MissionLaunchScreen
import com.sahil.octacode.ui.screens.ProjectsScreen
import com.sahil.octacode.ui.screens.RuntimeScreen
import com.sahil.octacode.ui.screens.SettingsAppearanceScreen
import com.sahil.octacode.ui.screens.SettingsAutonomyScreen
import com.sahil.octacode.ui.screens.SettingsChatScreen
import com.sahil.octacode.ui.screens.SettingsProvidersScreen
import com.sahil.octacode.ui.screens.SettingsScreen
import com.sahil.octacode.ui.screens.SettingsServerScreen
import com.sahil.octacode.ui.screens.SettingsSessionsScreen
import com.sahil.octacode.ui.screens.SplashScreen
import com.sahil.octacode.ui.screens.TerminalScreen
import com.sahil.octacode.ui.screens.WorkspacesScreen

@Composable
fun OctaNavGraph(
    navController: NavHostController,
    onOpenDrawer: () -> Unit = {},
) {
    NavHost(navController = navController, startDestination = Routes.SPLASH) {
        composable(Routes.SPLASH) {
            SplashScreen(onDone = {
                navController.navigate(Routes.HOME) {
                    popUpTo(Routes.SPLASH) { inclusive = true }
                }
            })
        }
        composable(Routes.HOME) {
            HomeScreen(
                onOpenChat = { navController.navigate(Routes.CHAT) },
                onNewMission = { navController.navigate(Routes.MISSION_LAUNCH) },
                onOpenMission = { id -> navController.navigate(Routes.missionDetail(id)) }
            )
        }
        composable(Routes.CHAT) {
            ChatScreen(
                // The hamburger's real job is the drawer, not Settings —
                // Settings stays reachable from the profile button and from
                // a row inside the drawer, so nothing is lost by fixing what
                // the menu icon promises.
                onOpenMenu = onOpenDrawer,
                onOpenProfile = { navController.navigate(Routes.SETTINGS) },
                onOpenProjects = { navController.navigate(Routes.PROJECTS) },
                onOpenWorkspaces = { navController.navigate(Routes.WORKSPACES) }
            )
        }
        composable(Routes.PROJECTS) { ProjectsScreen() }
        composable(Routes.WORKSPACES) {
            WorkspacesScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.TERMINAL) {
            // The not-installed state on this screen offers this route, so it
            // has to actually lead somewhere that can install the runtime.
            TerminalScreen(onOpenRuntime = { navController.navigate(Routes.RUNTIME) })
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                onOpenAppearance = { navController.navigate(Routes.SETTINGS_APPEARANCE) },
                onOpenSessions = { navController.navigate(Routes.SETTINGS_SESSIONS) },
                onOpenChat = { navController.navigate(Routes.SETTINGS_CHAT) },
                onOpenProviders = { navController.navigate(Routes.SETTINGS_PROVIDERS) },
                onOpenAutonomy = { navController.navigate(Routes.SETTINGS_AUTONOMY) },
                onOpenServer = { navController.navigate(Routes.SETTINGS_SERVER) },
                onOpenRuntime = { navController.navigate(Routes.RUNTIME) },
            )
        }
        composable(Routes.SETTINGS_SESSIONS) {
            SettingsSessionsScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.SETTINGS_CHAT) {
            SettingsChatScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.SETTINGS_APPEARANCE) {
            SettingsAppearanceScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.SETTINGS_PROVIDERS) {
            SettingsProvidersScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.SETTINGS_AUTONOMY) {
            SettingsAutonomyScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.SETTINGS_SERVER) {
            SettingsServerScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.RUNTIME) {
            RuntimeScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.MISSION_LAUNCH) {
            MissionLaunchScreen(
                onBack = { navController.popBackStack() },
                onStarted = { id ->
                    navController.navigate(Routes.missionDetail(id)) {
                        popUpTo(Routes.MISSION_LAUNCH) { inclusive = true }
                    }
                }
            )
        }
        composable(
            route = Routes.MISSION_DETAIL_PATTERN,
            arguments = listOf(navArgument(Routes.ARG_MISSION_ID) { type = NavType.StringType })
        ) { entry ->
            val id = entry.arguments?.getString(Routes.ARG_MISSION_ID).orEmpty()
            MissionDetailScreen(
                missionId = id,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
