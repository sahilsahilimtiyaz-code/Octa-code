package com.sahil.octacode.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.sahil.octacode.ui.screens.ChatScreen
import com.sahil.octacode.ui.screens.HomeScreen
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
        composable(Routes.HOME) { HomeScreen(onOpenChat = { navController.navigate(Routes.CHAT) }) }
        composable(Routes.CHAT) { ChatScreen() }
        composable(Routes.PROJECTS) { ProjectsScreen() }
        composable(Routes.TERMINAL) { TerminalScreen() }
        composable(Routes.SETTINGS) { SettingsScreen() }
    }
}
