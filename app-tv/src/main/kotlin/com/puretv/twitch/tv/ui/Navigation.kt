package com.puretv.twitch.tv.ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.puretv.twitch.tv.ui.screens.TvBrowseScreen
import com.puretv.twitch.tv.ui.screens.TvChannelScreen
import com.puretv.twitch.tv.ui.screens.TvHomeScreen
import com.puretv.twitch.tv.ui.screens.TvLoginScreen
import com.puretv.twitch.tv.ui.screens.TvSearchScreen
import com.puretv.twitch.tv.ui.screens.TvSettingsScreen
import com.puretv.twitch.tv.ui.screens.TvStreamScreen

/**
 * SECTION 07.2 — TV route table. Mirrors the phone app's [Routes] one-for-one
 * (so `core` repositories/ViewModID parametrization patterns line up) but
 * hosts purpose-built TV composables — see Section 12.2: app-tv shares no UI
 * with app-android.
 *
 *   TvHomeScreen     → Nav rail (Live/Following/Categories/Search/Settings) + content rows
 *   TvBrowseScreen   → Category/game grid, D-pad navigable
 *   TvSearchScreen   → On-screen-keyboard-driven search
 *   TvStreamScreen   → Immersive fullscreen player + auto-hide controls + chat overlay
 *   TvChannelScreen  → Channel profile + watch CTA
 *   TvSettingsScreen → Quality / ad-block / proxy / account, focusable rows
 *   TvLoginScreen    → OAuth entry point (same PKCE flow + redirect scheme as phone)
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
fun PureTvTvNavHost(navController: NavHostController = rememberNavController()) {
    NavHost(navController = navController, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            TvHomeScreen(
                onOpenStream = { navController.navigate(Routes.stream(it)) },
                onOpenChannel = { navController.navigate(Routes.channel(it)) },
                onOpenBrowse = { navController.navigate(Routes.BROWSE) },
                onOpenSearch = { navController.navigate(Routes.SEARCH) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                onOpenLogin = { navController.navigate(Routes.LOGIN) },
            )
        }
        composable(Routes.BROWSE) {
            TvBrowseScreen(onOpenChannel = { navController.navigate(Routes.channel(it)) }, onBack = navController::popBackStack)
        }
        composable(Routes.SEARCH) {
            TvSearchScreen(
                onOpenChannel = { navController.navigate(Routes.channel(it)) },
                onBack = navController::popBackStack,
            )
        }
        composable(Routes.SETTINGS) {
            TvSettingsScreen(onBack = navController::popBackStack)
        }
        composable(Routes.LOGIN) {
            TvLoginScreen(onLoggedIn = { navController.popBackStack() }, onBack = navController::popBackStack)
        }
        composable(Routes.STREAM) { backStackEntry ->
            val channelLogin = backStackEntry.arguments?.getString("channelLogin").orEmpty()
            TvStreamScreen(channelLogin = channelLogin, onBack = navController::popBackStack)
        }
        composable(Routes.CHANNEL) { backStackEntry ->
            val channelLogin = backStackEntry.arguments?.getString("channelLogin").orEmpty()
            TvChannelScreen(
                channelLogin = channelLogin,
                onWatch = { navController.navigate(Routes.stream(channelLogin)) },
                onBack = navController::popBackStack,
            )
        }
    }
}
