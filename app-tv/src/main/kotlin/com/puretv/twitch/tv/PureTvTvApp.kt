package com.puretv.twitch.tv

import android.app.Application
import com.puretv.twitch.core.adblock.AdBlockEngine
import com.puretv.twitch.core.di.coreModule
import com.puretv.twitch.tv.data.AppSettingsStore
import com.puretv.twitch.tv.data.TokenRefresher
import com.puretv.twitch.tv.di.tvModule
import com.puretv.twitch.tv.update.TvUpdateManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin

/**
 * SECTION 11 / 12.2, wires the shared `coreModule` (API client, OAuth,
 * ad-block engine, chat, emotes, repositories) plus [tvModule] (Room DB,
 * encrypted token storage, DataStore settings, the TV player wrapper, and
 * this app's ViewModels) into a single Koin graph.
 *
 * Mirrors the phone app's `PureTvApp` startup at the wiring level (Section 12.2:
 * app-tv shares no UI code with app-android, just `core`): the same off-main
 * settings prime + launch token refresh + ad-block-toggle sync run here so a
 * returning viewer's session survives token expiry and the first composition
 * never blocks on Keystore/DataStore disk I/O.
 */
class PureTvTvApp : Application() {

    // Process-lifetime scope for one-shot startup work. SupervisorJob so one
    // failure never tears down the others.
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        val koinApp = startKoin {
            androidLogger()
            androidContext(this@PureTvTvApp)
            modules(coreModule, tvModule)
        }

        appScope.launch {
            // Construct AppSettingsStore OFF the main thread: its init forces the
            // EncryptedSharedPreferences open (Keystore master-key build + prefs
            // decrypt) and primes the shared core TokenHolder so the first
            // authenticated request already has a bearer token. On first launch the
            // key is generated (hundreds of ms), doing it here keeps that off the
            // first composition where it would risk a startup ANR / visible jank.
            koinApp.koin.get<AppSettingsStore>()

            // Best-effort: extend the session with the stored refresh token once per
            // process (off-main, fail-soft, a failure keeps the current session).
            koinApp.koin.get<TokenRefresher>().refreshIfPossible()

            // Silent launch update check: surfaces an "Update available" banner on
            // Home / a prompt in Settings when a newer TV APK is published. Fully
            // fail-soft (no manifest / offline → stays idle, no nagging).
            koinApp.koin.get<TvUpdateManager>().checkForUpdates()
        }

        // Keep the in-process ad-block interceptor in sync with the user's
        // "Enable ad block" switch. The singleton AdBlockEngine + interceptor are
        // process-wide, so a single long-lived collector owns this; flipping the
        // switch in Settings takes effect on the next playlist refresh.
        appScope.launch {
            val engine = koinApp.koin.get<AdBlockEngine>()
            koinApp.koin.get<AppSettingsStore>().flow
                .map { it.adBlockEnabled }
                .distinctUntilChanged()
                .collect { engine.setEnabled(it) }
        }
    }
}
