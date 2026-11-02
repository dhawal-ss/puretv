package com.puretv.twitch.android.di

import androidx.media3.common.util.UnstableApi
import androidx.room.Room
import com.puretv.twitch.android.data.AppSettingsStore
import com.puretv.twitch.android.data.SecureTokenStore
import com.puretv.twitch.android.data.SessionManager
import com.puretv.twitch.android.data.TokenRefresher
import com.puretv.twitch.android.data.db.MIGRATION_1_2
import com.puretv.twitch.android.data.db.PureTvDatabase
import com.puretv.twitch.android.notify.DataStoreLiveNotifyStore
import com.puretv.twitch.android.notify.GoLiveMonitor
import com.puretv.twitch.android.notify.GoLiveNotifications
import com.puretv.twitch.android.player.BackgroundPlayback
import com.puretv.twitch.android.player.TwitchPlayer
import com.puretv.twitch.android.update.AndroidUpdateManager
import com.puretv.twitch.core.follows.FollowedChannelsService
import com.puretv.twitch.core.follows.FollowedChannelsSource
import com.puretv.twitch.android.ui.BrowseViewModel
import com.puretv.twitch.android.ui.CategoryViewModel
import com.puretv.twitch.android.ui.ChannelViewModel
import com.puretv.twitch.android.ui.HomeViewModel
import com.puretv.twitch.android.ui.FollowingViewModel
import com.puretv.twitch.android.ui.LoginViewModel
import com.puretv.twitch.android.ui.SearchViewModel
import com.puretv.twitch.android.ui.SettingsViewModel
import com.puretv.twitch.android.ui.StreamViewModel
import com.puretv.twitch.android.ui.VodLaunch
import com.puretv.twitch.android.ui.VodPlayerViewModel
import com.puretv.twitch.android.ui.WelcomeViewModel
import com.puretv.twitch.core.api.TwitchApiClient
import com.puretv.twitch.core.api.retryOnceIfUnauthorized
import com.puretv.twitch.core.di.TokenHolder
import com.puretv.twitch.core.notify.LiveFollowsMonitor
import com.puretv.twitch.core.emotes.EmoteCache
import com.puretv.twitch.core.emotes.PersistentEmoteCache
import com.puretv.twitch.core.storage.FileTextStore
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp
import kotlinx.coroutines.flow.first
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module
import java.io.File

/**
 * SECTION 11: phone/tablet-specific Koin bindings layered on top of
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
        // data (watch/search history) on any schema bump. Every schema change
        // gets a real Migration instead (1 to 2 added vod_progress).
        Room.databaseBuilder(get(), PureTvDatabase::class.java, PureTvDatabase.DB_NAME)
            .addMigrations(MIGRATION_1_2)
            .build()
    }
    single { get<PureTvDatabase>().vodProgressDao() }
    single { get<PureTvDatabase>().cachedStreamDao() }
    single { get<PureTvDatabase>().cachedChannelDao() }
    single { get<PureTvDatabase>().cachedEmoteDao() }
    // Emote lists survive restarts so chat shows emotes on its first lines.
    single<EmoteCache> { PersistentEmoteCache(FileTextStore(File(androidContext().cacheDir, "emotes"))) }
    single { get<PureTvDatabase>().watchHistoryDao() }
    single { get<PureTvDatabase>().searchHistoryDao() }

    single { SecureTokenStore(get()) }
    single { AppSettingsStore(get(), get(), get<TokenHolder>()) }
    single { SessionManager(get()) }
    single { TokenRefresher(get(), get(), get()) }
    // Go-live alerts. The monitor is the app's one asker of "which follows are
    // live" (see LiveFollowsMonitor): Home, Following, the in-app timer and the
    // background worker all read through it, and whatever just went live becomes a
    // notification. A 401 renews the sign-in once and asks again, because the
    // worker can run long after Twitch dropped a token early.
    single { GoLiveNotifications(androidContext()) }
    single { DataStoreLiveNotifyStore(androidContext()) }
    single {
        val api = get<TwitchApiClient>()
        val settings = get<AppSettingsStore>()
        val refresher = get<TokenRefresher>()
        val notifications = get<GoLiveNotifications>()
        val streamRepo = get<com.puretv.twitch.core.repository.StreamRepository>()
        LiveFollowsMonitor(
            fetchLive = { userId ->
                retryOnceIfUnauthorized(renew = { refresher.refreshIfNeeded(force = true) }) { api.getFollowedStreams(userId) }
            },
            // By login, so it covers channels the viewer does not follow.
            fetchWatched = { logins ->
                retryOnceIfUnauthorized(renew = { refresher.refreshIfNeeded(force = true) }) { streamRepo.streamsForChannels(logins) }
            },
            store = get<DataStoreLiveNotifyStore>(),
            settings = { settings.flow.first() },
            clock = System::currentTimeMillis,
            onGoLive = { streams -> notifications.announce(streams) },
        )
    }
    single { GoLiveMonitor(lazy { get<LiveFollowsMonitor>() }, lazy { get<AppSettingsStore>() }) }
    // Followed-rail data: real Twitch follows (paginated), live status (from the
    // shared monitor) and avatars. No local-pin store on Android, so
    // FollowingViewModel always passes an empty pin list to `load`.
    single<FollowedChannelsSource> {
        val monitor = get<LiveFollowsMonitor>()
        FollowedChannelsService(get(), followedLive = { userId -> monitor.followedLive(userId) })
    }

    // --- Playback ---------------------------------------------------------
    // TwitchPlayer(context, adBlockEngine, backupStreamResolver, httpClient,
    // playableStreamCache). The backupStreamResolver powers the seamless ad-free
    // backup player-type swap in the interceptor.
    single { TwitchPlayer(get(), get(), get(), get(), get()) }
    // Background play (issue #27): the media session service's on/off switch and
    // the leave/return pause rules, shared by the stream screen and MainActivity.
    single { BackgroundPlayback(get(), get()) }
    single { AndroidUpdateManager(get()) }

    // --- ViewModels --------------------------------------------------------
    // get() resolves each constructor argument by type, so argument order does
    // not matter as long as every type is bound above (the DAOs are).
    viewModel { HomeViewModel(get(), get(), get(), get(), get(), get(), get(), get()) }
    viewModel { FollowingViewModel(get(), get(), get()) }
    viewModel { WelcomeViewModel(get(), get()) }
    viewModel { BrowseViewModel(get(), get()) }
    viewModel { SearchViewModel(get(), get()) }
    viewModel { (gameId: String) -> CategoryViewModel(gameId, get()) }
    viewModel { (channelLogin: String) -> StreamViewModel(channelLogin, get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get()) }
    viewModel { (channelLogin: String) -> ChannelViewModel(channelLogin, get(), get(), get()) }
    viewModel { (launch: VodLaunch) -> VodPlayerViewModel(launch, get(), get(), get(), get(), get(), get(), get()) }
    viewModel { SettingsViewModel(get()) }
    viewModel { LoginViewModel(get(), get(), get()) }
}
