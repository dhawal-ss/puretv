package com.puretv.twitch.desktop

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.puretv.twitch.core.di.coreModule
import com.puretv.twitch.desktop.data.DesktopSettingsStore
import com.puretv.twitch.desktop.di.desktopModule
import com.puretv.twitch.desktop.platform.WindowsNative
import com.puretv.twitch.desktop.player.LocalStreamProxy
import com.puretv.twitch.desktop.player.DesktopPlayer
import com.puretv.twitch.desktop.ui.App
import io.ktor.client.HttpClient
import javax.swing.SwingUtilities
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.koin.core.context.GlobalContext.startKoin
import org.koin.dsl.koinApplication

fun main() {
    val koinApp = koinApplication {
        modules(coreModule, desktopModule)
    }
    startKoin(koinApp)

    val settingsStore = koinApp.koin.get<DesktopSettingsStore>()
    // Refresh a near-expired access token at startup so the session survives the
    // ~4h token lifetime instead of silently dying across restarts (audit F2).
    // Fire-and-forget on a background scope so it never blocks UI startup.
    CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
        runCatching { settingsStore.refreshIfNeeded(koinApp.koin.get<HttpClient>()) }
    }

    val vlcPlayer = koinApp.koin.get<DesktopPlayer>()
    val localStreamProxy = koinApp.koin.get<LocalStreamProxy>()
    Runtime.getRuntime().addShutdownHook(Thread {
        runCatching { runBlocking { localStreamProxy.stop() } }
        runCatching { vlcPlayer.release() }
    })

    // Create icon once before the Compose application loop so it isn't
    // recreated on every recomposition.
    val appIcon = createAppIcon()

    application {
        val windowState: WindowState = rememberWindowState(width = 1280.dp, height = 720.dp)
        Window(
            onCloseRequest = ::exitApplication,
            state = windowState,
            title = "PureTV for Twitch",
            icon = appIcon,
            undecorated = true,
        ) {
            // Re-enable Windows-native window management (Aero Snap, Snap Layouts,
            // Win+Arrow, edge-resize) on our undecorated frame. Runs once the
            // window is realized; one EDT-deferred retry covers the rare case
            // where the HWND isn't bound yet at first composition.
            LaunchedEffect(Unit) {
                if (!WindowsNative.enableBorderlessSnap(window)) {
                    SwingUtilities.invokeLater { WindowsNative.enableBorderlessSnap(window) }
                }
            }
            // `window` is FrameWindowScope.window (ComposeWindow extends java.awt.Frame).
            // Pass it to App so the custom title bar can move the window without
            // relying on LocalWindow (which is library-internal in CMP 1.7).
            App(koin = koinApp.koin, windowState = windowState, onClose = ::exitApplication, awtWindow = window)
        }
    }
}
