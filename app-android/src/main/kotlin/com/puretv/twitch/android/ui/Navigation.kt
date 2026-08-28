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
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.puretv.twitch.android.ui.components.ExpressiveIcons
import com.puretv.twitch.android.ui.components.expressiveClickable
import com.puretv.twitch.android.ui.screens.BrowseScreen
import com.puretv.twitch.android.ui.screens.CategoryScreen
import com.puretv.twitch.android.ui.screens.ChannelScreen
import com.puretv.twitch.android.ui.screens.FollowingScreen
import com.puretv.twitch.android.ui.screens.HomeScreen
import com.puretv.twitch.android.ui.screens.LoginScreen
import com.puretv.twitch.android.ui.screens.SearchScreen
import com.puretv.twitch.android.ui.screens.SettingsScreen
import com.puretv.twitch.android.ui.screens.StreamScreen
import com.puretv.twitch.android.ui.theme.PureTvTheme

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

/** [filledIcon] marks the selected destination, [outlinedIcon] every other tab. */
private data class TopTab(val route: String, val label: String, val filledIcon: ImageVector, val outlinedIcon: ImageVector)

private val TOP_TABS = listOf(
    TopTab(Routes.HOME, "Home", ExpressiveIcons.Home, ExpressiveIcons.HomeOutlined),
    TopTab(Routes.BROWSE, "Browse", ExpressiveIcons.Browse, ExpressiveIcons.BrowseOutlined),
    TopTab(Routes.SEARCH, "Search", ExpressiveIcons.Search, ExpressiveIcons.SearchOutlined),
    TopTab(Routes.FOLLOWING, "Following", ExpressiveIcons.Following, ExpressiveIcons.FollowingOutlined),
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
 * SECTION 06.1: the app shell. A persistent bottom nav bar over the nav graph.
 * The bar shows only on the four tab roots; full-screen routes render above it
 * with the bar hidden. contentWindowInsets is zeroed so the per-screen Scaffolds
 * keep owning the status-bar inset (no double top padding); the bar applies its
 * own bottom system-bar inset.
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
    val c = PureTvTheme.colors
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBar = currentRoute in TOP_TAB_ROUTES
    Scaffold(
        containerColor = c.surfaceLowest,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (showBar) {
                ExpressiveBottomBar(currentRoute = currentRoute, onSelect = { navController.switchTab(it) })
            }
        },
    ) { innerPadding ->
        PureTvNavHost(navController = navController, modifier = Modifier.padding(innerPadding))
    }
}

/**
 * The M3 Expressive bottom nav bar: a surfaceContainer plane rounded only on its
 * top corners (it sits flush against the bottom of the screen), holding one pill
 * per tab. A phone has no hover, so the pill's own press-morph
 * ([expressiveClickable]) is the only shape feedback; selection is carried by a
 * persistent secondaryContainer fill plus the filled glyph, so it survives a
 * still frame rather than needing the motion to be seen. The label only appears
 * on the selected pill, the same "grows to speak" idiom the desktop rail uses
 * for its expanded item.
 */
@Composable
private fun ExpressiveBottomBar(currentRoute: String?, onSelect: (String) -> Unit) {
    val c = PureTvTheme.colors
    val shapes = PureTvTheme.shapes
    val topRounded = RoundedCornerShape(topStart = shapes.pane, topEnd = shapes.pane)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(topRounded)
            .background(c.surfaceContainer)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TOP_TABS.forEach { tab ->
            val selected = currentRoute == tab.route
            val interaction = remember(tab.route) { MutableInteractionSource() }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .expressiveClickable(
                        interaction = interaction,
                        onClick = { onSelect(tab.route) },
                        restRadius = shapes.pill,
                        pressRadius = shapes.pillMorph,
                        selected = selected,
                        selectedColor = c.secondaryContainer,
                    )
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            ) {
                Icon(
                    imageVector = if (selected) tab.filledIcon else tab.outlinedIcon,
                    contentDescription = tab.label,
                    tint = if (selected) c.onSecondaryContainer else c.onSurfaceVariant,
                    modifier = Modifier.size(24.dp),
                )
                if (selected) {
                    Text(
                        tab.label,
                        style = MaterialTheme.typography.labelLarge,
                        color = c.onSecondaryContainer,
                        maxLines = 1,
                    )
                }
            }
        }
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
