package com.puretv.twitch.android

import android.app.Application
import com.puretv.twitch.android.data.AppSettingsStore
import com.puretv.twitch.android.data.TokenRefresher
import com.puretv.twitch.android.di.androidModule
import com.puretv.twitch.core.adblock.AdBlockEngine
import com.puretv.twitch.core.di.coreModule
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
 * SECTION 11 — wires `coreModule` (shared) + `androidModule` (phone/tablet
 * specific: Room DB, ExoPlayer, DataStore, ViewModels) into a single Koin graph.
 */
class PureTvApp : Application() {

    // Process-lifetime scope for one-shot startup work. SupervisorJob so one
    // failure never tears down the others.
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        val koinApp = startKoin {
            androidLogger()
            androidContext(this@PureTvApp)
            modules(coreModule, androidModule)
        }

        appScope.launch {
            // Construct AppSettingsStore OFF the main thread. Its init forces the
            // EncryptedSharedPreferences open (Android Keystore master-key build +
            // prefs decrypt), and on the very first launch the key is GENERATED,
            // which can take hundreds of ms. Doing it here keeps that disk + crypto
            // work off the first composition, where it would risk a startup ANR /
            // visible jank. This also primes the shared core TokenHolder so the
            // first authenticated request has a bearer token.
            koinApp.koin.get<AppSettingsStore>()

            // Best-effort: extend the session with the stored refresh token once per
            // process. Previously this ran from a composition LaunchedEffect, so it
            // re-fired on every Activity recreation (rotation), racing two refreshes
            // on the same rotating refresh token. Once per process, off-main, is both
            // correct and cheaper. It is fail-soft: a failure keeps the session.
            koinApp.koin.get<TokenRefresher>().refreshIfPossible()
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
