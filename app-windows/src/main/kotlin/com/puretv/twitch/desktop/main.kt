package com.puretv.twitch.desktop

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.puretv.twitch.core.di.coreModule
import com.puretv.twitch.desktop.data.DesktopSettingsStore
import com.puretv.twitch.desktop.di.desktopModule
import com.puretv.twitch.desktop.player.LocalStreamProxy
import com.puretv.twitch.desktop.player.VlcPlayer
import com.puretv.twitch.desktop.ui.App
import kotlinx.coroutines.runBlocking
import org.koin.core.context.GlobalContext.startKoin
import org.koin.dsl.koinApplication

fun main() {
    val koinApp = koinApplication {
        modules(coreModule, desktopModule)
    }
    startKoin(koinApp)

    koinApp.koin.get<DesktopSettingsStore>()

    val vlcPlayer = koinApp.koin.get<VlcPlayer>()
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
            // `window` is FrameWindowScope.window (ComposeWindow extends java.awt.Frame).
            // Pass it to App so the custom title bar can move the window without
            // relying on LocalWindow (which is library-internal in CMP 1.7).
            App(koin = koinApp.koin, windowState = windowState, onClose = ::exitApplication, awtWindow = window)
        }
    }
}
