package com.puretv.twitch.desktop.ui

import androidx.compose.runtime.snapshotFlow
import com.puretv.twitch.desktop.platform.ShortcutKey
import com.puretv.twitch.desktop.platform.WindowChrome
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.WindowState
import coil3.ImageLoader
import coil3.compose.setSingletonImageLoaderFactory
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.network.ktor3.KtorNetworkFetcherFactory
import coil3.request.crossfade
import com.puretv.twitch.core.stream.PlayableStreamCache
import com.puretv.twitch.desktop.data.DesktopSettingsStore
import com.puretv.twitch.desktop.data.WatchProgressStore
import com.puretv.twitch.desktop.ui.components.LocalStreamPrewarm
import com.puretv.twitch.desktop.ui.emotes.EmoteFrameCache
import com.puretv.twitch.desktop.ui.emotes.LocalEmoteAnimation
import com.puretv.twitch.desktop.ui.emotes.LocalEmoteFrameCache
import com.puretv.twitch.desktop.ui.emotes.LocalEmoteClock
import com.puretv.twitch.desktop.ui.emotes.rememberEmoteClock
import com.puretv.twitch.desktop.platform.WindowsNative
import com.puretv.twitch.desktop.platform.openInBrowser
import com.puretv.twitch.desktop.platform.SystemAnimations
import com.puretv.twitch.desktop.update.UpdateManager
import com.puretv.twitch.desktop.update.UpdateState
import com.puretv.twitch.desktop.ui.components.ContextMenuHost
import com.puretv.twitch.desktop.ui.components.ContextMenuPopup
import com.puretv.twitch.desktop.ui.components.LocalContextMenuHost
import com.puretv.twitch.desktop.ui.components.CountBadge
import com.puretv.twitch.desktop.ui.components.ExpressiveIcons
import com.puretv.twitch.desktop.ui.components.FollowedRail
import com.puretv.twitch.desktop.ui.components.UpdateBanner
import androidx.compose.ui.window.isTraySupported
import com.puretv.twitch.desktop.ui.components.expressiveClickable
import com.puretv.twitch.desktop.ui.screens.BrowseContent
import com.puretv.twitch.desktop.ui.screens.CategoryContent
import com.puretv.twitch.desktop.ui.screens.ChannelContent
import com.puretv.twitch.core.notify.GoLiveAlerts
import com.puretv.twitch.core.playback.FlipOrigin
import com.puretv.twitch.desktop.ui.screens.FollowingContent
import com.puretv.twitch.desktop.ui.screens.HomeContent
import com.puretv.twitch.desktop.ui.screens.LoginContent
import com.puretv.twitch.desktop.ui.screens.MultiViewContent
import com.puretv.twitch.desktop.ui.screens.SearchContent
import com.puretv.twitch.desktop.ui.screens.SettingsContent
import com.puretv.twitch.desktop.ui.screens.StreamContent
import com.puretv.twitch.desktop.ui.screens.VodPlayerContent
import com.puretv.twitch.desktop.ui.theme.PureTvDesktopTheme
import com.puretv.twitch.desktop.ui.theme.LocalQuietMotion
import com.puretv.twitch.desktop.ui.theme.PureTvMotion
import com.puretv.twitch.desktop.ui.theme.RouteDepth
import com.puretv.twitch.desktop.ui.theme.RouteTransition
import com.puretv.twitch.desktop.ui.theme.routeDirection
import com.puretv.twitch.desktop.ui.theme.PureTvTheme
import com.puretv.twitch.desktop.ui.theme.PureTvType
import com.puretv.twitch.desktop.ui.theme.ShapeIntensity
import com.puretv.twitch.desktop.ui.theme.ThemeVariant
import com.puretv.twitch.desktop.player.WindowPlayback
import java.awt.Frame
import java.awt.KeyEventDispatcher
import java.awt.KeyboardFocusManager
import java.awt.MouseInfo
import java.awt.event.KeyEvent
import javax.swing.SwingUtilities
import java.awt.Window as AwtWindow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.drop
import org.koin.core.Koin

/**
 * The navigation destinations, in rail order. Each carries a filled and an
 * outlined glyph: Material 3 marks the selected destination by filling its icon,
 * so selection survives a greyscale reading of the rail.
 */
enum class Destination(val label: String, val icon: ImageVector, val outlineIcon: ImageVector) {
    HOME("Home", ExpressiveIcons.Home, ExpressiveIcons.HomeOutlined),
    FOLLOWING("Following", ExpressiveIcons.Following, ExpressiveIcons.FollowingOutlined),
    BROWSE("Browse", ExpressiveIcons.Browse, ExpressiveIcons.BrowseOutlined),
    SEARCH("Search", ExpressiveIcons.Search, ExpressiveIcons.SearchOutlined),
    SETTINGS("Settings", ExpressiveIcons.Settings, ExpressiveIcons.SettingsOutlined),
    ACCOUNT("Account", ExpressiveIcons.Account, ExpressiveIcons.AccountOutlined),
}

internal sealed class Route {
    data object Top : Route()
    data class Category(val gameId: String, val gameName: String) : Route()
    data class Channel(val login: String) : Route()
    data class Stream(val login: String) : Route()
    /** Several live channels at once, opened from [login]'s stream screen. */
    data class Multi(val login: String) : Route()
    data class Vod(val launch: VodLaunch) : Route()
}

private val RAIL_EXPANDED = 236.dp
private val RAIL_COLLAPSED = 88.dp

/**
 * The window's content.
 *
 * [onClose] is the window's own close (the title bar X): it quits PureTV from the main
 * window and closes just this window from any other. Minimizing the main window is what
 * sends PureTV to the tray (see main.kt). [onQuit] always exits for real; the updater
 * uses it, because an update that only hid the window would leave PureTV running while
 * the installer replaces its files.
 */
@Composable
fun App(
    koin: Koin,
    windowState: WindowState,
    onClose: () -> Unit,
    onQuit: () -> Unit,
    awtWindow: AwtWindow,
    onWatching: (String?) -> Unit = {},
    windowVisible: Boolean = true,
    // Counts requests to bring the window forward (the tray's Open PureTV, a second launch).
    frontRequests: Int = 0,
    // Which window this is (see WindowRegistry). The defaults are the main window.
    windowId: Int = WindowRegistry.PRIMARY_ID,
    slot: Int = 0,
    start: WindowDestination = WindowDestination.Home,
    // The player of a window other than the main one; null uses the app-wide player.
    playback: WindowPlayback? = null,
) {
    val isPrimary = windowId == WindowRegistry.PRIMARY_ID
    val registry = remember { koin.get<WindowRegistry>() }
    // coil3's setSingletonImageLoaderFactory is itself @Composable and internally
    // memoizes the singleton, so calling it here in the composable body (idempotent
    // factory swap) is the intended usage; it can't be hoisted into remember{}.
    setSingletonImageLoaderFactory { context ->
        ImageLoader.Builder(context)
            .components { add(KtorNetworkFetcherFactory()) }
            // Bound the on-heap decoded-image cache explicitly. Coil's default is 25% of
            // max heap; on this app's modest heap that competes with playback + chat and
            // feeds GC pauses, so cap it lower and leave the app room.
            .memoryCache { MemoryCache.Builder().maxSizePercent(context, 0.20).build() }
            // Persist fetched avatars/thumbnails across navigation and restarts so they
            // are not re-downloaded + re-decoded every session (desktop had no disk cache
            // by default). One instance app-wide (see DesktopModule), shared with the
            // animated-emote decoder.
            .diskCache { koin.get<DiskCache>() }
            // Short: a thumbnail landing is not an event, and on a grid that staggers in
            // a longer one would still be fading pictures after the cards have landed.
            .crossfade(PureTvMotion.Fast)
            .build()
    }

    val settingsStore = remember { koin.get<DesktopSettingsStore>() }
    val settings by settingsStore.settings.collectAsState()
    val themeVariant = ThemeVariant.fromKey(settings.theme)
    val shapeIntensity = ShapeIntensity.fromKey(settings.shapeIntensity)
    val shell = rememberAppShellController(windowState, awtWindow)
    LaunchedEffect(shell) { snapshotFlow { windowState.placement }.collect { shell.onPlacementChanged(it) } }

    val updateManager = remember { koin.get<UpdateManager>() }
    val updateState by updateManager.state.collectAsState()
    var updateDismissed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { if (isPrimary) updateManager.checkForUpdates() }

    // The Windows animation-effects switch decides whether things move. Read at start and again
    // whenever the window regains focus, which is when someone who just flipped it comes back.
    val windowFocused = LocalWindowInfo.current.isWindowFocused
    LaunchedEffect(windowFocused) {
        if (windowFocused) {
            PureTvMotion.reduced = !SystemAnimations.enabled()
            registry.focused(windowId)
        }
    }

    val startScreen = remember { start.toStartScreen() }
    var route by remember { mutableStateOf(startScreen.route) }
    // Text for the search box when a window opens on a search (see WindowDestination.Search).
    var searchSeed by remember { mutableStateOf(startScreen.searchQuery) }
    val windowNotice by registry.notice.collectAsState()
    LaunchedEffect(windowNotice) {
        val shown = windowNotice ?: return@LaunchedEffect
        delay(NOTICE_MS)
        registry.clearNotice(shown)
    }
    PureTvDesktopTheme(variant = themeVariant, shapeIntensity = shapeIntensity) {
        val emoteFrameCache = remember { koin.get<EmoteFrameCache>() }
        // One clock for every animated emote in this window (see EmoteClock).
        val emoteClock = rememberEmoteClock()
        val streamPrewarm = remember {
            val cache = koin.get<PlayableStreamCache>()
            val prewarm: (String) -> Unit = { login -> cache.prewarm(login) }
            prewarm
        }
        CompositionLocalProvider(
            LocalAppShell provides shell,
            LocalAwtWindow provides awtWindow,
            LocalWindowPlayback provides playback,
            LocalEmoteAnimation provides settings.animateEmotes,
            LocalEmoteFrameCache provides emoteFrameCache,
            LocalEmoteClock provides emoteClock,
            LocalStreamPrewarm provides streamPrewarm,
            // Decoration that loops (the LIVE pulse) stops while a video is playing.
            LocalQuietMotion provides (route is Route.Stream || route is Route.Multi || route is Route.Vod),
        ) {
            var destination by remember { mutableStateOf(startScreen.tab) }
            var railExpanded by remember { mutableStateOf(true) }
            val c = PureTvTheme.colors
            val shapes = PureTvTheme.shapes
            // The row a stream is opened from is remembered for channel flipping; entry
            // points that are not a row (rail, search, following, notifications) clear it.
            val flipOrigin = remember { koin.get<FlipOrigin>() }

            // What the rest of the app can ask of this window: a go-live toast opening a stream, a
            // card opening a screen, coming forward, showing the multi-stream grid.
            val showTab: (Destination) -> Unit = { destination = it }
            // The OS title names the screen, so Alt+Tab tells the windows apart.
            LaunchedEffect(route, destination) { registry.setTitle(windowId, windowTitle(slot, screenName(route, destination))) }
            // Ctrl (Cmd on macOS) held while a card is clicked opens it in a new window instead of here.
            val windowInfo = LocalWindowInfo.current
            fun ctrlHeld() = ShortcutKey.isHeld(windowInfo.keyboardModifiers)
            fun openChannel(login: String) {
                if (ctrlHeld()) openInNewWindow(WindowDestination.Channel(login)) else route = Route.Channel(login)
            }
            fun openCategory(gameId: String, gameName: String) {
                if (ctrlHeld()) openInNewWindow(WindowDestination.Category(gameId, gameName)) else route = Route.Category(gameId, gameName)
            }
            DisposableEffect(registry, windowId) {
                fun front() {
                    (awtWindow as? Frame)?.let { f ->
                        if (f.extendedState and Frame.ICONIFIED != 0) f.extendedState = f.extendedState and Frame.ICONIFIED.inv()
                    }
                    awtWindow.toFront()
                    awtWindow.requestFocus()
                }
                val host = object : WindowHost {
                    override fun navigate(destination: WindowDestination) = onUiThread {
                        val where = destination
                        val target = where.toStartScreen()
                        shell.exitImmersive()
                        flipOrigin.record(emptyList()) // opened from a notification or menu, not from a row
                        route = target.route
                        // A screen opened on top of a tab keeps the tab; a tab destination switches to it.
                        if (where is WindowDestination.Home || where is WindowDestination.Search || where is WindowDestination.Category) {
                            showTab(target.tab)
                        }
                        if (where is WindowDestination.Search) searchSeed = target.searchQuery
                    }
                    override fun bringToFront() = onUiThread { front() }
                    override fun openMultiGrid() = onUiThread {
                        if (!registry.claimMultiGrid(windowId)) return@onUiThread
                        shell.exitImmersive()
                        if (route !is Route.Multi) route = Route.Multi("")
                        front()
                    }
                }
                registry.attach(windowId, host)
                onDispose { registry.detach(windowId, host) }
            }
            // Ctrl+N opens another window. Only the window that has the keyboard answers.
            DisposableEffect(awtWindow) {
                val dispatcher = KeyEventDispatcher { e ->
                    if (e.id != KeyEvent.KEY_PRESSED || e.keyCode != KeyEvent.VK_N || !ShortcutKey.isDown(e) || e.isAltDown || e.isShiftDown) {
                        return@KeyEventDispatcher false
                    }
                    if (!AppWindows.handlesKeys(awtWindow)) return@KeyEventDispatcher false
                    openInNewWindow(WindowDestination.Home)
                    true
                }
                val kfm = KeyboardFocusManager.getCurrentKeyboardFocusManager()
                kfm.addKeyEventDispatcher(dispatcher)
                onDispose { kfm.removeKeyEventDispatcher(dispatcher) }
            }
            // Closing a window other than the main one leaves no mini player or fullscreen behind.
            DisposableEffect(shell) { onDispose { if (!isPrimary) shell.exitImmersive() } }
            // Go-live toasts skip the channel that is open on screen. Multi-stream tells the
            // notifier about its own tiles (and clears them when it closes).
            LaunchedEffect(route, windowVisible) {
                val r = route
                onWatching(if (windowVisible && r is Route.Stream) r.login else null)
            }
            // One sleep timer for watching: it carries from a stream into multi-stream and back,
            // and is off again once PureTV is on any other screen.
            val multiSession = remember { koin.get<MultiSession>() }
            val sleep = multiSession.sleep
            val multiRunning by multiSession.current.collectAsState()
            // Streams in their own windows are being watched too, wherever the main window is, and so
            // is a stream in any other PureTV window: the timer stays while any of them watches.
            LaunchedEffect(route) {
                registry.setActivity(
                    windowId,
                    playing = route is Route.Stream || route is Route.Vod,
                    watching = route is Route.Stream || route is Route.Multi,
                )
            }
            val allWindows by registry.windows.collectAsState()
            val watching = allWindows.any { it.watching } || multiRunning != null
            LaunchedEffect(watching) { if (!watching) sleep.set(0) }
            // The timer ran out: whatever this window plays stops, wherever the timer was started.
            // Every window acts on each expiry once, whichever window's tick noticed it.
            val expiryGate = remember { SleepExpiryGate(sleep.expiryCount) }
            val expiries = sleep.expiryCount
            LaunchedEffect(expiries) {
                if (!expiryGate.take(expiries)) return@LaunchedEffect
                shell.exitImmersive() // fullscreen, theater and the mini player
                multiSession.end()
                when (val r = route) {
                    is Route.Stream -> route = Route.Channel(r.login)
                    is Route.Vod -> route = Route.Channel(r.launch.channelLogin)
                    is Route.Multi -> route = if (r.login.isBlank()) Route.Top else Route.Channel(r.login)
                    else -> Unit
                }
            }
            // This window's place on the desktop, for the windows popped out of multi-stream to open elsewhere.
            LaunchedEffect(multiSession, awtWindow) {
                if (isPrimary) multiSession.mainBounds = { MiniRect(awtWindow.x, awtWindow.y, awtWindow.width, awtWindow.height) }
            }
            // Multi-stream is one session for the app: only one window shows its grid at a time.
            LaunchedEffect(route is Route.Multi) { if (route !is Route.Multi) registry.releaseMultiGrid(windowId) }
            // The mini player is only ever a stream's: any other screen, and any request to bring
            // PureTV forward, puts the normal window back, so it can never be left stuck small.
            LaunchedEffect(route) { if (route !is Route.Stream) shell.exitMini() }
            LaunchedEffect(frontRequests) { if (frontRequests > 0) shell.exitMini() }
            // Hidden in the tray, nothing should keep playing or keep chat sockets open:
            // leaving the stream (or VOD) route disposes its player and chat.
            LaunchedEffect(windowVisible) {
                // Streams in their own windows stop too: the session ends and its windows close.
                if (!windowVisible && isPrimary) multiSession.end()
                if (!windowVisible && (route is Route.Stream || route is Route.Multi || route is Route.Vod)) {
                    shell.exitImmersive()
                    route = Route.Top
                }
            }

            // The window ground is the DEEPEST surface in the ladder, so the rail and
            // the content pane read as two cards floating on it. That separation is
            // what the 8dp gutter and the 28dp pane corners are for; without the
            // darker ground they would just look like arbitrary rounding.
            val contextMenus = remember { ContextMenuHost() }
            Surface(modifier = Modifier.fillMaxSize(), color = c.surfaceLowest) {
                CompositionLocalProvider(LocalContextMenuHost provides contextMenus) {
                Column(modifier = Modifier.fillMaxSize()) {
                    if (shell.playerMode != PlayerMode.FULLSCREEN && shell.playerMode != PlayerMode.MINI) {
                        CustomTitleBar(
                            shell = shell,
                            onClose = onClose,
                            awtWindow = awtWindow,
                            notice = windowNotice,
                            onNewWindow = { openInNewWindow(WindowDestination.Home) },
                        )
                        UpdateBanner(
                            state = if (updateDismissed) UpdateState.Idle else updateState,
                            onUpdate = {
                                val s = updateState
                                if (s is UpdateState.Available) updateManager.downloadAndInstall(s.info, onQuit)
                                else updateManager.checkForUpdates(force = true)
                            },
                            onOpenReleasePage = { url -> openInBrowser(url) },
                            onDismiss = { updateDismissed = true },
                            updateLabel = if (updateManager.canSelfInstall) "Update" else "Download",
                        )
                    }

                    // Immersive playback drops the gutter so the video fills the window
                    // edge to edge; the pane's rounding goes with it, since a rounded
                    // corner over a black video surface would just show the ground.
                    // Not animated: only a player screen goes immersive, and easing the
                    // gutter would resize the native video surface on every frame. The
                    // rail leaves in the same frame (it has no exit), so the shell
                    // switches in one step and the video resizes once.
                    val gutter = if (shell.isImmersive) 0.dp else 8.dp
                    val paneCorner = if (shell.isImmersive) 0.dp else shapes.pane

                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(start = gutter, end = gutter, bottom = gutter),
                        horizontalArrangement = Arrangement.spacedBy(gutter),
                    ) {
                        AnimatedVisibility(
                            visible = !shell.isImmersive,
                            enter = PureTvMotion.railEnter(),
                            exit = ExitTransition.None,
                        ) {
                            NavigationRail(
                                koin = koin,
                                expanded = railExpanded,
                                // Beside a playing video the width change is one step, not a sweep
                                // that resizes the native surface on every frame.
                                animateWidth = route !is Route.Stream && route !is Route.Multi && route !is Route.Vod,
                                onToggleExpanded = { railExpanded = !railExpanded },
                                selected = destination,
                                onSelect = {
                                    destination = it
                                    route = Route.Top
                                },
                                onOpenChannel = { login ->
                                    flipOrigin.record(emptyList()) // the rail has no row to flip through
                                    openChannel(login)
                                },
                                onResumeVod = { launch -> route = Route.Vod(launch) },
                                onSignIn = {
                                    destination = Destination.ACCOUNT
                                    route = Route.Top
                                },
                            )
                        }

                        // Which way the next screen travels: deeper (+1) comes in from the right, back
                        // (-1) from the left, a sideways move (another top-level tab) rises in place.
                        val depth = when (route) {
                            Route.Top -> RouteDepth.Top
                            is Route.Category, is Route.Channel -> RouteDepth.Detail
                            else -> RouteDepth.Player
                        }
                        val previousDepth = remember { arrayOf(depth) }
                        val routeKey: Any = if (route == Route.Top) destination else route
                        val direction = remember(routeKey) { routeDirection(previousDepth[0], depth) }
                        SideEffect { previousDepth[0] = depth }

                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(paneCorner.coerceAtLeast(0.dp)))
                                .background(c.surface),
                        ) {
                            // The three player screens host the native video surface and swap
                            // instantly: nothing is faded or moved around it. Every other screen
                            // eases in (see RouteTransition).
                            when (val r = route) {
                                is Route.Stream -> StreamContent(
                                    koin = koin,
                                    channelLogin = r.login,
                                    onBack = { route = Route.Channel(r.login) },
                                    onRequestSignIn = {
                                        destination = Destination.ACCOUNT
                                        route = Route.Top
                                    },
                                    // Channel flipping swaps the stream in place.
                                    onFlip = { login -> route = Route.Stream(login) },
                                    onOpenMultiView = {
                                        shell.exitImmersive()
                                        if (registry.claimMultiGrid(windowId)) {
                                            route = Route.Multi(r.login)
                                        } else {
                                            // Multi-stream is one session: it is open in another window.
                                            registry.postNotice("Multi-stream is open in another window.")
                                            registry.showMultiGridHolder(except = windowId)
                                        }
                                    },
                                    sleep = sleep,
                                )
                                is Route.Multi -> MultiViewContent(
                                    koin = koin,
                                    initialLogin = r.login,
                                    onBack = { route = if (r.login.isBlank()) Route.Top else Route.Channel(r.login) },
                                    onWatchSingle = { login -> route = Route.Stream(login) },
                                    sleep = sleep,
                                    onRequestSignIn = {
                                        destination = Destination.ACCOUNT
                                        route = Route.Top
                                    },
                                )
                                is Route.Vod -> VodPlayerContent(
                                    koin = koin,
                                    launch = r.launch,
                                    onBack = { route = Route.Channel(r.launch.channelLogin) },
                                )
                                is Route.Channel -> RouteTransition(routeKey, direction) {
                                    ChannelContent(
                                        koin = koin,
                                        channelLogin = r.login,
                                        onWatch = { route = Route.Stream(r.login) },
                                        onPlayVod = { launch -> route = Route.Vod(launch) },
                                        onBack = { route = Route.Top },
                                    )
                                }
                                is Route.Category -> RouteTransition(routeKey, direction) {
                                    CategoryContent(
                                        koin = koin,
                                        gameId = r.gameId,
                                        gameName = r.gameName,
                                        onOpenChannel = { login -> openChannel(login) },
                                        onBack = { route = Route.Top },
                                    )
                                }
                                Route.Top -> RouteTransition(routeKey, direction) { when (destination) {
                                    Destination.HOME -> HomeContent(
                                        koin = koin,
                                        onOpenChannel = { login -> openChannel(login) },
                                        onResumeVod = { launch -> route = Route.Vod(launch) },
                                        onOpenCategory = { gameId, gameName -> openCategory(gameId, gameName) },
                                        onOpenBrowse = { destination = Destination.BROWSE },
                                        onSignIn = { destination = Destination.ACCOUNT },
                                    )
                                    Destination.FOLLOWING -> FollowingContent(
                                        koin = koin,
                                        onOpenChannel = { login ->
                                            flipOrigin.record(emptyList()) // flips use the live follows
                                            openChannel(login)
                                        },
                                        onSignIn = { destination = Destination.ACCOUNT },
                                    )
                                    Destination.BROWSE -> BrowseContent(koin = koin, onOpenCategory = { gameId, gameName -> openCategory(gameId, gameName) })
                                    Destination.SEARCH -> SearchContent(
                                        koin = koin,
                                        onOpenChannel = { login ->
                                            flipOrigin.record(emptyList()) // search results are not a row to flip through
                                            openChannel(login)
                                        },
                                        initialQuery = searchSeed,
                                    )
                                    Destination.SETTINGS -> SettingsContent(koin = koin, onExit = onQuit)
                                    Destination.ACCOUNT -> LoginContent(koin = koin)
                                } }
                            }
                        }
                    }
                }
                ContextMenuPopup(contextMenus)
                }
            }
        }
    }
}

/** Runs [block] on the UI thread: windows are asked to move from threads of all kinds. */
private fun onUiThread(block: () -> Unit) {
    if (SwingUtilities.isEventDispatchThread()) block() else SwingUtilities.invokeLater(block)
}

// ── Custom title bar ───────────────────────────────────────────────────────────

@Composable
private fun CustomTitleBar(
    shell: AppShellController,
    onClose: () -> Unit,
    awtWindow: AwtWindow,
    notice: String?,
    onNewWindow: () -> Unit,
) {
    val c = PureTvTheme.colors

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .background(c.surfaceLowest),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Drag zone, filling available width to the left of the window buttons.
        // A press alone does NOTHING heavy: the native OS move loop
        // (WindowsNative.startWindowDrag) BLOCKS the EDT until mouse-release, so
        // entering it on every button-down, even a plain click, froze the UI for
        // the whole press. Instead we wait for real drag INTENT (the pointer moving
        // past a small threshold while held); only then do we hand the gesture to
        // Windows, which gives us Aero Snap / Snap Layouts for free. A double-click
        // toggles maximize, matching native title-bar behavior. If the native call
        // is unavailable we fall back to manual repositioning so the bar is never
        // "stuck".
        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .padding(start = 16.dp + WindowChrome.leadingInset())
                .pointerInput(awtWindow) {
                    // Linux: the window manager's title bar moves and maximizes the window.
                    if (!WindowChrome.headerMovesWindow()) return@pointerInput
                    var lastDownTime = 0L
                    var manualDrag = false
                    // armed: pressed on the caption and eligible to start a drag (not a
                    // double-click, not maximized) but no drag intent yet.
                    // handedOff: this press already started a drag (native or manual),
                    // so further Moves don't re-trigger the handoff.
                    var armed = false
                    var handedOff = false
                    var winX0 = 0; var winY0 = 0
                    var ptrX0 = 0; var ptrY0 = 0
                    awaitPointerEventScope {
                        while (true) {
                            val ev = awaitPointerEvent()
                            val change = ev.changes.firstOrNull()
                            when (ev.type) {
                                PointerEventType.Press -> {
                                    val t = change?.uptimeMillis ?: 0L
                                    armed = false; manualDrag = false; handedOff = false
                                    if (t - lastDownTime in 1..400) {
                                        // Double-click on the caption → maximize/restore.
                                        shell.toggleMaximize()
                                        lastDownTime = 0L
                                    } else if (shell.isMaximized) {
                                        // Maximized = locked. Ignore drags so the filled window
                                        // can't be nudged or accidentally un-maximized mid-drag.
                                        // Restore it first via the maximize button (or a
                                        // double-click), and then it's draggable again: a
                                        // calmer, more predictable feel than Windows' default.
                                        lastDownTime = t
                                    } else {
                                        lastDownTime = t
                                        // Record the press anchor (screen coords) and arm, but do
                                        // NOT enter the blocking OS move loop yet. We only commit to
                                        // a drag once the pointer actually moves (see Move below), so
                                        // a plain click never stalls the EDT.
                                        val press = MouseInfo.getPointerInfo().location
                                        ptrX0 = press.x; ptrY0 = press.y
                                        armed = true
                                    }
                                }
                                PointerEventType.Move -> if (change?.pressed == true && (armed || manualDrag)) {
                                    // Only hit MouseInfo (a native round-trip) while a press is
                                    // actually in progress, never on a plain hover-move.
                                    val p = MouseInfo.getPointerInfo().location
                                    if (armed && !handedOff &&
                                        kotlin.math.abs(p.x - ptrX0) + kotlin.math.abs(p.y - ptrY0) > DRAG_THRESHOLD
                                    ) {
                                        // Drag intent confirmed, so hand the gesture to the OS now.
                                        // startWindowDrag BLOCKS in the OS move loop until release
                                        // *if* it takes over; if it returns almost immediately it
                                        // didn't engage, so we drive a manual drag instead (the bar
                                        // is never left unresponsive).
                                        handedOff = true
                                        val startNs = System.nanoTime()
                                        val native = WindowsNative.startWindowDrag(awtWindow)
                                        val blockedMs = (System.nanoTime() - startNs) / 1_000_000
                                        if (!native || blockedMs < 60) {
                                            winX0 = awtWindow.location.x; winY0 = awtWindow.location.y
                                            manualDrag = true
                                        } else {
                                            // Native move loop just ended (mouse released). The OS
                                            // snap engine doesn't engage for a synthesized HTCAPTION
                                            // drag, so snap it ourselves if it was dragged to an edge.
                                            armed = false
                                            snapOnDrop(shell, ptrX0, ptrY0)
                                        }
                                    } else if (manualDrag && change?.pressed == true) {
                                        awtWindow.setLocation(winX0 + p.x - ptrX0, winY0 + p.y - ptrY0)
                                    }
                                }
                                PointerEventType.Release -> {
                                    if (manualDrag) snapOnDrop(shell, ptrX0, ptrY0)
                                    armed = false; manualDrag = false; handedOff = false
                                }
                            }
                        }
                    }
                },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppMark(size = 26.dp, corner = 9.dp, fontSize = 15.sp)
            Spacer(Modifier.width(10.dp))
            Text(
                "PureTV for Twitch",
                color = c.onSurfaceVariant,
                style = MaterialTheme.typography.labelMedium,
            )
            if (notice != null) {
                Spacer(Modifier.width(16.dp))
                Text(
                    notice,
                    color = c.primary,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        // Window control buttons, outside the drag zone so clicks are not swallowed
        WinButton(onClick = onNewWindow) { NewWindowIcon() }
        // macOS and Linux keep the system's own window buttons (see WindowChrome).
        if (WindowChrome.customFrame()) {
            WinButton(onClick = { shell.minimize() }) { MinimizeIcon() }
            WinButton(onClick = { shell.toggleMaximize() }) { MaximizeIcon(shell.isMaximized) }
            WinButton(onClick = onClose, isClose = true) {
                Icon(ExpressiveIcons.Close, "Close", tint = PureTvTheme.colors.onSurfaceVariant, modifier = Modifier.size(18.dp))
            }
        }
        Spacer(Modifier.width(8.dp))
    }
}

/**
 * The app mark: a rounded-square primary chip holding a "P". It morphs to a full
 * circle on hover, which is the smallest and most-seen instance of the shape
 * language the rest of the app is built on.
 */
@Composable
private fun AppMark(
    size: androidx.compose.ui.unit.Dp,
    corner: androidx.compose.ui.unit.Dp,
    fontSize: androidx.compose.ui.unit.TextUnit,
) {
    val c = PureTvTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val radius by animateDpAsState(if (hovered) size / 2 else corner, PureTvMotion.morph(), label = "appMark")

    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(radius.coerceAtLeast(0.dp)))
            .background(c.primary)
            .hoverable(interaction),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "P",
            color = c.onPrimary,
            fontFamily = PureTvType.display,
            fontWeight = FontWeight.ExtraBold,
            fontSize = fontSize,
        )
    }
}

/**
 * Manhattan distance in screen px the pointer must travel while pressed before a
 * title-bar press is treated as a drag (and the blocking OS move loop is entered).
 * Small enough to feel immediate, large enough that a plain click never qualifies.
 */
private const val DRAG_THRESHOLD = 4

/** How long a message in the title bar (why a window did not open) stays. */
private const val NOTICE_MS = 5_000L

/**
 * Snaps the window when a title-bar drag is released at a screen edge. Requires
 * real movement from the [pressX]/[pressY] button-down point (screen coords) so a
 * plain click, even on a window already sitting at the top of the screen, never
 * triggers a snap.
 */
private fun snapOnDrop(shell: AppShellController, pressX: Int, pressY: Int) {
    val rel = MouseInfo.getPointerInfo().location
    if (kotlin.math.abs(rel.x - pressX) + kotlin.math.abs(rel.y - pressY) > 24) {
        shell.snapForDrop(rel)
    }
}

/**
 * Window control. Round at rest like every other icon affordance; close morphs
 * into the error container so the destructive one is unmistakable without
 * needing a different icon.
 */
@Composable
private fun WinButton(onClick: () -> Unit, isClose: Boolean = false, content: @Composable () -> Unit) {
    val c = PureTvTheme.colors
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .size(36.dp)
            .expressiveClickable(
                interaction = interaction,
                onClick = onClick,
                restRadius = 18.dp,
                hoverRadius = if (isClose) 12.dp else PureTvTheme.shapes.pillMorph,
                color = Color.Transparent,
                hoverColor = if (isClose) c.errorContainer else c.surfaceHigh,
            ),
        contentAlignment = Alignment.Center,
    ) { content() }
}

/** A plus: open another PureTV window (Ctrl+N). */
@Composable
private fun NewWindowIcon() {
    val color = PureTvTheme.colors.onSurfaceVariant
    Box(
        Modifier.size(14.dp).drawBehind {
            val w = 1.5.dp.toPx()
            drawLine(color, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), strokeWidth = w)
            drawLine(color, Offset(size.width / 2, 0f), Offset(size.width / 2, size.height), strokeWidth = w)
        },
    )
}

@Composable
private fun MinimizeIcon() {
    Box(
        Modifier
            .size(width = 12.dp, height = 1.5.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(PureTvTheme.colors.onSurfaceVariant),
    )
}

@Composable
private fun MaximizeIcon(isMaximized: Boolean) {
    val color = PureTvTheme.colors.onSurfaceVariant
    Box(
        Modifier.size(11.dp).drawBehind {
            val s = Stroke(width = 1.5.dp.toPx())
            val r = CornerRadius(3.dp.toPx())
            if (isMaximized) {
                val pad = 2.dp.toPx()
                drawRoundRect(color, topLeft = Offset(pad, 0f), size = Size(size.width - pad, size.height - pad), cornerRadius = r, style = s)
                drawRoundRect(color, topLeft = Offset(0f, pad), size = Size(size.width - pad, size.height - pad), cornerRadius = r, style = s)
            } else {
                drawRoundRect(color, cornerRadius = r, style = s)
            }
        },
    )
}

// ── Navigation rail ────────────────────────────────────────────────────────────

/**
 * The Material 3 Expressive navigation rail: a tonal card that expands from an
 * icon strip to a full-width panel carrying labels and the live-follow list.
 *
 * Collapsing is a deliberate affordance rather than a breakpoint. On a stream
 * page the rail is dead weight, and 148dp of width matters at 1280px; the user
 * decides, and the width eases on the shared move curve so the whole shell moves
 * as one thing. Beside a playing video ([animateWidth] false) it changes in one step.
 */
@Composable
private fun NavigationRail(
    koin: Koin,
    expanded: Boolean,
    animateWidth: Boolean,
    onToggleExpanded: () -> Unit,
    selected: Destination,
    onSelect: (Destination) -> Unit,
    onOpenChannel: (String) -> Unit,
    onResumeVod: (VodLaunch) -> Unit,
    onSignIn: () -> Unit,
) {
    val c = PureTvTheme.colors
    val shapes = PureTvTheme.shapes
    val railVm = rememberDesktopViewModel { koin.get<FollowedRailViewModel>() }
    val railState by railVm.state.collectAsState()
    val railSettingsStore = remember { koin.get<DesktopSettingsStore>() }
    val railSettings by railSettingsStore.settings.collectAsState()
    val windowInfo = LocalWindowInfo.current
    val width by animateDpAsState(
        targetValue = if (expanded) RAIL_EXPANDED else RAIL_COLLAPSED,
        // Structural: it feeds layout, so no overshoot (see PureTvMotion.MorphSpring).
        animationSpec = if (animateWidth) PureTvMotion.move() else snap(),
        label = "railWidth",
    )

    // Resume is only offered when there is genuinely something to resume, so the
    // most prominent control in the rail is never a dead button.
    val watchStore = remember { koin.get<WatchProgressStore>() }
    val progress by watchStore.progress.collectAsState()
    val resumable = remember(progress) { watchStore.continueWatching().firstOrNull() }

    // Load once on first composition (regardless of focus, so a cold start that
    // opens unfocused/behind another window still populates), then poll every 60s
    // but only while the window is focused.
    LaunchedEffect(Unit) {
        railVm.refresh()
        while (true) {
            delay(60_000)
            if (windowInfo.isWindowFocused) railVm.refresh()
        }
    }

    // Reload the rail on EVERY auth transition, both directions. On sign-in it
    // populates immediately (instead of sitting empty until the next 60s poll); on
    // sign-out it must reload too so loadOnce() sees the null user id and CLEARS the
    // rail. Otherwise the previous account's follows linger on screen after logout.
    // drop(1) skips the current value (startup is already covered by the initial
    // refresh above), so this fires only on a real login/logout transition.
    LaunchedEffect(Unit) {
        koin.get<DesktopSettingsStore>().loggedInState.drop(1).collect {
            railVm.refresh()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxHeight()
            .width(width)
            .clip(shapes.paneShape)
            .background(c.surfaceContainer)
            .clipToBounds()
            .padding(vertical = 12.dp),
    ) {
        RailRow(
            onClick = onToggleExpanded,
            expanded = expanded,
            height = 52.dp,
            restRadius = 20.dp,
            hoverRadius = shapes.pillMorph,
            color = Color.Transparent,
            hoverColor = c.surfaceHigh,
            icon = ExpressiveIcons.Menu,
            iconTint = c.onSurface,
            contentDescription = if (expanded) "Collapse navigation" else "Expand navigation",
        ) {
            Text(
                "PureTV",
                style = MaterialTheme.typography.titleLarge,
                color = c.onSurface,
                maxLines = 1,
                softWrap = false,
            )
        }

        Spacer(Modifier.height(8.dp))

        if (resumable != null) {
            RailRow(
                onClick = { onResumeVod(VodLaunch(resumable.vodId, resumable.channelLogin, resumable.title, resumable.thumbnailUrl)) },
                expanded = expanded,
                height = 60.dp,
                restRadius = 20.dp,
                hoverRadius = 30.dp,
                color = c.primaryContainer,
                hoverColor = c.primary,
                icon = ExpressiveIcons.Resume,
                iconTint = c.onPrimaryContainer,
                contentDescription = "Resume watching",
            ) {
                Text(
                    "Resume",
                    style = MaterialTheme.typography.labelLarge,
                    color = c.onPrimaryContainer,
                    maxLines = 1,
                    softWrap = false,
                )
            }
            Spacer(Modifier.height(16.dp))
        }

        Destination.entries.forEach { dest ->
            val isSelected = dest == selected
            val liveCount = railState.live.size
            RailRow(
                onClick = { onSelect(dest) },
                expanded = expanded,
                height = 56.dp,
                restRadius = 28.dp,
                hoverRadius = 16.dp,
                color = if (isSelected) c.secondaryContainer else Color.Transparent,
                hoverColor = if (isSelected) c.secondaryContainer else c.surfaceHigh,
                icon = if (isSelected) dest.icon else dest.outlineIcon,
                iconTint = if (isSelected) c.onSecondaryContainer else c.onSurfaceVariant,
                contentDescription = dest.label,
                trailing = {
                    // The live count belongs to Following and only means something when
                    // signed in with someone actually streaming.
                    if (dest == Destination.FOLLOWING && liveCount > 0) CountBadge(liveCount.toString())
                },
            ) {
                Text(
                    dest.label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = if (isSelected) c.onSecondaryContainer else c.onSurfaceVariant,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Clip,
                    modifier = Modifier.weight(1f, fill = false),
                )
            }
            Spacer(Modifier.height(4.dp))
        }

        // The live-follow list is the rail's reason to be wide. Collapsed, it would
        // be a column of anonymous avatars, so it simply is not there.
        if (expanded) {
            FollowedRail(
                state = railState,
                onToggleOffline = { railVm.toggleOffline() },
                onOpenChannel = onOpenChannel,
                onSignIn = onSignIn,
                alertLogins = railSettings.notifyChannels,
                onToggleAlerts = { login ->
                    railSettingsStore.updateSettings { GoLiveAlerts.bellTapped(it, login) }
                },
                modifier = Modifier.weight(1f).fillMaxWidth().padding(top = 20.dp),
            )
        }
    }
}

/**
 * One rail row. Collapsed it is a centred icon; expanded the label fades in
 * beside it. Both states share the same height, fill and morph so the rail's
 * width change is the only thing the eye tracks.
 */
@Composable
private fun RailRow(
    onClick: () -> Unit,
    expanded: Boolean,
    height: androidx.compose.ui.unit.Dp,
    restRadius: androidx.compose.ui.unit.Dp,
    hoverRadius: androidx.compose.ui.unit.Dp,
    color: Color,
    hoverColor: Color,
    icon: ImageVector,
    iconTint: Color,
    contentDescription: String,
    trailing: @Composable () -> Unit = {},
    label: @Composable () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .height(height)
            .expressiveClickable(
                interaction = interaction,
                onClick = onClick,
                restRadius = restRadius,
                hoverRadius = hoverRadius,
                color = color,
                hoverColor = hoverColor,
            )
            .padding(horizontal = if (expanded) 18.dp else 0.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (expanded) Arrangement.spacedBy(14.dp) else Arrangement.Center,
    ) {
        Icon(icon, contentDescription = contentDescription, tint = iconTint, modifier = Modifier.size(24.dp))
        if (expanded) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                label()
                trailing()
            }
        }
    }
}
