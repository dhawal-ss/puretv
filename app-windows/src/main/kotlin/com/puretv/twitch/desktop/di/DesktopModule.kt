package com.puretv.twitch.desktop.di

import com.puretv.twitch.desktop.platform.AppDirs
import com.puretv.twitch.desktop.auth.DesktopOAuthManager
import com.puretv.twitch.desktop.data.DesktopSettingsStore
import com.puretv.twitch.desktop.data.FollowStore
import com.puretv.twitch.desktop.data.LiveNotifyFileStore
import com.puretv.twitch.desktop.notify.DesktopLiveNotifier
import com.puretv.twitch.desktop.ui.NavigationRequests
import com.puretv.twitch.core.api.TwitchApiClient
import com.puretv.twitch.core.api.retryOnceIfUnauthorized
import com.puretv.twitch.core.notify.LiveFollowsMonitor
import com.puretv.twitch.core.repository.StreamRepository
import com.puretv.twitch.core.playback.FlipOrigin
import com.puretv.twitch.desktop.player.ChannelClaims
import com.puretv.twitch.desktop.player.PlayTicket
import com.puretv.twitch.desktop.player.WindowPlayback
import androidx.compose.ui.window.TrayState
import com.puretv.twitch.desktop.data.ViewerHistoryStore
import com.puretv.twitch.desktop.data.WatchProgressStore
import com.puretv.twitch.core.audio.AudioNormalizerProfile
import com.puretv.twitch.core.model.PlaybackBackend
import com.puretv.twitch.desktop.player.DesktopPlayer
import com.puretv.twitch.desktop.player.DesktopPlayerProvider
import com.puretv.twitch.core.emotes.EmoteCache
import com.puretv.twitch.core.emotes.PersistentEmoteCache
import com.puretv.twitch.core.storage.FileTextStore
import java.io.File
import coil3.disk.DiskCache
import okio.Path.Companion.toOkioPath
import com.puretv.twitch.desktop.player.LocalStreamProxy
import com.puretv.twitch.desktop.player.MpvPlayer
import com.puretv.twitch.desktop.player.NativeLibs
import com.puretv.twitch.desktop.player.VlcEngine
import com.puretv.twitch.desktop.player.RtxVlcEngines
import com.puretv.twitch.desktop.player.RtxVideoChoice
import com.puretv.twitch.desktop.platform.RtxGpu
import com.puretv.twitch.desktop.player.VlcPlayer
import com.puretv.twitch.desktop.update.UpdateManager
import com.puretv.twitch.core.follows.FollowedChannelsService
import com.puretv.twitch.core.follows.FollowedChannelsSource
import com.puretv.twitch.core.follows.FollowedRef
import com.puretv.twitch.desktop.ui.BrowseViewModel
import com.puretv.twitch.desktop.ui.CategoryViewModel
import com.puretv.twitch.desktop.ui.ChannelStatsViewModel
import com.puretv.twitch.desktop.ui.ChannelViewModel
import com.puretv.twitch.desktop.ui.FollowedRailViewModel
import com.puretv.twitch.desktop.ui.HomeViewModel
import com.puretv.twitch.desktop.ui.LoginViewModel
import com.puretv.twitch.desktop.ui.MultiSession
import com.puretv.twitch.desktop.ui.MultiViewViewModel
import com.puretv.twitch.desktop.ui.TileSource
import com.puretv.twitch.desktop.ui.SearchViewModel
import com.puretv.twitch.desktop.ui.WindowRegistry
import com.puretv.twitch.desktop.ui.SettingsViewModel
import com.puretv.twitch.desktop.ui.emotes.EmoteFrameCache
import com.puretv.twitch.desktop.ui.StreamViewModel
import com.puretv.twitch.desktop.ui.VodListViewModel
import com.puretv.twitch.desktop.ui.VodLaunch
import com.puretv.twitch.desktop.ui.VodChatViewModel
import com.puretv.twitch.desktop.ui.VodPlayerViewModel
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp
import org.koin.core.parameter.parametersOf
import org.koin.dsl.module

/**
 * SECTION 11/12.4: Windows-specific Koin bindings layered on top of
 * `coreModule` (shared API client, repositories, ad-block engine, chat
 * client - Sections 3 to 5). Mirrors `androidModule`/`tvModule` in shape;
 * differs in *what* backs persistence (file-based, not Room/DataStore) and
 * playback (VLCJ + local proxy, not ExoPlayer).
 *
 * NOTE - ViewModels are bound with `factory { }`, not Koin's `viewModel { }`
 * DSL (that's an AndroidX-lifecycle integration this plain-JVM module doesn't
 * pull in). Screens obtain them via `rememberDesktopViewModel { koin.get() }`
 * / `rememberDesktopViewModel(login) { koin.get { parametersOf(login) } }`.
 */
val desktopModule = module {
    // --- Networking engine (coreModule provides `single { buildKtorClient(get()) }`,
    // which resolves an `HttpClientEngine` - OkHttp here, matching Android/TV). ---
    single<HttpClientEngine> { OkHttp.create() }

    // --- Persistence -------------------------------------------------------
    // Takes TokenHolder (from coreModule) so saved tokens flow back into the
    // shared HttpClient auth path on both fresh login and cold-start restore.
    single { DesktopSettingsStore(get()) }
    // Local "Following" list (the in-app library) - see FollowStore.
    single { FollowStore() }
    // Go-live alerts. The monitor is the app's one asker of live status (see
    // LiveFollowsMonitor): the rail reads the followed feed through it, the alert timer
    // in main.kt polls the channels the viewer turned the bell on for, and whatever
    // just went live becomes a tray notification.
    single { TrayState() }
    single { NavigationRequests() }
    single { DesktopLiveNotifier() }
    single { LiveNotifyFileStore() }
    single {
        val store = get<DesktopSettingsStore>()
        val api = get<TwitchApiClient>()
        val notifier = get<DesktopLiveNotifier>()
        val streams = get<StreamRepository>()
        val http = get<HttpClient>()
        LiveFollowsMonitor(
            fetchLive = { userId -> api.getFollowedStreams(userId) },
            // By login, so it covers channels the viewer does not follow and works signed
            // out. A 401 renews the sign-in once and asks again, because the tray can sit
            // for days and Twitch may drop a token early.
            fetchWatched = { logins ->
                retryOnceIfUnauthorized(renew = { store.refreshIfNeeded(http, force = true) }) { streams.streamsForChannels(logins) }
            },
            store = get<LiveNotifyFileStore>(),
            settings = { store.settings.value },
            clock = System::currentTimeMillis,
            onGoLive = { streams -> notifier.show(streams) },
        )
    }
    // Followed-rail data: real Twitch follows (paginated) plus local pins, with live
    // status from the shared monitor.
    single<FollowedChannelsSource> {
        val monitor = get<LiveFollowsMonitor>()
        FollowedChannelsService(get(), followedLive = { userId -> monitor.followedLive(userId) })
    }
    // Per-VOD playback positions ("continue watching") - see WatchProgressStore.
    single { WatchProgressStore() }
    // Locally-persisted viewer-count history for the channel stats panel - see ViewerHistoryStore.
    single { ViewerHistoryStore() }
    // In-app auto-updater (GitHub Releases) - see UpdateManager.
    single { UpdateManager() }
    // One Coil disk cache for the whole app: the image loader fills it with
    // avatars, thumbnails and emotes, and the animated-emote decoder reads from
    // it, so an emote already on disk is never downloaded again. Bounded so it
    // can never grow without limit.
    single<DiskCache> {
        DiskCache.Builder()
            .directory(AppDirs.imageCache.toOkioPath())
            .maxSizeBytes(256L * 1024 * 1024)
            .build()
    }
    single { EmoteFrameCache(get(), get()) }
    // Emote lists survive restarts so chat shows emotes on its first lines.
    single<EmoteCache> {
        PersistentEmoteCache(FileTextStore(File(AppDirs.cache, "emotes")))
    }

    // --- Playback ------------------------------------------------------------
    // Built on first use (the background warm-up just after the first window, or
    // the first stream open), never on the way to the first window: creating it
    // loads libVLC and its plugins, or the 120 MB libmpv.
    // The one libVLC instance: the single-stream player and every multi-stream tile make
    // their media players from it. VLC takes the audio normalizer only as instance
    // arguments, read when libVLC starts.
    single {
        val store = get<DesktopSettingsStore>()
        VlcEngine { if (store.settings.value.audioNormalizer) AudioNormalizerProfile.vlcArgs() else emptyList() }
    }
    // NVIDIA RTX Video runs on instances of its own, for the main player only (see
    // RtxVlcEngines). Without an RTX card the choice is always "nothing".
    single {
        val store = get<DesktopSettingsStore>()
        RtxVlcEngines(
            standard = get(),
            extraArgs = { if (store.settings.value.audioNormalizer) AudioNormalizerProfile.vlcArgs() else emptyList() },
            choice = { RtxVideoChoice.from(store.settings.value, RtxGpu.present) },
        )
    }
    single {
        val store = get<DesktopSettingsStore>()
        val engine = get<VlcEngine>()
        val rtx = get<RtxVlcEngines>()
        DesktopPlayerProvider {
            val mpv = store.settings.value.playbackBackend == PlaybackBackend.MPV && NativeLibs.mpvSupported()
            if (mpv) MpvPlayer(store) else VlcPlayer(engine, rtx = rtx)
        }
    }
    single<DesktopPlayer> { get<DesktopPlayerProvider>().player }
    // LocalStreamProxy takes HttpClient (variant fetches) + BackupStreamResolver
    // (the player-type swap that actually removes ads) on top of the shared
    // PlayableStreamCache and AdBlockEngine. See the /variant route.
    single {
        val settingsStore = get<DesktopSettingsStore>()
        LocalStreamProxy(get(), get(), get(), get(), lowLatency = { settingsStore.settings.value.lowLatencyMode })
    }

    // --- Auth ------------------------------------------------------------------
    single { DesktopOAuthManager() }

    // --- ViewModels (plain factories - see note above) -------------------------
    factory { HomeViewModel(get(), get(), get(), get()) }
    factory {
        val store = get<DesktopSettingsStore>()
        val follows = get<FollowStore>()
        FollowedRailViewModel(
            source = get(),
            loggedInUserId = { if (store.isLoggedIn) store.sessionUserId else null },
            localPins = { follows.followed.value.map { FollowedRef(it.id, it.login, it.displayName) } },
        )
    }
    factory { BrowseViewModel(get()) }
    // Category drill-down takes (gameId, gameName) from the tapped Browse card.
    factory { (gameId: String, gameName: String) -> CategoryViewModel(gameId, gameName, get()) }
    factory { SearchViewModel(get()) }
    // Tells a stale stop from a current one when one stream replaces another (flip, rail click).
    single { PlayTicket() }
    // The row a stream was opened from (Home, a category), so the keyboard can flip through it.
    single { FlipOrigin() }
    // Which windows watch which channel (see ChannelClaims), and the main window's player
    // and ticket: the app-wide ones. Other windows pass their own WindowPlayback as a second
    // parameter of the player view models below.
    single { ChannelClaims() }
    single {
        val players = get<DesktopPlayerProvider>()
        WindowPlayback(
            get(), { players.player }, isolated = false, claims = get(),
            // Screens wait for the build off the UI thread (PlayerGate), never on it.
            built = { players.isBuilt }, build = { players.awaitBuilt() },
        )
    }
    factory { params ->
        val playback = params.getOrNull<WindowPlayback>() ?: get<WindowPlayback>()
        StreamViewModel(
            params.get<String>(), get(), get(), get(), get(), get(), get(), playback.player, get(), get(), get(), get(), get(), get(),
            playback.playTicket, playback.isolated, playback.claims,
        )
    }
    factory { (initial: TileSource) ->
        MultiViewViewModel(
            initial, get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(),
            claims = get(), admit = get<WindowRegistry>()::admitPlayer,
        )
    }
    // The multi-stream session outlives the screen: tiles popped out into their own windows keep playing.
    single {
        val koin = getKoin()
        MultiSession(
            create = { login -> koin.get<MultiViewViewModel> { parametersOf(TileSource.Live(login)) } },
            createVod = { vod -> koin.get<MultiViewViewModel> { parametersOf(vod) } },
            admit = { koin.get<WindowRegistry>().admitPlayer() },
        )
    }
    // Every PureTV window and the limits between them. Tiles count toward the player cap.
    single {
        val koin = getKoin()
        WindowRegistry(tiles = { koin.get<MultiSession>().current.value?.state?.value?.layout?.tiles?.size ?: 0 })
            .also { WindowRegistry.instance = it }
    }
    factory { (channelLogin: String) -> ChannelViewModel(channelLogin, get(), get(), get()) }
    factory { (channelLogin: String) -> ChannelStatsViewModel(channelLogin, get(), get()) }
    factory {
        val players = get<DesktopPlayerProvider>()
        // Live on mpv; never builds a player just to tell it about the setting.
        SettingsViewModel(
            get(),
            onAudioNormalizerChange = { on -> players.ifCreated { it.setAudioNormalizer(on) } },
            onRtxVideoChange = { players.ifCreated { it.applyRtxVideo() } },
        )
    }
    // LoginViewModel collaborators: settingsStore, oauthManager, httpClient,
    // tokenHolder (for "set token BEFORE first authed call"), apiClient (for
    // resolving the broadcaster's own userId/login via GET /users).
    factory { LoginViewModel(get(), get(), get(), get(), get()) }
    factory { (userId: String) -> VodListViewModel(userId, get()) }
    factory { params ->
        val playback = params.getOrNull<WindowPlayback>() ?: get<WindowPlayback>()
        VodPlayerViewModel(params.get<VodLaunch>(), playback.player, get(), get(), get(), playback.playTicket)
    }
    factory { params ->
        val playback = params.getOrNull<WindowPlayback>() ?: get<WindowPlayback>()
        VodChatViewModel(params.get<String>(0), params.get<String>(1), playback.player, get(), get(), get())
    }
}

/** Convenience for screens: `koin.get<StreamViewModel> { parametersOf(channelLogin) }`. */
internal inline fun <reified T : Any> org.koin.core.Koin.getWithParam(param: Any): T =
    get(parameters = { parametersOf(param) })
