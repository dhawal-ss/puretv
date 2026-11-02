package com.puretv.twitch.desktop.ui

import com.puretv.twitch.desktop.platform.ShortcutKey
import com.puretv.twitch.desktop.platform.WindowChrome
import com.puretv.twitch.desktop.platform.AppDirs
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState
import com.puretv.twitch.desktop.platform.WindowsNative
import com.puretv.twitch.desktop.player.ChannelClaims
import com.puretv.twitch.desktop.player.VlcEngines
import com.puretv.twitch.desktop.player.WindowPlayback
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import com.puretv.twitch.core.model.AppSettings
import com.puretv.twitch.desktop.data.DesktopSettingsStore
import com.puretv.twitch.desktop.ui.components.ExpressiveButton
import com.puretv.twitch.desktop.ui.components.ExpressiveButtonSize
import com.puretv.twitch.desktop.ui.components.ExpressiveButtonStyle
import com.puretv.twitch.desktop.ui.theme.PureTvDesktopTheme
import com.puretv.twitch.desktop.ui.theme.PureTvTheme
import com.puretv.twitch.desktop.ui.theme.ShapeIntensity
import com.puretv.twitch.desktop.ui.theme.ThemeVariant
import java.awt.Dimension
import java.awt.KeyEventDispatcher
import java.awt.event.KeyEvent
import java.awt.Frame
import java.awt.KeyboardFocusManager
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.io.File
import javax.swing.SwingUtilities
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.core.Koin

/**
 * The PureTV windows opened with "New window" or "Open in new window": one OS window per
 * secondary entry of the [WindowRegistry], each a whole [App] with its own screens and its
 * own player. Called from the application, outside the main window, so they stay up when
 * the main window is on another screen. [visible] follows the main window (hiding it to
 * the tray hides them all), [frontRequests] counts requests to bring everything forward.
 *
 * Shared with the main window: sign-in, settings, follows, caches, the update banner and the
 * theme, because they come from the same Koin graph.
 */
@Composable
fun SecondaryWindows(koin: Koin, visible: Boolean, frontRequests: Int, icon: Painter?, onQuit: () -> Unit) {
    val registry = remember { koin.get<WindowRegistry>() }
    val windows by registry.windows.collectAsState()
    val boundsStore = remember { WindowBoundsStores.shared }
    windows.filter { it.kind == WindowKind.SECONDARY }.forEach { entry ->
        key(entry.id) {
            SecondaryWindow(koin, registry, boundsStore, entry, visible, frontRequests, icon, onQuit)
        }
    }
}

/** Where each window slot was left, kept next to the other desktop files. */
internal object WindowBoundsStores {
    val shared: PopoutBoundsStore by lazy {
        PopoutBoundsStore(File(AppDirs.data, "app-windows.txt"))
    }
}

@Composable
private fun SecondaryWindow(
    koin: Koin,
    registry: WindowRegistry,
    boundsStore: PopoutBoundsStore,
    entry: WindowEntry,
    visible: Boolean,
    frontRequests: Int,
    icon: Painter?,
    onQuit: () -> Unit,
) {
    val slotKey = "slot${entry.slot}"
    // Where it opens is decided once: where this slot was left, otherwise another monitor.
    val initial = remember(entry.id) {
        val works = PopoutLayout.screenWorkAreas()
        val open = AppWindows.all().filter { it.isShowing }.map { MiniRect(it.x, it.y, it.width, it.height) }
        val active = KeyboardFocusManager.getCurrentKeyboardFocusManager().activeWindow
        val inUse = (active?.takeIf { w -> AppWindows.all().any { it === w } } ?: AppWindows.all().firstOrNull())
            ?.let { MiniRect(it.x, it.y, it.width, it.height) }
        val fresh = NewWindowLayout.place(works, inUse?.let { PopoutLayout.monitorOf(it, works) }, open)
        if (fresh == null) null else NewWindowLayout.resolve(boundsStore.recall(slotKey), fresh, works)
    }
    val windowState = rememberWindowState(
        position = initial?.let { WindowPosition(it.x.dp, it.y.dp) } ?: WindowPosition.PlatformDefault,
        size = initial?.let { DpSize(it.width.dp, it.height.dp) }
            ?: DpSize(NewWindowLayout.DEFAULT_WIDTH.dp, NewWindowLayout.DEFAULT_HEIGHT.dp),
    )

    // This window's own player, made off the UI thread: the first one loads libVLC, which is slow.
    val secondary = remember(entry.id) { WindowPlayback.secondary(koin.get<VlcEngines>(), koin.get<ChannelClaims>()) }
    var ready by remember(entry.id) { mutableStateOf(false) }
    var failed by remember(entry.id) { mutableStateOf(false) }
    var attempt by remember(entry.id) { mutableStateOf(0) }
    LaunchedEffect(secondary, attempt) {
        failed = false
        ready = withContext(Dispatchers.IO) { runCatching { secondary.prepare() }.getOrDefault(false) }
        failed = !ready
    }
    DisposableEffect(secondary) { onDispose { secondary.release() } }
    DisposableEffect(entry.id) {
        onDispose { koin.get<com.puretv.twitch.desktop.notify.DesktopLiveNotifier>().watchingIn(entry.id, null) }
    }

    Window(
        onCloseRequest = { registry.close(entry.id) },
        visible = visible,
        state = windowState,
        title = entry.title.ifBlank { windowTitle(entry.slot, null) },
        icon = icon,
        undecorated = WindowChrome.customFrame(),
    ) {
        val awtWindow = window
        remember(awtWindow) { WindowChrome.styleNativeFrame(awtWindow) }
        LaunchedEffect(visible) {
            if (!visible) return@LaunchedEffect
            if (!WindowsNative.enableBorderlessSnap(awtWindow)) {
                SwingUtilities.invokeLater { WindowsNative.enableBorderlessSnap(awtWindow) }
            }
        }
        LaunchedEffect(frontRequests) {
            if (frontRequests == 0) return@LaunchedEffect
            if (awtWindow.extendedState and Frame.ICONIFIED != 0) awtWindow.extendedState = awtWindow.extendedState and Frame.ICONIFIED.inv()
            awtWindow.toFront()
        }
        // Ctrl+W closes this window (Esc too while it only shows the error below). Only the window
        // that has the keyboard answers.
        val latestFailed = rememberUpdatedState(failed)
        DisposableEffect(awtWindow) {
            val dispatcher = KeyEventDispatcher { e ->
                if (e.id != KeyEvent.KEY_PRESSED) return@KeyEventDispatcher false
                val closeKey = (e.keyCode == KeyEvent.VK_W && ShortcutKey.isDown(e) && !e.isAltDown) ||
                    (e.keyCode == KeyEvent.VK_ESCAPE && latestFailed.value)
                if (!closeKey || !AppWindows.handlesKeys(awtWindow)) return@KeyEventDispatcher false
                registry.close(entry.id)
                true
            }
            val kfm = KeyboardFocusManager.getCurrentKeyboardFocusManager()
            kfm.addKeyEventDispatcher(dispatcher)
            onDispose { kfm.removeKeyEventDispatcher(dispatcher) }
        }
        DisposableEffect(awtWindow) {
            AppWindows.add(awtWindow)
            awtWindow.minimumSize = Dimension(NewWindowLayout.MIN_WIDTH, NewWindowLayout.MIN_HEIGHT)
            // Where the window is left is remembered for its slot (maximized is not a place).
            val flushSoon = javax.swing.Timer(FLUSH_DELAY_MS) { boundsStore.flush() }.apply { isRepeats = false }
            val track = object : ComponentAdapter() {
                override fun componentMoved(e: ComponentEvent) = save()
                override fun componentResized(e: ComponentEvent) = save()
                private fun save() {
                    // Not a place worth keeping: maximized, or minimized (Windows reports -32000 then).
                    if (!WindowBoundsGate.shouldSave(awtWindow.extendedState, awtWindow.x, awtWindow.y)) return
                    // A fullscreen player covers the whole monitor; that is not a place to come back to.
                    if (awtWindow.graphicsConfiguration?.bounds?.let { it.width == awtWindow.width && it.height == awtWindow.height } == true) return
                    boundsStore.remember(slotKey, MiniRect(awtWindow.x, awtWindow.y, awtWindow.width, awtWindow.height))
                    flushSoon.restart()
                }
            }
            awtWindow.addComponentListener(track)
            onDispose {
                awtWindow.removeComponentListener(track)
                flushSoon.stop()
                AppWindows.remove(awtWindow)
                boundsStore.flush()
            }
        }
        if (ready) {
            App(
                koin = koin,
                windowState = windowState,
                onClose = { registry.close(entry.id) },
                onQuit = onQuit,
                awtWindow = awtWindow,
                onWatching = { koin.get<com.puretv.twitch.desktop.notify.DesktopLiveNotifier>().watchingIn(entry.id, it) },
                windowVisible = visible,
                frontRequests = frontRequests,
                windowId = entry.id,
                slot = entry.slot,
                start = entry.start,
                playback = secondary.playback,
            )
        } else if (failed) {
            SetupFailed(
                themeKey = koin.get<DesktopSettingsStore>().settings.collectAsState().value,
                onRetry = { attempt++ },
                onClose = { registry.close(entry.id) },
            )
        } else {
            Box(Modifier.fillMaxSize().background(Color(0xFF0E0E10)))
        }
    }
}

/** Shown in place of a window that could not get its video player ready: a way to try again and a way out. */
@Composable
private fun SetupFailed(themeKey: AppSettings, onRetry: () -> Unit, onClose: () -> Unit) {
    PureTvDesktopTheme(ThemeVariant.fromKey(themeKey.theme), ShapeIntensity.fromKey(themeKey.shapeIntensity)) {
        val c = PureTvTheme.colors
        Column(
            modifier = Modifier.fillMaxSize().background(c.surfaceLowest).padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text("This window could not start its video player", style = MaterialTheme.typography.titleLarge, color = c.onSurface)
            Spacer(Modifier.height(8.dp))
            Text(
                "Other PureTV windows are not affected. Try again, or close this window (Esc).",
                style = MaterialTheme.typography.bodyMedium,
                color = c.onSurfaceVariant,
            )
            Spacer(Modifier.height(24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ExpressiveButton(text = "Try again", onClick = onRetry, style = ExpressiveButtonStyle.Filled, size = ExpressiveButtonSize.Medium)
                ExpressiveButton(text = "Close window", onClick = onClose, style = ExpressiveButtonStyle.Tonal, size = ExpressiveButtonSize.Medium)
            }
        }
    }
}

private const val FLUSH_DELAY_MS = 600
