package com.puretv.twitch.android.di

import androidx.media3.common.util.UnstableApi
import androidx.room.Room
import com.puretv.twitch.android.data.AppSettingsStore
import com.puretv.twitch.android.data.SecureTokenStore
import com.puretv.twitch.android.data.SessionManager
import com.puretv.twitch.android.data.TokenRefresher
import com.puretv.twitch.android.data.db.PureTvDatabase
import com.puretv.twitch.android.player.TwitchPlayer
import com.puretv.twitch.android.ui.BrowseViewModel
import com.puretv.twitch.android.ui.CategoryViewModel
import com.puretv.twitch.android.ui.ChannelViewModel
import com.puretv.twitch.android.ui.HomeViewModel
import com.puretv.twitch.android.ui.FollowingViewModel
import com.puretv.twitch.android.ui.LoginViewModel
import com.puretv.twitch.android.ui.SearchViewModel
import com.puretv.twitch.android.ui.SettingsViewModel
import com.puretv.twitch.android.ui.StreamViewModel
import com.puretv.twitch.android.ui.WelcomeViewModel
import com.puretv.twitch.core.di.TokenHolder
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

/**
 * SECTION 11 — phone/tablet-specific Koin bindings layered on top of
 * `coreModule`: Room database, encrypted token storage, DataStore-backed
 * settings, the ExoPlayer wrapper (singleton so PiP keeps the same
 * playback session across navigation), and screen ViewModels.
 */
@OptIn(UnstableApi::class)
val androidModule = module {
    // --- Networking engine -------------------------------------------------
    // coreModule's `single { buildKtorClient(get()) }` resolves an
    // HttpClientEngine from here (OkHttp, matching DesktopModule and core's
    // android target). Without it the whole networking graph (HttpClient ->
    // TwitchApiClient -> repositories -> ViewModels) cannot construct, and the
    // app crashes on the first screen.
    single<HttpClientEngine> { OkHttp.create() }

    // --- Persistence -----------------------------------------------------
    single {
        // No .fallbackToDestructiveMigration(): that silently wipes all user
        // data (watch/search history) on any schema bump. The @Database is at
        // version 1 with no migrations yet, so omitting it is safe today; real
        // Migration objects get added here when the schema first changes.
        Room.databaseBuilder(get(), PureTvDatabase::class.java, PureTvDatabase.DB_NAME)
            .build()
    }
    single { get<PureTvDatabase>().cachedStreamDao() }
    single { get<PureTvDatabase>().cachedChannelDao() }
    single { get<PureTvDatabase>().cachedEmoteDao() }
    single { get<PureTvDatabase>().watchHistoryDao() }
    single { get<PureTvDatabase>().searchHistoryDao() }

    single { SecureTokenStore(get()) }
    single { AppSettingsStore(get(), get(), get<TokenHolder>()) }
    single { SessionManager(get()) }
    single { TokenRefresher(get(), get()) }

    // --- Playback ---------------------------------------------------------
    single { TwitchPlayer(get(), get()) }

    // --- ViewModels --------------------------------------------------------
    // get() resolves each constructor argument by type, so argument order does
    // not matter as long as every type is bound above (the DAOs are).
    viewModel { HomeViewModel(get(), get(), get(), get(), get(), get()) }
    viewModel { FollowingViewModel(get(), get(), get()) }
    viewModel { WelcomeViewModel(get()) }
    viewModel { BrowseViewModel(get()) }
    viewModel { SearchViewModel(get(), get()) }
    viewModel { (gameId: String) -> CategoryViewModel(gameId, get()) }
    viewModel { (channelLogin: String) -> StreamViewModel(channelLogin, get(), get(), get(), get(), get(), get(), get(), get()) }
    viewModel { (channelLogin: String) -> ChannelViewModel(channelLogin, get(), get()) }
    viewModel { SettingsViewModel(get()) }
    viewModel { LoginViewModel(get(), get(), get()) }
}
