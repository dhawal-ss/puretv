package com.puretv.twitch.android

import android.app.Application
import com.puretv.twitch.android.di.androidModule
import com.puretv.twitch.core.di.coreModule
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin

/**
 * SECTION 11 — wires `coreModule` (shared) + `androidModule` (phone/tablet
 * specific: Room DB, ExoPlayer, DataStore, ViewModels) into a single Koin graph.
 */
class PureTvApp : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidLogger()
            androidContext(this@PureTvApp)
            modules(coreModule, androidModule)
        }
    }
}
