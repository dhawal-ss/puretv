package com.puretv.twitch.desktop.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
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
import androidx.compose.runtime.LaunchedEffect
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
import okio.Path.Companion.toOkioPath
import com.puretv.twitch.desktop.data.DesktopSettingsStore
import com.puretv.twitch.desktop.data.WatchProgressStore
import com.puretv.twitch.desktop.ui.emotes.EmoteFrameCache
import com.puretv.twitch.desktop.ui.emotes.LocalEmoteAnimation
import com.puretv.twitch.desktop.ui.emotes.LocalEmoteFrameCache
import com.puretv.twitch.desktop.platform.WindowsNative
import com.puretv.twitch.desktop.platform.openInBrowser
import com.puretv.twitch.desktop.update.UpdateManager
import com.puretv.twitch.desktop.update.UpdateState
import com.puretv.twitch.desktop.ui.components.CountBadge
import com.puretv.twitch.desktop.ui.components.ExpressiveIcons
import com.puretv.twitch.desktop.ui.components.FollowedRail
import com.puretv.twitch.desktop.ui.components.UpdateBanner
import com.puretv.twitch.desktop.ui.components.expressiveClickable
import com.puretv.twitch.desktop.ui.screens.BrowseContent
import com.puretv.twitch.desktop.ui.screens.CategoryContent
import com.puretv.twitch.desktop.ui.screens.ChannelContent
import com.puretv.twitch.desktop.ui.screens.FollowingContent
import com.puretv.twitch.desktop.ui.screens.HomeContent
import com.puretv.twitch.desktop.ui.screens.LoginContent
import com.puretv.twitch.desktop.ui.screens.SearchContent
import com.puretv.twitch.desktop.ui.screens.SettingsContent
import com.puretv.twitch.desktop.ui.screens.StreamContent
import com.puretv.twitch.desktop.ui.screens.VodPlayerContent
import com.puretv.twitch.desktop.ui.theme.PureTvDesktopTheme
import com.puretv.twitch.desktop.ui.theme.PureTvMotion
import com.puretv.twitch.desktop.ui.theme.PureTvTheme
import com.puretv.twitch.desktop.ui.theme.PureTvType
import com.puretv.twitch.desktop.ui.theme.ShapeIntensity
import com.puretv.twitch.desktop.ui.theme.ThemeVariant
import java.awt.MouseInfo
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

private sealed class Route {
    data object Top : Route()
    data class Category(val gameId: String, val gameName: String) : Route()
    data class Channel(val login: String) : Route()
    data class Stream(val login: String) : Route()
    data class Vod(val launch: VodLaunch) : Route()
}

private val RAIL_EXPANDED = 236.dp
private val RAIL_COLLAPSED = 88.dp

@Composable
fun App(koin: Koin, windowState: WindowState, onClose: () -> Unit, awtWindow: AwtWindow) {
    // coil3's setSingletonImageLoaderFactory is itself @Composable and internally
    // memoizes the singleton, so calling it here in the composable body (idempotent
    // factory swap) is the intended usage; it can't be hoisted into remember{}.
    setSingletonImageLoaderFactory { context ->
        val imageCacheDir = java.io.File(
            System.getenv("APPDATA") ?: System.getProperty("user.home"),
            "PureTwitch/image_cache",
        )
        ImageLoader.Builder(context)
            .components { add(KtorNetworkFetcherFactory()) }
            // Bound the on-heap decoded-image cache explicitly. Coil's default is 25% of
            // max heap; on this app's modest heap that competes with playback + chat and
            // feeds GC pauses, so cap it lower and leave the app room.
            .memoryCache { MemoryCache.Builder().maxSizePercent(context, 0.20).build() }
            // Persist fetched avatars/thumbnails across navigation and restarts so they
            // are not re-downloaded + re-decoded every session (desktop had no disk cache
            // by default). Bounded so it can never grow without limit.
            .diskCache {
                DiskCache.Builder()
                    .directory(imageCacheDir.toOkioPath())
                    .maxSizeBytes(256L * 1024 * 1024)
                    .build()
            }
            .crossfade(true)
            .build()
    }

    val settingsStore = remember { koin.get<DesktopSettingsStore>() }
    val settings by settingsStore.settings.collectAsState()
    val themeVariant = ThemeVariant.fromKey(settings.theme)
    val shapeIntensity = ShapeIntensity.fromKey(settings.shapeIntensity)
    val shell = rememberAppShellController(windowState, awtWindow)

    val updateManager = remember { koin.get<UpdateManager>() }
    val updateState by updateManager.state.collectAsState()
    var updateDismissed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { updateManager.checkForUpdates() }

    PureTvDesktopTheme(variant = themeVariant, shapeIntensity = shapeIntensity) {
        val emoteFrameCache = remember { koin.get<EmoteFrameCache>() }
        CompositionLocalProvider(
            LocalAppShell provides shell,
            LocalEmoteAnimation provides settings.animateEmotes,
            LocalEmoteFrameCache provides emoteFrameCache,
        ) {
            var destination by remember { mutableStateOf(Destination.HOME) }
            var route by remember { mutableStateOf<Route>(Route.Top) }
            var railExpanded by remember { mutableStateOf(true) }
            val c = PureTvTheme.colors
            val shapes = PureTvTheme.shapes

            // The window ground is the DEEPEST surface in the ladder, so the rail and
            // the content pane read as two cards floating on it. That separation is
            // what the 8dp gutter and the 28dp pane corners are for; without the
            // darker ground they would just look like arbitrary rounding.
            Surface(modifier = Modifier.fillMaxSize(), color = c.surfaceLowest) {
                Column(modifier = Modifier.fillMaxSize()) {
                    if (shell.playerMode != PlayerMode.FULLSCREEN) {
                        CustomTitleBar(shell = shell, onClose = onClose, awtWindow = awtWindow)
                        if (!updateDismissed) {
                            UpdateBanner(
                                state = updateState,
                                onUpdate = {
                                    val s = updateState
                                    if (s is UpdateState.Available) updateManager.downloadAndInstall(s.info, onClose)
                                    else updateManager.checkForUpdates(force = true)
                                },
                                onOpenReleasePage = { url -> openInBrowser(url) },
                                onDismiss = { updateDismissed = true },
                            )
                        }
                    }

                    // Immersive playback drops the gutter so the video fills the window
                    // edge to edge; the pane's rounding goes with it, since a rounded
                    // corner over a black video surface would just show the ground.
                    // Not the morph spring: this one is structural, and an overshooting
                    // spring would drive the value negative on the way to 0, which
                    // Modifier.padding and Arrangement.spacedBy both reject at layout time.
                    val gutter by animateDpAsState(
                        targetValue = if (shell.isImmersive) 0.dp else 8.dp,
                        animationSpec = tween(PureTvMotion.Medium, easing = PureTvMotion.Standard),
                        label = "shellGutter",
                    )
                    val paneCorner by animateDpAsState(
                        targetValue = if (shell.isImmersive) 0.dp else shapes.pane,
                        animationSpec = tween(PureTvMotion.Medium, easing = PureTvMotion.Standard),
                        label = "paneCorner",
                    )

                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(start = gutter, end = gutter, bottom = gutter),
                        horizontalArrangement = Arrangement.spacedBy(gutter),
                    ) {
                        AnimatedVisibility(
                            visible = !shell.isImmersive,
                            enter = slideInHorizontally { -it },
                            exit = slideOutHorizontally { -it },
                        ) {
                            NavigationRail(
                                koin = koin,
                                expanded = railExpanded,
                                onToggleExpanded = { railExpanded = !railExpanded },
                                selected = destination,
                                onSelect = {
                                    destination = it
                                    route = Route.Top
                                },
                                onOpenChannel = { login -> route = Route.Channel(login) },
                                onResumeVod = { launch -> route = Route.Vod(launch) },
                                onSignIn = {
                                    destination = Destination.ACCOUNT
                                    route = Route.Top
                                },
                            )
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(paneCorner.coerceAtLeast(0.dp)))
                                .background(c.surface),
                        ) {
                            when (val r = route) {
                                is Route.Stream -> StreamContent(
                                    koin = koin,
                                    channelLogin = r.login,
                                    onBack = { route = Route.Channel(r.login) },
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
                                is Route.Channel -> ChannelContent(
                                    koin = koin,
                                    channelLogin = r.login,
                                    onWatch = { route = Route.Stream(r.login) },
                                    onPlayVod = { launch -> route = Route.Vod(launch) },
                                    onBack = { route = Route.Top },
                                )
                                is Route.Category -> CategoryContent(
                                    koin = koin,
                                    gameId = r.gameId,
                                    gameName = r.gameName,
                                    onOpenChannel = { login -> route = Route.Channel(login) },
                                    onBack = { route = Route.Top },
                                )
                                Route.Top -> when (destination) {
                                    Destination.HOME -> HomeContent(
                                        koin = koin,
                                        onOpenChannel = { login -> route = Route.Channel(login) },
                                        onResumeVod = { launch -> route = Route.Vod(launch) },
                                    )
                                    Destination.FOLLOWING -> FollowingContent(
                                        koin = koin,
                                        onOpenChannel = { login -> route = Route.Channel(login) },
                                        onSignIn = { destination = Destination.ACCOUNT },
                                    )
                                    Destination.BROWSE -> BrowseContent(koin = koin, onOpenCategory = { gameId, gameName -> route = Route.Category(gameId, gameName) })
                                    Destination.SEARCH -> SearchContent(koin = koin, onOpenChannel = { login -> route = Route.Channel(login) })
                                    Destination.SETTINGS -> SettingsContent(koin = koin, onExit = onClose)
                                    Destination.ACCOUNT -> LoginContent(koin = koin)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── Custom title bar ───────────────────────────────────────────────────────────

@Composable
private fun CustomTitleBar(shell: AppShellController, onClose: () -> Unit, awtWindow: AwtWindow) {
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
                .padding(start = 16.dp)
                .pointerInput(awtWindow) {
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
        }

        // Window control buttons, outside the drag zone so clicks are not swallowed
        WinButton(onClick = { shell.minimize() }) { MinimizeIcon() }
        WinButton(onClick = { shell.toggleMaximize() }) { MaximizeIcon(shell.isMaximized) }
        WinButton(onClick = onClose, isClose = true) {
            Icon(ExpressiveIcons.Close, "Close", tint = PureTvTheme.colors.onSurfaceVariant, modifier = Modifier.size(18.dp))
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
    val radius by animateDpAsState(if (hovered) size / 2 else corner, PureTvMotion.MorphSpring, label = "appMark")

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
 * decides, and the width animates on the same spring as every corner in the app
 * so the whole shell moves as one thing.
 */
@Composable
private fun NavigationRail(
    koin: Koin,
    expanded: Boolean,
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
    val windowInfo = LocalWindowInfo.current
    val width by animateDpAsState(
        targetValue = if (expanded) RAIL_EXPANDED else RAIL_COLLAPSED,
        animationSpec = PureTvMotion.MorphSpring,
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
