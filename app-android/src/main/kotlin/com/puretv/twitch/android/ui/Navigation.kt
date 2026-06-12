package com.puretv.twitch.android.ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.puretv.twitch.android.ui.screens.BrowseScreen
import com.puretv.twitch.android.ui.screens.ChannelScreen
import com.puretv.twitch.android.ui.screens.HomeScreen
import com.puretv.twitch.android.ui.screens.LoginScreen
import com.puretv.twitch.android.ui.screens.SearchScreen
import com.puretv.twitch.android.ui.screens.SettingsScreen
import com.puretv.twitch.android.ui.screens.StreamScreen

/**
 * SECTION 06.2 — Jetpack Navigation Compose route table.
 *
 *   HomeScreen     → Followed channels + Featured streams grid
 *   BrowseScreen   → Categories / games grid
 *   SearchScreen   → Search channels + games
 *   StreamScreen   → Full-screen player + chat sidebar
 *   ChannelScreen  → Channel profile + recent clips
 *   SettingsScreen → Quality, proxy config, ad-block mode, account
 *   LoginScreen    → OAuth entry point
 */
object Routes {
    const val HOME = "home"
    const val BROWSE = "browse"
    const val SEARCH = "search"
    const val SETTINGS = "settings"
    const val LOGIN = "login"
    const val STREAM = "stream/{channelLogin}"
    const val CHANNEL = "channel/{channelLogin}"

    fun stream(channelLogin: String) = "stream/$channelLogin"
    fun channel(channelLogin: String) = "channel/$channelLogin"
}

@Composable
fun PureTvNavHost(navController: NavHostController = rememberNavController()) {
    NavHost(navController = navController, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(
                onOpenStream = { navController.navigate(Routes.stream(it)) },
                onOpenChannel = { navController.navigate(Routes.channel(it)) },
                onOpenBrowse = { navController.navigate(Routes.BROWSE) },
                onOpenSearch = { navController.navigate(Routes.SEARCH) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                onOpenLogin = { navController.navigate(Routes.LOGIN) },
            )
        }
        composable(Routes.BROWSE) {
            BrowseScreen(onOpenChannel = { navController.navigate(Routes.channel(it)) }, onBack = navController::popBackStack)
        }
        composable(Routes.SEARCH) {
            SearchScreen(
                onOpenChannel = { navController.navigate(Routes.channel(it)) },
                onBack = navController::popBackStack,
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(onBack = navController::popBackStack)
        }
        composable(Routes.LOGIN) {
            LoginScreen(onLoggedIn = { navController.popBackStack() }, onBack = navController::popBackStack)
        }
        composable(Routes.STREAM) { backStackEntry ->
            val channelLogin = backStackEntry.arguments?.getString("channelLogin").orEmpty()
            StreamScreen(channelLogin = channelLogin, onBack = navController::popBackStack)
        }
        composable(Routes.CHANNEL) { backStackEntry ->
            val channelLogin = backStackEntry.arguments?.getString("channelLogin").orEmpty()
            ChannelScreen(
                channelLogin = channelLogin,
                onWatch = { navController.navigate(Routes.stream(channelLogin)) },
                onBack = navController::popBackStack,
            )
        }
    }
}
