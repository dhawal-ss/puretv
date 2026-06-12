package com.puretv.twitch.android

import android.app.PictureInPictureParams
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.media3.common.util.UnstableApi
import androidx.navigation.compose.rememberNavController
import com.puretv.twitch.android.ui.PureTvNavHost
import com.puretv.twitch.android.ui.Routes
import com.puretv.twitch.android.ui.theme.PureTvTheme

/**
 * SECTION 06.1 / 06.5 [CRITICAL] — single-Activity host for the Navigation
 * Compose graph. Two responsibilities beyond hosting Compose:
 *
 *  1. Capture the OAuth redirect (`puretv-twitch://auth?code=...&state=...`)
 *     declared in the manifest's deep-link `intent-filter` and forward it
 *     to whichever `LoginViewModel` is alive (via a simple broadcast Flow —
 *     see [AuthRedirectBus]).
 *  2. Trigger Picture-in-Picture from [onUserLeaveHint] whenever the user
 *     backgrounds the app while a stream is open (Section 6.5).
 */
@UnstableApi
class MainActivity : ComponentActivity() {

    private var currentRouteIsStream: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleAuthRedirect(intent)

        setContent {
            val navController = rememberNavController()

            LaunchedEffect(navController) {
                navController.currentBackStackEntryFlow.collect { entry ->
                    currentRouteIsStream = entry.destination.route == Routes.STREAM
                }
            }

            PureTvTheme {
                PureTvNavHost(navController = navController)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleAuthRedirect(intent)
    }

    /** Section 3.2 — extracts `code`/`state` from the `puretv-twitch://auth` deep link. */
    private fun handleAuthRedirect(intent: Intent?) {
        val data = intent?.data ?: return
        if (data.scheme != "puretv-twitch" || data.host != "auth") return

        val code = data.getQueryParameter("code")
        val state = data.getQueryParameter("state")
        if (code != null && state != null) {
            AuthRedirectBus.emit(code, state)
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
        // Hide chat/chrome while in PiP — handled reactively by StreamScreen via
        // LocalContext-based PiP-mode detection if you wire an ambient provider;
        // kept minimal here since PlayerView already auto-hides its controller.
    }
}

/**
 * Minimal in-process pub/sub so [MainActivity.onNewIntent] (which fires
 * outside Compose's lifecycle) can hand the OAuth `code`/`state` pair to
 * whichever `LoginViewModel` instance is currently collecting — avoids
 * threading the Activity's Intent through the Compose tree.
 */
object AuthRedirectBus {
    data class Redirect(val code: String, val state: String)

    private val _events = kotlinx.coroutines.flow.MutableSharedFlow<Redirect>(extraBufferCapacity = 1)
    val events: kotlinx.coroutines.flow.SharedFlow<Redirect> = _events

    fun emit(code: String, state: String) {
        _events.tryEmit(Redirect(code, state))
    }
}
