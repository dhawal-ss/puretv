package com.puretv.twitch.tv

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import androidx.media3.common.util.UnstableApi
import com.puretv.twitch.tv.data.AppSettingsStore
import com.puretv.twitch.tv.update.TvUpdateManager
import com.puretv.twitch.tv.ui.PureTvTvNavHost
import com.puretv.twitch.tv.ui.theme.PureTvTvTheme
import com.puretv.twitch.tv.ui.theme.ThemeVariant
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.android.ext.android.getKoin

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
 *
 * ## Why the theme is not read from inside composition
 *
 * The palette is a user setting, so it comes from [AppSettingsStore]. Resolving
 * that store is not free: its construction forces the encrypted token prefs
 * open, which builds a Keystore master key, and on a first run that is hundreds
 * of milliseconds of work that [PureTvTvApp] deliberately does off the main
 * thread. Resolving it inside `setContent` would drag all of it back onto the
 * main thread and into the first frame, where a slow SoC turns it into a
 * startup stall and any failure in it takes the Activity down before the app
 * has drawn anything. So the store is resolved on IO and the variant arrives as
 * plain Compose state, defaulting to the standard palette until it does.
 */
@UnstableApi
class TvMainActivity : ComponentActivity() {

    /** Drives the palette. Read in composition; only ever written from IO. */
    private val themeVariant = mutableStateOf(ThemeVariant.VIOLET_DUSK)

    // A settings read is never worth losing the Activity for, so failures land
    // in the log and the default palette stays.
    private val uiScope = CoroutineScope(
        SupervisorJob() + Dispatchers.Main.immediate +
            CoroutineExceptionHandler { _, e -> Log.w(TAG, "Theme observer stopped", e) },
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleAuthRedirect(intent)
        observeTheme()

        setContent {
            PureTvTvTheme(variant = themeVariant.value) {
                PureTvTvNavHost()
            }
        }
    }

    /**
     * Picks the update back up after the viewer has been to system Settings to
     * grant "install unknown apps".
     *
     * Nothing was downloaded before they left, by design, so there is no
     * in-flight work to resume: this only re-reads consent and, if it is now
     * granted, starts the install that was deferred. Without it the viewer
     * returns to the very screen asking for the permission they just gave,
     * which reads as the app having ignored them. Best-effort, and never worth
     * taking the Activity down for.
     */
    override fun onResume() {
        super.onResume()
        runCatching { getKoin().get<TvUpdateManager>().refreshInstallConsent() }
            .onFailure { Log.w(TAG, "Could not re-check install consent", it) }
    }

    override fun onDestroy() {
        uiScope.cancel()
        super.onDestroy()
    }

    /** Collected at the root so changing the palette re-tones the whole tree at once. */
    private fun observeTheme() {
        uiScope.launch {
            val store = withContext(Dispatchers.IO) { getKoin().get<AppSettingsStore>() }
            store.flow
                .map { ThemeVariant.fromKey(it.theme) }
                .distinctUntilChanged()
                .catch { e -> Log.w(TAG, "Theme setting unreadable, keeping the default", e) }
                .collect { themeVariant.value = it }
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

    private companion object {
        const val TAG = "TvMainActivity"
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
