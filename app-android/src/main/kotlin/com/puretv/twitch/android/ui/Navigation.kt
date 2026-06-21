package com.puretv.twitch.android.ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.puretv.twitch.android.ui.screens.BrowseScreen
import com.puretv.twitch.android.ui.screens.CategoryScreen
import com.puretv.twitch.android.ui.screens.ChannelScreen
import com.puretv.twitch.android.ui.screens.HomeScreen
import com.puretv.twitch.android.ui.screens.LoginScreen
import com.puretv.twitch.android.ui.screens.SearchScreen
import com.puretv.twitch.android.ui.screens.SettingsScreen
import com.puretv.twitch.android.ui.screens.StreamScreen

/**
 * Navigate, collapsing a rapid double-tap into a single destination. Without
 * launchSingleTop a fast double-tap on a card pushes two identical entries onto
 * the back stack (and, for the stream route, two attach cycles on the shared
 * ExoPlayer), which looks janky and wastes a back press.
 */
private fun NavHostController.go(route: String) = navigate(route) { launchSingleTop = true }

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
    const val CATEGORY = "category/{gameId}"

    fun stream(channelLogin: String) = "stream/$channelLogin"
    fun channel(channelLogin: String) = "channel/$channelLogin"
    fun category(gameId: String) = "category/$gameId"
}

@Composable
fun PureTvNavHost(navController: NavHostController = rememberNavController()) {
    NavHost(navController = navController, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(
                onOpenStream = { navController.go(Routes.stream(it)) },
                onOpenChannel = { navController.go(Routes.channel(it)) },
                onOpenBrowse = { navController.go(Routes.BROWSE) },
                onOpenCategory = { navController.go(Routes.category(it)) },
                onOpenSearch = { navController.go(Routes.SEARCH) },
                onOpenSettings = { navController.go(Routes.SETTINGS) },
                onOpenLogin = { navController.go(Routes.LOGIN) },
            )
        }
        composable(Routes.BROWSE) {
            BrowseScreen(
                onOpenCategory = { navController.go(Routes.category(it)) },
                onBack = navController::popBackStack,
            )
        }
        composable(Routes.SEARCH) {
            SearchScreen(
                onOpenChannel = { navController.go(Routes.channel(it)) },
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
                onWatch = { navController.go(Routes.stream(channelLogin)) },
                onBack = navController::popBackStack,
            )
        }
        composable(Routes.CATEGORY) { backStackEntry ->
            val gameId = backStackEntry.arguments?.getString("gameId").orEmpty()
            CategoryScreen(
                gameId = gameId,
                onOpenStream = { navController.go(Routes.stream(it)) },
                onBack = navController::popBackStack,
            )
        }
    }
}
