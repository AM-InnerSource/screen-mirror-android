package io.bettercommerce.screenmirror.ui

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import io.bettercommerce.screenmirror.ui.navigation.Routes
import io.bettercommerce.screenmirror.ui.screens.HomeScreen
import io.bettercommerce.screenmirror.ui.screens.LoopbackScreen
import io.bettercommerce.screenmirror.ui.screens.ReceiverScreen
import io.bettercommerce.screenmirror.ui.screens.SenderScreen
import io.bettercommerce.screenmirror.ui.screens.SettingsScreen

/**
 * Root composable holding the navigation graph for the app.
 */
@Composable
fun ScreenMirrorApp() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(
                onMirrorThisDevice = { navController.navigate(Routes.SENDER) },
                onViewAnotherDevice = { navController.navigate(Routes.RECEIVER) },
                onSelfTest = { navController.navigate(Routes.LOOPBACK) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
            )
        }
        composable(Routes.SENDER) {
            SenderScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.RECEIVER) {
            ReceiverScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.LOOPBACK) {
            LoopbackScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(onBack = { navController.popBackStack() })
        }
    }
}
