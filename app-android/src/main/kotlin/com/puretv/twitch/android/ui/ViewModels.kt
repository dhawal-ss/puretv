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
import com.puretv.twitch.android.data.db.WatchHistoryDao
import com.puretv.twitch.android.data.db.WatchHistoryEntry
import com.puretv.twitch.android.data.db.toCachedStream
import com.puretv.twitch.android.data.db.toStreamInfo
import com.puretv.twitch.android.player.TwitchPlayer
import com.puretv.twitch.core.adblock.AdBlockEngine
import com.puretv.twitch.core.adblock.AdBlockStatus
import com.puretv.twitch.core.api.DeviceAuth
import com.puretv.twitch.core.api.DevicePollResult
import com.puretv.twitch.core.api.TwitchConfig
import com.puretv.twitch.core.chat.TwitchChatClient
import com.puretv.twitch.core.emotes.EmoteRepository
import com.puretv.twitch.core.model.AppSettings
import com.puretv.twitch.core.model.ChatEvent
import com.puretv.twitch.core.model.ChatMessage
import com.puretv.twitch.core.model.StreamInfo
import com.puretv.twitch.core.repository.ChannelRepository
import com.puretv.twitch.core.repository.StreamRepository
import com.puretv.twitch.core.repository.UserRepository
import com.puretv.twitch.core.session.SessionState
import io.ktor.client.HttpClient
import kotlin.jvm.Volatile
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * SECTION 11 — ViewModels backing the phone/tablet screens. Each pulls
 * shared business logic from `core` repositories/clients (Sections 3–5)
 * and exposes simple immutable UI state via [StateFlow].
 */

data class HomeUiState(
    val isLoggedIn: Boolean = false,
    val followedLive: List<StreamInfo> = emptyList(),
    val games: List<com.puretv.twitch.core.model.GameInfo> = emptyList(),
    val topStreams: List<StreamInfo> = emptyList(),
    val continueWatching: List<WatchHistoryEntry> = emptyList(),
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
) : ViewModel() {
    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    // Once the network returns a fresh top-streams list, stop letting the cached
    // snapshot overwrite it (the cache is only for the instant first paint).
    // @Volatile documents that every collector mutates this on the main
    // dispatcher; it keeps the contract correct if a collector ever moves off it.
    @Volatile private var freshTopStreamsArrived = false

    init {
        // "Continue watching": most recently opened channels, newest first.
        viewModelScope.launch {
            watchHistoryDao.observeRecent().collect { history ->
                _state.update { it.copy(continueWatching = history) }
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
                    is SessionState.LoggedIn ->
                        loadLoggedInHome()
                }
            }
        }
        // Followed "Live now": re-fetch whenever the follow set changes.
        viewModelScope.launch {
            userRepository.followedLogins.collect { follows ->
                val live = if (follows.isEmpty()) emptyList()
                else runCatching { streamRepository.streamsForChannels(follows.take(100)) }.getOrDefault(emptyList())
                _state.update { it.copy(followedLive = live) }
            }
        }
    }

    private suspend fun loadLoggedInHome() {
        _state.update { it.copy(isLoggedIn = true, isLoading = it.topStreams.isEmpty(), error = null) }
        val gamesOk = runCatching { channelRepository.topGames() }
            .onSuccess { g -> _state.update { it.copy(games = g) } }.isSuccess
        val topOk = runCatching { streamRepository.refreshTopStreams() }.isSuccess
        runCatching { userRepository.loadFollowsForCurrentUser() }
        val failed = !gamesOk && !topOk && _state.value.topStreams.isEmpty()
        _state.update {
            it.copy(isLoading = false, error = if (failed) "Couldn't load. Check your connection and try again." else null)
        }
    }

    fun refresh() = viewModelScope.launch {
        _state.update { it.copy(isLoading = true, error = null) }
        val ok = runCatching { streamRepository.refreshTopStreams() }.isSuccess
        runCatching { channelRepository.topGames() }.onSuccess { g -> _state.update { it.copy(games = g) } }
        _state.update {
            it.copy(isLoading = false, error = if (!ok && it.topStreams.isEmpty()) "Couldn't refresh. Check your connection." else null)
        }
    }
}

data class BrowseUiState(
    val games: List<com.puretv.twitch.core.model.GameInfo> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
)

class BrowseViewModel(private val channelRepository: ChannelRepository) : ViewModel() {
    private val _state = MutableStateFlow(BrowseUiState())
    val state: StateFlow<BrowseUiState> = _state.asStateFlow()

    init { load() }

    fun retry() = load()

    private fun load() = viewModelScope.launch {
        _state.update { it.copy(isLoading = true, error = null) }
        runCatching { channelRepository.topGames() }
            .onSuccess { games -> _state.update { it.copy(games = games, isLoading = false) } }
            .onFailure { e -> _state.update { it.copy(isLoading = false, error = e.message ?: "Couldn't load categories.") } }
    }
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
    val results: List<com.puretv.twitch.core.api.ChannelSearchResult> = emptyList(),
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
            _state.update { it.copy(results = emptyList(), isSearching = false, error = null) }
            return
        }
        searchJob = viewModelScope.launch {
            _state.update { it.copy(isSearching = true, error = null) }
            delay(300) // debounce: wait for typing to settle before hitting the network
            runCatching { channelRepository.search(query) }
                .onSuccess { results -> _state.update { it.copy(results = results, isSearching = false) } }
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

data class StreamUiState(
    val channel: com.puretv.twitch.core.model.ChannelInfo? = null,
    val streamInfo: StreamInfo? = null,
    val playableUrl: String? = null,
    val adBlockStatus: AdBlockStatus = AdBlockStatus.UNKNOWN,
    val chatMessages: List<ChatMessage> = emptyList(),
    val chatFraction: Float = 0.5f,
    val chatEnabled: Boolean = true,
    val emotes: Map<String, String> = emptyMap(),
    val isLoading: Boolean = true,
    val playbackError: String? = null,
    val isLoggedIn: Boolean = false,
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
) : ViewModel() {
    private val _state = MutableStateFlow(StreamUiState())
    val state: StateFlow<StreamUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val channel = runCatching { channelRepository.getChannel(channelLogin) }.getOrNull()
            val liveInfo = runCatching { streamRepository.streamsForChannels(listOf(channelLogin)).firstOrNull() }.getOrNull()
            _state.update { it.copy(channel = channel, streamInfo = liveInfo, isLoading = false) }

            // Record this open for the Home "Continue watching" rail.
            channel?.let { ch ->
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

            resolvePlayable()

            val global = runCatching { emoteRepository.loadGlobalEmotes() }.getOrDefault(emptyList())
            val channelEmotes = channel?.let {
                runCatching { emoteRepository.loadChannelEmotes(it.id, it.login) }.getOrDefault(emptyList())
            } ?: emptyList()
            _state.update { s -> s.copy(emotes = (global + channelEmotes).associate { it.name to it.url }) }
        }
        viewModelScope.launch {
            adBlockEngine.status.collect { status -> _state.update { it.copy(adBlockStatus = status) } }
        }
        viewModelScope.launch {
            // Anonymous (read-only) IRC login when the user hasn't authenticated yet —
            // `TwitchChatClient` falls back to a `justinfanNNNN` identity for `null`.
            // Sending requires a real OAuth token (see Section 5.1).
            val settings = settingsStore.flow.first()
            _state.update { it.copy(chatFraction = settings.chatFraction, chatEnabled = settings.chatEnabled) }
            val token = settings.accessToken.takeIf { it.isNotBlank() }
            // Anonymous IRC can read but not send; gate the composer on a real token.
            _state.update { it.copy(isLoggedIn = token != null) }
            val username = settings.username.takeIf { it.isNotBlank() }
            chatClient.connect(channelLogin, token, username)
            chatClient.events.collect { event ->
                if (event is ChatEvent.Message) {
                    _state.update { current ->
                        current.copy(chatMessages = (current.chatMessages + event.message).takeLast(200))
                    }
                }
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
        runCatching { streamRepository.resolvePlayableStream(channelLogin) }
            .onSuccess { playable -> _state.update { it.copy(playableUrl = playable.masterUrl, playbackError = null) } }
            .onFailure { e -> _state.update { it.copy(playbackError = e.message ?: "Could not load this stream. It may be offline.") } }
    }

    fun retry() = viewModelScope.launch { resolvePlayable() }

    fun sendChatMessage(text: String) = viewModelScope.launch {
        chatClient.sendMessage(channelLogin, text)
    }

    fun setChatFraction(fraction: Float) = viewModelScope.launch {
        settingsStore.update { it.copy(chatFraction = fraction) }
    }

    fun setChatEnabled(enabled: Boolean) = viewModelScope.launch {
        settingsStore.update { it.copy(chatEnabled = enabled) }
    }

    override fun onCleared() {
        super.onCleared()
        chatClient.disconnect()
        // Teardown lives here, not in PlayerSurface.onDispose: onCleared fires only
        // on a real back-stack pop (leaving the stream), not on rotation or PiP,
        // so playback is not torn down and re-buffered on a config change.
        twitchPlayer.exoPlayer.stop()
        twitchPlayer.exoPlayer.clearMediaItems()
    }
}

data class ChannelUiState(
    val channel: com.puretv.twitch.core.model.ChannelInfo? = null,
    val liveStream: StreamInfo? = null,
    val isLive: Boolean = false,
    val isLoading: Boolean = true,
    val error: String? = null,
)

class ChannelViewModel(
    private val channelLogin: String,
    private val channelRepository: ChannelRepository,
    private val streamRepository: StreamRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(ChannelUiState())
    val state: StateFlow<ChannelUiState> = _state.asStateFlow()

    init { load() }

    fun retry() = load()

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

    fun setPreferredQuality(quality: com.puretv.twitch.core.model.StreamQuality) = viewModelScope.launch {
        settingsStore.update { it.copy(preferredQuality = quality.name.lowercase()) }
    }

    fun setAdBlockEnabled(enabled: Boolean) = viewModelScope.launch {
        settingsStore.update { it.copy(adBlockEnabled = enabled) }
    }

    fun setAnimateEmotes(enabled: Boolean) = viewModelScope.launch {
        settingsStore.update { it.copy(animateEmotes = enabled) }
    }

    fun setAdBlockStrategy(strategy: com.puretv.twitch.core.adblock.AdBlockStrategy) = viewModelScope.launch {
        settingsStore.update { it.copy(adBlockStrategy = strategy.name.lowercase()) }
    }

    fun setProxyUrl(url: String) = viewModelScope.launch {
        settingsStore.update { it.copy(customProxyUrl = url) }
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
    val liveFollows: List<StreamInfo> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
)

/**
 * Backs the Following tab. Observes the session: when logged in it loads the
 * follow set and shows the channels that are live right now; when logged out it
 * shows a connect prompt. Reuses the shared UserRepository follow cache, so it
 * stays in sync with Home's "Live now" rail.
 */
class FollowingViewModel(
    private val userRepository: UserRepository,
    private val streamRepository: StreamRepository,
    private val sessionManager: SessionManager,
) : ViewModel() {
    private val _state = MutableStateFlow(FollowingUiState())
    val state: StateFlow<FollowingUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            sessionManager.state.collect { session ->
                when (session) {
                    is SessionState.LoggedOut -> _state.update { FollowingUiState(isLoggedIn = false) }
                    is SessionState.LoggedIn -> {
                        _state.update { it.copy(isLoggedIn = true, isLoading = it.liveFollows.isEmpty(), error = null) }
                        runCatching { userRepository.loadFollowsForCurrentUser() }
                    }
                }
            }
        }
        viewModelScope.launch {
            userRepository.followedLogins.collect { follows ->
                val live = if (follows.isEmpty()) emptyList()
                else runCatching { streamRepository.streamsForChannels(follows.take(100)) }.getOrDefault(emptyList())
                _state.update { it.copy(liveFollows = live, isLoading = false) }
            }
        }
    }

    fun refresh() = viewModelScope.launch {
        _state.update { it.copy(isLoading = true, error = null) }
        runCatching { userRepository.loadFollowsForCurrentUser() }
        _state.update { it.copy(isLoading = false) }
    }
}

/**
 * Backs the Welcome screen's decorative blurred backdrop: the thumbnails of last
 * session's cached streams (empty on a true first launch, where Welcome falls
 * back to a branded gradient). Connect itself is handled by LoginViewModel.
 */
class WelcomeViewModel(cachedStreamDao: CachedStreamDao) : ViewModel() {
    val thumbnails: StateFlow<List<String>> = cachedStreamDao.observeAll()
        .map { rows -> rows.mapNotNull { it.thumbnailUrl.ifBlank { null } }.take(9) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}
