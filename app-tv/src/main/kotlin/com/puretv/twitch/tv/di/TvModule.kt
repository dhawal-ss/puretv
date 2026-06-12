package com.puretv.twitch.tv.di

import androidx.media3.common.util.UnstableApi
import androidx.room.Room
import com.puretv.twitch.core.di.TokenHolder
import com.puretv.twitch.tv.data.AppSettingsStore
import com.puretv.twitch.tv.data.SecureTokenStore
import com.puretv.twitch.tv.data.db.PureTvTvDatabase
import com.puretv.twitch.tv.player.TvPlayer
import com.puretv.twitch.tv.ui.BrowseViewModel
import com.puretv.twitch.tv.ui.ChannelViewModel
import com.puretv.twitch.tv.ui.HomeViewModel
import com.puretv.twitch.tv.ui.LoginViewModel
import com.puretv.twitch.tv.ui.SearchViewModel
import com.puretv.twitch.tv.ui.SettingsViewModel
import com.puretv.twitch.tv.ui.StreamViewModel
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

/**
 * SECTION 11 / 12.2 — TV-specific Koin bindings layered on top of
 * `coreModule`, mirroring `androidModule` 1:1 (Room database, encrypted
 * token storage, DataStore-backed settings, the ExoPlayer wrapper, and
 * screen ViewModels) but pointed at this app's own TV-specific classes.
 */
@OptIn(UnstableApi::class)
val tvModule = module {
    // --- Persistence -----------------------------------------------------
    single {
        Room.databaseBuilder(get(), PureTvTvDatabase::class.java, PureTvTvDatabase.DB_NAME)
            .fallbackToDestructiveMigration()
            .build()
    }
    single { get<PureTvTvDatabase>().cachedStreamDao() }
    single { get<PureTvTvDatabase>().cachedChannelDao() }
    single { get<PureTvTvDatabase>().cachedEmoteDao() }
    single { get<PureTvTvDatabase>().watchHistoryDao() }
    single { get<PureTvTvDatabase>().searchHistoryDao() }

    single { SecureTokenStore(get()) }
    single { AppSettingsStore(get(), get(), get<TokenHolder>()) }

    // --- Playback ---------------------------------------------------------
    // Singleton: Section 7.4's immersive fullscreen stream screen is the only
    // consumer, but keeping it a Koin singleton (rather than per-screen) keeps
    // the wiring identical to the phone app and avoids re-creating ExoPlayer
    // on configuration changes.
    single { TvPlayer(get(), get()) }

    // --- ViewModels --------------------------------------------------------
    viewModel { HomeViewModel(get(), get(), get()) }
    viewModel { BrowseViewModel(get()) }
    viewModel { SearchViewModel(get()) }
    viewModel { (channelLogin: String) -> StreamViewModel(channelLogin, get(), get(), get(), get(), get(), get()) }
    viewModel { (channelLogin: String) -> ChannelViewModel(channelLogin, get(), get()) }
    viewModel { SettingsViewModel(get()) }
    viewModel { LoginViewModel(get()) }
}
