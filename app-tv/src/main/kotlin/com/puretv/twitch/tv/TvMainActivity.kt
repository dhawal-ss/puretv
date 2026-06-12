package com.puretv.twitch.tv

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.media3.common.util.UnstableApi
import com.puretv.twitch.tv.ui.PureTvTvNavHost
import com.puretv.twitch.tv.ui.theme.PureTvTvTheme

/**
 * SECTION 07.1 / 07.2 [CRITICAL] — single-Activity host for the TV Navigation
 * Compose graph, launched via the LEANBACK_LAUNCHER intent filter.
 *
 * Two responsibilities beyond hosting Compose (mirrors `MainActivity` on the
 * phone, minus PiP — Android TV doesn't support Picture-in-Picture):
 *
 *  1. Capture the OAuth redirect (`puretv-twitch://auth?code=...&state=...`)
 *     and forward it to whichever `TvLoginViewModel` is alive via
 *     [AuthRedirectBus] — same in-process pub/sub pattern as the phone app,
 *     bridging Activity Intent handling into the Compose tree.
 *  2. Nothing else needs platform-activity wiring: D-pad/remote input is
 *     handled declaratively by Compose's focus system inside each screen
 *     (Section 7.3) — no `dispatchKeyEvent` override required at this layer.
 */
@UnstableApi
class TvMainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleAuthRedirect(intent)

        setContent {
            PureTvTvTheme {
                PureTvTvNavHost()
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
}

/**
 * Minimal in-process pub/sub so [TvMainActivity.onNewIntent] (which fires
 * outside Compose's lifecycle) can hand the OAuth `code`/`state` pair to
 * whichever `LoginViewModel` instance is currently collecting. Identical
 * design to the phone app's `AuthRedirectBus` (kept as a separate object —
 * not shared — since it lives in this app's package and is wired into this
 * app's `TvMainActivity`/`LoginViewModel` only).
 */
object AuthRedirectBus {
    data class Redirect(val code: String, val state: String)

    private val _events = kotlinx.coroutines.flow.MutableSharedFlow<Redirect>(extraBufferCapacity = 1)
    val events: kotlinx.coroutines.flow.SharedFlow<Redirect> = _events

    fun emit(code: String, state: String) {
        _events.tryEmit(Redirect(code, state))
    }
}
