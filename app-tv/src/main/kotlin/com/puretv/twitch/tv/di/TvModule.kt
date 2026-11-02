package com.puretv.twitch.tv.di

import androidx.media3.common.util.UnstableApi
import androidx.room.Room
import com.puretv.twitch.core.api.TwitchApiClient
import com.puretv.twitch.core.api.retryOnceIfUnauthorized
import com.puretv.twitch.core.di.TokenHolder
import com.puretv.twitch.core.notify.LiveFollowsMonitor
import com.puretv.twitch.core.playback.FlipOrigin
import com.puretv.twitch.core.emotes.EmoteCache
import com.puretv.twitch.core.emotes.PersistentEmoteCache
import com.puretv.twitch.core.storage.FileTextStore
import com.puretv.twitch.core.follows.FollowedChannelsService
import com.puretv.twitch.core.follows.FollowedChannelsSource
import com.puretv.twitch.tv.data.AppSettingsStore
import com.puretv.twitch.tv.data.SecureTokenStore
import com.puretv.twitch.tv.data.TokenRefresher
import com.puretv.twitch.tv.data.db.MIGRATION_1_2
import com.puretv.twitch.tv.data.db.PureTvTvDatabase
import com.puretv.twitch.tv.notify.DataStoreLiveNotifyStore
import com.puretv.twitch.tv.notify.TvGoLiveMonitor
import com.puretv.twitch.tv.player.TvPlayer
import com.puretv.twitch.tv.update.TvUpdateManager
import com.puretv.twitch.tv.ui.BrowseViewModel
import com.puretv.twitch.tv.ui.CategoryViewModel
import com.puretv.twitch.tv.ui.ChannelViewModel
import com.puretv.twitch.tv.ui.HomeViewModel
import com.puretv.twitch.tv.ui.LoginViewModel
import com.puretv.twitch.tv.ui.SearchViewModel
import com.puretv.twitch.tv.ui.SettingsViewModel
import com.puretv.twitch.tv.ui.StreamViewModel
import com.puretv.twitch.tv.ui.TvFollowingViewModel
import com.puretv.twitch.tv.ui.TvVodViewModel
import com.puretv.twitch.tv.ui.VodLaunch
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp
import kotlinx.coroutines.flow.first
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module
import java.io.File

/**
 * SECTION 11 / 12.2: TV-specific Koin bindings layered on top of
 * `coreModule`, mirroring `androidModule` 1:1 (Room database, encrypted
 * token storage, DataStore-backed settings, the ExoPlayer wrapper, and
 * screen ViewModels) but pointed at this app's own TV-specific classes.
 */
@OptIn(UnstableApi::class)
val tvModule = module {
    // --- Networking engine -------------------------------------------------
    // coreModule's `single { buildKtorClient(get()) }` resolves an
    // HttpClientEngine from here (OkHttp, matching androidModule / core's
    // android target). Without it the whole networking graph (HttpClient ->
    // TwitchApiClient -> repositories -> ViewModels) cannot construct, and the
    // app crashes on the first screen.
    single<HttpClientEngine> { OkHttp.create() }

    // --- Persistence -----------------------------------------------------
    single {
        Room.databaseBuilder(get(), PureTvTvDatabase::class.java, PureTvTvDatabase.DB_NAME)
            // The explicit migration keeps watch history across the update; the
            // destructive fallback stays only as a last resort for an unknown version.
            .addMigrations(MIGRATION_1_2)
            .fallbackToDestructiveMigration()
            .build()
    }
    single { get<PureTvTvDatabase>().vodProgressDao() }
    single { get<PureTvTvDatabase>().cachedStreamDao() }
    single { get<PureTvTvDatabase>().cachedChannelDao() }
    single { get<PureTvTvDatabase>().cachedEmoteDao() }
    // Emote lists survive restarts so chat shows emotes on its first lines.
    single<EmoteCache> { PersistentEmoteCache(FileTextStore(File(androidContext().cacheDir, "emotes"))) }
    single { get<PureTvTvDatabase>().watchHistoryDao() }
    single { get<PureTvTvDatabase>().searchHistoryDao() }

    single { SecureTokenStore(get()) }
    single { AppSettingsStore(get(), get(), get<TokenHolder>()) }
    // Launch-time access-token refresh + identity backfill (fail-soft).
    single { TokenRefresher(get(), get(), get()) }
    // Go-live banners. The monitor is the app's one asker of "which follows are
    // live" (see LiveFollowsMonitor): Home "Live now", Following and the banner
    // timer all read through it, and whatever just went live becomes a banner. A
    // 401 renews the sign-in once (the refresher's own one-minute guard applies)
    // and asks again.
    single { DataStoreLiveNotifyStore(androidContext()) }
    single { TvGoLiveMonitor(lazy { get<LiveFollowsMonitor>() }, lazy { get<AppSettingsStore>() }) }
    single {
        val api = get<TwitchApiClient>()
        val settings = get<AppSettingsStore>()
        val refresher = get<TokenRefresher>()
        val banners = get<TvGoLiveMonitor>()
        val streamRepo = get<com.puretv.twitch.core.repository.StreamRepository>()
        LiveFollowsMonitor(
            fetchLive = { userId ->
                retryOnceIfUnauthorized(renew = { refresher.refreshIfPossible() }) { api.getFollowedStreams(userId) }
            },
            // By login, so it covers channels the viewer does not follow and works signed out.
            fetchWatched = { logins ->
                retryOnceIfUnauthorized(renew = { refresher.refreshIfPossible() }) { streamRepo.streamsForChannels(logins) }
            },
            store = get<DataStoreLiveNotifyStore>(),
            settings = { settings.flow.first() },
            clock = System::currentTimeMillis,
            onGoLive = { streams -> banners.announce(streams) },
        )
    }
    // Followed-rail data for the Following destination: real Twitch follows
    // (paginated), no local pins on TV, live status from the shared monitor.
    // Mirrors desktopModule's binding.
    single<FollowedChannelsSource> {
        val monitor = get<LiveFollowsMonitor>()
        FollowedChannelsService(get(), followedLive = { userId -> monitor.followedLive(userId) })
    }

    // In-app updater (GitHub Releases → PackageInstaller). Singleton so the
    // Settings section and the Home update banner observe one state.
    single { TvUpdateManager(get()) }

    // --- Playback ---------------------------------------------------------
    // Singleton: Section 7.4's immersive fullscreen stream screen is the only
    // consumer, but keeping it a Koin singleton (rather than per-screen) keeps
    // the wiring identical to the phone app and avoids re-creating ExoPlayer
    // on configuration changes. TvPlayer(context, adBlockEngine, backupStreamResolver,
    // httpClient, playableStreamCache): the backup resolver powers the seamless
    // ad-free player-type swap; the cache serves the master it already fetched.
    single { TvPlayer(get(), get(), get(), get(), get()) }

    // --- ViewModels --------------------------------------------------------
    // HomeViewModel(streamRepo, userRepo, settings, cachedStreamDao, tokenRefresher,
    // liveFollows): cached-first paint/write-through, the on-failure token refresh
    // so "Live Now" survives token expiry, and the shared live follows monitor.
    // + watchHistoryDao and vodProgressDao for the "Continue watching" row.
    viewModel { HomeViewModel(get(), get(), get(), get(), get(), get(), get(), get()) }
    // TvFollowingViewModel(followedChannelsSource, settings): the Following
    // destination's own nav-rail screen, see TvFollowingScreen.
    viewModel { TvFollowingViewModel(get(), get()) }
    // BrowseViewModel(channelRepo, tokenRefresher, settings): tokenRefresher recovers
    // the expired-token 401 that used to blank the category grid; settings hold
    // the chosen order.
    viewModel { BrowseViewModel(get(), get(), get()) }
    viewModel { (gameId: String) -> CategoryViewModel(gameId, get()) }
    viewModel { SearchViewModel(get()) }
    // The row a stream was opened from (Home, a category, Continue watching), so the
    // remote can flip through it. Screens record it when a card is opened.
    single { FlipOrigin() }
    // StreamViewModel(login, streams, channels, chat, emotes, adBlock, settings, cache,
    // badges, 7TV, liveFollows, flipOrigin, watchHistory): a channel flip switches inside
    // this view model, and what is watched lands in the watch history.
    viewModel { (channelLogin: String) -> StreamViewModel(channelLogin, get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get()) }
    viewModel { (channelLogin: String) -> ChannelViewModel(channelLogin, get(), get(), get(), get()) }
    viewModel { (launch: VodLaunch) -> TvVodViewModel(launch, get(), get(), get(), get(), get(), get()) }
    viewModel { SettingsViewModel(get()) }
    // Device Code Grant flow: (httpClient, settingsStore, userRepository).
    viewModel { LoginViewModel(get(), get(), get()) }
}
