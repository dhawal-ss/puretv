package com.puretv.twitch.android

import android.app.PictureInPictureParams
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.media3.common.util.UnstableApi
import androidx.navigation.compose.rememberNavController
import com.puretv.twitch.android.data.TokenRefresher
import com.puretv.twitch.android.ui.MainScaffold
import com.puretv.twitch.android.ui.Routes
import com.puretv.twitch.android.ui.theme.PureTvTheme
import org.koin.compose.koinInject

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val navController = rememberNavController()
            val tokenRefresher = koinInject<TokenRefresher>()

            LaunchedEffect(Unit) {
                // Best-effort: extend the session with the stored refresh token on
                // launch so a returning user is not silently logged out.
                tokenRefresher.refreshIfPossible()
            }

            LaunchedEffect(navController) {
                navController.currentBackStackEntryFlow.collect { entry ->
                    currentRouteIsStream = entry.destination.route == Routes.STREAM
                }
            }

            CompositionLocalProvider(LocalIsInPip provides isInPipState.value) {
                PureTvTheme {
                    MainScaffold(navController = navController)
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
        if (currentRouteIsStream && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val params = PictureInPictureParams.Builder()
                .setAspectRatio(Rational(16, 9))
                .build()
            runCatching { enterPictureInPictureMode(params) }
        }
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        // Surface PiP state into Compose so StreamScreen collapses to video only
        // (no back button, pills, ad badge, live badge, or chat) in the small window.
        isInPipState.value = isInPictureInPictureMode
    }
}
