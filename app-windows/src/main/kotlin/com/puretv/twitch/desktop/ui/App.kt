package com.puretv.twitch.desktop.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tv
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.WindowState
import coil3.ImageLoader
import coil3.compose.setSingletonImageLoaderFactory
import coil3.network.ktor3.KtorNetworkFetcherFactory
import coil3.request.crossfade
import com.puretv.twitch.desktop.data.DesktopSettingsStore
import com.puretv.twitch.desktop.ui.emotes.EmoteFrameCache
import com.puretv.twitch.desktop.ui.emotes.LocalEmoteAnimation
import com.puretv.twitch.desktop.ui.emotes.LocalEmoteFrameCache
import com.puretv.twitch.desktop.platform.WindowsNative
import com.puretv.twitch.desktop.update.UpdateManager
import com.puretv.twitch.desktop.update.UpdateState
import com.puretv.twitch.desktop.ui.components.FollowedRail
import com.puretv.twitch.desktop.ui.components.Kicker
import com.puretv.twitch.desktop.ui.components.UpdateBanner
import com.puretv.twitch.desktop.ui.screens.BrowseContent
import com.puretv.twitch.desktop.ui.screens.CategoryContent
import com.puretv.twitch.desktop.ui.screens.ChannelContent
import com.puretv.twitch.desktop.ui.screens.HomeContent
import com.puretv.twitch.desktop.ui.screens.LoginContent
import com.puretv.twitch.desktop.ui.screens.SearchContent
import com.puretv.twitch.desktop.ui.screens.SettingsContent
import com.puretv.twitch.desktop.ui.screens.StreamContent
import com.puretv.twitch.desktop.ui.screens.VodPlayerContent
import com.puretv.twitch.desktop.ui.theme.PureTvDesktopTheme
import com.puretv.twitch.desktop.ui.theme.PureTvTheme
import com.puretv.twitch.desktop.ui.theme.PureTvType
import com.puretv.twitch.desktop.ui.theme.ThemeVariant
import java.awt.MouseInfo
import java.awt.Window as AwtWindow
import kotlinx.coroutines.delay
import org.koin.core.Koin

enum class Destination(val label: String, val icon: ImageVector) {
    HOME("Home", Icons.Filled.Home),
    BROWSE("Browse", Icons.Filled.Tv),
    SEARCH("Search", Icons.Filled.Search),
    SETTINGS("Settings", Icons.Filled.Settings),
    ACCOUNT("Account", Icons.AutoMirrored.Filled.Login),
}

private sealed class Route {
    data object Top : Route()
    data class Category(val gameId: String, val gameName: String) : Route()
    data class Channel(val login: String) : Route()
    data class Stream(val login: String) : Route()
    data class Vod(val launch: VodLaunch) : Route()
}

@Composable
fun App(koin: Koin, windowState: WindowState, onClose: () -> Unit, awtWindow: AwtWindow) {
    // coil3's setSingletonImageLoaderFactory is itself @Composable and internally
    // memoizes the singleton, so calling it here in the composable body (idempotent
    // factory swap) is the intended usage — it can't be hoisted into remember{}.
    setSingletonImageLoaderFactory { context ->
        ImageLoader.Builder(context)
            .components { add(KtorNetworkFetcherFactory()) }
            .crossfade(true)
            .build()
    }

    val settingsStore = remember { koin.get<DesktopSettingsStore>() }
    val settings by settingsStore.settings.collectAsState()
    val themeVariant = ThemeVariant.fromKey(settings.theme)
    val shell = rememberAppShellController(windowState, awtWindow)

    val updateManager = remember { koin.get<UpdateManager>() }
    val updateState by updateManager.state.collectAsState()
    var updateDismissed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { updateManager.checkForUpdates() }

    PureTvDesktopTheme(variant = themeVariant) {
        val emoteFrameCache = remember { koin.get<EmoteFrameCache>() }
        CompositionLocalProvider(
            LocalAppShell provides shell,
            LocalEmoteAnimation provides settings.animateEmotes,
            LocalEmoteFrameCache provides emoteFrameCache,
        ) {
            var destination by remember { mutableStateOf(Destination.HOME) }
            var route by remember { mutableStateOf<Route>(Route.Top) }
            val c = PureTvTheme.colors

            Surface(modifier = Modifier.fillMaxSize(), color = c.background) {
                Column(modifier = Modifier.fillMaxSize()) {
                    if (shell.playerMode != PlayerMode.FULLSCREEN) {
                        CustomTitleBar(shell = shell, onClose = onClose, awtWindow = awtWindow)
                        Box(Modifier.fillMaxWidth().height(1.dp).background(c.hairline))
                        if (!updateDismissed) {
                            UpdateBanner(
                                state = updateState,
                                onUpdate = {
                                    val s = updateState
                                    if (s is UpdateState.Available) updateManager.downloadAndInstall(s.info, onClose)
                                    else updateManager.checkForUpdates(force = true)
                                },
                                onDismiss = { updateDismissed = true },
                            )
                        }
                    }

                    Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        AnimatedVisibility(
                            visible = !shell.isImmersive,
                            enter = slideInHorizontally { -it },
                            exit = slideOutHorizontally { -it },
                        ) {
                            NavigationSidebar(
                                koin = koin,
                                selected = destination,
                                onSelect = {
                                    destination = it
                                    route = Route.Top
                                },
                                onOpenChannel = { login -> route = Route.Channel(login) },
                                onSignIn = {
                                    destination = Destination.ACCOUNT
                                    route = Route.Top
                                },
                            )
                        }

                        Box(modifier = Modifier.fillMaxSize()) {
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
            .height(38.dp)
            .background(c.background),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Drag zone — fills available width to the left of the window buttons.
        // On the first real drag movement we hand the gesture to Windows via a
        // native move loop (WindowsNative.startWindowDrag), which gives us Aero
        // Snap / Snap Layouts for free. A double-click toggles maximize, matching
        // native title-bar behavior. If the native call is unavailable we fall
        // back to manual repositioning so the title bar is never "stuck".
        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .padding(start = 12.dp)
                .pointerInput(awtWindow) {
                    var lastDownTime = 0L
                    var manualDrag = false
                    var winX0 = 0; var winY0 = 0
                    var ptrX0 = 0; var ptrY0 = 0
                    awaitPointerEventScope {
                        while (true) {
                            val ev = awaitPointerEvent()
                            val change = ev.changes.firstOrNull()
                            when (ev.type) {
                                PointerEventType.Press -> {
                                    val t = change?.uptimeMillis ?: 0L
                                    if (t - lastDownTime in 1..400) {
                                        // Double-click on the caption → maximize/restore.
                                        shell.toggleMaximize()
                                        lastDownTime = 0L
                                        manualDrag = false
                                    } else if (shell.isMaximized) {
                                        // Maximized = locked. Ignore drags so the filled window
                                        // can't be nudged or accidentally un-maximized mid-drag.
                                        // Restore it first via the maximize button (or a
                                        // double-click), and then it's draggable again — a
                                        // calmer, more predictable feel than Windows' default.
                                        lastDownTime = t
                                        manualDrag = false
                                    } else {
                                        lastDownTime = t
                                        // Hand the drag to the OS at button-down. startWindowDrag
                                        // BLOCKS in the OS move loop until the mouse is released
                                        // *if* it takes over; if it returns almost immediately it
                                        // didn't engage, so we drive a manual drag instead (the bar
                                        // is never left unresponsive).
                                        val press = MouseInfo.getPointerInfo().location
                                        ptrX0 = press.x; ptrY0 = press.y
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
                                            manualDrag = false
                                            snapOnDrop(shell, ptrX0, ptrY0)
                                        }
                                    }
                                }
                                PointerEventType.Move -> if (manualDrag && change?.pressed == true) {
                                    val p = MouseInfo.getPointerInfo().location
                                    awtWindow.setLocation(winX0 + p.x - ptrX0, winY0 + p.y - ptrY0)
                                }
                                PointerEventType.Release -> {
                                    if (manualDrag) snapOnDrop(shell, ptrX0, ptrY0)
                                    manualDrag = false
                                }
                            }
                        }
                    }
                },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .background(c.twitchPurple, RoundedCornerShape(5.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text("P", color = c.background, fontFamily = PureTvType.display, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
            }
            Spacer(Modifier.width(8.dp))
            Text("PureTV for Twitch", color = c.textSecondary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        }

        // Window control buttons — outside the drag zone so clicks are not swallowed
        WinButton(onClick = { shell.minimize() }) { MinimizeIcon() }
        WinButton(onClick = { shell.toggleMaximize() }) { MaximizeIcon(shell.isMaximized) }
        WinButton(onClick = onClose, isClose = true) {
            Icon(Icons.Filled.Close, "Close", tint = PureTvTheme.colors.textSecondary, modifier = Modifier.size(14.dp))
        }
    }
}

/**
 * Snaps the window when a title-bar drag is released at a screen edge. Requires
 * real movement from the [pressX]/[pressY] button-down point (screen coords) so a
 * plain click — even on a window already sitting at the top of the screen — never
 * triggers a snap.
 */
private fun snapOnDrop(shell: AppShellController, pressX: Int, pressY: Int) {
    val rel = MouseInfo.getPointerInfo().location
    if (kotlin.math.abs(rel.x - pressX) + kotlin.math.abs(rel.y - pressY) > 24) {
        shell.snapForDrop(rel)
    }
}

@Composable
private fun WinButton(onClick: () -> Unit, isClose: Boolean = false, content: @Composable () -> Unit) {
    val c = PureTvTheme.colors
    var hovered by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .width(46.dp)
            .fillMaxHeight()
            .background(
                when {
                    isClose && hovered -> Color(0xFFC42B1C)
                    hovered -> c.surfaceHover
                    else -> Color.Transparent
                },
            )
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val ev = awaitPointerEvent()
                        hovered = when (ev.type) {
                            PointerEventType.Enter -> true
                            PointerEventType.Exit -> false
                            else -> hovered
                        }
                    }
                }
            }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
private fun MinimizeIcon() {
    Box(Modifier.size(width = 10.dp, height = 1.dp).background(PureTvTheme.colors.textSecondary))
}

@Composable
private fun MaximizeIcon(isMaximized: Boolean) {
    val color = PureTvTheme.colors.textSecondary
    Box(
        Modifier.size(10.dp).drawBehind {
            val s = Stroke(width = 1.5.dp.toPx())
            val r = CornerRadius(1.dp.toPx())
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

// ── Navigation sidebar ─────────────────────────────────────────────────────────

@Composable
private fun NavigationSidebar(
    koin: Koin,
    selected: Destination,
    onSelect: (Destination) -> Unit,
    onOpenChannel: (String) -> Unit,
    onSignIn: () -> Unit,
) {
    val c = PureTvTheme.colors
    val railVm = rememberDesktopViewModel { koin.get<FollowedRailViewModel>() }
    val railState by railVm.state.collectAsState()
    val windowInfo = LocalWindowInfo.current

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

    Column(
        modifier = Modifier
            .fillMaxHeight()
            .width(200.dp)
            .background(c.surface),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .background(c.twitchPurple, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text("P", color = c.background, fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleLarge)
            }
            Text(
                "PureTV",
                color = c.textPrimary,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(start = 10.dp),
            )
        }

        Box(Modifier.fillMaxWidth().height(1.dp).background(c.hairline))
        Spacer(Modifier.height(16.dp))
        Kicker("Menu", modifier = Modifier.padding(horizontal = 18.dp, vertical = 2.dp))
        Spacer(Modifier.height(8.dp))

        Destination.entries.forEach { dest ->
            NavItem(icon = dest.icon, label = dest.label, selected = selected == dest, onClick = { onSelect(dest) })
        }

        Spacer(Modifier.height(16.dp))
        FollowedRail(
            state = railState,
            onToggleOffline = { railVm.toggleOffline() },
            onOpenChannel = onOpenChannel,
            onSignIn = onSignIn,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun NavItem(icon: ImageVector, label: String, selected: Boolean, onClick: () -> Unit) {
    val c = PureTvTheme.colors
    var hovered by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp)) {
        if (selected) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(28.dp)
                    .align(Alignment.CenterStart)
                    .background(c.twitchPurple, RoundedCornerShape(topEnd = 4.dp, bottomEnd = 4.dp)),
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 10.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(when { selected -> c.twitchPurple.copy(alpha = 0.14f); hovered -> c.surfaceHover; else -> Color.Transparent })
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val ev = awaitPointerEvent()
                            hovered = when (ev.type) { PointerEventType.Enter -> true; PointerEventType.Exit -> false; else -> hovered }
                        }
                    }
                }
                .clickable(onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, label, tint = if (selected) c.twitchPurpleLight else c.textSecondary, modifier = Modifier.size(18.dp))
            Text(
                label,
                color = if (selected) c.textPrimary else c.textSecondary,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                modifier = Modifier.padding(start = 10.dp),
            )
        }
    }
}
