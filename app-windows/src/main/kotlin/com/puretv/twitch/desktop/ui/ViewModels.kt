package com.puretv.twitch.desktop.ui

import com.puretv.twitch.core.adblock.AdBlockEngine
import com.puretv.twitch.core.adblock.AdBlockStatus
import com.puretv.twitch.core.adblock.AdBlockStrategy
import com.puretv.twitch.core.api.ChannelSearchResult
import com.puretv.twitch.core.api.DeviceAuth
import com.puretv.twitch.core.api.DeviceCodeResponse
import com.puretv.twitch.core.api.DevicePollResult
import com.puretv.twitch.core.api.TokenResponse
import com.puretv.twitch.core.api.TwitchApiClient
import com.puretv.twitch.core.api.TwitchConfig
import com.puretv.twitch.core.chat.BadgeIndex
import com.puretv.twitch.core.chat.BadgeRepository
import com.puretv.twitch.core.chat.ChatChange
import com.puretv.twitch.core.chat.ChatRoomModes
import com.puretv.twitch.core.chat.ChatTimeline
import com.puretv.twitch.core.chat.ChatTimelineState
import com.puretv.twitch.core.chat.TwitchChatClient
import com.puretv.twitch.core.chat.applying
import com.puretv.twitch.core.chat.batchedEvery
import com.puretv.twitch.core.di.TokenHolder
import com.puretv.twitch.core.emotes.EmoteRepository
import com.puretv.twitch.core.emotes.PickableEmote
import com.puretv.twitch.core.emotes.ResolvedEmote
import com.puretv.twitch.core.emotes.SevenTvEmoteDelta
import com.puretv.twitch.core.chat.buildSelfEcho
import com.puretv.twitch.core.chat.outgoingChatBody
import com.puretv.twitch.core.emotes.SevenTvEventClient
import com.puretv.twitch.core.emotes.SevenTvPaint
import com.puretv.twitch.core.emotes.SevenTvPaintStore
import com.puretv.twitch.core.emotes.applySevenTvDelta
import com.puretv.twitch.core.emotes.buildEmoteIndex
import com.puretv.twitch.core.emotes.applyThirdPartyEmotes
import com.puretv.twitch.core.emotes.buildPickableEmotes
import com.puretv.twitch.core.emotes.prioritizeThirdParty
import com.puretv.twitch.core.model.ChannelEmote
import com.puretv.twitch.core.model.EmoteProvider
import com.puretv.twitch.core.model.AppSettings
import com.puretv.twitch.core.model.Badge
import com.puretv.twitch.core.model.ChannelInfo
import com.puretv.twitch.core.model.ChatMessage
import com.puretv.twitch.core.model.GameInfo
import com.puretv.twitch.core.model.StreamInfo
import com.puretv.twitch.core.model.StreamQuality
import com.puretv.twitch.core.model.PlaybackBackend
import com.puretv.twitch.core.model.UpscalingMode
import com.puretv.twitch.core.perf.StreamOpenTiming
import com.puretv.twitch.core.stream.PlayableStreamCache
import com.puretv.twitch.core.stream.Failure
import com.puretv.twitch.core.stream.FailureKind
import com.puretv.twitch.core.stream.StreamFailures
import com.puretv.twitch.core.repository.ChannelRepository
import com.puretv.twitch.core.repository.StreamRepository
import com.puretv.twitch.desktop.auth.DesktopOAuthManager
import com.puretv.twitch.desktop.data.DesktopSettingsStore
import com.puretv.twitch.desktop.data.FollowStore
import com.puretv.twitch.desktop.data.FollowedChannel
import com.puretv.twitch.desktop.player.LocalStreamProxy
import com.puretv.twitch.desktop.player.PlaybackStallWatchdog
import com.puretv.twitch.desktop.player.ChannelClaims
import com.puretv.twitch.desktop.player.PlayTicket
import com.puretv.twitch.desktop.player.ProxyUnavailableException
import com.puretv.twitch.desktop.player.DesktopPlayer
import io.ktor.client.HttpClient
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * SECTION 11 (desktop) — same shape as the phone/TV ViewModels (Section 11),
 * adapted to [DesktopViewModel] (no Android lifecycle) and to desktop-only
 * collaborators: [DesktopPlayer]/[LocalStreamProxy] for playback and
 * [DesktopSettingsStore]/[DesktopOAuthManager] for persistence and login.
 */

// ---- Home ------------------------------------------------------------------

/** A saved-channel card on Home — live (badge + viewers) or offline (dimmed). */
data class FollowCardState(
    val login: String,
    val displayName: String,
    val avatarUrl: String,
    val isLive: Boolean,
    val viewerCount: Int = 0,
    val title: String = "",
    val gameName: String = "",
    val thumbnailUrl: String = "",
)

data class HomeUiState(
    val isLoggedIn: Boolean = false,
    val following: List<FollowCardState> = emptyList(),
    val topStreams: List<StreamInfo> = emptyList(),
    /** Popular categories, loaded only while signed out (signed in, Browse has them). */
    val topGames: List<GameInfo> = emptyList(),
    val isLoading: Boolean = false,
    /** The last live-streams load failed, so an empty Home can say so and offer a retry. */
    val loadFailed: Boolean = false,
)

class HomeViewModel(
    private val streamRepository: StreamRepository,
    private val channelRepository: ChannelRepository,
    private val followStore: FollowStore,
    private val settingsStore: DesktopSettingsStore,
) : DesktopViewModel() {
    // Seeded from the in-memory session flag so a signed-in cold start never
    // flashes the signed-out layout before the first emission.
    private val _state = MutableStateFlow(HomeUiState(isLoggedIn = settingsStore.isLoggedIn))
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    // Live status of the locally-followed channels. Refreshed when the followed
    // set changes and on `refresh()`. `getStreams` returns only LIVE channels,
    // so anything saved-but-absent here renders as offline.
    private val followedLive = MutableStateFlow<List<StreamInfo>>(emptyList())

    // One load at a time: Retry, Refresh and a sign-in change cancel and replace
    // the load in flight instead of racing it.
    private var loadJob: Job? = null

    init {
        load(showSkeleton = true)
        scope.launch {
            combine(
                streamRepository.topStreams,
                followStore.followed,
                followedLive,
                settingsStore.loggedInState,
            ) { top, followed, live, loggedIn -> HomeSources(top, followed, live, loggedIn) }
                .collect { (top, followed, live, loggedIn) ->
                    _state.update {
                        it.copy(
                            isLoggedIn = loggedIn,
                            following = buildFollowingCards(followed, live),
                            topStreams = top,
                        )
                    }
                }
        }
        // When the user follows/unfollows, re-query who's live.
        scope.launch {
            followStore.followed.collect { refreshFollowedLive() }
        }
        // Signing in or out while Home is open reloads from the right source:
        // Helix once signed in, the anonymous directory (with its category
        // shelf) once signed out.
        scope.launch {
            settingsStore.loggedInState.drop(1).distinctUntilChanged().collect { refresh() }
        }
    }

    private suspend fun refreshFollowedLive() {
        val logins = followStore.followed.value.map { it.login }
        val live =
            if (logins.isEmpty()) emptyList()
            else runCatching { streamRepository.streamsForChannels(logins) }.getOrDefault(emptyList())
        currentCoroutineContext().ensureActive()
        followedLive.value = live
    }

    private fun buildFollowingCards(
        followed: List<FollowedChannel>,
        live: List<StreamInfo>,
    ): List<FollowCardState> {
        val liveByLogin = live.associateBy { it.userLogin.lowercase() }
        return followed
            .map { ch ->
                val s = liveByLogin[ch.login.lowercase()]
                if (s != null) FollowCardState(
                    login = ch.login,
                    displayName = ch.displayName.ifBlank { s.userName },
                    avatarUrl = ch.profileImageUrl,
                    isLive = true,
                    viewerCount = s.viewerCount,
                    title = s.title,
                    gameName = s.gameName,
                    thumbnailUrl = s.thumbnailUrl,
                ) else FollowCardState(
                    login = ch.login,
                    displayName = ch.displayName,
                    avatarUrl = ch.profileImageUrl,
                    isLive = false,
                )
            }
            // Live first (most viewers first), then offline alphabetically.
            .sortedWith(
                compareByDescending<FollowCardState> { it.isLive }
                    .thenByDescending { it.viewerCount }
                    .thenBy { it.displayName.lowercase() },
            )
    }

    fun refresh() = load(showSkeleton = false)

    private fun load(showSkeleton: Boolean) {
        loadJob?.cancel()
        loadJob = scope.launch {
            if (showSkeleton) _state.update { it.copy(isLoading = true) }
            loadDirectory()
            refreshFollowedLive()
            _state.update { it.copy(isLoading = false) }
        }
    }

    /**
     * Top live streams, plus popular categories when signed out. Signed out,
     * the repositories read both through anonymous GQL; the category shelf is
     * skipped when signed in so a signed-in Home makes no extra call.
     */
    private suspend fun loadDirectory() = coroutineScope {
        val games = if (settingsStore.isLoggedIn) {
            null
        } else {
            async { runCatching { channelRepository.topGames().take(HOME_CATEGORY_COUNT) }.getOrNull() }
        }
        val streamsOk = runCatching { streamRepository.refreshTopStreams() }.isSuccess
        val loadedGames = games?.await()
        // runCatching also swallows cancellation: a load replaced mid-flight
        // must not write its result over the newer one.
        ensureActive()
        _state.update { it.copy(loadFailed = !streamsOk, topGames = loadedGames ?: it.topGames) }
    }

    private data class HomeSources(
        val top: List<StreamInfo>,
        val followed: List<FollowedChannel>,
        val live: List<StreamInfo>,
        val loggedIn: Boolean,
    )

    private companion object {
        // Enough to fill the shelf on a wide window; Browse has the full list.
        const val HOME_CATEGORY_COUNT = 12
    }
}

// ---- Browse ------------------------------------------------------------------

data class BrowseUiState(
    val games: List<GameInfo> = emptyList(),
    val isLoading: Boolean = true,
    /** Non-null when the load failed — lets the screen show an error + Retry instead of
     *  an empty grid that's indistinguishable from "no categories". */
    val error: String? = null,
)

class BrowseViewModel(private val channelRepository: ChannelRepository) : DesktopViewModel() {
    private val _state = MutableStateFlow(BrowseUiState())
    val state: StateFlow<BrowseUiState> = _state.asStateFlow()

    init { load() }

    /** (Re)load the category grid. Public so the screen's Retry button can call it. */
    fun load() {
        scope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            runCatching { channelRepository.topGames() }
                .onSuccess { games -> _state.update { it.copy(games = games, isLoading = false, error = null) } }
                .onFailure { e -> _state.update { it.copy(isLoading = false, error = StreamFailures.classify(e).sentence("Couldn't load categories")) } }
        }
    }
}

// ---- Category (live streams within a game) -----------------------------------

data class CategoryUiState(
    val gameName: String = "",
    val streams: List<StreamInfo> = emptyList(),
    val isLoading: Boolean = true,
    /** Non-null when the load failed — distinguishes a network error from "this category
     *  genuinely has no live streams right now". */
    val error: String? = null,
)

/**
 * Backs the Browse → Category screen: loads the live streams in one game,
 * ordered by viewers. [gameName] is passed through from the Browse card so the
 * header renders instantly, before the stream list resolves.
 */
class CategoryViewModel(
    private val gameId: String,
    gameName: String,
    private val streamRepository: StreamRepository,
) : DesktopViewModel() {
    private val _state = MutableStateFlow(CategoryUiState(gameName = gameName, isLoading = true))
    val state: StateFlow<CategoryUiState> = _state.asStateFlow()

    init { load() }

    /** (Re)load this category's live streams. Public so the screen's Retry button can call it. */
    fun load() {
        scope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            runCatching { streamRepository.streamsForGame(gameId) }
                .onSuccess { streams -> _state.update { it.copy(streams = streams, isLoading = false, error = null) } }
                .onFailure { e -> _state.update { it.copy(isLoading = false, error = StreamFailures.classify(e).sentence("Couldn't load this category")) } }
        }
    }
}

// ---- Search ------------------------------------------------------------------

data class SearchUiState(
    val query: String = "",
    val results: List<ChannelSearchResult> = emptyList(),
    val isSearching: Boolean = false,
    /** Non-null when the search request failed — lets the screen say "search failed" with
     *  a Retry rather than showing the same blank as "no matches". */
    val error: String? = null,
)

class SearchViewModel(private val channelRepository: ChannelRepository) : DesktopViewModel() {
    private val _state = MutableStateFlow(SearchUiState())
    val state: StateFlow<SearchUiState> = _state.asStateFlow()

    // One in-flight search at a time. Cancelling the previous job both debounces
    // typing and prevents an earlier query's slower response from overwriting a
    // newer query's results (the out-of-order/stale-result race) — without it,
    // typing "ab" then "abc" could leave "ab"'s results showing under "abc".
    private var searchJob: Job? = null

    fun onQueryChange(query: String) {
        _state.update { it.copy(query = query) }
        searchJob?.cancel()
        if (query.length < 2) {
            _state.update { it.copy(results = emptyList(), isSearching = false, error = null) }
            return
        }
        searchJob = scope.launch {
            _state.update { it.copy(isSearching = true, error = null) }
            delay(300) // debounce: wait for typing to settle before hitting the network
            runCatching { channelRepository.search(query) }
                .onSuccess { results -> _state.update { it.copy(results = results, isSearching = false, error = null) } }
                .onFailure { e ->
                    _state.update {
                        it.copy(results = emptyList(), isSearching = false, error = StreamFailures.classify(e).sentence("Search failed"))
                    }
                }
        }
    }

    /** Re-run the current query (for the screen's Retry affordance). */
    fun retry() = onQueryChange(_state.value.query)
}

// ---- Stream (player + chat) ---------------------------------------------------

data class StreamUiState(
    val channel: ChannelInfo? = null,
    val streamInfo: StreamInfo? = null,
    val playableUrl: String? = null,
    val currentQuality: StreamQuality = StreamQuality.AUTO,
    val adBlockStatus: AdBlockStatus = AdBlockStatus.UNKNOWN,
    val chatMessages: List<ChatMessage> = emptyList(),
    /** Messages that @-mention the local viewer, kept in their own buffer so the
     *  Mentions tab survives the main chat buffer churning past its cap. */
    val mentionMessages: List<ChatMessage> = emptyList(),
    /** Slow, emote-only, followers-only and sub-only, shown above the composer. */
    val roomModes: ChatRoomModes = ChatRoomModes(),
    val emotes: List<PickableEmote> = emptyList(),
    /** Resolved chat badge art (global ∪ channel) for rendering real badge icons. */
    val badges: BadgeIndex = BadgeIndex.EMPTY,
    /** True only when we hold both a token and the token-owner's login (can speak). */
    val canChat: Boolean = false,
    /** The signed-in viewer's login, so @-completion never suggests yourself. */
    val selfLogin: String? = null,
    /** The message the composer is currently replying to, if any. */
    val replyingTo: ChatMessage? = null,
    val isLoading: Boolean = true,
    /** A stream-level fatal error (e.g. the local proxy port is in use). Distinct from
     *  a transient player-engine error; shown in place of the video with priority. */
    val fatalError: String? = null,
    /** 7TV name paints by chatter Twitch id, an immutable snapshot (see [SevenTvPaintStore]). */
    val namePaints: Map<String, SevenTvPaint> = emptyMap(),
)

/** How often learned 7TV name paints reach the chat: at most twice a second. */
private const val NAME_PAINT_BATCH_MS = 500L

/** Stream failures no retry can fix: say why straight away. */
internal val FINAL_FAILURES = setOf(FailureKind.OFFLINE, FailureKind.RESTRICTED, FailureKind.REGION_BLOCKED)

/** Pauses before each automatic retry of a failed stream on Windows. */
internal val AUTO_RETRY_DELAYS_MS = longArrayOf(2_000, 5_000, 10_000)

class StreamViewModel(
    private val channelLogin: String,
    private val streamRepository: StreamRepository,
    private val channelRepository: ChannelRepository,
    private val chatClient: TwitchChatClient,
    private val emoteRepository: EmoteRepository,
    private val adBlockEngine: AdBlockEngine,
    private val settingsStore: DesktopSettingsStore,
    private val vlcPlayer: DesktopPlayer,
    private val localStreamProxy: LocalStreamProxy,
    private val followStore: FollowStore,
    private val apiClient: TwitchApiClient,
    private val sevenTvEventClient: SevenTvEventClient,
    private val badgeRepository: BadgeRepository,
    private val playableStreamCache: PlayableStreamCache,
    private val playTicket: PlayTicket,
    // A window other than the main one: it forgets its channel's ad-block state when it leaves it.
    private val isolated: Boolean = false,
    private val claims: ChannelClaims = ChannelClaims(),
) : DesktopViewModel() {
    private val _state = MutableStateFlow(StreamUiState())
    val state: StateFlow<StreamUiState> = _state.asStateFlow()

    // Taken the moment this view model is built, which is before the previous screen's
    // view model is cleaned up: its stop must not reach the stream started here.
    private val ticket = playTicket.issue()

    // Identity for the optimistic local echo of our own sent messages — Twitch
    // never echoes your own PRIVMSG back. Captured from USERSTATE/GLOBALUSERSTATE.
    private var selfLogin: String? = null
    private var selfDisplayName: String? = null
    private var selfColor: String? = null
    private var selfBadges: List<Badge> = emptyList()
    // From GLOBALUSERSTATE only, so our own echoed lines can wear our 7TV name paint.
    private var selfUserId: String = ""
    private var echoCounter = 0
    private val chatTimeline = ChatTimeline(channelLogin, selfLogin = { selfLogin }, clock = System::currentTimeMillis)
    private val playbackStallWatchdog = PlaybackStallWatchdog()
    private val openTiming = StreamOpenTiming(channelLogin)
    // Automatic retries after a player error; reset once the stream plays again.
    @Volatile private var autoRetries = 0

    // Written on the emote-load coroutine, read on the chat-collect + send paths.
    // Safe as a @Volatile reference because the map is immutable after publish:
    // buildEmoteIndex returns a fresh map that is never mutated in place. Readers
    // only ever see a fully-built map — do NOT switch this to in-place mutation.
    //
    // TWO indices on purpose:
    //  - emoteIndex (THIRD-PARTY only) tokenizes INCOMING messages. Twitch-native
    //    emotes already arrive `emotes=`-tagged, so they must NOT be name-matched
    //    here — otherwise a non-subscriber who merely TYPES a channel sub-emote's
    //    name would see it wrongly rendered as the emote (Twitch shows it as text).
    //  - selfEchoIndex (third-party + first-party Twitch) tokenizes only YOUR OWN
    //    echoed message, which never carries an `emotes=` tag, so a typed Twitch
    //    emote (Kappa, your sub emotes) can only be resolved here by name.
    @Volatile private var emoteIndex: Map<String, ResolvedEmote> = emptyMap()
    @Volatile private var selfEchoIndex: Map<String, ResolvedEmote> = emptyMap()

    // The four emote source lists the indices + picker are built from. Held as fields
    // (not init-coroutine locals) so a live 7TV EventAPI delta can mutate the channel
    // set and rebuild everything without re-fetching. Mutated only on the single chat
    // scope, so a plain var is safe; the @Volatile indices above are what other
    // coroutines read.
    private var tpGlobalEmotes: List<ChannelEmote> = emptyList()
    private var tpChannelEmotes: List<ChannelEmote> = emptyList()
    private var twitchGlobalEmotes: List<ChannelEmote> = emptyList()
    private var twitchChannelEmotes: List<ChannelEmote> = emptyList()

    val isFollowed: StateFlow<Boolean> = followStore.followed
        .map { list -> list.any { it.login.equals(channelLogin, ignoreCase = true) } }
        .stateIn(scope, SharingStarted.Eagerly, followStore.isFollowed(channelLogin))

    init {
        claims.acquire(channelLogin)
        // Reported per channel, so another window's or tile's channel never moves this pill.
        adBlockEngine.resetForNewStream(channelLogin)
        // Mint the playback token and fetch the playlist now, while the proxy starts and
        // the video surface attaches, instead of when VLC first asks the proxy for them.
        // Joins a resolution a hover already started; /stream then finds it ready.
        playableStreamCache.prewarm(channelLogin)
        scope.launch {
            // SECTION 08.3 — VLC talks to our local proxy (port 7979), never
            // straight to Twitch's CDN: that's the choke point where every
            // ad-block strategy in `core` gets applied uniformly (Section 4).
            // A port conflict (second PureTV window / leftover process) must surface as
            // a clear message, not silently kill this coroutine and hang on "Loading…".
            val proxyStart = runCatching { localStreamProxy.start() }
            if (proxyStart.isFailure) {
                _state.update {
                    it.copy(
                        isLoading = false,
                        fatalError = (proxyStart.exceptionOrNull() as? ProxyUnavailableException)?.message
                            ?: "PureTV's local video proxy could not start. Try closing other PureTV windows and reopening this stream.",
                    )
                }
                return@launch
            }

            val preferredQuality = StreamQuality.fromSetting(settingsStore.settings.value.preferredQuality)

            // Playback first: VLC only needs the local proxy URL, and the proxy
            // resolves the stream itself, so nothing below holds up the picture.
            playAt(preferredQuality)

            val channelLookup = async { runCatching { channelRepository.getChannel(channelLogin) }.getOrNull() }
            val liveLookup = async {
                runCatching { streamRepository.streamsForChannels(listOf(channelLogin)).firstOrNull() }.getOrNull()
            }
            // Twitch's own globals need no channel id, so ask for them right away.
            val twitchGlobalLookup = async { runCatching { apiClient.getGlobalEmotes() }.getOrDefault(emptyList()) }
            val channel = channelLookup.await()
            _state.update { it.copy(channel = channel, streamInfo = liveLookup.await(), isLoading = false) }

            // Badge art only needs the channel id, not the emote lists below, so fetch it
            // on its own coroutine. That way early chat messages don't render the
            // "MOD"/"SUB" text-chip fallback for longer than necessary. Best-effort:
            // failure keeps the chips.
            channel?.let { ch ->
                scope.launch {
                    val badgeIndex = runCatching { badgeRepository.loadForChannel(ch.id) }.getOrDefault(BadgeIndex.EMPTY)
                    _state.update { it.copy(badges = badgeIndex) }
                }
            }
            val twitchChannelLookup = async {
                channel?.let { runCatching { apiClient.getChannelTwitchEmotes(it.id) }.getOrDefault(emptyList()) }
                    ?: emptyList()
            }

            // Third-party (BTTV/FFZ/7TV) emotes: the copy cached from the last visit
            // first, so the first chat lines already render, then the fresh set. Each
            // pass rebuilds the indices and picker (honoring the provider toggles and
            // priority) and re-maps messages that arrived before emotes loaded.
            twitchGlobalEmotes = twitchGlobalLookup.await()
            emoteRepository.thirdPartyEmotes(channel?.id, channel?.login).collect { emotes ->
                tpGlobalEmotes = emotes.global
                tpChannelEmotes = emotes.channel
                rebuildEmoteIndicesAndPublish()
                openTiming.mark("emotes_ready")
            }
            twitchChannelEmotes = twitchChannelLookup.await()
            rebuildEmoteIndicesAndPublish()
            // Subscribe to live 7TV emote add/remove for this channel (EventAPI),
            // plus name paints (receive only, no 7TV presence is ever sent).
            channel?.let { sevenTvEventClient.connect(it.id, cosmetics = true) }
        }
        // Live 7TV emote updates: fold each delta into the channel set and rebuild.
        scope.launch {
            sevenTvEventClient.updates.collect { applyEmoteDelta(it) }
        }
        // 7TV name paints: this one collector owns the store (the scope is
        // multi-threaded), and chat gets an immutable snapshot at most twice a
        // second, only when something changed.
        scope.launch {
            val paintStore = SevenTvPaintStore()
            sevenTvEventClient.cosmetics.batchedEvery(NAME_PAINT_BATCH_MS).collect { batch ->
                var changed = false
                batch.forEach { if (paintStore.apply(it)) changed = true }
                if (changed) {
                    val snapshot = paintStore.snapshot()
                    _state.update { it.copy(namePaints = snapshot) }
                }
            }
        }
        // Per-provider emote toggles are live: flipping show7tv/showBttv/showFfz in
        // Settings re-filters the visible emotes without leaving the stream. drop(1)
        // skips the initial value (the channel load already builds from current settings).
        scope.launch {
            settingsStore.settings
                .map { Triple(it.show7tvEmotes, it.showBttvEmotes, it.showFfzEmotes) }
                .distinctUntilChanged()
                .drop(1)
                .collect { rebuildEmoteIndicesAndPublish() }
        }
        scope.launch {
            adBlockEngine.statusFor(channelLogin).collect { status -> _state.update { it.copy(adBlockStatus = status) } }
        }
        scope.launch {
            // The first picture: VLC opening its video output for this stream. Its
            // "playing" event comes about half a second earlier, before any frame is
            // drawn, so it stays a separate mark. A player that cannot tell (mpv)
            // marks the first picture at "playing", as before.
            val outputs = vlcPlayer.videoOutputs
            if (outputs == null) {
                vlcPlayer.status.first { it.isPlaying }
                openTiming.mark("first_frame")
            } else {
                val before = outputs.value
                launch {
                    vlcPlayer.status.first { it.isPlaying }
                    openTiming.mark("vlc_playing")
                }
                outputs.first { it > before }
                openTiming.mark("first_frame")
            }
        }
        scope.launch {
            // VLC reports every failure as the same generic fault. Ask the local
            // proxy what really happened: a stream that cannot play (offline,
            // restricted, blocked in this region) is explained at once; anything
            // else is retried with a fresh session after a growing pause, and only
            // then explained, instead of leaving a black pane to re-open by hand.
            vlcPlayer.status.map { it.error to it.isPlaying }.distinctUntilChanged().collect { (error, playing) ->
                if (playing) autoRetries = 0
                val url = _state.value.playableUrl
                if (error == null || url == null || _state.value.fatalError != null) return@collect
                val failure = localStreamProxy.failureFor(channelLogin)
                when {
                    failure != null && failure.kind in FINAL_FAILURES ->
                        _state.update { it.copy(fatalError = failure.message()) }
                    autoRetries < AUTO_RETRY_DELAYS_MS.size -> {
                        delay(AUTO_RETRY_DELAYS_MS[autoRetries])
                        autoRetries++
                        // Read again after the pause: a quality picked meanwhile is the
                        // one to retry, and a stream that recovered needs no restart.
                        val latest = _state.value.playableUrl ?: return@collect
                        if (vlcPlayer.status.value.isPlaying) return@collect
                        playableStreamCache.invalidate(channelLogin)
                        playbackStallWatchdog.reset()
                        startPlayback(latest)
                    }
                    else -> _state.update {
                        it.copy(fatalError = (failure ?: Failure(FailureKind.UNKNOWN, error)).message())
                    }
                }
            }
        }
        scope.launch {
            // libVLC can remain in PLAYING after its HLS demuxer has stopped
            // advancing, so no error callback reaches the UI. Detect that exact
            // state and perform the same fresh local-URL load that previously
            // required leaving and re-entering the stream manually.
            while (true) {
                delay(2_000)
                val url = _state.value.playableUrl
                if (url == null) {
                    playbackStallWatchdog.reset()
                    continue
                }
                if (playbackStallWatchdog.sample(vlcPlayer.status.value, System.currentTimeMillis())) {
                    // A stalled session should not be reused: mint a fresh one.
                    playableStreamCache.invalidate(channelLogin)
                    startPlayback(url)
                }
            }
        }
        scope.launch {
            // Anonymous (read-only) IRC identity unless we have BOTH a token
            // and the resolved login of the token's owner — Twitch IRC will
            // drop the connection if PASS/NICK don't match. Guard the decrypt so
            // a token-store read error degrades to anonymous chat instead of
            // silently killing the whole chat coroutine (audit U5).
            val saved = runCatching { settingsStore.loadTokens() }.getOrNull()
            selfLogin = saved?.login
            _state.update { it.copy(canChat = saved?.accessToken != null && saved.login != null, selfLogin = saved?.login) }
            // Each redial reads the sign-in again: the token is refreshed about every 4 hours
            // while a stream can stay open far longer.
            chatClient.connect(channelLogin, saved?.accessToken, saved?.login) {
                settingsStore.loadTokens()?.let { it.accessToken to it.login } ?: (null to null)
            }
            // A busy chat arrives in batches (about ten a second), each applied in one
            // state update, so 30+ messages a second no longer recompose the stream
            // screen 30+ times. Core's ChatTimeline holds the chat rules every app
            // shares; rows are built here, outside the update lambda, which may run
            // more than once.
            chatClient.events.batchedEvery().collect { batch ->
                val changes = chatTimeline.changesFor(
                    batch,
                    onSelfState = { self ->
                        self.displayName.ifBlank { null }?.let { selfDisplayName = it }
                        selfColor = self.color
                        selfBadges = self.badges
                        self.userId.ifBlank { null }?.let { selfUserId = it }
                    },
                    decorate = { it.copy(parsedParts = applyThirdPartyEmotes(it.parsedParts, emoteIndex)) },
                )
                if (changes.isNotEmpty()) _state.update { it.withChat(changes) }
            }
        }
    }

    /** [changes] applied to the chat rows, mentions and room modes (see [applying]). */
    private fun StreamUiState.withChat(changes: List<ChatChange>): StreamUiState {
        val before = ChatTimelineState(chatMessages, mentionMessages, roomModes)
        val after = before.applying(changes)
        return if (after === before) this
        else copy(chatMessages = after.messages, mentionMessages = after.mentions, roomModes = after.modes)
    }

    private fun playAt(quality: StreamQuality) {
        val url = LocalStreamProxy.streamUrl(channelLogin, quality)
        playbackStallWatchdog.reset()
        autoRetries = 0
        openTiming.mark("play_requested")
        _state.update { it.copy(playableUrl = url, currentQuality = quality) }
        startPlayback(url)
    }

    /**
     * Every (re)start of the live stream goes through here, so a retry or a stall
     * recovery keeps Settings > Low latency instead of falling back to the
     * player's default delay.
     */
    private fun startPlayback(url: String) {
        vlcPlayer.play(url, lowLatency = settingsStore.settings.value.lowLatencyMode)
    }

    /** A quality picked in the player: played now and kept for next time (see [StreamQuality.preferenceFor]). */
    fun setQuality(quality: StreamQuality) {
        playAt(quality)
        StreamQuality.preferenceFor(quality)?.let { preference ->
            settingsStore.updateSettings { it.copy(preferredQuality = preference) }
        }
    }

    /** Live-apply the scaler to the running player AND persist it. The whole point:
     *  it changes the picture mid-stream, not just on restart. */
    fun setUpscaling(mode: UpscalingMode) {
        vlcPlayer.setUpscaling(mode)
        settingsStore.updateSettings { it.copy(upscalingMode = mode) }
    }

    /** NVIDIA RTX Video switches from the player menu: saved, then applied to this stream. */
    fun setRtxSuperResolution(on: Boolean) {
        settingsStore.updateSettings { it.copy(rtxSuperResolution = on) }
        vlcPlayer.applyRtxVideo()
    }

    fun setRtxHdr(on: Boolean) {
        settingsStore.updateSettings { it.copy(rtxHdr = on) }
        vlcPlayer.applyRtxVideo()
    }

    /** Apply a scaler to the running player WITHOUT persisting — drives the
     *  hold-to-compare A/B (preview Off while held, restore the saved mode on release). */
    fun previewUpscaling(mode: UpscalingMode) = vlcPlayer.setUpscaling(mode)

    /** Persist the backend choice; takes effect on next launch (restart-gated). */
    fun setPlaybackBackend(backend: PlaybackBackend) =
        settingsStore.updateSettings { it.copy(playbackBackend = backend) }

    fun togglePlayPause() = vlcPlayer.togglePlayPause()

    fun setVolume(volume: Int) = vlcPlayer.setVolume(volume)

    fun toggleMute() = vlcPlayer.toggleMute()

    fun startReply(m: ChatMessage) {
        _state.update { it.copy(replyingTo = m) }
    }

    fun cancelReply() {
        _state.update { it.copy(replyingTo = null) }
    }

    fun sendChatMessage(text: String) = scope.launch {
        val replyParent = _state.value.replyingTo
        chatClient.sendMessage(channelLogin, text, replyParent?.id)
        // Optimistic local echo: Twitch never sends our own message back to us,
        // so render it ourselves or the sender never sees what they typed.
        // Echo ONLY what actually went on the wire: outgoingChatBody is the same
        // CRLF-to-space + 500-char clamp buildPrivmsgLine applies, so the sender
        // never sees more than was sent (M8).
        val wireText = outgoingChatBody(text)
        val echo = buildSelfEcho(
            id = "self-" + (echoCounter++).toString(),
            login = selfLogin ?: "you",
            displayName = selfDisplayName,
            color = selfColor,
            badges = selfBadges,
            channel = channelLogin,
            text = wireText,
            timestamp = System.currentTimeMillis(),
            replyParent = replyParent,
            emoteIndex = selfEchoIndex,
            userId = selfUserId,
        )
        val echoed = listOf(ChatChange.Add(echo))
        _state.update { it.withChat(echoed).copy(replyingTo = null) }
    }

    /** Rebuild both emote indices + the picker list from the current source lists and
     *  re-tokenize the visible buffer. Called after channel emotes load and on every
     *  live 7TV delta. Cheap enough to run wholesale: deltas are infrequent and the
     *  buffer is capped at 200. Runs on the chat scope (single-threaded for these fields). */
    private fun rebuildEmoteIndicesAndPublish() {
        // Honor the per-provider toggles and apply 7TV > BTTV > FFZ collision priority,
        // so a disabled provider's emotes never render and 7TV wins name clashes.
        val enabled = enabledThirdPartyProviders()
        val tpChan = prioritizeThirdParty(tpChannelEmotes, enabled)
        val tpGlob = prioritizeThirdParty(tpGlobalEmotes, enabled)
        // Incoming: third-party only (Twitch emotes arrive tagged — never name-match
        // them, or typed sub-emote names mis-render). Self-echo: add first-party Twitch
        // (your own message carries no `emotes=` tag).
        emoteIndex = buildEmoteIndex(tpChan, tpGlob)
        selfEchoIndex = buildEmoteIndex(tpChan, tpGlob, twitchChannelEmotes, twitchGlobalEmotes)
        val picks = buildPickableEmotes(twitchChannelEmotes, tpChan, twitchGlobalEmotes, tpGlob)
        _state.update { st ->
            st.copy(
                emotes = picks,
                chatMessages = st.chatMessages.map {
                    it.copy(parsedParts = applyThirdPartyEmotes(it.parsedParts, emoteIndex))
                },
                mentionMessages = st.mentionMessages.map {
                    it.copy(parsedParts = applyThirdPartyEmotes(it.parsedParts, emoteIndex))
                },
            )
        }
    }

    /** Third-party providers currently enabled in settings (Twitch is always on). */
    private fun enabledThirdPartyProviders(): Set<EmoteProvider> {
        val s = settingsStore.settings.value
        return buildSet {
            if (s.show7tvEmotes) add(EmoteProvider.SEVENTV)
            if (s.showBttvEmotes) add(EmoteProvider.BTTV)
            if (s.showFfzEmotes) add(EmoteProvider.FFZ)
        }
    }

    /**
     * Fold a live 7TV EventAPI delta into the channel emote set (core's
     * [applySevenTvDelta], shared with phone and TV), then rebuild. No rebuild when
     * the delta changes nothing.
     */
    private fun applyEmoteDelta(delta: SevenTvEmoteDelta) {
        tpChannelEmotes = applySevenTvDelta(tpChannelEmotes, delta) ?: return
        rebuildEmoteIndicesAndPublish()
    }

    /** Adds/removes this channel from the local Following list (see [FollowStore]). */
    fun toggleFollow() {
        val ch = _state.value.channel ?: return
        followStore.toggle(
            FollowedChannel(
                id = ch.id,
                login = ch.login,
                displayName = ch.displayName,
                profileImageUrl = ch.profileImageUrl,
            ),
        )
    }

    override fun onCleared() {
        chatClient.disconnect()
        sevenTvEventClient.disconnect()
        // Only when no newer screen has taken the player (a flip or a rail click builds
        // the next stream's view model first), or the new stream would be stopped.
        playTicket.stopIfCurrent(ticket) { vlcPlayer.stop() }
        // Another window may still watch this channel; only the last one to leave forgets it.
        if (claims.release(channelLogin) && isolated) adBlockEngine.forgetChannel(channelLogin)
    }
}

// ---- Channel profile -----------------------------------------------------------

data class ChannelUiState(
    val channel: ChannelInfo? = null,
    val isLive: Boolean = false,
)

class ChannelViewModel(
    private val channelLogin: String,
    private val channelRepository: ChannelRepository,
    private val streamRepository: StreamRepository,
    private val followStore: FollowStore,
) : DesktopViewModel() {
    private val _state = MutableStateFlow(ChannelUiState())
    val state: StateFlow<ChannelUiState> = _state.asStateFlow()

    val isFollowed: StateFlow<Boolean> = followStore.followed
        .map { list -> list.any { it.login.equals(channelLogin, ignoreCase = true) } }
        .stateIn(scope, SharingStarted.Eagerly, followStore.isFollowed(channelLogin))

    init {
        scope.launch {
            val channel = runCatching { channelRepository.getChannel(channelLogin) }.getOrNull()
            val live = runCatching {
                channel?.id?.let { streamRepository.streamsForChannels(listOf(channelLogin)).isNotEmpty() } ?: false
            }.getOrDefault(false)
            _state.update { it.copy(channel = channel, isLive = live) }
        }
    }

    /** Adds/removes this channel from the local Following list (see [FollowStore]). */
    fun toggleFollow() {
        val ch = _state.value.channel ?: return
        followStore.toggle(
            FollowedChannel(
                id = ch.id,
                login = ch.login,
                displayName = ch.displayName,
                profileImageUrl = ch.profileImageUrl,
            ),
        )
    }
}

// ---- Settings -----------------------------------------------------------------

data class SettingsUiState(
    val settings: AppSettings = AppSettings(),
    val isLoggedIn: Boolean = false,
    val loginUsername: String? = null,
    val proxyPort: Int = LocalStreamProxy.PORT,
)

class SettingsViewModel(
    private val settingsStore: DesktopSettingsStore,
    // Tells a running player about the audio normalizer switch (mpv applies it live).
    private val onAudioNormalizerChange: (Boolean) -> Unit = {},
    // Tells a running main player the RTX Video settings changed.
    private val onRtxVideoChange: () -> Unit = {},
) : DesktopViewModel() {
    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    init {
        scope.launch {
            settingsStore.settings.collect { settings ->
                _state.update {
                    it.copy(
                        settings = settings,
                        // Audit U9: cached session state, no per-emission token decrypt.
                        isLoggedIn = settingsStore.isLoggedIn,
                        loginUsername = settingsStore.sessionLogin,
                    )
                }
            }
        }
    }

    fun setPreferredQuality(quality: StreamQuality) =
        settingsStore.updateSettings { it.copy(preferredQuality = quality.name.lowercase()) }

    fun setAdBlockEnabled(enabled: Boolean) =
        settingsStore.updateSettings { it.copy(adBlockEnabled = enabled) }

    fun setAdBlockStrategy(strategy: AdBlockStrategy) =
        settingsStore.updateSettings { it.copy(adBlockStrategy = strategy.name.lowercase()) }

    fun setProxyUrl(url: String) =
        settingsStore.updateSettings { it.copy(customProxyUrl = url) }

    fun setTheme(key: String) =
        settingsStore.updateSettings { it.copy(theme = key) }

    fun setAnimateEmotes(enabled: Boolean) =
        settingsStore.updateSettings { it.copy(animateEmotes = enabled) }

    fun setLowLatency(enabled: Boolean) =
        settingsStore.updateSettings { it.copy(lowLatencyMode = enabled) }

    fun setAudioNormalizer(enabled: Boolean) {
        settingsStore.updateSettings { it.copy(audioNormalizer = enabled) }
        onAudioNormalizerChange(enabled)
    }

    fun setRtxSuperResolution(on: Boolean) {
        settingsStore.updateSettings { it.copy(rtxSuperResolution = on) }
        onRtxVideoChange()
    }

    fun setRtxHdr(on: Boolean) {
        settingsStore.updateSettings { it.copy(rtxHdr = on) }
        onRtxVideoChange()
    }

    // upscalingMode + playbackBackend are now owned by the in-player gear menu
    // (StreamViewModel/VodPlayerViewModel.setUpscaling/setPlaybackBackend) — single
    // write path, so the old SettingsViewModel setters were removed.

    fun logOut() {
        settingsStore.clearTokens()
    }
}

// ---- Login (desktop OAuth via local browser) ----------------------------------

data class LoginUiState(
    val isAuthenticating: Boolean = false,
    /** Shown while waiting for browser approval. */
    val userCode: String? = null,
    val verificationUri: String? = null,
    /** False when no browser could be launched, so the screen must not claim it opened one. */
    val browserOpened: Boolean = true,
    val error: String? = null,
    val isLoggedIn: Boolean = false,
)

/**
 * SECTION 03.2/10 — drives the desktop Twitch login via the Device Code Grant
 * flow ([DeviceAuth]): requests a device/user code, opens the activate page in
 * the system browser, polls for the token, then persists it through
 * [DesktopSettingsStore]'s encrypted store. No client_secret, no loopback.
 */
class LoginViewModel(
    private val settingsStore: DesktopSettingsStore,
    private val oauthManager: DesktopOAuthManager,
    private val httpClient: HttpClient,
    private val tokenHolder: TokenHolder,
    private val apiClient: TwitchApiClient,
) : DesktopViewModel() {
    private val _state = MutableStateFlow(LoginUiState())
    val state: StateFlow<LoginUiState> = _state.asStateFlow()

    fun beginLogin() {
        if (_state.value.isAuthenticating) return
        _state.update { it.copy(isAuthenticating = true, error = null, userCode = null, verificationUri = null) }

        scope.launch {
            val device = runCatching { DeviceAuth.requestDeviceCode(httpClient) }.getOrNull()
            if (device == null) {
                _state.update { it.copy(isAuthenticating = false, error = "Couldn't start sign-in — check your connection and try again.") }
                return@launch
            }
            // The bare activate page, as on phone and TV (see TwitchConfig.ACTIVATE_URL).
            // This used to append `?public_code=` to verification_uri, which already
            // carries a query string, so the pre-fill was malformed and the page often
            // failed to take the code. The screen copies the code for pasting instead.
            val opened = oauthManager.openInBrowser(TwitchConfig.ACTIVATE_URL)
            _state.update { it.copy(userCode = device.userCode, verificationUri = device.verificationUri, browserOpened = opened) }
            pollForToken(device)
        }
    }

    private suspend fun pollForToken(device: DeviceCodeResponse) {
        val deadline = System.currentTimeMillis() + device.expiresInSeconds * 1000
        var intervalMs = device.intervalSeconds.coerceAtLeast(1) * 1000
        while (System.currentTimeMillis() < deadline) {
            kotlinx.coroutines.delay(intervalMs)
            when (val result = runCatching { DeviceAuth.pollOnce(httpClient, device.deviceCode) }
                .getOrDefault(DevicePollResult.Pending)) {
                is DevicePollResult.Success -> { onAuthenticated(result.token); return }
                is DevicePollResult.Pending -> {}
                is DevicePollResult.SlowDown -> intervalMs += 5_000
                is DevicePollResult.Expired -> {
                    _state.update { it.copy(isAuthenticating = false, userCode = null, error = "Sign-in code expired — please try again.") }
                    return
                }
            }
        }
        _state.update { it.copy(isAuthenticating = false, userCode = null, error = "Sign-in timed out — please try again.") }
    }

    private suspend fun onAuthenticated(token: TokenResponse) {
        // ORDERING IS LOAD-BEARING: push the token before the first authed call.
        tokenHolder.update(token.accessToken)
        val expiresAt = System.currentTimeMillis() / 1000 + token.expiresInSeconds
        // Resolve identity BEST-EFFORT first, then persist the session EXACTLY ONCE
        // with whatever identity we got. Two reasons this ordering matters:
        //  1. getUsers() is wrapped in runCatching, so a transient /users failure no
        //     longer throws past saveTokens and silently discards a freshly-minted
        //     access+refresh token (the durability bug this method originally had).
        //  2. saveTokens flips DesktopSettingsStore.loggedInState, which the shell
        //     uses to trigger the followed rail's load. That load needs the user id
        //     (GET /channels/followed). Saving once, AFTER identity is known, means
        //     the single logged-in emission already carries the userId — saving first
        //     with userId=null fired the rail load against a null id (empty rail) and
        //     the later id-bearing save did NOT re-emit (StateFlow dedupes true==true),
        //     so the rail never populated on sign-in.
        val me = runCatching { apiClient.getUsers().firstOrNull() }.getOrNull()
        settingsStore.saveTokens(
            accessToken = token.accessToken,
            refreshToken = token.refreshToken,
            expiresAtEpochSeconds = expiresAt,
            userId = me?.id,
            login = me?.login,
        )
        _state.update { it.copy(isAuthenticating = false, userCode = null, isLoggedIn = true) }
    }
}
