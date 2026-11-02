package com.puretv.twitch.android.ui

import androidx.annotation.OptIn
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.util.UnstableApi
import com.puretv.twitch.android.data.AppSettingsStore
import com.puretv.twitch.android.data.SessionManager
import com.puretv.twitch.android.data.db.CachedStreamDao
import com.puretv.twitch.android.data.db.SearchHistoryDao
import com.puretv.twitch.android.data.db.SearchHistoryEntry
import com.puretv.twitch.android.data.db.VodProgressDao
import com.puretv.twitch.android.data.db.WatchHistoryDao
import com.puretv.twitch.android.data.db.WatchHistoryEntry
import com.puretv.twitch.android.data.db.toCachedStream
import com.puretv.twitch.android.data.db.toCore
import com.puretv.twitch.android.data.db.toStreamInfo
import com.puretv.twitch.android.player.BackgroundPlayback
import com.puretv.twitch.android.player.TwitchPlayer
import com.puretv.twitch.core.adblock.AdBlockEngine
import com.puretv.twitch.core.adblock.AdBlockStatus
import com.puretv.twitch.core.audio.AudioLevelling
import com.puretv.twitch.core.api.ChannelSearchResult
import com.puretv.twitch.core.api.DeviceAuth
import com.puretv.twitch.core.api.DevicePollResult
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
import com.puretv.twitch.core.chat.buildSelfEcho
import com.puretv.twitch.core.chat.outgoingChatBody
import com.puretv.twitch.core.emotes.EmoteRepository
import com.puretv.twitch.core.emotes.PickableEmote
import com.puretv.twitch.core.emotes.ResolvedEmote
import com.puretv.twitch.core.emotes.SevenTvEmoteDelta
import com.puretv.twitch.core.emotes.SevenTvEventClient
import com.puretv.twitch.core.emotes.applySevenTvDelta
import com.puretv.twitch.core.emotes.replaySevenTvDeltas
import com.puretv.twitch.core.emotes.buildEmoteIndex
import com.puretv.twitch.core.emotes.buildPickableEmotes
import com.puretv.twitch.core.emotes.prioritizeThirdParty
import com.puretv.twitch.core.follows.FollowRow
import com.puretv.twitch.core.follows.FollowedChannelsSource
import com.puretv.twitch.core.model.AppSettings
import com.puretv.twitch.core.model.Badge
import com.puretv.twitch.core.model.CategorySort
import com.puretv.twitch.core.model.sortedCategories
import com.puretv.twitch.core.model.ChatMessage
import com.puretv.twitch.core.model.StreamQuality
import com.puretv.twitch.core.model.ChannelEmote
import com.puretv.twitch.core.model.ChatTextSize
import com.puretv.twitch.core.model.EmoteProvider
import com.puretv.twitch.core.model.GameInfo
import com.puretv.twitch.core.model.StreamInfo
import com.puretv.twitch.core.model.VideoInfo
import com.puretv.twitch.core.model.VideoType
import com.puretv.twitch.core.notify.GoLiveAlerts
import com.puretv.twitch.core.notify.LiveFollowsMonitor
import com.puretv.twitch.core.repository.ChannelRepository
import com.puretv.twitch.core.repository.StreamRepository
import com.puretv.twitch.core.repository.UserRepository
import com.puretv.twitch.core.repository.VodRepository
import com.puretv.twitch.core.perf.StreamOpenTiming
import com.puretv.twitch.core.vod.ResumePolicy
import com.puretv.twitch.core.vod.WatchProgress
import com.puretv.twitch.core.stream.PlayableStreamCache
import com.puretv.twitch.core.stream.StreamFailures
import com.puretv.twitch.core.session.SessionState
import io.ktor.client.HttpClient
import kotlin.jvm.Volatile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * SECTION 11: ViewModels backing the phone/tablet screens. Each pulls
 * shared business logic from `core` repositories/clients (Sections 3 to 5)
 * and exposes simple immutable UI state via [StateFlow].
 */

data class HomeUiState(
    val isLoggedIn: Boolean = false,
    val followedLive: List<StreamInfo> = emptyList(),
    val games: List<com.puretv.twitch.core.model.GameInfo> = emptyList(),
    val topStreams: List<StreamInfo> = emptyList(),
    val continueWatching: List<WatchHistoryEntry> = emptyList(),
    /** Past broadcasts worth resuming (ResumePolicy), most recently watched first. */
    val resumableVods: List<WatchProgress> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
)

class HomeViewModel(
    private val streamRepository: StreamRepository,
    private val userRepository: UserRepository,
    private val channelRepository: ChannelRepository,
    private val sessionManager: SessionManager,
    private val watchHistoryDao: WatchHistoryDao,
    private val cachedStreamDao: CachedStreamDao,
    private val vodProgressDao: VodProgressDao,
    private val liveFollows: LiveFollowsMonitor,
) : ViewModel() {
    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    // Once the network returns a fresh top-streams list, stop letting the cached
    // snapshot overwrite it (the cache is only for the instant first paint).
    // @Volatile documents that every collector mutates this on the main
    // dispatcher; it keeps the contract correct if a collector ever moves off it.
    @Volatile private var freshTopStreamsArrived = false

    // Cancel-and-replace handle so a double-tapped refresh cannot run two
    // overlapping loads that flicker isLoading/error against each other.
    private var refreshJob: Job? = null

    init {
        // "Continue watching": most recently opened channels, newest first.
        viewModelScope.launch {
            watchHistoryDao.observeRecent().collect { history ->
                _state.update { it.copy(continueWatching = history) }
            }
        }
        viewModelScope.launch {
            vodProgressDao.observeRecent().collect { rows ->
                val resumable = rows.map { it.toCore() }.filter { ResumePolicy.resumePositionMs(it) != null }
                _state.update { it.copy(resumableVods = resumable) }
            }
        }
        // Cached-first: paint last session's streams instantly, before any network.
        // isLoading is cleared here because cached content is immediately visible.
        viewModelScope.launch {
            cachedStreamDao.observeAll().collect { cached ->
                if (!freshTopStreamsArrived && cached.isNotEmpty()) {
                    _state.update { it.copy(topStreams = cached.map { c -> c.toStreamInfo() }, isLoading = false) }
                }
            }
        }
        // Fresh top streams from the network + write-through to the cache.
        viewModelScope.launch {
            streamRepository.topStreams.collect { top ->
                if (top.isNotEmpty()) {
                    freshTopStreamsArrived = true
                    _state.update { it.copy(topStreams = top) }
                    runCatching {
                        val now = System.currentTimeMillis()
                        cachedStreamDao.upsertAll(top.map { it.toCachedStream(now) })
                        // Bound the cache: drop channels not seen in the last day so the
                        // instant-paint snapshot can't accumulate thousands of stale rows
                        // (and Home's observeAll re-read stays cheap).
                        cachedStreamDao.pruneStaleEntries(now - CACHE_TTL_MS)
                    }
                }
            }
        }
        // React to the session: load real content on login, and repopulate on the
        // logged-out to logged-in transition (the instant-populate fix). Helix
        // discovery needs a token, so logged-out relies on cached content only.
        viewModelScope.launch {
            sessionManager.state.collect { session ->
                when (session) {
                    is SessionState.LoggedOut ->
                        _state.update { it.copy(isLoggedIn = false, isLoading = it.topStreams.isEmpty(), error = null) }
                    is SessionState.LoggedIn -> {
                        userId = session.userId
                        loadLoggedInHome()
                    }
                }
            }
        }
    }

    /** The signed-in viewer's id, for "Live now"; set by the session collector. */
    @Volatile private var userId: String = ""

    /**
     * Followed "Live now", asked of Twitch directly (one /streams/followed call)
     * on every load and refresh. It used to be recomputed only when the follow
     * list changed, so live status went stale while every refresh still
     * re-downloaded the whole follow list. Asked through the shared
     * [LiveFollowsMonitor], so a refresh right after the go-live timer (or
     * Following) fetched reuses that answer, and a go-live it sees is announced.
     */
    private suspend fun refreshFollowedLive() {
        val id = userId.ifBlank { return }
        runCatching { liveFollows.followedLive(id) }
            .onSuccess { live -> _state.update { it.copy(followedLive = live.sortedByDescending { s -> s.viewerCount }) } }
    }

    private suspend fun loadLoggedInHome() {
        _state.update { it.copy(isLoggedIn = true, isLoading = it.topStreams.isEmpty(), error = null) }
        refreshFollowedLive()
        val games = runCatching { channelRepository.topGames() }
            .onSuccess { g -> _state.update { it.copy(games = g) } }
        val top = runCatching { streamRepository.refreshTopStreams() }
        runCatching { userRepository.loadFollowsForCurrentUser() }
        val failed = games.isFailure && top.isFailure && _state.value.topStreams.isEmpty()
        _state.update {
            it.copy(
                isLoading = false,
                error = if (failed) StreamFailures.classify(top.exceptionOrNull()).sentence("Couldn't load") else null,
            )
        }
    }

    fun refresh() {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            val top = runCatching { streamRepository.refreshTopStreams() }
            runCatching { channelRepository.topGames() }.onSuccess { g -> _state.update { it.copy(games = g) } }
            refreshFollowedLive()
            _state.update {
                it.copy(
                    isLoading = false,
                    error = if (top.isFailure && it.topStreams.isEmpty()) {
                        StreamFailures.classify(top.exceptionOrNull()).sentence("Couldn't refresh")
                    } else {
                        null
                    },
                )
            }
        }
    }

    private companion object {
        // Streams older than this are dropped from the instant-paint cache.
        const val CACHE_TTL_MS = 24L * 60 * 60 * 1000
    }
}

data class BrowseUiState(
    /** Everything loaded, in [shownSort] order. */
    val games: List<GameInfo> = emptyList(),
    val isLoading: Boolean = true,
    /** Further pages are on their way (the grid shows placeholder tiles at its end). */
    val isLoadingMore: Boolean = false,
    val error: String? = null,
    /** The order picked (saved in Settings), for the sort control. */
    val sort: CategorySort = CategorySort.DEFAULT,
    /** The order [games] is in. Trails [sort] while every page loads for an order that needs them. */
    val shownSort: CategorySort = CategorySort.DEFAULT,
)

/**
 * The whole category directory, a page at a time (issue #41): the first page on
 * open, the next whenever the grid nears its end ([loadMore]), until Twitch
 * hands back no cursor. Pages arrive in Twitch's order (most viewers first),
 * which is kept as is; the grid shows [sortedCategories] of it. Fewest viewers
 * and A to Z need every page, so picking one loads the rest first.
 */
class BrowseViewModel(
    private val channelRepository: ChannelRepository,
    private val settingsStore: AppSettingsStore,
) : ViewModel() {
    private val _state = MutableStateFlow(BrowseUiState())
    val state: StateFlow<BrowseUiState> = _state.asStateFlow()

    // Everything loaded, in Twitch's order (that order is the viewer ranking).
    private var raw: List<GameInfo> = emptyList()

    // Where the next page starts; null once the directory is exhausted (or signed
    // out, where Twitch refuses anonymous cursors and one page is all there is).
    private var cursor: String? = null
    private var loadJob: Job? = null
    private var moreJob: Job? = null

    init {
        viewModelScope.launch {
            settingsStore.flow.map { it.browseSort }.distinctUntilChanged().collect { sort ->
                _state.update { it.copy(sort = sort) }
                // While the first page is still coming, load() applies the order itself.
                if (loadJob?.isActive != true) applySort()
            }
        }
        load()
    }

    fun retry() = load()

    /** Saved, so Browse opens in the same order next time; the collector above applies it. */
    fun setSort(sort: CategorySort) = viewModelScope.launch {
        // Shown at once, so a quick second press steps on from this pick rather
        // than from the one still being saved.
        _state.update { it.copy(sort = sort) }
        settingsStore.update { it.copy(browseSort = sort) }
    }

    private fun load() {
        loadJob?.cancel()
        // A page still arriving for the old cursor must not land on the fresh first page.
        moreJob?.cancel()
        loadJob = viewModelScope.launch {
            _state.update { it.copy(isLoading = true, isLoadingMore = false, error = null) }
            runCatching { channelRepository.topGamesPage() }
                .onSuccess { page ->
                    raw = page.games.distinctBy { g -> g.id }
                    cursor = page.cursor
                    _state.update { it.copy(isLoading = false) }
                    applySort()
                }
                .onFailure { e -> _state.update { it.copy(isLoading = false, error = e.message ?: "Couldn't load categories.") } }
        }
    }

    /** The grid shows [raw] in [order]. */
    private fun publish(order: CategorySort) {
        _state.update { it.copy(games = sortedCategories(raw, order), shownSort = order) }
    }

    /**
     * Shows the picked order. One that needs every page (Fewest viewers, A to Z)
     * first loads the rest, up to [MAX_FILL_PAGES], with the placeholder tiles up
     * and the current order still showing; a page that fails ends the filling and
     * the order applies to what arrived.
     */
    private fun applySort() {
        val sort = _state.value.sort
        // A fill still running for an earlier pick must not land its order after this one.
        moreJob?.cancel()
        if (!sort.needsEveryPage || cursor == null) {
            _state.update { it.copy(isLoadingMore = false) }
            publish(sort)
            return
        }
        publish(_state.value.shownSort)
        moreJob = viewModelScope.launch {
            _state.update { it.copy(isLoadingMore = true) }
            var pages = 0
            while (pages < MAX_FILL_PAGES) {
                val after = cursor ?: break
                val page = try {
                    channelRepository.topGamesPage(after = after)
                } catch (e: Exception) {
                    // Cancelled (another order, or a reload) ends here without showing anything.
                    ensureActive()
                    break
                }
                cursor = page.cursor
                raw = raw.appendingPage(page.games)
                pages++
            }
            _state.update { it.copy(isLoadingMore = false) }
            publish(sort)
        }
    }

    /** Appends the next page. A no-op while any load is running or when there is nothing more. */
    fun loadMore() {
        val after = cursor ?: return
        if (loadJob?.isActive == true || moreJob?.isActive == true) return
        moreJob = viewModelScope.launch {
            _state.update { it.copy(isLoadingMore = true) }
            runCatching { channelRepository.topGamesPage(after = after) }
                .onSuccess { page ->
                    cursor = page.cursor
                    raw = raw.appendingPage(page.games)
                    _state.update { it.copy(isLoadingMore = false) }
                    publish(_state.value.shownSort)
                }
                // The grid keeps what it has and the cursor stays, so the next scroll
                // tries again. Cancelled (a fill took over) leaves the indicator to it.
                .onFailure {
                    ensureActive()
                    _state.update { it.copy(isLoadingMore = false) }
                }
        }
    }

    private companion object {
        // About 4,000 categories: well past where anyone scrolls, and a bound on
        // how many requests one pick of Fewest viewers or A to Z can cost.
        const val MAX_FILL_PAGES = 40
    }
}

/**
 * [page] added after these games, minus any already shown: the top-games ranking
 * shifts between requests, so pages overlap, and a repeated id would crash the
 * grid (it is keyed by id).
 */
internal fun List<GameInfo>.appendingPage(page: List<GameInfo>): List<GameInfo> {
    val seen = mapTo(HashSet()) { it.id }
    val fresh = page.filter { seen.add(it.id) }
    return if (fresh.isEmpty()) this else this + fresh
}

data class CategoryUiState(
    val gameName: String = "",
    val streams: List<StreamInfo> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
)

/** Streams currently live in a single category (game), reached by tapping a game tile. */
class CategoryViewModel(
    private val gameId: String,
    private val streamRepository: StreamRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(CategoryUiState())
    val state: StateFlow<CategoryUiState> = _state.asStateFlow()

    init { load() }

    fun retry() = load()

    private fun load() = viewModelScope.launch {
        _state.update { it.copy(isLoading = true, error = null) }
        runCatching { streamRepository.streamsForGame(gameId) }
            .onSuccess { streams ->
                _state.update {
                    it.copy(streams = streams, gameName = streams.firstOrNull()?.gameName ?: it.gameName, isLoading = false)
                }
            }
            .onFailure { e -> _state.update { it.copy(isLoading = false, error = e.message ?: "Couldn't load this category.") } }
    }
}

data class SearchUiState(
    val query: String = "",
    val results: List<ChannelSearchResult> = emptyList(),
    /** Categories matching the query, shown above the channels (issue #41). */
    val categories: List<GameInfo> = emptyList(),
    val recentSearches: List<String> = emptyList(),
    val isSearching: Boolean = false,
    val error: String? = null,
)

class SearchViewModel(
    private val channelRepository: ChannelRepository,
    private val searchHistoryDao: SearchHistoryDao,
) : ViewModel() {
    private val _state = MutableStateFlow(SearchUiState())
    val state: StateFlow<SearchUiState> = _state.asStateFlow()

    // One in-flight search at a time. Cancelling the previous job both debounces
    // and prevents an earlier query's slower response from overwriting a newer
    // query's results (the out-of-order/stale-result race).
    private var searchJob: Job? = null

    init {
        viewModelScope.launch {
            searchHistoryDao.observeRecent().collect { rows ->
                // Dedupe for display; the table may hold repeats of the same term.
                _state.update { it.copy(recentSearches = rows.map { r -> r.query }.distinct().take(10)) }
            }
        }
    }

    fun onQueryChange(query: String) {
        _state.update { it.copy(query = query) }
        searchJob?.cancel()
        if (query.length < 2) {
            _state.update { it.copy(results = emptyList(), categories = emptyList(), isSearching = false, error = null) }
            return
        }
        searchJob = viewModelScope.launch {
            _state.update { it.copy(isSearching = true, error = null) }
            delay(300) // debounce: wait for typing to settle before hitting the network
            searchChannelsAndCategories(
                channels = { channelRepository.search(query) },
                categories = { channelRepository.searchCategories(query) },
            )
                .onSuccess { hits ->
                    _state.update { it.copy(results = hits.channels, categories = hits.categories, isSearching = false) }
                }
                .onFailure { e -> _state.update { it.copy(isSearching = false, error = e.message ?: "Search failed.") } }
        }
    }

    /** Persist the current query as a recent search (on submit, or when a result is opened). */
    fun commitSearch() {
        val q = _state.value.query.trim()
        if (q.length < 2) return
        viewModelScope.launch {
            searchHistoryDao.insert(SearchHistoryEntry(query = q, searchedAtEpochMs = System.currentTimeMillis()))
        }
    }

    fun clearHistory() = viewModelScope.launch { searchHistoryDao.clear() }
}

/** What one query found: channels, plus the categories shown above them. */
internal data class SearchHits(val channels: List<ChannelSearchResult>, val categories: List<GameInfo>)

/**
 * Asks for channels and categories at the same time. Categories are a bonus: if
 * that call fails they come back empty and the channels still show. Only a
 * channel failure fails the search. Cancellation (a newer keystroke) is never
 * turned into a failure, so a superseded search cannot paint an error.
 */
internal suspend fun searchChannelsAndCategories(
    channels: suspend () -> List<ChannelSearchResult>,
    categories: suspend () -> List<GameInfo>,
): Result<SearchHits> = coroutineScope {
    // Caught inside the async: a failure escaping it would cancel this scope, and
    // the channel search with it. A real cancellation still surfaces at await().
    val categoryLookup = async { runCatching { categories() }.getOrDefault(emptyList()) }
    val found = try {
        channels()
    } catch (e: Exception) {
        // Rethrows when this search was cancelled; anything else is a real failure.
        ensureActive()
        categoryLookup.cancel()
        return@coroutineScope Result.failure(e)
    }
    Result.success(SearchHits(found, categoryLookup.await()))
}

data class StreamUiState(
    val channel: com.puretv.twitch.core.model.ChannelInfo? = null,
    val streamInfo: StreamInfo? = null,
    val playableUrl: String? = null,
    val adBlockStatus: AdBlockStatus = AdBlockStatus.UNKNOWN,
    val chatMessages: List<ChatMessage> = emptyList(),
    /** Slow, emote-only, followers-only and sub-only, shown above the composer. */
    val roomModes: ChatRoomModes = ChatRoomModes(),
    val chatFraction: Float = 0.5f,
    val chatEnabled: Boolean = true,
    /** Third-party (7TV/BTTV/FFZ) name-to-url map the chat rows word-match against.
     *  Third-party ONLY on purpose: Twitch-native emotes arrive `emotes=`-tagged, so
     *  name-matching them would render a sub emote a non-subscriber merely typed. */
    val emotes: Map<String, String> = emptyMap(),
    /** Everything the picker and autocomplete offer, first-party Twitch included,
     *  in [buildPickableEmotes] order (channel sets before globals). */
    val pickableEmotes: List<PickableEmote> = emptyList(),
    /** Chat badge art (global and channel); the rows draw text chips until it loads. */
    val badges: BadgeIndex = BadgeIndex.EMPTY,
    /** Settings > Chat > badges. */
    val showBadges: Boolean = true,
    /** The signed-in viewer's login, so @-completion never suggests yourself. */
    val selfLogin: String? = null,
    val isLoading: Boolean = true,
    val playbackError: String? = null,
    val isLoggedIn: Boolean = false,
    /** Settings > Chat > Text size, as a scale for the chat list. */
    val chatTextScale: Float = 1f,
    /** What the player plays at: the saved preference on open, then the viewer's pick. */
    val quality: StreamQuality = StreamQuality.AUTO,
    /** Settings > Low latency, read with the playable URL so they reach the player together. */
    val lowLatency: Boolean = false,
    /** Settings > Even out loud and quiet audio, also switchable from the player controls. */
    val audioNormalizer: Boolean = false,
)

@OptIn(UnstableApi::class)
class StreamViewModel(
    private val channelLogin: String,
    private val streamRepository: StreamRepository,
    private val channelRepository: ChannelRepository,
    private val chatClient: TwitchChatClient,
    private val emoteRepository: EmoteRepository,
    private val adBlockEngine: AdBlockEngine,
    private val settingsStore: AppSettingsStore,
    private val watchHistoryDao: WatchHistoryDao,
    private val twitchPlayer: TwitchPlayer,
    private val apiClient: TwitchApiClient,
    private val backgroundPlayback: BackgroundPlayback,
    private val playableStreamCache: PlayableStreamCache,
    private val badgeRepository: BadgeRepository,
    private val sevenTvEventClient: SevenTvEventClient,
) : ViewModel() {
    private val _state = MutableStateFlow(StreamUiState())
    val state: StateFlow<StreamUiState> = _state.asStateFlow()

    // Identity for the optimistic local echo of our own sent messages: Twitch never
    // echoes your own PRIVMSG back. Login comes from the stored session, the rest
    // from USERSTATE/GLOBALUSERSTATE. Every writer and reader here (emote load,
    // chat collect, send) runs on viewModelScope's main dispatcher, so plain vars.
    private var selfLogin: String? = null
    private var selfDisplayName: String? = null
    private var selfColor: String? = null
    private var selfBadges: List<Badge> = emptyList()
    private var echoCounter = 0
    private val chatTimeline = ChatTimeline(channelLogin, selfLogin = { selfLogin }, clock = System::currentTimeMillis)

    // Tokenizes only YOUR OWN echoed message: third-party plus first-party Twitch,
    // because your own message carries no `emotes=` tag, so a typed Kappa or sub
    // emote can only be resolved by name. Incoming rows keep using the
    // third-party-only [StreamUiState.emotes] map.
    private var selfEchoIndex: Map<String, ResolvedEmote> = emptyMap()

    // The emote source lists everything above is built from. Fields (not locals of
    // the load coroutine) so a live 7TV change can fold into the channel set and
    // rebuild without fetching again. Main dispatcher only, like the identity above.
    private var tpChannelEmotes: List<ChannelEmote> = emptyList()
    /** Live 7TV changes this stream, replayed over each third-party list that finishes loading. */
    private val liveEmoteDeltas = ArrayList<SevenTvEmoteDelta>()
    private var tpGlobalEmotes: List<ChannelEmote> = emptyList()
    private var twitchChannelEmotes: List<ChannelEmote> = emptyList()
    private var twitchGlobalEmotes: List<ChannelEmote> = emptyList()

    private val openTiming = StreamOpenTiming(channelLogin)

    // The quality this screen last set on the player; null until it has set one.
    @Volatile private var appliedQuality: StreamQuality? = null

    /**
     * This screen's claim on the shared player, handed to PlayerSurface. Callbacks
     * are installed when playback actually starts, not here, so a stream opened on
     * top of another does not take them before it plays and gets no say in them
     * after the screen below has taken the player back.
     */
    val playTicket = TwitchPlayer.PlayTicket(
        onFirstFrame = { openTiming.mark("first_frame") },
        onGaveUp = { onPlaybackGaveUp() },
        quality = { appliedQuality },
    )
    private var freshSessionRetries = 0

    init {
        adBlockEngine.resetForNewStream()
        // Playback first: resolving the stream does not need the channel info
        // below, so the player starts while the metadata and emotes load.
        viewModelScope.launch { resolvePlayable() }
        viewModelScope.launch {
            val channelLookup = async { runCatching { channelRepository.getChannel(channelLogin) }.getOrNull() }
            val liveLookup = async {
                runCatching { streamRepository.streamsForChannels(listOf(channelLogin)).firstOrNull() }.getOrNull()
            }
            // Twitch's own globals need no channel id, so ask for them right away.
            val twitchGlobalLookup = async { runCatching { apiClient.getGlobalEmotes() }.getOrDefault(emptyList()) }
            val channel = channelLookup.await()
            _state.update { it.copy(channel = channel, streamInfo = liveLookup.await(), isLoading = false) }

            // Record this open for the Home "Continue watching" rail.
            channel?.let { ch ->
                launch {
                    runCatching {
                        watchHistoryDao.upsert(
                            WatchHistoryEntry(
                                channelLogin = ch.login,
                                channelDisplayName = ch.displayName,
                                lastWatchedEpochMs = System.currentTimeMillis(),
                                totalWatchTimeMs = 0L,
                            ),
                        )
                    }
                }
            }

            val twitchChannelLookup = async {
                channel?.let { runCatching { apiClient.getChannelTwitchEmotes(it.id) }.getOrDefault(emptyList()) }
                    ?: emptyList()
            }

            // Badge art only needs the channel id, so it loads on its own coroutine and
            // early lines show the text chips for as short a time as possible.
            // Best-effort: a failure (or no session) keeps the chips.
            channel?.let { ch ->
                launch {
                    val badges = runCatching { badgeRepository.loadForChannel(ch.id) }.getOrDefault(BadgeIndex.EMPTY)
                    _state.update { it.copy(badges = badges) }
                }
            }

            // Live 7TV emote changes for as long as this stream is open. Connected before
            // the emote lists load, so a change pushed meanwhile is replayed over them.
            channel?.let { sevenTvEventClient.connect(it.id) }

            // Third-party emotes: the copy cached from the last visit first, so the
            // first chat lines already render, then the fresh set.
            emoteRepository.thirdPartyEmotes(channel?.id, channel?.login).collect { emotes ->
                tpChannelEmotes = replaySevenTvDeltas(emotes.channel, liveEmoteDeltas)
                tpGlobalEmotes = emotes.global
                rebuildEmotes()
                openTiming.mark("emotes_ready")
            }

            // First-party Twitch emotes (Helix; best-effort, empty without a token)
            // then join the picker, autocomplete and self-echo only, never incoming rows.
            twitchGlobalEmotes = twitchGlobalLookup.await()
            twitchChannelEmotes = twitchChannelLookup.await()
            rebuildEmotes()
        }
        viewModelScope.launch {
            sevenTvEventClient.updates.collect { delta ->
                liveEmoteDeltas += delta
                tpChannelEmotes = applySevenTvDelta(tpChannelEmotes, delta) ?: return@collect
                rebuildEmotes()
            }
        }
        viewModelScope.launch {
            adBlockEngine.status.collect { status -> _state.update { it.copy(adBlockStatus = status) } }
        }
        // Followed, not read once: the player control and Settings write the same
        // flag, and the app-level collector applies it to the player.
        viewModelScope.launch {
            settingsStore.flow.map { it.audioNormalizer }.distinctUntilChanged().collect { on ->
                _state.update { it.copy(audioNormalizer = on) }
            }
        }
        viewModelScope.launch {
            // Anonymous (read-only) IRC login when the user hasn't authenticated yet,
            // `TwitchChatClient` falls back to a `justinfanNNNN` identity for `null`.
            // Sending requires a real OAuth token (see Section 5.1).
            val settings = settingsStore.flow.first()
            val preferred = StreamQuality.fromSetting(settings.preferredQuality)
            twitchPlayer.setQuality(preferred)
            appliedQuality = preferred
            _state.update {
                it.copy(
                    chatFraction = settings.chatFraction,
                    chatEnabled = settings.chatEnabled,
                    chatTextScale = ChatTextSize.from(settings.chatFontSize).scale,
                    showBadges = settings.showBadges,
                    quality = preferred,
                )
            }
            val token = settings.accessToken.takeIf { it.isNotBlank() }
            // Anonymous IRC can read but not send; gate the composer on a real token.
            val username = settings.username.takeIf { it.isNotBlank() }
            selfLogin = username
            _state.update { it.copy(isLoggedIn = token != null, selfLogin = username) }
            chatClient.connect(channelLogin, token, username)
            // A busy chat arrives in batches (about ten a second), each applied in
            // one state update instead of one per message. Core's ChatTimeline holds
            // the chat rules every app shares (moderation tombstones, sub and raid
            // rows, mentions, room modes); the batch is prepared out here because
            // the update lambda may run more than once.
            chatClient.events.batchedEvery().collect { batch ->
                val changes = chatTimeline.changesFor(
                    batch,
                    // Our own name/color/badges, used to dress the self-echo.
                    onSelfState = { self ->
                        self.displayName.ifBlank { null }?.let { selfDisplayName = it }
                        selfColor = self.color
                        selfBadges = self.badges
                    },
                )
                if (changes.isNotEmpty()) _state.update { it.withChat(changes) }
            }
        }
    }

    /**
     * Resolve the ad-free playable master URL. On failure we set [playbackError]
     * instead of leaving [playableUrl] null forever (which the player surface
     * would render as an infinite spinner with no escape). The UI shows the
     * error with a Retry that calls [retry].
     */
    private suspend fun resolvePlayable() {
        _state.update { it.copy(playbackError = null) }
        runCatching { playableStreamCache.get(channelLogin) }
            .onSuccess { playable ->
                openTiming.mark("playable_url")
                val lowLatency = settingsStore.flow.first().lowLatencyMode
                _state.update { it.copy(playableUrl = playable.masterUrl, playbackError = null, lowLatency = lowLatency) }
            }
            .onFailure { e -> _state.update { it.copy(playbackError = StreamFailures.classify(e).message()) } }
    }

    fun retry() = viewModelScope.launch {
        // A retry follows a failure, so never reuse the cached session.
        freshSessionRetries = 0
        playableStreamCache.invalidate(channelLogin)
        resolvePlayable()
    }

    /**
     * The player spent its backed-off retries. Its signed stream URL may simply
     * have expired (a long pause, a network change), so ask Twitch for a fresh
     * session once; if that stream also fails, say so instead of staying black.
     */
    private fun onPlaybackGaveUp() {
        viewModelScope.launch {
            if (freshSessionRetries < MAX_FRESH_SESSION_RETRIES) {
                freshSessionRetries++
                playableStreamCache.invalidate(channelLogin)
                resolvePlayable()
            } else {
                _state.update { it.copy(playbackError = STOPPED_PLAYING) }
            }
        }
    }

    fun sendChatMessage(text: String) = viewModelScope.launch {
        chatClient.sendMessage(channelLogin, text)
        // Optimistic local echo: Twitch never sends our own message back, so render
        // it ourselves or the sender never sees what they typed. outgoingChatBody
        // mirrors the wire clamp, so the echo never shows more than was sent. The
        // "self-" ids never collide with Twitch's UUID message ids (LazyColumn keys).
        val echo = buildSelfEcho(
            id = "self-" + (echoCounter++).toString(),
            login = selfLogin ?: "you",
            displayName = selfDisplayName,
            color = selfColor,
            badges = selfBadges,
            channel = channelLogin,
            text = outgoingChatBody(text),
            timestamp = System.currentTimeMillis(),
            emoteIndex = selfEchoIndex,
        )
        val echoed = listOf(ChatChange.Add(echo))
        _state.update { it.withChat(echoed) }
    }

    /**
     * Rebuilds the row emote map, the picker and the self-echo index from the
     * source lists. Runs when emotes load and on every live 7TV change. Android has
     * no per-provider toggles in its settings UI, so every provider is on;
     * prioritizeThirdParty still applies the 7TV > BTTV > FFZ collision order the
     * desktop uses, and the row map follows the same order (channel before global).
     */
    private fun rebuildEmotes() {
        val tpChannel = prioritizeThirdParty(tpChannelEmotes, ALL_THIRD_PARTY)
        val tpGlobal = prioritizeThirdParty(tpGlobalEmotes, ALL_THIRD_PARTY)
        selfEchoIndex = buildEmoteIndex(tpChannel, tpGlobal, twitchChannelEmotes, twitchGlobalEmotes)
        val rowEmotes = buildEmoteIndex(tpChannel, tpGlobal).mapValues { it.value.url }
        val picks = buildPickableEmotes(twitchChannelEmotes, tpChannel, twitchGlobalEmotes, tpGlobal)
        _state.update { it.copy(emotes = rowEmotes, pickableEmotes = picks) }
    }

    /**
     * [changes] applied to the chat rows and room modes (see [applying]). The phone
     * has no Mentions tab, so a ping is only highlighted in place.
     */
    private fun StreamUiState.withChat(changes: List<ChatChange>): StreamUiState {
        val before = ChatTimelineState(messages = chatMessages, modes = roomModes)
        val after = before.applying(changes)
        return if (after === before) this else copy(chatMessages = after.messages, roomModes = after.modes)
    }

    /** A quality picked in the player: applied now and kept for next time (see [StreamQuality.preferenceFor]). */
    fun setQuality(quality: StreamQuality) {
        twitchPlayer.setQuality(quality)
        appliedQuality = quality
        _state.update { it.copy(quality = quality) }
        StreamQuality.preferenceFor(quality)?.let { preference ->
            viewModelScope.launch { settingsStore.update { it.copy(preferredQuality = preference) } }
        }
    }

    fun setChatFraction(fraction: Float) = viewModelScope.launch {
        settingsStore.update { it.copy(chatFraction = fraction) }
    }

    fun setChatEnabled(enabled: Boolean) = viewModelScope.launch {
        settingsStore.update { it.copy(chatEnabled = enabled) }
    }

    /** The player's audio levelling button (discussion #36): flips the saved setting, applied live. */
    fun toggleAudioNormalizer() = viewModelScope.launch {
        settingsStore.update { it.copy(audioNormalizer = !it.audioNormalizer) }
    }

    override fun onCleared() {
        super.onCleared()
        chatClient.disconnect()
        sevenTvEventClient.disconnect()
        // Teardown lives here, not in PlayerSurface.onDispose: onCleared fires only
        // on a real back-stack pop (leaving the stream), not on rotation or PiP,
        // so playback is not torn down and re-buffered on a config change.
        // Only while this screen still owns the player: when a stream opened on top of
        // another is popped, the one below has usually taken the player back by now,
        // and clearing it here would leave that one black.
        if (twitchPlayer.stopIfOwner(playTicket)) {
            // In-app Back means done watching, not "keep playing in the background":
            // the media notification goes with the stream.
            backgroundPlayback.onStreamClosed()
        }
    }

    private companion object {
        val ALL_THIRD_PARTY = setOf(EmoteProvider.SEVENTV, EmoteProvider.BTTV, EmoteProvider.FFZ)
        const val MAX_FRESH_SESSION_RETRIES = 1
        const val STOPPED_PLAYING =
            "This stream stopped playing. PureTV tried to reconnect but couldn't. Check your connection, then try again."
    }
}

data class ChannelUiState(
    val channel: com.puretv.twitch.core.model.ChannelInfo? = null,
    val liveStream: StreamInfo? = null,
    val isLive: Boolean = false,
    val isLoading: Boolean = true,
    val error: String? = null,
    /** Past videos, newest first, of the [videoFilter] kind (null means all). */
    val videos: List<VideoInfo> = emptyList(),
    val videoFilter: VideoType? = null,
    /** Helix cursor for the next page; null when there is nothing more. */
    val videoCursor: String? = null,
    val videosLoading: Boolean = false,
    val videosError: String? = null,
)

class ChannelViewModel(
    private val channelLogin: String,
    private val channelRepository: ChannelRepository,
    private val streamRepository: StreamRepository,
    private val vodRepository: VodRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(ChannelUiState())
    val state: StateFlow<ChannelUiState> = _state.asStateFlow()

    // Cancel-and-replace: a filter change must not have an older page land after it.
    private var videosJob: Job? = null

    init { load() }

    fun retry() = load()

    /** Shows only one kind of video (null for all of them), from the first page. */
    fun setVideoFilter(type: VideoType?) {
        if (_state.value.videoFilter == type) return
        _state.update { it.copy(videoFilter = type) }
        loadVideos(reset = true)
    }

    fun loadMoreVideos() {
        val s = _state.value
        if (s.videosLoading || s.videoCursor == null) return
        loadVideos(reset = false)
    }

    fun retryVideos() = loadVideos(reset = _state.value.videos.isEmpty())

    /** Same paging rules as the Windows VodListViewModel. */
    private fun loadVideos(reset: Boolean) {
        val userId = _state.value.channel?.id ?: return
        // The cursor is captured now (load more wants the page that was showing);
        // the filter is read inside, so a filter change that lands first wins.
        val cursor = if (reset) null else _state.value.videoCursor
        if (reset) videosJob?.cancel()
        _state.update { it.copy(videosLoading = true, videosError = null) }
        videosJob = viewModelScope.launch {
            runCatching { vodRepository.videosFor(userId = userId, type = _state.value.videoFilter, after = cursor) }
                .onSuccess { page ->
                    _state.update {
                        it.copy(
                            videos = if (reset) page.videos else it.videos + page.videos,
                            videoCursor = page.cursor,
                            videosLoading = false,
                            videosError = null,
                        )
                    }
                }
                .onFailure { e ->
                    // A filter change cancels the page in flight; that is not a failure,
                    // and the new request owns the loading state.
                    if (e is CancellationException) return@onFailure
                    _state.update {
                        it.copy(videosLoading = false, videosError = StreamFailures.classify(e).sentence("Couldn't load videos"))
                    }
                }
        }
    }

    private fun load() = viewModelScope.launch {
        _state.update { it.copy(isLoading = true, error = null) }
        runCatching {
            // Keep the real StreamInfo (not just a boolean) so the page can show
            // the live viewer count, title, and game instead of a bogus "0 viewers".
            val channel = channelRepository.getChannel(channelLogin)
            val live = streamRepository.streamsForChannels(listOf(channelLogin)).firstOrNull()
            channel to live
        }.onSuccess { (channel, live) ->
            _state.update {
                it.copy(
                    channel = channel,
                    liveStream = live,
                    isLive = live != null,
                    isLoading = false,
                    error = if (channel == null) "Channel not found." else null,
                )
            }
            if (channel != null) loadVideos(reset = true)
        }.onFailure { e ->
            _state.update { it.copy(isLoading = false, error = e.message ?: "Couldn't load this channel.") }
        }
    }
}

data class SettingsUiState(
    val settings: AppSettings = AppSettings(),
    val isLoggedIn: Boolean = false,
    val loginUsername: String? = null,
)

class SettingsViewModel(private val settingsStore: AppSettingsStore) : ViewModel() {
    val state: StateFlow<SettingsUiState> = settingsStore.flow.let { flow ->
        val initial = SettingsUiState()
        val mutable = MutableStateFlow(initial)
        viewModelScope.launch {
            flow.collect { prefs ->
                mutable.update {
                    it.copy(
                        settings = prefs,
                        isLoggedIn = prefs.accessToken.isNotBlank(),
                        loginUsername = prefs.username.takeIf { name -> name.isNotBlank() },
                    )
                }
            }
        }
        mutable.asStateFlow()
    }

    fun setChatTextSize(size: ChatTextSize) = viewModelScope.launch {
        settingsStore.update { it.copy(chatFontSize = size.sp) }
    }

    fun setShowBadges(enabled: Boolean) = viewModelScope.launch {
        settingsStore.update { it.copy(showBadges = enabled) }
    }

    fun setPreferredQuality(quality: com.puretv.twitch.core.model.StreamQuality) = viewModelScope.launch {
        settingsStore.update { it.copy(preferredQuality = quality.name.lowercase()) }
    }

    fun setAdBlockEnabled(enabled: Boolean) = viewModelScope.launch {
        settingsStore.update { it.copy(adBlockEnabled = enabled) }
    }

    fun setAnimateEmotes(enabled: Boolean) = viewModelScope.launch {
        settingsStore.update { it.copy(animateEmotes = enabled) }
    }

    fun setBackgroundPlay(enabled: Boolean) = viewModelScope.launch {
        settingsStore.update { it.copy(backgroundPlay = enabled) }
    }

    fun setPipOnLeave(enabled: Boolean) = viewModelScope.launch {
        settingsStore.update { it.copy(pipOnLeave = enabled) }
    }

    fun setLowLatency(enabled: Boolean) = viewModelScope.launch {
        settingsStore.update { it.copy(lowLatencyMode = enabled) }
    }

    fun setAudioOnlyInBackground(enabled: Boolean) = viewModelScope.launch {
        settingsStore.update { it.copy(audioOnlyInBackground = enabled) }
    }

    // PureTvApp hands the saved value to the player, which applies it live.
    fun setAudioNormalizer(enabled: Boolean) = viewModelScope.launch {
        settingsStore.update { it.copy(audioNormalizer = enabled) }
    }

    // Applied live the same way as the switch above.
    fun setAudioLevelling(level: AudioLevelling) = viewModelScope.launch {
        settingsStore.update { it.copy(audioLevelling = level) }
    }

    fun setAdBlockStrategy(strategy: com.puretv.twitch.core.adblock.AdBlockStrategy) = viewModelScope.launch {
        settingsStore.update { it.copy(adBlockStrategy = strategy.name.lowercase()) }
    }

    fun setProxyUrl(url: String) = viewModelScope.launch {
        settingsStore.update { it.copy(customProxyUrl = url) }
    }

    fun setLiveNotifications(enabled: Boolean) = viewModelScope.launch {
        settingsStore.update { it.copy(liveNotifications = enabled) }
    }

    fun dismissPerChannelNote() = viewModelScope.launch {
        settingsStore.update { it.copy(perChannelNoteDismissed = true) }
    }

    fun removeAlertChannel(login: String) = viewModelScope.launch {
        settingsStore.update { it.copy(notifyChannels = GoLiveAlerts.toggled(it.notifyChannels, login)) }
    }

    fun turnOffAllAlertChannels() = viewModelScope.launch {
        settingsStore.update { it.copy(notifyChannels = emptySet()) }
    }

    fun logOut() = settingsStore.clearSession()
}

data class LoginUiState(
    val isAuthenticating: Boolean = false,
    val userCode: String? = null,
    val verificationUri: String? = null,
    val error: String? = null,
    val isLoggedIn: Boolean = false,
)

/**
 * SECTION 03.2 drives the Twitch Device Code Grant flow, the same flow the
 * desktop app uses. Twitch does not accept custom-scheme redirect URIs, so the
 * authorization-code + `puretv-twitch://auth` approach cannot work on mobile;
 * device flow needs no redirect at all. The screen shows [userCode] and points
 * the user at [verificationUri] (twitch.tv/activate); this VM polls until Twitch
 * reports the code authorized, then persists the session.
 */
class LoginViewModel(
    private val httpClient: HttpClient,
    private val settingsStore: AppSettingsStore,
    private val userRepository: UserRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(LoginUiState())
    val state: StateFlow<LoginUiState> = _state.asStateFlow()

    private var loginJob: Job? = null

    fun beginLogin() {
        if (loginJob?.isActive == true) return
        loginJob = viewModelScope.launch {
            _state.value = LoginUiState(isAuthenticating = true)

            val device = runCatching { DeviceAuth.requestDeviceCode(httpClient) }.getOrElse { e ->
                _state.update { it.copy(isAuthenticating = false, error = e.message ?: "Could not start sign-in.") }
                return@launch
            }
            _state.update { it.copy(userCode = device.userCode, verificationUri = device.verificationUri) }

            var intervalMs = device.intervalSeconds.coerceAtLeast(1) * 1_000
            val expiresMs = device.expiresInSeconds.coerceAtLeast(1) * 1_000
            var elapsedMs = 0L
            while (elapsedMs < expiresMs) {
                delay(intervalMs)
                elapsedMs += intervalMs
                when (val result = runCatching { DeviceAuth.pollOnce(httpClient, device.deviceCode) }.getOrNull()) {
                    is DevicePollResult.Success -> {
                        val token = result.token
                        // Persist the token first so the API client is authenticated,
                        // then look up the account identity (Helix /users with no
                        // params returns the current user) and persist it so chat
                        // sends under the right name and Settings shows the username.
                        // The expiry lets TokenRefresher renew it before it lapses.
                        settingsStore.setSession(
                            accessToken = token.accessToken,
                            refreshToken = token.refreshToken,
                            expiresAtEpochSeconds = System.currentTimeMillis() / 1_000 + token.expiresInSeconds,
                        )
                        val me = runCatching { userRepository.getCurrentUser() }.getOrNull()
                        if (me != null) {
                            settingsStore.setSession(
                                accessToken = token.accessToken,
                                refreshToken = token.refreshToken,
                                username = me.login,
                                userId = me.id,
                            )
                        }
                        _state.update { it.copy(isAuthenticating = false, isLoggedIn = true) }
                        return@launch
                    }
                    is DevicePollResult.SlowDown -> intervalMs += 5_000
                    is DevicePollResult.Expired -> {
                        // Clear the now-dead code so the screen returns to the start
                        // state and the "Continue with Twitch" retry button reappears.
                        _state.update { it.copy(isAuthenticating = false, userCode = null, verificationUri = null, error = "Sign-in expired, please try again.") }
                        return@launch
                    }
                    // Pending, or a transient null from a network hiccup: keep polling.
                    else -> {}
                }
            }
            _state.update { it.copy(isAuthenticating = false, userCode = null, verificationUri = null, error = "Sign-in timed out, please try again.") }
        }
    }

    override fun onCleared() {
        loginJob?.cancel()
        super.onCleared()
    }
}

data class FollowingUiState(
    val isLoggedIn: Boolean = false,
    val isLoading: Boolean = false,
    val live: List<FollowRow> = emptyList(),
    val offline: List<FollowRow> = emptyList(),
    val errored: Boolean = false,
    /** Why the last load failed, in words (see StreamFailures); null when it worked. */
    val errorReason: String? = null,
)

/**
 * Backs the Following tab. Drives [FollowedChannelsSource] directly, the same
 * live-plus-offline merge desktop's [com.puretv.twitch.desktop.ui.FollowedRailViewModel]
 * uses, rather than the old followedLogins + streamsForChannels path that could
 * only ever show who is live. Android has no local-pin store like desktop's
 * FollowStore, so [FollowedChannelsSource.load] always gets an empty pin list;
 * only real Twitch follows show up here.
 */
class FollowingViewModel(
    private val source: FollowedChannelsSource,
    private val sessionManager: SessionManager,
    private val settingsStore: AppSettingsStore,
) : ViewModel() {
    private val _state = MutableStateFlow(FollowingUiState(isLoggedIn = sessionManager.state.value is SessionState.LoggedIn))
    val state: StateFlow<FollowingUiState> = _state.asStateFlow()

    /** Lowercased logins with their go-live bell on (long-press a row to change). */
    val alertLogins: StateFlow<Set<String>> = settingsStore.flow
        .map { it.notifyChannels }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    /** Turns [login]'s go-live alerts on, or off if they were on. */
    fun toggleAlerts(login: String) = viewModelScope.launch {
        settingsStore.update { GoLiveAlerts.bellTapped(it, login) }
    }

    // A single in-flight load. Cancel-and-replace on refresh() so a double-tapped
    // pull-to-refresh and a sign-in landing mid-load can't both write the final
    // state, the slower one winning with stale data. Mirrors desktop's loadJob.
    private var loadJob: Job? = null

    init {
        viewModelScope.launch {
            sessionManager.state.collect { session ->
                when (session) {
                    is SessionState.LoggedOut -> {
                        // Drop cached profiles so a different account signing in
                        // later in this process never reuses them.
                        loadJob?.cancel()
                        source.clear()
                        _state.update { FollowingUiState(isLoggedIn = false) }
                    }
                    is SessionState.LoggedIn -> {
                        _state.update { it.copy(isLoggedIn = true) }
                        load(session.userId)
                    }
                }
            }
        }
    }

    /** Fire-and-forget reload (pull-to-refresh). No-op while signed out. */
    fun refresh() {
        val userId = (sessionManager.state.value as? SessionState.LoggedIn)?.userId ?: return
        load(userId)
    }

    private fun load(userId: String) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _state.update { it.copy(isLoading = it.live.isEmpty() && it.offline.isEmpty(), errored = false) }
            runCatching {
                // Fast partial: live channels are in (avatars may still be
                // loading). Drop the loading state now so the list appears
                // without waiting on avatars.
                source.load(userId, emptyList()) { partial ->
                    _state.update { it.copy(isLoading = false, errored = false, live = partial.live, offline = partial.offline) }
                }
            }
                .onSuccess { full -> _state.update { it.copy(isLoading = false, errored = false, live = full.live, offline = full.offline) } }
                .onFailure { e -> _state.update { it.copy(isLoading = false, errored = true, errorReason = StreamFailures.classify(e).reason()) } }
        }
    }
}

/**
 * Backs the Welcome screen's decorative blurred backdrop: the thumbnails of last
 * session's cached streams (empty on a true first launch, where Welcome falls
 * back to a branded gradient). Connect itself is handled by LoginViewModel.
 */
class WelcomeViewModel(cachedStreamDao: CachedStreamDao, settingsStore: AppSettingsStore) : ViewModel() {
    val thumbnails: StateFlow<List<String>> = cachedStreamDao.observeAll()
        .map { rows -> rows.mapNotNull { it.thumbnailUrl.ifBlank { null } }.take(9) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** True when Twitch, not the viewer, ended the session (see TokenRefresher). */
    val signedOutByTwitch: StateFlow<Boolean> = settingsStore.signedOutByTwitch
}
