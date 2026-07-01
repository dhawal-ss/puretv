package com.puretv.twitch.android.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import com.puretv.twitch.android.data.SessionManager
import com.puretv.twitch.android.ui.screens.WelcomeScreen
import com.puretv.twitch.core.session.SessionState
import org.koin.compose.koinInject
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.puretv.twitch.android.ui.screens.BrowseScreen
import com.puretv.twitch.android.ui.screens.CategoryScreen
import com.puretv.twitch.android.ui.screens.ChannelScreen
import com.puretv.twitch.android.ui.screens.FollowingScreen
import com.puretv.twitch.android.ui.screens.HomeScreen
import com.puretv.twitch.android.ui.screens.LoginScreen
import com.puretv.twitch.android.ui.screens.SearchScreen
import com.puretv.twitch.android.ui.screens.SettingsScreen
import com.puretv.twitch.android.ui.screens.StreamScreen
import com.puretv.twitch.android.ui.theme.PureTvColors

/**
 * Navigate, collapsing a rapid double-tap into a single destination. Without
 * launchSingleTop a fast double-tap on a card pushes two identical entries (and,
 * for the stream route, two attach cycles on the shared ExoPlayer).
 */
private fun NavHostController.go(route: String) = navigate(route) { launchSingleTop = true }

/**
 * Switch bottom-nav tabs, preserving each tab's own back stack and scroll state.
 * popUpTo(start){saveState} + restoreState is the standard Compose bottom-nav
 * pattern: the outgoing tab's stack is saved, the incoming tab's is restored.
 */
private fun NavHostController.switchTab(route: String) = navigate(route) {
    popUpTo(graph.findStartDestination().id) { saveState = true }
    launchSingleTop = true
    restoreState = true
}

/**
 * SECTION 06.2: Jetpack Navigation Compose route table.
 *   HOME / BROWSE / SEARCH / FOLLOWING are bottom-tab roots.
 *   STREAM / CHANNEL / CATEGORY / SETTINGS / LOGIN are full-screen routes.
 */
object Routes {
    const val HOME = "home"
    const val BROWSE = "browse"
    const val SEARCH = "search"
    const val FOLLOWING = "following"
    const val SETTINGS = "settings"
    const val LOGIN = "login"
    const val STREAM = "stream/{channelLogin}"
    const val CHANNEL = "channel/{channelLogin}"
    const val CATEGORY = "category/{gameId}"

    fun stream(channelLogin: String) = "stream/$channelLogin"
    fun channel(channelLogin: String) = "channel/$channelLogin"
    fun category(gameId: String) = "category/$gameId"
}

private data class TopTab(val route: String, val label: String, val icon: ImageVector)

private val TOP_TABS = listOf(
    TopTab(Routes.HOME, "Home", Icons.Filled.Home),
    TopTab(Routes.BROWSE, "Browse", Icons.Filled.GridView),
    TopTab(Routes.SEARCH, "Search", Icons.Filled.Search),
    TopTab(Routes.FOLLOWING, "Following", Icons.Filled.FavoriteBorder),
)

private val TOP_TAB_ROUTES = TOP_TABS.map { it.route }.toSet()

/**
 * SECTION 06.0: the app root. Gates the entire tab shell behind authentication:
 * logged out shows the Welcome connect screen (option C peek), logged in shows
 * the tab shell. The crossfade is the "blur lift" from the gate into content.
 * Sign-in flips SessionState, so this swap and Home's reactive populate happen
 * automatically with no callback.
 */
@Composable
fun RootScreen(navController: NavHostController = rememberNavController()) {
    val sessionManager = koinInject<SessionManager>()
    val session by sessionManager.state.collectAsState()
    val loggedIn = session is SessionState.LoggedIn
    AnimatedContent(
        targetState = loggedIn,
        transitionSpec = { fadeIn(tween(420)) togetherWith fadeOut(tween(420)) },
        label = "root-gate",
    ) { isLoggedIn ->
        if (isLoggedIn) MainScaffold(navController = navController) else WelcomeScreen()
    }
}

/**
 * SECTION 06.1: the app shell. A persistent bottom NavigationBar over the nav
 * graph. The bar shows only on the four tab roots; full-screen routes render
 * above it with the bar hidden. contentWindowInsets is zeroed so the per-screen
 * Scaffolds keep owning the status-bar inset (no double top padding); the
 * NavigationBar applies its own bottom system-bar inset.
 */
@Composable
fun MainScaffold(navController: NavHostController = rememberNavController()) {
    // The NavController is retained across a logout -> Welcome -> re-login swap, so
    // a previous session's back stack would otherwise survive into the new session
    // (landing re-login deep in the old stack). Entering the shell, reset to the
    // start tab. This runs only when MainScaffold (re)enters composition, i.e. at
    // login; it is a no-op on a fresh stack and does not interfere mid-session.
    LaunchedEffect(Unit) {
        runCatching { navController.popBackStack(Routes.HOME, inclusive = false) }
    }
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBar = currentRoute in TOP_TAB_ROUTES
    Scaffold(
        containerColor = PureTvColors.Background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (showBar) {
                NavigationBar(containerColor = PureTvColors.Surface1) {
                    TOP_TABS.forEach { tab ->
                        NavigationBarItem(
                            selected = currentRoute == tab.route,
                            onClick = { navController.switchTab(tab.route) },
                            icon = { Icon(tab.icon, contentDescription = tab.label) },
                            label = { Text(tab.label) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = PureTvColors.TwitchPurpleLight,
                                selectedTextColor = PureTvColors.TwitchPurpleLight,
                                indicatorColor = PureTvColors.Surface3,
                                unselectedIconColor = PureTvColors.TextSecondary,
                                unselectedTextColor = PureTvColors.TextSecondary,
                            ),
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        PureTvNavHost(navController = navController, modifier = Modifier.padding(innerPadding))
    }
}

@Composable
fun PureTvNavHost(navController: NavHostController, modifier: Modifier = Modifier) {
    NavHost(
        navController = navController,
        startDestination = Routes.HOME,
        modifier = modifier,
        enterTransition = {
            val bothTabs = initialState.destination.route in TOP_TAB_ROUTES &&
                targetState.destination.route in TOP_TAB_ROUTES
            if (bothTabs) fadeIn(tween(180))
            else fadeIn(tween(220)) + scaleIn(initialScale = 0.96f, animationSpec = tween(220))
        },
        exitTransition = {
            val bothTabs = initialState.destination.route in TOP_TAB_ROUTES &&
                targetState.destination.route in TOP_TAB_ROUTES
            if (bothTabs) fadeOut(tween(180)) else fadeOut(tween(160))
        },
        popEnterTransition = { fadeIn(tween(180)) },
        popExitTransition = { fadeOut(tween(160)) + scaleOut(targetScale = 0.96f, animationSpec = tween(160)) },
    ) {
        composable(Routes.HOME) {
            HomeScreen(
                onOpenStream = { navController.go(Routes.stream(it)) },
                onOpenChannel = { navController.go(Routes.channel(it)) },
                onOpenBrowse = { navController.switchTab(Routes.BROWSE) },
                onOpenCategory = { navController.go(Routes.category(it)) },
                onOpenSearch = { navController.switchTab(Routes.SEARCH) },
                onOpenSettings = { navController.go(Routes.SETTINGS) },
                onOpenLogin = { navController.go(Routes.LOGIN) },
            )
        }
        composable(Routes.BROWSE) {
            BrowseScreen(onOpenCategory = { navController.go(Routes.category(it)) })
        }
        composable(Routes.SEARCH) {
            SearchScreen(onOpenChannel = { navController.go(Routes.channel(it)) })
        }
        composable(Routes.FOLLOWING) {
            FollowingScreen(
                onOpenStream = { navController.go(Routes.stream(it)) },
                onOpenLogin = { navController.go(Routes.LOGIN) },
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
