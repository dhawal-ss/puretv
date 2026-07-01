package com.puretv.twitch.android

import android.app.PictureInPictureParams
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.Rational
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.media3.common.util.UnstableApi
import androidx.navigation.compose.rememberNavController
import com.puretv.twitch.android.player.TwitchPlayer
import com.puretv.twitch.android.ui.RootScreen
import com.puretv.twitch.android.ui.Routes
import com.puretv.twitch.android.ui.theme.PureTvTheme
import org.koin.android.ext.android.inject

/**
 * True while the activity is in Picture-in-Picture. StreamScreen reads this to
 * collapse to video only (no chrome, no chat) in the tiny PiP window.
 */
internal val LocalIsInPip = staticCompositionLocalOf { false }

/**
 * SECTION 06.1 / 06.5 [CRITICAL] — single-Activity host for the Navigation
 * Compose graph. Its one responsibility beyond hosting Compose is to trigger
 * Picture-in-Picture from [onUserLeaveHint] whenever the user backgrounds the
 * app while a stream is open (Section 6.5).
 *
 * Note: the phone signs in via Twitch device-code flow, so there is no OAuth
 * redirect to capture here (the old `puretv-twitch://auth` deep-link plumbing
 * was dead and has been removed).
 */
@UnstableApi
class MainActivity : ComponentActivity() {

    private var currentRouteIsStream: Boolean = false
    private val isInPipState = mutableStateOf(false)

    // App-wide singleton; released in onDestroy when the task is genuinely
    // finishing so codec/audio resources are not held for the process lifetime.
    private val twitchPlayer: TwitchPlayer by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Allow the app to use the display cutout (camera punch-hole) once, up
        // front. Set at Activity creation and NEVER toggled at runtime: changing
        // layoutInDisplayCutoutMode on a live window mis-computes the safe-area
        // inset on some OEM skins (OxygenOS), which left a persistent gap on one
        // side after exiting fullscreen. In non-fullscreen the status bar covers
        // the cutout anyway; in fullscreen the hidden bars let the video fill it.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode =
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                    } else {
                        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                    }
            }
        }

        setContent {
            val navController = rememberNavController()

            LaunchedEffect(navController) {
                navController.currentBackStackEntryFlow.collect { entry ->
                    currentRouteIsStream = entry.destination.route == Routes.STREAM
                }
            }

            CompositionLocalProvider(LocalIsInPip provides isInPipState.value) {
                PureTvTheme {
                    RootScreen(navController = navController)
                }
            }
        }
    }

    /**
     * Section 6.5 — entering PiP when the user navigates away (home button,
     * recents) while watching a stream keeps playback visible in a floating
     * window. The 16:9 aspect ratio matches Twitch's source video shape.
     */
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        // Only float a PiP window when a stream is actually playing. Entering PiP
        // from a spinner or an error overlay just pops a black, chrome-less window
        // the user cannot retry from.
        val isPlaying = runCatching { twitchPlayer.exoPlayer.isPlaying }.getOrDefault(false)
        if (currentRouteIsStream && isPlaying && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val params = PictureInPictureParams.Builder()
                .setAspectRatio(Rational(16, 9))
                .build()
            runCatching { enterPictureInPictureMode(params) }
        }
    }

    override fun onDestroy() {
        // Free the shared player only on a real finish (not a config-change teardown;
        // the manifest's configChanges already blocks rotation recreation). If the
        // process survives and the user reopens, TwitchPlayer rebuilds a fresh player.
        if (isFinishing) {
            twitchPlayer.release()
        }
        super.onDestroy()
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        // Surface PiP state into Compose so StreamScreen collapses to video only
        // (no back button, pills, ad badge, live badge, or chat) in the small window.
        isInPipState.value = isInPictureInPictureMode
    }
}
