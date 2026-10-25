package com.puretv.twitch.android.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import com.puretv.twitch.android.data.SessionManager
import com.puretv.twitch.android.ui.screens.WelcomeScreen
import com.puretv.twitch.core.session.SessionState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
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
import androidx.navigation.NavType
import androidx.navigation.navArgument
import android.net.Uri
import com.puretv.twitch.android.ui.screens.VodPlayerScreen
import com.puretv.twitch.android.ui.components.AskForGoLiveNotifications
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
import com.puretv.twitch.android.ui.theme.LocalReducedMotion
import com.puretv.twitch.android.ui.theme.PureTvMotion
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
 *   STREAM / VOD / CHANNEL / CATEGORY / SETTINGS / LOGIN are full-screen routes.
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
    const val VOD = "vod/{vodId}?login={login}&title={title}&thumb={thumb}"

    fun stream(channelLogin: String) = "stream/$channelLogin"
    fun channel(channelLogin: String) = "channel/$channelLogin"
    fun category(gameId: String) = "category/$gameId"

    /** A past broadcast. Titles carry spaces, slashes and ampersands, so every value is encoded. */
    fun vod(launch: VodLaunch) =
        "vod/${Uri.encode(launch.vodId)}?login=${Uri.encode(launch.channelLogin)}" +
            "&title=${Uri.encode(launch.title)}&thumb=${Uri.encode(launch.thumbnailUrl)}"
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
fun RootScreen(
    navController: NavHostController = rememberNavController(),
    pendingOpen: MutableStateFlow<String?>? = null,
) {
    val sessionManager = koinInject<SessionManager>()
    val session by sessionManager.state.collectAsState()
    val loggedIn = session is SessionState.LoggedIn
    AnimatedContent(
        targetState = loggedIn,
        transitionSpec = { fadeIn(tween(PureTvMotion.Slow, easing = PureTvMotion.Standard)) togetherWith fadeOut(tween(PureTvMotion.Medium)) },
        label = "root-gate",
    ) { isLoggedIn ->
        if (isLoggedIn) MainScaffold(navController = navController, pendingOpen = pendingOpen) else WelcomeScreen()
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
fun MainScaffold(
    navController: NavHostController = rememberNavController(),
    pendingOpen: MutableStateFlow<String?>? = null,
) {
    // The NavController is retained across a logout -> Welcome -> re-login swap, so
    // a previous session's back stack would otherwise survive into the new session
    // (landing re-login deep in the old stack). Entering the shell, reset to the
    // start tab. This runs only when MainScaffold (re)enters composition, i.e. at
    // login; it is a no-op on a fresh stack and does not interfere mid-session.
    // [pendingOpen] carries a route asked for from outside the app (a tapped
    // go-live notification). It is consumed here, after that reset and in the same
    // coroutine, so the reset can never pop the stream the viewer just asked for.
    LaunchedEffect(Unit) {
        runCatching { navController.popBackStack(Routes.HOME, inclusive = false) }
        pendingOpen?.filterNotNull()?.collect { route ->
            pendingOpen.value = null
            runCatching {
                val onStream = navController.currentBackStackEntry?.destination?.route == Routes.STREAM
                when {
                    route in TOP_TAB_ROUTES -> navController.switchTab(route)
                    // Watching another stream: a fresh entry on top. launchSingleTop
                    // would reuse the open stream's entry, and with it that stream's
                    // view model, for the new channel.
                    onStream -> navController.navigate(route)
                    else -> navController.go(route)
                }
            }
        }
    }
    AskForGoLiveNotifications()
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
                val tint by animateColorAsState(
                    if (selected) c.onSecondaryContainer else c.onSurfaceVariant,
                    tween(PureTvMotion.Medium, easing = PureTvMotion.Standard),
                    label = "tabTint",
                )
                Icon(
                    imageVector = if (selected) tab.filledIcon else tab.outlinedIcon,
                    contentDescription = tab.label,
                    tint = tint,
                    modifier = Modifier.size(24.dp),
                )
                // The label grows out of the icon rather than appearing, so the pill's
                // width change reads as the selection moving.
                AnimatedVisibility(visible = selected, enter = PureTvMotion.LabelEnter, exit = PureTvMotion.LabelExit) {
                    Text(
                        tab.label,
                        style = MaterialTheme.typography.labelLarge,
                        color = c.onSecondaryContainer,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
        }
    }
}

/** Routes that host the video surface: they fade only, so the surface is never moved, scaled or recreated. */
private fun String?.hostsPlayer() = this == Routes.STREAM || this == Routes.VOD

@Composable
fun PureTvNavHost(navController: NavHostController, modifier: Modifier = Modifier) {
    val reduced = LocalReducedMotion.current
    NavHost(
        navController = navController,
        startDestination = Routes.HOME,
        // Opaque, so two screens fading across each other never show the root behind them.
        modifier = modifier.background(PureTvTheme.colors.surfaceLowest),
        // Tabs swap with a fade-through; a detail screen pushes in from the right and
        // pops back out to it. Any move to or from a player route is a plain fade.
        enterTransition = {
            val from = initialState.destination.route
            val to = targetState.destination.route
            when {
                to.hostsPlayer() -> PureTvMotion.playerEnter(reduced)
                from in TOP_TAB_ROUTES && to in TOP_TAB_ROUTES -> PureTvMotion.tabEnter(reduced)
                else -> PureTvMotion.pushEnter(reduced)
            }
        },
        exitTransition = {
            val from = initialState.destination.route
            val to = targetState.destination.route
            when {
                from.hostsPlayer() || to.hostsPlayer() -> PureTvMotion.tabExit(reduced)
                from in TOP_TAB_ROUTES && to in TOP_TAB_ROUTES -> PureTvMotion.tabExit(reduced)
                else -> PureTvMotion.pushExit(reduced)
            }
        },
        popEnterTransition = {
            if (initialState.destination.route.hostsPlayer()) PureTvMotion.tabEnter(reduced) else PureTvMotion.popEnter(reduced)
        },
        popExitTransition = {
            if (initialState.destination.route.hostsPlayer()) PureTvMotion.playerExit(reduced) else PureTvMotion.popExit(reduced)
        },
    ) {
        composable(Routes.HOME) {
            HomeScreen(
                onOpenStream = { navController.go(Routes.stream(it)) },
                onOpenChannel = { navController.go(Routes.channel(it)) },
                onOpenBrowse = { navController.switchTab(Routes.BROWSE) },
                onOpenCategory = { navController.go(Routes.category(it)) },
                onOpenSearch = { navController.switchTab(Routes.SEARCH) },
                onOpenVod = { navController.go(Routes.vod(it)) },
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
                onOpenChannel = { navController.go(Routes.channel(it)) },
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
                onOpenVod = { navController.go(Routes.vod(it)) },
                onBack = navController::popBackStack,
            )
        }
        composable(
            Routes.VOD,
            arguments = listOf(
                navArgument("vodId") { type = NavType.StringType },
                navArgument("login") { type = NavType.StringType; defaultValue = "" },
                navArgument("title") { type = NavType.StringType; defaultValue = "" },
                navArgument("thumb") { type = NavType.StringType; defaultValue = "" },
            ),
        ) { backStackEntry ->
            val args = backStackEntry.arguments
            val launch = VodLaunch(
                vodId = args?.getString("vodId").orEmpty(),
                channelLogin = args?.getString("login").orEmpty(),
                title = args?.getString("title").orEmpty(),
                thumbnailUrl = args?.getString("thumb").orEmpty(),
            )
            VodPlayerScreen(launch = launch, onBack = navController::popBackStack)
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
