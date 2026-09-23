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
import com.sahil.octacode.ui.screens.SettingsScreen
import com.sahil.octacode.ui.screens.SplashScreen
import com.sahil.octacode.ui.screens.TerminalScreen

@Composable
fun OctaNavGraph(navController: NavHostController) {
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
        composable(Routes.CHAT) { ChatScreen() }
        composable(Routes.PROJECTS) { ProjectsScreen() }
        composable(Routes.TERMINAL) { TerminalScreen() }
        composable(Routes.SETTINGS) { SettingsScreen() }
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
