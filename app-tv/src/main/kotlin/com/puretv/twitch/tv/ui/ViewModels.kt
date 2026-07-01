package com.puretv.twitch.tv.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.puretv.twitch.core.adblock.AdBlockEngine
import com.puretv.twitch.core.adblock.AdBlockStatus
import com.puretv.twitch.core.api.DeviceAuth
import com.puretv.twitch.core.api.DevicePollResult
import com.puretv.twitch.core.chat.TwitchChatClient
import com.puretv.twitch.core.emotes.EmoteRepository
import com.puretv.twitch.core.model.AppSettings
import com.puretv.twitch.core.model.ChatEvent
import com.puretv.twitch.core.model.ChatMessage
import com.puretv.twitch.core.model.StreamInfo
import com.puretv.twitch.core.model.GameInfo
import com.puretv.twitch.core.repository.ChannelRepository
import com.puretv.twitch.core.repository.StreamRepository
import com.puretv.twitch.core.repository.UserRepository
import com.puretv.twitch.tv.data.AppSettingsStore
import com.puretv.twitch.tv.data.TokenRefresher
import com.puretv.twitch.tv.data.db.CachedStreamDao
import com.puretv.twitch.tv.data.db.toCachedStream
import com.puretv.twitch.tv.data.db.toStreamInfo
import io.ktor.client.HttpClient
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * SECTION 11 / 07 — ViewModels backing the TV screens. Functionally
 * identical to the phone app's `ViewModels.kt` (same `core` repositories,
 * same UI-state shape) — duplicated rather than shared because Android
 * `ViewModel`/`viewModelScope` and this app's `AppSettingsStore` are
 * platform/app-specific (Section 12.2).
 */

data class HomeUiState(
    val isLoggedIn: Boolean = false,
    val followedLive: List<StreamInfo> = emptyList(),
    val topStreams: List<StreamInfo> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
)

/**
 * Landing-screen state. Rebuilt (from the one-shot loader the TV app shipped
 * with) to match the phone app's resilience:
 *   • Cached-first paint — last session's streams show instantly from Room,
 *     so the grid is never empty on a cold, offline, or token-expired start.
 *   • Write-through — every fresh network result is persisted (24h TTL).
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
) : ViewModel() {
    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    // Once the network returns a fresh top-streams list, stop letting the cached
    // snapshot overwrite it (the cache is only for the instant first paint).
    @Volatile private var freshTopStreamsArrived = false

    // Cancel-and-replace so a resume + timer refresh landing together can't run
    // two overlapping loads that flicker isLoading against each other.
    private var refreshJob: Job? = null

    init {
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
        // Login flag + "Following — Live now" (followed channels present in the
        // top-streams set), recomputed whenever the follow set / session changes.
        viewModelScope.launch {
            combine(
                streamRepository.topStreams,
                userRepository.followedLogins,
                settings.flow,
            ) { top, follows, prefs ->
                Triple(top, follows, prefs)
            }.collect { (top, follows, prefs) ->
                _state.update {
                    it.copy(
                        isLoggedIn = prefs.accessToken.isNotBlank(),
                        followedLive = top.filter { s -> s.userLogin in follows },
                    )
                }
            }
        }
        refresh()
    }

    fun refresh() {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            _state.update { it.copy(isLoading = it.topStreams.isEmpty(), error = null) }
            val prefs = settings.flow.first()
            if (prefs.userId.isNotBlank()) runCatching { userRepository.loadFollows(prefs.userId) }
            val ok = loadTopStreamsResilient()
            _state.update {
                it.copy(
                    isLoading = false,
                    error = if (!ok && it.topStreams.isEmpty()) "Couldn't load. Check your connection and try again." else null,
                )
            }
        }
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

data class BrowseUiState(
    val games: List<GameInfo> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
)

/**
 * Category grid. Previously a one-shot load that swallowed any failure into an
 * empty list — so a transient network/token failure blanked the grid until a
 * full relaunch (the reported "categories just disappear" bug). Now it keeps the
 * last-known games on failure, surfaces an error + retry only when it has nothing
 * to show, and refreshes the token once before giving up.
 */
class BrowseViewModel(
    private val channelRepository: ChannelRepository,
    private val tokenRefresher: TokenRefresher,
) : ViewModel() {
    private val _state = MutableStateFlow(BrowseUiState())
    val state: StateFlow<BrowseUiState> = _state.asStateFlow()

    private var loadJob: Job? = null

    init { refresh() }

    fun retry() = refresh()

    fun refresh() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _state.update { it.copy(isLoading = it.games.isEmpty(), error = null) }
            loadGamesResilient()
                .onSuccess { games ->
                    // ifEmpty: a transient empty response must not wipe a good grid.
                    _state.update { it.copy(games = games.ifEmpty { it.games }, isLoading = false, error = null) }
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

    private suspend fun loadGamesResilient(): Result<List<GameInfo>> {
        val first = runCatching { channelRepository.topGames() }
        if (first.isSuccess) return first
        runCatching { tokenRefresher.refreshIfPossible() }
        return runCatching { channelRepository.topGames() }
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
    val isSearching: Boolean = false,
)

class SearchViewModel(private val channelRepository: ChannelRepository) : ViewModel() {
    private val _state = MutableStateFlow(SearchUiState())
    val state: StateFlow<SearchUiState> = _state.asStateFlow()

    fun onQueryChange(query: String) {
        _state.update { it.copy(query = query) }
        if (query.length < 2) {
            _state.update { it.copy(results = emptyList(), isSearching = false) }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isSearching = true) }
            val results = runCatching { channelRepository.search(query) }.getOrDefault(emptyList())
            _state.update { it.copy(results = results, isSearching = false) }
        }
    }
}

data class StreamUiState(
    val channel: com.puretv.twitch.core.model.ChannelInfo? = null,
    val streamInfo: StreamInfo? = null,
    val playableUrl: String? = null,
    val adBlockStatus: AdBlockStatus = AdBlockStatus.UNKNOWN,
    val chatMessages: List<ChatMessage> = emptyList(),
    val isLoading: Boolean = true,
)

class StreamViewModel(
    private val channelLogin: String,
    private val streamRepository: StreamRepository,
    private val channelRepository: ChannelRepository,
    private val chatClient: TwitchChatClient,
    private val emoteRepository: EmoteRepository,
    private val adBlockEngine: AdBlockEngine,
    private val settingsStore: AppSettingsStore,
) : ViewModel() {
    private val _state = MutableStateFlow(StreamUiState())
    val state: StateFlow<StreamUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val channel = runCatching { channelRepository.getChannel(channelLogin) }.getOrNull()
            val liveInfo = runCatching {
                streamRepository.streamsForChannels(listOf(channelLogin)).firstOrNull()
            }.getOrNull()
            _state.update { it.copy(channel = channel, streamInfo = liveInfo, isLoading = false) }

            runCatching { streamRepository.resolvePlayableStream(channelLogin) }
                .onSuccess { playable ->
                    _state.update { it.copy(playableUrl = playable.masterUrl) }
                }

            runCatching { emoteRepository.loadGlobalEmotes() }
            channel?.let { runCatching { emoteRepository.loadChannelEmotes(it.id, it.login) } }
        }
        viewModelScope.launch {
            adBlockEngine.status.collect { status -> _state.update { it.copy(adBlockStatus = status) } }
        }
        viewModelScope.launch {
            // Section 7.4 — TV viewers are overwhelmingly unauthenticated/lean-back;
            // fall back to the anonymous `justinfanNNNN` IRC identity exactly like
            // the phone app when no OAuth session is present (Section 5.1).
            val settings = settingsStore.flow.first()
            val token = settings.accessToken.takeIf { it.isNotBlank() }
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

    fun sendChatMessage(text: String) = viewModelScope.launch {
        chatClient.sendMessage(channelLogin, text)
    }

    override fun onCleared() {
        super.onCleared()
        chatClient.disconnect()
    }
}

data class ChannelUiState(
    val channel: com.puretv.twitch.core.model.ChannelInfo? = null,
    val isLive: Boolean = false,
    val viewerCount: Long = 0L,
)

class ChannelViewModel(
    private val channelLogin: String,
    private val channelRepository: ChannelRepository,
    private val streamRepository: StreamRepository,
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

    fun setPreferredQuality(quality: com.puretv.twitch.core.model.StreamQuality) = viewModelScope.launch {
        settingsStore.update { it.copy(preferredQuality = quality.name.lowercase()) }
    }

    fun setAdBlockEnabled(enabled: Boolean) = viewModelScope.launch {
        settingsStore.update { it.copy(adBlockEnabled = enabled) }
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
