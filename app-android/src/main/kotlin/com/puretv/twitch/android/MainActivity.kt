package com.puretv.twitch.android

import android.app.PictureInPictureParams
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.util.Rational
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.core.view.WindowCompat
import androidx.media3.common.util.UnstableApi
import androidx.navigation.compose.rememberNavController
import com.puretv.twitch.android.player.TwitchPlayer
import com.puretv.twitch.android.update.AndroidUpdateManager
import com.puretv.twitch.android.ui.RootScreen
import com.puretv.twitch.android.ui.Routes
import com.puretv.twitch.android.data.AppSettingsStore
import com.puretv.twitch.android.ui.theme.ShapeIntensity
import com.puretv.twitch.android.ui.theme.ThemeVariant
import com.puretv.twitch.android.ui.theme.PureTvTheme
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.android.ext.android.getKoin
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

    /** Drives the theme. Read in composition; only ever written from IO. */
    private val themeVariant = mutableStateOf(ThemeVariant.VIOLET_DUSK)
    private val shapeIntensity = mutableStateOf(ShapeIntensity.EXPRESSIVE)

    // A settings read is never worth losing the Activity for, so failures land
    // in the log and the default theme stays.
    private val uiScope = CoroutineScope(
        SupervisorJob() + Dispatchers.Main.immediate +
            CoroutineExceptionHandler { _, e -> Log.w(TAG, "Theme observer stopped", e) },
    )

    // App-wide singleton; released in onDestroy when the task is genuinely
    // finishing so codec/audio resources are not held for the process lifetime.
    private val twitchPlayer: TwitchPlayer by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Edge to edge, explicitly. Without this the decor view fits system windows
        // itself, which means it, and not Compose, owns the inset arithmetic: every
        // windowInsetsPadding() in the app reads whatever the decor left over, and
        // the value only recomputes when the decor decides to. Since `configChanges`
        // keeps this Activity alive across rotation and StreamScreen hides and shows
        // the system bars on every fullscreen toggle, a stale decor inset had nothing
        // to force it back, which is how a gap could survive on one edge after
        // leaving fullscreen. Compose insets are live and recompute on every change,
        // so each screen below now states its own inset intent and gets a fresh
        // answer every time.
        WindowCompat.setDecorFitsSystemWindows(window, false)

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

        observeTheme()

        setContent {
            val navController = rememberNavController()

            LaunchedEffect(navController) {
                navController.currentBackStackEntryFlow.collect { entry ->
                    currentRouteIsStream = entry.destination.route == Routes.STREAM
                }
            }

            CompositionLocalProvider(LocalIsInPip provides isInPipState.value) {
                PureTvTheme(
                    variant = themeVariant.value,
                    shapeIntensity = shapeIntensity.value,
                ) {
                    RootScreen(navController = navController)
                }
            }
        }
    }

    /**
     * The palette and shape intensity are user settings, so the theme is driven
     * from [AppSettingsStore] rather than from defaults, and collected at the
     * root so a change re-tones the whole tree at once.
     *
     * Resolved on IO rather than with `koinInject()` inside `setContent`.
     * Constructing that store forces the encrypted token prefs open, which
     * builds a Keystore master key, and on a first run that is hundreds of
     * milliseconds of work [PureTvApp] deliberately keeps off the main thread.
     * Resolving it in composition drags all of it into the first frame, and any
     * failure in it takes the Activity down before the app has drawn. The
     * variant arrives as plain Compose state instead, with the standard palette
     * showing until it does.
     */
    private fun observeTheme() {
        uiScope.launch {
            val store = withContext(Dispatchers.IO) { getKoin().get<AppSettingsStore>() }
            store.flow
                .catch { e -> Log.w(TAG, "Theme setting unreadable, keeping the default", e) }
                .collect { settings ->
                    themeVariant.value = ThemeVariant.fromKey(settings.theme)
                    shapeIntensity.value = ShapeIntensity.fromKey(settings.shapeIntensity)
                }
        }
    }

    /**
     * Section 6.5 — entering PiP when the user navigates away (home button,
     * recents) while watching a stream keeps playback visible in a floating
     * window. The 16:9 aspect ratio matches Twitch's source video shape.
     */
    /**
     * Picks the update back up after the viewer has been to system Settings to
     * grant "install unknown apps". Nothing was downloaded before they left, by
     * design, so this only re-reads consent and starts the deferred install.
     * Best-effort, and never worth taking the Activity down for.
     */
    override fun onResume() {
        super.onResume()
        runCatching { getKoin().get<AndroidUpdateManager>().refreshInstallConsent() }
            .onFailure { Log.w(TAG, "Could not re-check install consent", it) }
    }

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
        uiScope.cancel()
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

    private companion object {
        const val TAG = "MainActivity"
    }
}
