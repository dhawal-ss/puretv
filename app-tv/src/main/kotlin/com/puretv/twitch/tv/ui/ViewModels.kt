package com.puretv.twitch.tv.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.puretv.twitch.core.adblock.AdBlockEngine
import com.puretv.twitch.core.adblock.AdBlockStatus
import com.puretv.twitch.core.audio.AudioLevelling
import com.puretv.twitch.core.api.ChannelSearchResult
import com.puretv.twitch.core.api.DeviceAuth
import com.puretv.twitch.core.api.DevicePollResult
import com.puretv.twitch.core.api.GamesPage
import com.puretv.twitch.core.chat.BadgeIndex
import com.puretv.twitch.core.chat.BadgeRepository
import com.puretv.twitch.core.chat.ChatChange
import com.puretv.twitch.core.chat.ChatRoomModes
import com.puretv.twitch.core.chat.ChatTimeline
import com.puretv.twitch.core.chat.ChatTimelineState
import com.puretv.twitch.core.chat.TwitchChatClient
import com.puretv.twitch.core.chat.applying
import com.puretv.twitch.core.chat.batchedEvery
import com.puretv.twitch.core.emotes.EmoteRepository
import com.puretv.twitch.core.emotes.SevenTvEmoteDelta
import com.puretv.twitch.core.emotes.SevenTvEventClient
import com.puretv.twitch.core.emotes.applySevenTvDelta
import com.puretv.twitch.core.emotes.replaySevenTvDeltas
import com.puretv.twitch.core.emotes.buildEmoteIndex
import com.puretv.twitch.core.emotes.prioritizeThirdParty
import com.puretv.twitch.core.model.ChannelEmote
import com.puretv.twitch.core.model.EmoteProvider
import com.puretv.twitch.core.follows.FollowRow
import com.puretv.twitch.core.follows.FollowedChannelsSource
import com.puretv.twitch.core.model.AppSettings
import com.puretv.twitch.core.model.ChatMessage
import com.puretv.twitch.core.model.StreamQuality
import com.puretv.twitch.core.model.ChatTextSize
import com.puretv.twitch.core.model.StreamInfo
import com.puretv.twitch.core.model.TV_CHAT_BESIDE_VIDEO
import com.puretv.twitch.core.model.GameInfo
import com.puretv.twitch.core.model.CategorySort
import com.puretv.twitch.core.model.sortedCategories
import com.puretv.twitch.core.notify.GoLiveAlerts
import com.puretv.twitch.core.notify.LiveFollowsMonitor
import com.puretv.twitch.core.playback.ChannelFlip
import com.puretv.twitch.core.playback.ContinueItem
import com.puretv.twitch.core.playback.ContinueWatching
import com.puretv.twitch.core.playback.RecentChannel
import com.puretv.twitch.core.vod.WatchProgress
import com.puretv.twitch.tv.data.db.VodProgressDao
import com.puretv.twitch.tv.data.db.WatchHistoryDao
import com.puretv.twitch.tv.data.db.WatchHistoryEntry
import com.puretv.twitch.tv.data.db.toCore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import com.puretv.twitch.core.playback.FlipDirection
import com.puretv.twitch.core.playback.FlipOrigin
import com.puretv.twitch.core.playback.PendingFlip
import com.puretv.twitch.core.playback.PrewarmGate
import com.puretv.twitch.core.playback.SleepStatus
import com.puretv.twitch.core.playback.SleepTimer
import com.puretv.twitch.core.playback.SleepTimerText
import com.puretv.twitch.core.repository.ChannelRepository
import com.puretv.twitch.core.repository.StreamRepository
import com.puretv.twitch.core.repository.UserRepository
import com.puretv.twitch.core.repository.VodRepository
import com.puretv.twitch.core.model.VideoInfo
import com.puretv.twitch.core.model.VideoType
import com.puretv.twitch.core.perf.StreamOpenTiming
import com.puretv.twitch.core.stream.PlayableStreamCache
import com.puretv.twitch.core.stream.StreamFailures
import com.puretv.twitch.tv.data.AppSettingsStore
import com.puretv.twitch.tv.data.TokenRefresher
import com.puretv.twitch.tv.data.db.CachedStreamDao
import com.puretv.twitch.tv.data.db.toCachedStream
import com.puretv.twitch.tv.data.db.toStreamInfo
import io.ktor.client.HttpClient
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * SECTION 11 / 07: ViewModels backing the TV screens. Functionally
 * identical to the phone app's `ViewModels.kt` (same `core` repositories,
 * same UI-state shape), duplicated rather than shared because Android
 * `ViewModel`/`viewModelScope` and this app's `AppSettingsStore` are
 * platform/app-specific (Section 12.2).
 */

data class HomeUiState(
    val isLoggedIn: Boolean = false,
    /** False until the stored session has been read, so a signed-in cold start never flashes the sign-in panel. */
    val sessionKnown: Boolean = false,
    val followedLive: List<StreamInfo> = emptyList(),
    val topStreams: List<StreamInfo> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    /** The "Continue watching" row: live channels, then broadcasts to resume, then offline channels. */
    val continueWatching: List<ContinueItem> = emptyList(),
)

/**
 * Landing-screen state. Rebuilt (from the one-shot loader the TV app shipped
 * with) to match the phone app's resilience:
 *   • Cached-first paint: last session's streams show instantly from Room,
 *     so the grid is never empty on a cold, offline, or token-expired start.
 *   • Write-through: every fresh network result is persisted (24h TTL).
 *   • [refresh] is re-runnable and IS wired (resume + periodic, see
 *     `TvHomeScreen`), so "Live Now" repopulates instead of freezing on the
 *     first snapshot. On a failed refresh it refreshes the token once and
 *     retries, recovering the expired-token case that used to 401 silently.
 */
class HomeViewModel(
    private val streamRepository: StreamRepository,
    private val userRepository: UserRepository,
    private val settings: AppSettingsStore,
    private val cachedStreamDao: CachedStreamDao,
    private val tokenRefresher: TokenRefresher,
    private val liveFollows: LiveFollowsMonitor,
    private val watchHistoryDao: WatchHistoryDao,
    private val vodProgressDao: VodProgressDao,
) : ViewModel() {
    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    // The pieces of the "Continue watching" row. Each can change on its own (history
    // and VOD positions are Room flows, live status arrives with a refresh), and the
    // row is rebuilt from all of them by core's ContinueWatching.
    private var recentChannels: List<RecentChannel> = emptyList()
    private var resumableVods: List<WatchProgress> = emptyList()
    private var lookedUpLive: Map<String, StreamInfo> = emptyMap()

    // Once the network returns a fresh top-streams list, stop letting the cached
    // snapshot overwrite it (the cache is only for the instant first paint).
    @Volatile private var freshTopStreamsArrived = false

    // Cancel-and-replace so a resume + timer refresh landing together can't run
    // two overlapping loads that flicker isLoading against each other.
    private var refreshJob: Job? = null

    init {
        // "Continue watching": channels watched before, and broadcasts left part way.
        viewModelScope.launch {
            watchHistoryDao.observeRecent().collect { history ->
                val before = recentChannels
                recentChannels = history.map { RecentChannel(it.channelLogin, it.channelDisplayName) }
                rebuildContinueWatching()
                // A newly watched channel needs its live status now, not at the next refresh.
                if (recentChannels.map { it.login } != before.map { it.login }) refreshContinueLive()
            }
        }
        viewModelScope.launch {
            vodProgressDao.observeRecent().collect { rows ->
                resumableVods = rows.map { it.toCore() }
                rebuildContinueWatching()
            }
        }
        // Cached-first: paint last session's streams instantly, before any network.
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
                    _state.update { it.copy(topStreams = top, error = null) }
                    runCatching {
                        val now = System.currentTimeMillis()
                        cachedStreamDao.upsertAll(top.map { it.toCachedStream(now) })
                        // Bound the cache so the instant-paint snapshot can't accumulate
                        // thousands of stale rows.
                        cachedStreamDao.pruneStaleEntries(now - CACHE_TTL_MS)
                    }
                }
            }
        }
        // Login flag, recomputed whenever the session changes. Signed out, Home
        // still loads: core reads the public directory through anonymous GQL.
        viewModelScope.launch {
            settings.flow.collect { prefs ->
                val loggedIn = prefs.accessToken.isNotBlank()
                _state.update {
                    it.copy(
                        isLoggedIn = loggedIn,
                        sessionKnown = true,
                        // Logging out drops the follows the moment it happens.
                        followedLive = if (loggedIn) it.followedLive else emptyList(),
                    )
                }
                // Continue watching marks channels live from the follows; rebuild so a
                // signed-out row stops carrying the last account's live status.
                if (!loggedIn) rebuildContinueWatching()
            }
        }
        refresh()
    }

    fun refresh() {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            _state.update { it.copy(isLoading = it.topStreams.isEmpty(), error = null) }
            val prefs = settings.flow.first()
            // Live status straight from Twitch on every refresh (resume and the 90s
            // poll): one /streams/followed call instead of re-downloading the whole
            // follow list and then checking it. Through the shared monitor, so this
            // refresh is also the go-live banner's check and never doubles it.
            if (prefs.userId.isNotBlank()) {
                runCatching { liveFollows.followedLive(prefs.userId) }
                    .onSuccess { live -> _state.update { it.copy(followedLive = live.sortedByDescending { s -> s.viewerCount }) } }
            } else {
                // Signed out (or just logged out): the last account's follows must
                // not linger as a FOLLOWING hero above the sign-in panel.
                _state.update { it.copy(followedLive = emptyList()) }
            }
            val ok = loadTopStreamsResilient()
            _state.update {
                it.copy(
                    isLoading = false,
                    error = if (!ok && it.topStreams.isEmpty()) "Couldn't load. Check your connection and try again." else null,
                )
            }
            refreshContinueLive()
        }
    }

    /**
     * Which recently watched channels are live right now. The follows and top streams
     * already answered for many of them; only the rest are asked about (one call, at most
     * a dozen logins, and none when everyone is already known).
     */
    private suspend fun refreshContinueLive() {
        val s = _state.value
        val known = (s.followedLive + s.topStreams).associateBy { it.userLogin.lowercase() }
        val ask = ContinueWatching.loginsToCheck(recentChannels, known.keys)
        // Answers merge into what is known: a channel asked about now is replaced by the
        // answer (live or not), the rest keep their last answer.
        val asked = ask.mapTo(HashSet()) { it.lowercase() }
        val stillKnown = lookedUpLive.filterKeys { it !in asked }
        lookedUpLive = if (ask.isEmpty()) {
            stillKnown.filterKeys { key -> recentChannels.any { it.login.equals(key, ignoreCase = true) } }
        } else {
            runCatching { streamRepository.streamsForChannels(ask) }
                .map { found -> stillKnown + found.associateBy { it.userLogin.lowercase() } }
                .getOrDefault(lookedUpLive)
        }
        rebuildContinueWatching()
    }

    private fun rebuildContinueWatching() {
        val s = _state.value
        val live = (s.topStreams + s.followedLive + lookedUpLive.values).associateBy { it.userLogin.lowercase() }
        val items = ContinueWatching.build(recentChannels, resumableVods, live)
        _state.update { if (it.continueWatching == items) it else it.copy(continueWatching = items) }
    }

    /**
     * Refresh top streams; if it fails (a Helix 401 on an expired user token is
     * the common cause) refresh the token once and retry. On success the
     * write-through collector above updates the UI + cache.
     */
    private suspend fun loadTopStreamsResilient(): Boolean {
        if (runCatching { streamRepository.refreshTopStreams() }.isSuccess) return true
        runCatching { tokenRefresher.refreshIfPossible() }
        return runCatching { streamRepository.refreshTopStreams() }.isSuccess
    }

    private companion object {
        // Streams older than this are dropped from the instant-paint cache.
        const val CACHE_TTL_MS = 24L * 60 * 60 * 1000
    }
}

data class TvFollowingUiState(
    val isLoggedIn: Boolean = false,
    val isLoading: Boolean = false,
    val live: List<FollowRow> = emptyList(),
    val offline: List<FollowRow> = emptyList(),
    val errored: Boolean = false,
    /** Why the last load failed, in words (see StreamFailures); null when it worked. */
    val errorReason: String? = null,
)

/**
 * Drives the Following destination (its own nav-rail entry, not just Home's
 * shelf). TV ships no local-pin store the way desktop does, so every row
 * comes straight from the Twitch follow graph and [FollowedChannelsSource.load]
 * always sees an empty pin list.
 *
 * Mirrors the desktop app's `FollowedRailViewModel`: a single in-flight load,
 * cancelled and replaced on [refresh] so a screen re-entry and a manual retry
 * can't race; a fast partial paint the moment live status resolves (avatars
 * may still be loading); and a logged-out reset that clears the profile cache
 * so a different account signing in within the same process never reuses it.
 * The signed-in user id is read the same way [HomeViewModel.refresh] reads
 * it, from `settings.flow.first()`, rather than a synchronous accessor.
 */
class TvFollowingViewModel(
    private val source: FollowedChannelsSource,
    private val settings: AppSettingsStore,
) : ViewModel() {
    private val _state = MutableStateFlow(TvFollowingUiState())
    val state: StateFlow<TvFollowingUiState> = _state.asStateFlow()

    private var loadJob: Job? = null

    // Bumped on every sign-out; a load that started under an older count is stale.
    @Volatile private var signOutCount = 0

    /** Lowercased logins with their go-live bell on (press Menu on a channel to change). */
    val alertLogins: StateFlow<Set<String>> = settings.flow
        .map { it.notifyChannels }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    /** Turns [login]'s go-live banners on, or off if they were on. */
    fun toggleAlerts(login: String) = viewModelScope.launch {
        settings.update { GoLiveAlerts.bellTapped(it, login) }
    }

    init {
        viewModelScope.launch {
            settings.flow.collect { prefs ->
                val loggedIn = prefs.accessToken.isNotBlank()
                if (!loggedIn) {
                    // A load already in flight belongs to the account that just left; its
                    // answer must not land after this wipe (see loadOnce).
                    signOutCount++
                    // Signing out drops the last account's follows at once, so the next
                    // account never sees them while its own list loads.
                    _state.update { it.copy(isLoggedIn = false, isLoading = false, live = emptyList(), offline = emptyList(), errored = false) }
                } else {
                    _state.update { it.copy(isLoggedIn = true) }
                }
            }
        }
        refresh()
    }

    fun refresh() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch { loadOnce() }
    }

    private suspend fun loadOnce() {
        val userId = settings.flow.first().userId
        if (userId.isBlank()) {
            // Signed out (manually or via token-expiry): drop cached profiles so a
            // different account signing in next within this process never reuses them.
            source.clear()
            _state.update { it.copy(isLoggedIn = false, isLoading = false, live = emptyList(), offline = emptyList()) }
            return
        }
        val startedAt = signOutCount
        _state.update { it.copy(isLoggedIn = true, isLoading = it.live.isEmpty() && it.offline.isEmpty()) }
        runCatching {
            source.load(userId, emptyList()) { partial ->
                if (signOutCount != startedAt) return@load
                // Fast partial: live channels are in (avatars may still be loading). Drop
                // the loading state now so the list appears without waiting on avatars.
                _state.update { it.copy(isLoading = false, errored = false, live = partial.live, offline = partial.offline) }
            }
        }
            .onSuccess { full -> if (signOutCount == startedAt) _state.update { it.copy(isLoading = false, errored = false, live = full.live, offline = full.offline) } }
            .onFailure { e -> if (signOutCount == startedAt) _state.update { it.copy(isLoading = false, errored = true, errorReason = StreamFailures.classify(e).reason()) } }
    }
}

data class BrowseUiState(
    /** Everything loaded, in [shownSort] order. */
    val games: List<GameInfo> = emptyList(),
    val isLoading: Boolean = true,
    /** Further pages are on their way. */
    val isLoadingMore: Boolean = false,
    val error: String? = null,
    /** The order picked (saved in Settings), for the sort button. */
    val sort: CategorySort = CategorySort.DEFAULT,
    /** The order [games] is in. Trails [sort] while every page loads for an order that needs them. */
    val shownSort: CategorySort = CategorySort.DEFAULT,
)

/**
 * Category grid. Previously a one-shot load that swallowed any failure into an
 * empty list, so a transient network/token failure blanked the grid until a
 * full relaunch (the reported "categories just disappear" bug). Now it keeps the
 * last-known games on failure, surfaces an error + retry only when it has nothing
 * to show, and refreshes the token once before giving up.
 *
 * Paged (issue #41): [refresh] loads the first page, [loadMore] appends the next
 * as the grid nears its end, until Twitch hands back no cursor. Pages arrive in
 * Twitch's order (most viewers first), which is kept as is; the grid shows
 * [sortedCategories] of it. Fewest viewers and A to Z need every page, so
 * picking one loads the rest first.
 */
class BrowseViewModel(
    private val channelRepository: ChannelRepository,
    private val tokenRefresher: TokenRefresher,
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

    // When the first page last arrived, so a resume only starts over once the
    // list is worth refreshing.
    private var loadedAtMs = 0L

    init {
        viewModelScope.launch {
            settingsStore.flow.map { it.browseSort }.distinctUntilChanged().collect { sort ->
                _state.update { it.copy(sort = sort) }
                // While the first page is still coming, refresh() applies the order itself.
                if (loadJob?.isActive != true) applySort()
            }
        }
        refresh()
    }

    fun retry() = refresh()

    /**
     * The screen came back (Back from a category, or the app returning). Starts
     * over only when there is nothing good to show or the list is older than
     * [STALE_AFTER_MS]; otherwise the games, cursor and scroll position stay, so
     * the viewer is where they left off.
     */
    fun onResume() {
        if (loadJob?.isActive == true) return
        val s = _state.value
        val stale = System.currentTimeMillis() - loadedAtMs > STALE_AFTER_MS
        if (s.games.isEmpty() || s.error != null || stale) refresh()
    }

    /** Saved, so Browse opens in the same order next time; the collector above applies it. */
    fun setSort(sort: CategorySort) = viewModelScope.launch {
        // Shown at once, so a quick second press steps on from this pick rather
        // than from the one still being saved.
        _state.update { it.copy(sort = sort) }
        settingsStore.update { it.copy(browseSort = sort) }
    }

    /** Back to the first page (on open, retry, and a resume once the list is stale). */
    fun refresh() {
        loadJob?.cancel()
        // A page still arriving for the old cursor must not land on the fresh first page.
        moreJob?.cancel()
        loadJob = viewModelScope.launch {
            _state.update { it.copy(isLoading = it.games.isEmpty(), isLoadingMore = false, error = null) }
            loadPageResilient(after = null)
                .onSuccess { page ->
                    // A transient empty response must not wipe a good grid, nor the
                    // cursor that goes with it.
                    if (page.games.isNotEmpty()) {
                        raw = page.games.distinctBy { g -> g.id }
                        cursor = page.cursor
                        loadedAtMs = System.currentTimeMillis()
                    }
                    _state.update { it.copy(isLoading = false, error = null) }
                    applySort()
                }
                .onFailure { e ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            error = if (it.games.isEmpty()) (e.message ?: "Couldn't load categories.") else null,
                        )
                    }
                }
        }
    }

    /** The grid shows [raw] in [order]. */
    private fun publish(order: CategorySort) {
        _state.update { it.copy(games = sortedCategories(raw, order), shownSort = order) }
    }

    /**
     * Shows the picked order. One that needs every page (Fewest viewers, A to Z)
     * first loads the rest, up to [MAX_FILL_PAGES], with the current order still
     * showing; a page that fails ends the filling and the order applies to what
     * arrived.
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
                val page = loadPageResilient(after).getOrNull()
                if (page == null) {
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
            loadPageResilient(after)
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

    private suspend fun loadPageResilient(after: String?): Result<GamesPage> {
        val first = runCatching { channelRepository.topGamesPage(after = after) }
        if (first.isSuccess) return first
        runCatching { tokenRefresher.refreshIfPossible() }
        return runCatching { channelRepository.topGamesPage(after = after) }
    }

    private companion object {
        // About 4,000 categories: well past where anyone scrolls, and a bound on
        // how many requests one pick of Fewest viewers or A to Z can cost.
        const val MAX_FILL_PAGES = 40

        // Older than this, a resume starts the list over from the first page.
        const val STALE_AFTER_MS = 5L * 60 * 1000
    }
}

/**
 * [page] added after these games, minus any already shown: the top-games ranking
 * shifts between requests, so pages overlap, and a repeated id would crash the
 * grid (it is keyed by id).
 */
private fun List<GameInfo>.appendingPage(page: List<GameInfo>): List<GameInfo> {
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
    val isSearching: Boolean = false,
)

class SearchViewModel(private val channelRepository: ChannelRepository) : ViewModel() {
    private val _state = MutableStateFlow(SearchUiState())
    val state: StateFlow<SearchUiState> = _state.asStateFlow()

    // Cancel-and-replace: every keystroke used to start its own search and none
    // were cancelled, so a slow answer for "xq" could land after, and overwrite,
    // the answer for "xqc". Typing on a remote is slow, so a short pause also
    // keeps each letter from costing a Helix call.
    private var searchJob: Job? = null

    fun onQueryChange(query: String) {
        _state.update { it.copy(query = query) }
        searchJob?.cancel()
        if (query.length < 2) {
            _state.update { it.copy(results = emptyList(), categories = emptyList(), isSearching = false) }
            return
        }
        searchJob = viewModelScope.launch {
            _state.update { it.copy(isSearching = true) }
            delay(SEARCH_DEBOUNCE_MS)
            // Channels and categories together. A category failure only empties the
            // categories; a channel failure empties the result, as it always has.
            val hits = searchChannelsAndCategories(
                channels = { channelRepository.search(query) },
                categories = { channelRepository.searchCategories(query) },
            ).getOrNull()
            _state.update {
                it.copy(results = hits?.channels.orEmpty(), categories = hits?.categories.orEmpty(), isSearching = false)
            }
        }
    }

    private companion object {
        const val SEARCH_DEBOUNCE_MS = 350L
    }
}

/** What one query found: channels, plus the categories shown above them. */
private class SearchHits(val channels: List<ChannelSearchResult>, val categories: List<GameInfo>)

/**
 * Asks for channels and categories at the same time (the phone app has the same
 * helper). Only a channel failure fails the search, and cancellation (a newer
 * keystroke) is never turned into a failure.
 */
private suspend fun searchChannelsAndCategories(
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
    /** Slow, emote-only, followers-only and sub-only, shown in the chat header. */
    val roomModes: ChatRoomModes = ChatRoomModes(),
    /** Third-party (7TV/BTTV/FFZ) name-to-url map the chat rows word-match against.
     *  Third-party only: Twitch's own emotes arrive tagged in each message. */
    val emotes: Map<String, String> = emptyMap(),
    /** Chat badge art (global and channel); rows draw text chips until it loads. */
    val badges: BadgeIndex = BadgeIndex.EMPTY,
    /** Settings > Chat > badges. */
    val showBadges: Boolean = true,
    val isLoading: Boolean = true,
    /**
     * Why playback could not start, or null while it is fine. Stream resolution
     * is the ONLY path that produces [playableUrl], so when it fails the screen
     * has nothing to render: without this the viewer gets a black rectangle with
     * no audio and no explanation, and chat keeps scrolling beside it (it is a
     * separate coroutine), which reads as "the app works, the stream is gone".
     * Mirrors the phone app's `StreamUiState.playbackError`.
     */
    val playbackError: String? = null,
    /** Settings > Chat text size, as a scale for the chat overlay. */
    val chatTextScale: Float = 1f,
    /** Settings > Chat position: true puts chat beside the video instead of over it. */
    val chatBesideVideo: Boolean = false,
    /** Settings > Chat background, 0.4 to 1. */
    val chatOpacity: Float = 0.85f,
    /**
     * What the player plays at: the saved preference once settings load (null
     * until then, so the screen never applies a placeholder first), then the
     * viewer's pick.
     */
    val quality: StreamQuality? = null,
    /** Settings > Low latency, read with the playable URL so they reach the player together. */
    val lowLatency: Boolean = false,

    /** Settings > Up and Down change channel (off: they show the controls instead). */
    val upDownFlips: Boolean = true,

    /** Settings > Even out loud and quiet audio, also switchable from the player controls. */
    val audioNormalizer: Boolean = false,
    /** How strongly, when [audioNormalizer] is on. */
    val audioLevelling: AudioLevelling = AudioLevelling.DEFAULT,

    // Channel flipping. The channel can change without leaving the screen (see
    // StreamViewModel.flip), so the screen reads the login from here, not from its route.
    /** The channel on screen. */
    val channelLogin: String = "",
    /** Counts resolved streams, so the screen starts playback once per resolution even when a URL repeats. */
    val playGeneration: Int = 0,
    /** Where a flip in progress will land, for the banner; null when none is pending. */
    val flipTarget: String? = null,
    /** A short note for the banner when a flip had nowhere to go. */
    val flipNote: String? = null,

    // Sleep timer. Text only, so the screen recomposes when the words change (once a
    // minute, then once a second in the last minute), not on every tick.
    /** The sleep control's label, for example "Sleep: Off" or "Sleep: 24 min left". */
    val sleepLabel: String = "Sleep: Off",
    /** The last-minute warning, or null when it is not showing. */
    val sleepWarning: String? = null,
    /** The time ran out: the screen stops watching. */
    val sleepExpired: Boolean = false,
)

class StreamViewModel(
    private val channelLogin: String,
    private val streamRepository: StreamRepository,
    private val channelRepository: ChannelRepository,
    private val chatClient: TwitchChatClient,
    private val emoteRepository: EmoteRepository,
    private val adBlockEngine: AdBlockEngine,
    private val settingsStore: AppSettingsStore,
    private val playableStreamCache: PlayableStreamCache,
    private val badgeRepository: BadgeRepository,
    private val sevenTvEventClient: SevenTvEventClient,
    private val liveFollows: LiveFollowsMonitor,
    private val flipOrigin: FlipOrigin,
    private val watchHistoryDao: WatchHistoryDao,
) : ViewModel() {
    private val _state = MutableStateFlow(StreamUiState(channelLogin = channelLogin))
    val state: StateFlow<StreamUiState> = _state.asStateFlow()

    // The channel on screen. It starts as the route's and changes in place on a flip:
    // the screen stays, and so does the shared player, because leaving the screen is
    // what stops that player.
    private var currentLogin = channelLogin
    private var openTiming = StreamOpenTiming(channelLogin)

    // The signed-in viewer's login (null when watching anonymously), so a line
    // that @-mentions them is highlighted. Written before chat connects.
    private var selfLogin: String? = null
    private var chatTimeline = newChatTimeline(channelLogin)

    // Everything that belongs to the channel on screen: the stream, its details,
    // emotes and chat. A flip cancels it and starts again for the next channel.
    private var channelJob: Job? = null

    // The third-party emote lists the row map is built from. Fields so a live 7TV
    // change can fold into the channel set without fetching again. Every reader
    // and writer runs on viewModelScope's main dispatcher, so plain vars.
    private var tpChannelEmotes: List<ChannelEmote> = emptyList()
    /** Live 7TV changes this stream, replayed over each third-party list that finishes loading. */
    private val liveEmoteDeltas = ArrayList<SevenTvEmoteDelta>()

    /** The channel whose 7TV feed is open; a delta buffered from another channel is ignored. */
    private var liveEmoteChannelId: String? = null
    private var tpGlobalEmotes: List<ChannelEmote> = emptyList()

    // Flipping: a burst of presses becomes one change (see PendingFlip), and a
    // channel is prewarmed only when the viewer shows intent (see PrewarmGate).
    private val pendingFlip = PendingFlip()
    private val prewarmGate = PrewarmGate(System::currentTimeMillis)
    private var settleJob: Job? = null
    private var noteJob: Job? = null

    // Sleep timer: lives with the screen, so it carries across flips. Not saved.
    private val sleepTimer = SleepTimer(System::currentTimeMillis)
    private var sleepJob: Job? = null
    private var lastFlipDirection = FlipDirection.NEXT
    @Volatile private var signedIn = false
    @Volatile private var viewerUserId = ""

    // Watch history ("Continue watching" on Home). A channel counts once its first frame
    // is up, and its time is added when the viewer flips away or leaves. The writes
    // outlive the view model on purpose: leaving the screen cancels viewModelScope.
    private val historyScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var watchedLogin: String? = null
    // The channel whose stream URL the screen was last handed (see onFirstFrame).
    private var readyLogin: String? = null
    private var watchStartedAtMs = 0L

    private fun startWatchHistory() {
        if (watchedLogin != null) return
        val login = currentLogin
        val name = _state.value.channel?.displayName?.takeIf { it.isNotBlank() } ?: login
        val now = System.currentTimeMillis()
        watchedLogin = login
        watchStartedAtMs = now
        historyScope.launch {
            // Never an upsert: that would reset the channel's watch time on every visit.
            runCatching {
                watchHistoryDao.insertIfAbsent(WatchHistoryEntry(login, name, now, 0L))
                watchHistoryDao.touch(login, name, now)
                watchHistoryDao.prune()
            }
        }
    }

    private fun endWatchHistory() {
        val login = watchedLogin ?: return
        watchedLogin = null
        val now = System.currentTimeMillis()
        val watchedMs = now - watchStartedAtMs
        if (watchedMs <= 0) return
        historyScope.launch { runCatching { watchHistoryDao.addWatchTime(login, watchedMs, now) } }
    }

    private fun newChatTimeline(login: String) =
        ChatTimeline(login, selfLogin = { selfLogin }, clock = System::currentTimeMillis)

    init {
        viewModelScope.launch {
            sevenTvEventClient.updates.collect { delta ->
                if (delta.channelTwitchId != liveEmoteChannelId) return@collect
                liveEmoteDeltas += delta
                tpChannelEmotes = applySevenTvDelta(tpChannelEmotes, delta) ?: return@collect
                rebuildEmotes()
            }
        }
        viewModelScope.launch {
            adBlockEngine.status.collect { status -> _state.update { it.copy(adBlockStatus = status) } }
        }
        viewModelScope.launch {
            // Settings the whole screen shares, whichever channel is on.
            settingsStore.flow.collect { settings ->
                signedIn = settings.accessToken.isNotBlank()
                viewerUserId = settings.userId
                refreshFollows()
                _state.update {
                    it.copy(
                        chatTextScale = ChatTextSize.from(settings.chatFontSize).scale,
                        showBadges = settings.showBadges,
                        chatBesideVideo = settings.tvChatMode == TV_CHAT_BESIDE_VIDEO,
                        chatOpacity = settings.tvChatOpacity.coerceIn(0.4f, 1f),
                        // The saved quality is the starting point; a pick in the player
                        // owns it from then on.
                        quality = it.quality ?: StreamQuality.fromSetting(settings.preferredQuality),
                        upDownFlips = settings.tvRemoteFlip,
                        audioNormalizer = settings.audioNormalizer,
                        audioLevelling = settings.audioLevelling,
                    )
                }
            }
        }
        openChannel(channelLogin)
    }

    /**
     * Starts the stream, details, emotes and chat for [login], replacing whatever the
     * channel on screen had running. Used for the first channel and for every flip,
     * so a flipped stream is opened exactly like a normal one: ad-block state and
     * timing start over, chat reconnects to the new channel and nothing from the
     * previous channel stays connected.
     */
    private fun openChannel(login: String) {
        channelJob?.cancel()
        endWatchHistory()
        currentLogin = login
        openTiming = StreamOpenTiming(login)
        chatTimeline = newChatTimeline(login)
        freshSessionRetries = 0
        tpChannelEmotes = emptyList()
        liveEmoteDeltas.clear()
        liveEmoteChannelId = null
        // The old channel's sockets go first, so nothing of it lands in the new one.
        chatClient.disconnect()
        sevenTvEventClient.disconnect()
        adBlockEngine.resetForNewStream()
        _state.update {
            // The previous playableUrl stays until the new one arrives, so the old picture
            // keeps playing instead of the player view being torn down and rebuilt.
            it.copy(
                channelLogin = login,
                channel = null,
                streamInfo = null,
                chatMessages = emptyList(),
                roomModes = ChatRoomModes(),
                emotes = emptyMap(),
                badges = BadgeIndex.EMPTY,
                isLoading = true,
                playbackError = null,
            )
        }
        rebuildEmotes()
        channelJob = viewModelScope.launch {
            // Playback first: resolving the stream does not need the channel info
            // below, so the picture starts while the metadata loads.
            launch { resolvePlayable() }
            launch {
                val channelLookup = async { runCatching { channelRepository.getChannel(login) }.getOrNull() }
                val liveLookup = async {
                    runCatching { streamRepository.streamsForChannels(listOf(login)).firstOrNull() }.getOrNull()
                }
                val channel = channelLookup.await()
                _state.update { it.copy(channel = channel, streamInfo = liveLookup.await(), isLoading = false) }

                // Badge art only needs the channel id, so it loads on its own coroutine.
                // Best-effort: Helix wants a session, so a signed-out TV keeps the chips.
                channel?.let { ch ->
                    launch {
                        val badges = runCatching { badgeRepository.loadForChannel(ch.id) }.getOrDefault(BadgeIndex.EMPTY)
                        _state.update { it.copy(badges = badges) }
                    }
                }

                // Live 7TV emote changes for as long as this stream is open. The socket
                // does not wait for the emote lists below, which can take a moment; changes pushed
                // meanwhile are replayed over them.
                channel?.let {
                    liveEmoteChannelId = it.id
                    sevenTvEventClient.connect(it.id)
                }

                // Third-party (7TV/BTTV/FFZ) emotes: the copy cached from the last visit
                // first, so the first lines already render, then the fresh set.
                emoteRepository.thirdPartyEmotes(channel?.id, channel?.login).collect { emotes ->
                    tpChannelEmotes = replaySevenTvDeltas(emotes.channel, liveEmoteDeltas)
                    tpGlobalEmotes = emotes.global
                    rebuildEmotes()
                    openTiming.mark("emotes_ready")
                }
            }
            launch {
                // Section 7.4: TV viewers are overwhelmingly unauthenticated/lean-back;
                // fall back to the anonymous `justinfanNNNN` IRC identity exactly like
                // the phone app when no OAuth session is present (Section 5.1).
                val settings = settingsStore.flow.first()
                val token = settings.accessToken.takeIf { it.isNotBlank() }
                val username = settings.username.takeIf { it.isNotBlank() }
                selfLogin = username
                chatClient.connect(login, token, username)
                // A busy chat arrives in batches (about ten a second), each applied in
                // one state update instead of one per message. Core's ChatTimeline holds
                // the chat rules every app shares (moderation tombstones, sub and raid
                // rows, mentions, room modes); the batch is prepared out here because
                // the update lambda may run more than once.
                val timeline = chatTimeline
                chatClient.events.batchedEvery().collect { batch ->
                    val changes = timeline.changesFor(batch)
                    if (changes.isNotEmpty()) _state.update { it.withChat(changes) }
                }
            }
        }
    }

    /**
     * Keeps the monitor's live follows fresh for the flip ring. The alert timer polls
     * only the channels with a bell, so this asks for the followed feed itself (shared
     * for 30 seconds, so a burst of presses is one request). The ring read right after
     * uses the previous answer; the next press sees this one.
     */
    private fun refreshFollows() {
        val userId = viewerUserId
        if (!signedIn || userId.isBlank()) return
        viewModelScope.launch { runCatching { liveFollows.followedLive(userId) } }
    }

    /**
     * The channels a flip walks: the viewer's live follows when they are signed in and
     * watching one of them, otherwise the row this stream was opened from.
     */
    private fun flipRing(): List<String> {
        refreshFollows()
        val follows = if (signedIn) liveFollows.liveNow.value.map { it.userLogin } else null
        return ChannelFlip.ringFor(currentLogin, follows, flipOrigin.logins)
    }

    /**
     * The viewer reached for the remote. Prewarms both neighbours of the channel on screen
     * (at most once a minute each) so a flip is instant; never on a timer.
     */
    fun onRemoteIntent() {
        prewarmGate.take(ChannelFlip.neighbours(flipRing(), currentLogin)).forEach(playableStreamCache::prewarm)
    }

    /**
     * One step of channel flipping. Presses within a moment of each other walk the ring
     * together (the banner shows where it would land) and only the last one plays.
     */
    fun flip(direction: FlipDirection) {
        val ring = flipRing()
        val target = pendingFlip.press(ring, currentLogin, direction, System.currentTimeMillis())
        if (target == null) {
            // Nowhere to go (a ring of one): drop any target an earlier press left pending.
            pendingFlip.cancel()
            settleJob?.cancel()
            _state.update { it.copy(flipTarget = null) }
            showFlipNote("No other channel to switch to")
            return
        }
        lastFlipDirection = direction
        onRemoteIntent()
        _state.update { it.copy(flipTarget = target, flipNote = null) }
        // Warm the one the viewer is heading for, so the settle below is quick.
        prewarmGate.take(listOf(target)).forEach(playableStreamCache::prewarm)
        settleJob?.cancel()
        settleJob = viewModelScope.launch {
            val next = pendingFlip.awaitSettled({ currentLogin }, System::currentTimeMillis)
            commitFlip(next)
        }
    }

    private fun commitFlip(next: String?) {
        if (next == null) {
            _state.update { it.copy(flipTarget = null) }
            return
        }
        // The banner stays up until the new stream is ready (see resolvePlayable).
        openChannel(next)
        // Heading the same way, the next channel is the likeliest one to want.
        ChannelFlip.step(flipRing(), next, lastFlipDirection)?.let { ahead ->
            prewarmGate.take(listOf(ahead)).forEach(playableStreamCache::prewarm)
        }
    }

    /** The sleep control was pressed: next preset (Off, 15, 30, 60, 90, 120 minutes), counting from now. */
    fun cycleSleepTimer() {
        sleepTimer.cycle()
        sleepJob?.cancel()
        sleepJob = viewModelScope.launch {
            while (true) {
                val status = sleepTimer.status()
                publishSleep(status)
                if (status !is SleepStatus.Running) break
                delay(1_000)
            }
        }
    }

    /** The screen has acted on [StreamUiState.sleepExpired]. */
    fun onSleepHandled() {
        _state.update { it.copy(sleepExpired = false) }
    }

    /** Any remote key. In the sleep timer's last minute it buys another 15 minutes. */
    fun onViewerActivity() {
        if (sleepTimer.onActivity()) {
            val status = sleepTimer.status()
            publishSleep(status)
        }
    }

    private fun publishSleep(status: SleepStatus) {
        _state.update {
            when (status) {
                SleepStatus.Off -> it.copy(sleepLabel = "Sleep: Off", sleepWarning = null)
                is SleepStatus.Running -> it.copy(
                    sleepLabel = SleepTimerText.remaining(status.remainingMs),
                    sleepWarning = if (status.warning) SleepTimerText.warning(status.remainingMs, "button") else null,
                )
                SleepStatus.Expired -> it.copy(sleepLabel = "Sleep: Off", sleepWarning = null, sleepExpired = true)
            }
        }
    }

    private fun showFlipNote(note: String) {
        _state.update { it.copy(flipNote = note) }
        noteJob?.cancel()
        noteJob = viewModelScope.launch {
            delay(FLIP_NOTE_MS)
            _state.update { it.copy(flipNote = null) }
        }
    }

    /**
     * Rebuilds the name-to-url map chat rows match words against. Runs when emotes
     * load and on every live 7TV change. TV has no per-provider toggles in its
     * settings, so every provider is on; collisions resolve 7TV > BTTV > FFZ and
     * channel before global, as on the other apps.
     */
    private fun rebuildEmotes() {
        val map = buildEmoteIndex(
            prioritizeThirdParty(tpChannelEmotes, ALL_THIRD_PARTY),
            prioritizeThirdParty(tpGlobalEmotes, ALL_THIRD_PARTY),
        ).mapValues { it.value.url }
        _state.update { it.copy(emotes = map) }
    }

    /** [changes] applied to the chat rows and room modes (see [applying]). */
    private fun StreamUiState.withChat(changes: List<ChatChange>): StreamUiState {
        val before = ChatTimelineState(messages = chatMessages, modes = roomModes)
        val after = before.applying(changes)
        return if (after === before) this else copy(chatMessages = after.messages, roomModes = after.modes)
    }

    /**
     * Resolve the signed master-playlist URL the player consumes.
     *
     * The failure branch is the point of this function. `runCatching { }` with
     * only an `onSuccess` swallows every cause (a GQL schema change, a blocked
     * usher host, an offline channel, a dead network) and leaves [playableUrl]
     * null, which the screen renders as an indistinguishable black frame. Every
     * one of those needs a different fix, so the message is surfaced verbatim
     * and logged under [TAG] for `adb logcat`.
     */
    private suspend fun resolvePlayable() {
        // A flip while this was resolving moved on to another channel: its answer is stale.
        val login = currentLogin
        _state.update { it.copy(playbackError = null) }
        runCatching { playableStreamCache.get(login) }
            .onSuccess { playable ->
                if (login != currentLogin) return
                openTiming.mark("playable_url")
                val lowLatency = settingsStore.flow.first().lowLatencyMode
                if (login != currentLogin) return
                readyLogin = login
                _state.update {
                    it.copy(
                        playableUrl = playable.masterUrl,
                        playGeneration = it.playGeneration + 1,
                        playbackError = null,
                        lowLatency = lowLatency,
                        // A newer flip is already on its way: its banner stays.
                        flipTarget = if (pendingFlip.pending != null) it.flipTarget else null,
                    )
                }
            }
            .onFailure { e ->
                if (login != currentLogin) return
                Log.w(TAG, "Stream resolution failed for $login", e)
                val failure = StreamFailures.classify(e)
                // The technical detail stays on screen in small print: a TV owner
                // cannot run adb, so a photo of this screen is the bug report.
                // No playable URL either: after a flip the previous channel's picture
                // must not keep playing under the explanation.
                _state.update {
                    it.copy(
                        playableUrl = null,
                        flipTarget = if (pendingFlip.pending != null) it.flipTarget else null,
                        playbackError = failure.message() +
                            if (failure.detail.isBlank()) "" else "\n\nDetails: ${failure.detail}",
                    )
                }
            }
    }

    /** Re-run resolution after a failure (OK on the remote), with a fresh session. */
    fun retry() = viewModelScope.launch {
        freshSessionRetries = 0
        playableStreamCache.invalidate(currentLogin)
        resolvePlayable()
    }

    private var freshSessionRetries = 0

    /**
     * The player spent its backed-off retries. Its signed stream URL may simply
     * have expired (a long pause, a network change), so ask Twitch for a fresh
     * session once; if that stream also fails, say so instead of staying black.
     */
    fun onPlaybackGaveUp() {
        viewModelScope.launch {
            if (freshSessionRetries < MAX_FRESH_SESSION_RETRIES) {
                freshSessionRetries++
                playableStreamCache.invalidate(currentLogin)
                resolvePlayable()
            } else {
                _state.update {
                    it.copy(
                        playbackError = "This stream stopped playing. PureTV tried to reconnect but couldn't. " +
                            "Check your connection, then press OK to try again.",
                    )
                }
            }
        }
    }

    /** The player put this stream's first frame on screen (local timing only). */
    fun onFirstFrame(generation: Int) {
        // Only the stream of the channel on screen counts: the previous channel's picture
        // can still be playing, or rendering a frame, while the next one resolves.
        if (generation != _state.value.playGeneration || readyLogin != currentLogin) return
        openTiming.mark("first_frame")
        startWatchHistory()
    }

    /** A quality picked in the player: applied by the screen and kept for next time (see [StreamQuality.preferenceFor]). */
    fun setQuality(quality: StreamQuality) {
        _state.update { it.copy(quality = quality) }
        StreamQuality.preferenceFor(quality)?.let { preference ->
            viewModelScope.launch { settingsStore.update { it.copy(preferredQuality = preference) } }
        }
    }

    /**
     * The player's audio levelling button (discussion #36): one button for a
     * remote, stepping Off, Light, Balanced, Strong and back to Off. Saved like
     * the Settings switch and strength, which the app-level collector applies
     * to the player live.
     */
    fun cycleAudioLevelling() = viewModelScope.launch {
        settingsStore.update { s ->
            if (!s.audioNormalizer) {
                s.copy(audioNormalizer = true, audioLevelling = AudioLevelling.LIGHT)
            } else {
                when (s.audioLevelling) {
                    AudioLevelling.LIGHT -> s.copy(audioLevelling = AudioLevelling.BALANCED)
                    AudioLevelling.BALANCED -> s.copy(audioLevelling = AudioLevelling.STRONG)
                    AudioLevelling.STRONG -> s.copy(audioNormalizer = false)
                }
            }
        }
    }

    fun sendChatMessage(text: String) = viewModelScope.launch {
        chatClient.sendMessage(currentLogin, text)
    }

    override fun onCleared() {
        super.onCleared()
        endWatchHistory()
        chatClient.disconnect()
        sevenTvEventClient.disconnect()
    }

    private companion object {
        const val TAG = "StreamViewModel"
        const val MAX_FRESH_SESSION_RETRIES = 1
        const val FLIP_NOTE_MS = 2_500L
        val ALL_THIRD_PARTY = setOf(EmoteProvider.SEVENTV, EmoteProvider.BTTV, EmoteProvider.FFZ)
    }
}

data class ChannelUiState(
    val channel: com.puretv.twitch.core.model.ChannelInfo? = null,
    val isLive: Boolean = false,
    val viewerCount: Long = 0L,
    /** Past broadcasts, newest first. Signed out this is a single page from the anonymous GQL directory. */
    val videos: List<VideoInfo> = emptyList(),
    /** Helix cursor for the next page; null when there is nothing more. */
    val videoCursor: String? = null,
    val videosLoading: Boolean = false,
)

class ChannelViewModel(
    private val channelLogin: String,
    private val channelRepository: ChannelRepository,
    private val streamRepository: StreamRepository,
    private val vodRepository: VodRepository,
    private val settings: AppSettingsStore,
) : ViewModel() {
    private val _state = MutableStateFlow(ChannelUiState())
    val state: StateFlow<ChannelUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val channel = runCatching { channelRepository.getChannel(channelLogin) }.getOrNull()
            val stream = runCatching {
                streamRepository.streamsForChannels(listOf(channelLogin)).firstOrNull()
            }.getOrNull()
            _state.update {
                it.copy(channel = channel, isLive = stream != null, viewerCount = stream?.viewerCount?.toLong() ?: 0L)
            }
            // Signed out the repository reads one page from the anonymous GQL directory (no cursor, so no "More").
            if (channel != null) loadMoreVideos()
        }
    }

    /** The next page of past broadcasts (the first page when none are loaded). Best-effort. */
    fun loadMoreVideos() {
        val s = _state.value
        val userId = s.channel?.id ?: return
        if (s.videosLoading || (s.videos.isNotEmpty() && s.videoCursor == null)) return
        _state.update { it.copy(videosLoading = true) }
        viewModelScope.launch {
            runCatching { vodRepository.videosFor(userId = userId, type = VideoType.ARCHIVE, after = s.videoCursor) }
                .onSuccess { page ->
                    _state.update { it.copy(videos = it.videos + page.videos, videoCursor = page.cursor, videosLoading = false) }
                }
                .onFailure { _state.update { it.copy(videosLoading = false) } }
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
        val mutable = MutableStateFlow(SettingsUiState())
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

    fun setTvChatMode(mode: String) = viewModelScope.launch {
        settingsStore.update { it.copy(tvChatMode = mode) }
    }

    fun setTvChatOpacity(opacity: Float) = viewModelScope.launch {
        settingsStore.update { it.copy(tvChatOpacity = opacity) }
    }

    fun setPreferredQuality(quality: com.puretv.twitch.core.model.StreamQuality) = viewModelScope.launch {
        settingsStore.update { it.copy(preferredQuality = quality.name.lowercase()) }
    }

    fun setAdBlockEnabled(enabled: Boolean) = viewModelScope.launch {
        settingsStore.update { it.copy(adBlockEnabled = enabled) }
    }

    fun setLowLatency(enabled: Boolean) = viewModelScope.launch {
        settingsStore.update { it.copy(lowLatencyMode = enabled) }
    }

    fun setLiveNotifications(enabled: Boolean) = viewModelScope.launch {
        settingsStore.update { it.copy(liveNotifications = enabled) }
    }

    // PureTvTvApp hands the saved value to the player, which applies it live.
    fun setAudioNormalizer(enabled: Boolean) = viewModelScope.launch {
        settingsStore.update { it.copy(audioNormalizer = enabled) }
    }

    // Applied live the same way as the switch above.
    fun setAudioLevelling(level: AudioLevelling) = viewModelScope.launch {
        settingsStore.update { it.copy(audioLevelling = level) }
    }

    fun setTvRemoteFlip(enabled: Boolean) = viewModelScope.launch {
        settingsStore.update { it.copy(tvRemoteFlip = enabled) }
    }

    fun setAdBlockStrategy(strategy: com.puretv.twitch.core.adblock.AdBlockStrategy) = viewModelScope.launch {
        settingsStore.update { it.copy(adBlockStrategy = strategy.name.lowercase()) }
    }

    fun setProxyUrl(url: String) = viewModelScope.launch {
        settingsStore.update { it.copy(customProxyUrl = url) }
    }

    fun dismissPerChannelNote() = viewModelScope.launch {
        settingsStore.update { it.copy(perChannelNoteDismissed = true) }
    }

    fun removeAlertChannel(login: String) = viewModelScope.launch {
        settingsStore.update { it.copy(notifyChannels = GoLiveAlerts.toggled(it.notifyChannels, login)) }
    }

    /** Clears the whole go-live list (a single channel is turned off from its page or Following). */
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
 * SECTION 03.2 / 07: Twitch Device Code Grant flow, the correct 10-foot login.
 *
 * Twitch does not accept custom-scheme redirect URIs, so the authorization-code
 * + `puretv-twitch://auth` redirect approach cannot work on a TV (there is no
 * browser round-trip back into the app, and no keyboard to type credentials).
 * The device flow needs no redirect: [TvLoginScreen] shows [userCode] and
 * points the viewer at [verificationUri] (twitch.tv/activate) to enter it on a
 * phone/computer, while this VM polls until Twitch reports the code authorized,
 * then persists the session. Same flow the phone and desktop apps use.
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
                        // Persist token-first so the API client is authenticated, then
                        // look up the account identity and persist it so chat sends
                        // under the right name and Settings shows the username.
                        settingsStore.setSession(accessToken = token.accessToken, refreshToken = token.refreshToken)
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
                        _state.update {
                            it.copy(isAuthenticating = false, userCode = null, verificationUri = null, error = "Sign-in expired, please try again.")
                        }
                        return@launch
                    }
                    // Pending, or a transient null from a network hiccup: keep polling.
                    else -> {}
                }
            }
            _state.update {
                it.copy(isAuthenticating = false, userCode = null, verificationUri = null, error = "Sign-in timed out, please try again.")
            }
        }
    }

    override fun onCleared() {
        loginJob?.cancel()
        super.onCleared()
    }
}
