package com.puretv.twitch.tv

import android.app.Application
import com.puretv.twitch.core.di.coreModule
import com.puretv.twitch.tv.di.tvModule
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin

/**
 * SECTION 11 / 12.2 — wires the shared `coreModule` (API client, OAuth,
 * ad-block engine, chat, emotes, repositories) plus [tvModule] (Room DB,
 * encrypted token storage, DataStore settings, the TV player wrapper, and
 * this app's ViewModels) into a single Koin graph.
 *
 * Deliberately mirrors `PureTvApp` (phone/tablet) at the wiring level only —
 * per Section 12.2, app-tv shares no UI code with app-android, just `core`.
 */
class PureTvTvApp : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidLogger()
            androidContext(this@PureTvTvApp)
            modules(coreModule, tvModule)
        }
    }
}
