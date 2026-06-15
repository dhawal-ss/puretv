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
import com.puretv.twitch.core.chat.TwitchChatClient
import com.puretv.twitch.core.di.TokenHolder
import com.puretv.twitch.core.emotes.EmoteRepository
import com.puretv.twitch.core.model.AppSettings
import com.puretv.twitch.core.model.ChannelInfo
import com.puretv.twitch.core.model.ChatEvent
import com.puretv.twitch.core.model.ChatMessage
import com.puretv.twitch.core.model.GameInfo
import com.puretv.twitch.core.model.StreamInfo
import com.puretv.twitch.core.model.StreamQuality
import com.puretv.twitch.core.repository.ChannelRepository
import com.puretv.twitch.core.repository.StreamRepository
import com.puretv.twitch.desktop.auth.DesktopOAuthManager
import com.puretv.twitch.desktop.data.DesktopSettingsStore
import com.puretv.twitch.desktop.data.FollowStore
import com.puretv.twitch.desktop.data.FollowedChannel
import com.puretv.twitch.desktop.player.LocalStreamProxy
import com.puretv.twitch.desktop.player.VlcPlayer
import io.ktor.client.HttpClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * SECTION 11 (desktop) — same shape as the phone/TV ViewModels (Section 11),
 * adapted to [DesktopViewModel] (no Android lifecycle) and to desktop-only
 * collaborators: [VlcPlayer]/[LocalStreamProxy] for playback and
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
    val isLoading: Boolean = false,
)

class HomeViewModel(
    private val streamRepository: StreamRepository,
    private val followStore: FollowStore,
    private val settingsStore: DesktopSettingsStore,
) : DesktopViewModel() {
    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    // Live status of the locally-followed channels. Refreshed when the followed
    // set changes and on `refresh()`. `getStreams` returns only LIVE channels,
    // so anything saved-but-absent here renders as offline.
    private val followedLive = MutableStateFlow<List<StreamInfo>>(emptyList())

    init {
        scope.launch {
            _state.update { it.copy(isLoading = true) }
            runCatching { streamRepository.refreshTopStreams() }
            refreshFollowedLive()
            _state.update { it.copy(isLoading = false) }
        }
        scope.launch {
            combine(
                streamRepository.topStreams,
                followStore.followed,
                followedLive,
            ) { top, followed, live -> Triple(top, followed, live) }
                .collect { (top, followed, live) ->
                    _state.update {
                        it.copy(
                            isLoggedIn = settingsStore.loadTokens() != null,
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
    }

    private suspend fun refreshFollowedLive() {
        val logins = followStore.followed.value.map { it.login }
        followedLive.value =
            if (logins.isEmpty()) emptyList()
            else runCatching { streamRepository.streamsForChannels(logins) }.getOrDefault(emptyList())
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

    fun refresh() = scope.launch {
        runCatching { streamRepository.refreshTopStreams() }
        refreshFollowedLive()
    }
}

// ---- Browse ------------------------------------------------------------------

data class BrowseUiState(val games: List<GameInfo> = emptyList())

class BrowseViewModel(private val channelRepository: ChannelRepository) : DesktopViewModel() {
    private val _state = MutableStateFlow(BrowseUiState())
    val state: StateFlow<BrowseUiState> = _state.asStateFlow()

    init {
        scope.launch {
            runCatching { channelRepository.topGames() }
                .onSuccess { games -> _state.update { it.copy(games = games) } }
        }
    }
}

// ---- Category (live streams within a game) -----------------------------------

data class CategoryUiState(
    val gameName: String = "",
    val streams: List<StreamInfo> = emptyList(),
    val isLoading: Boolean = true,
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

    init {
        scope.launch {
            val streams = runCatching { streamRepository.streamsForGame(gameId) }.getOrDefault(emptyList())
            _state.update { it.copy(streams = streams, isLoading = false) }
        }
    }
}

// ---- Search ------------------------------------------------------------------

data class SearchUiState(
    val query: String = "",
    val results: List<ChannelSearchResult> = emptyList(),
    val isSearching: Boolean = false,
)

class SearchViewModel(private val channelRepository: ChannelRepository) : DesktopViewModel() {
    private val _state = MutableStateFlow(SearchUiState())
    val state: StateFlow<SearchUiState> = _state.asStateFlow()

    fun onQueryChange(query: String) {
        _state.update { it.copy(query = query) }
        if (query.length < 2) {
            _state.update { it.copy(results = emptyList(), isSearching = false) }
            return
        }
        scope.launch {
            _state.update { it.copy(isSearching = true) }
            val results = runCatching { channelRepository.search(query) }.getOrDefault(emptyList())
            _state.update { it.copy(results = results, isSearching = false) }
        }
    }
}

// ---- Stream (player + chat) ---------------------------------------------------

data class StreamUiState(
    val channel: ChannelInfo? = null,
    val streamInfo: StreamInfo? = null,
    val playableUrl: String? = null,
    val currentQuality: StreamQuality = StreamQuality.AUTO,
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
    private val settingsStore: DesktopSettingsStore,
    private val vlcPlayer: VlcPlayer,
    private val localStreamProxy: LocalStreamProxy,
    private val followStore: FollowStore,
) : DesktopViewModel() {
    private val _state = MutableStateFlow(StreamUiState())
    val state: StateFlow<StreamUiState> = _state.asStateFlow()

    val isFollowed: StateFlow<Boolean> = followStore.followed
        .map { list -> list.any { it.login.equals(channelLogin, ignoreCase = true) } }
        .stateIn(scope, SharingStarted.Eagerly, followStore.isFollowed(channelLogin))

    init {
        scope.launch {
            // SECTION 08.3 — VLC talks to our local proxy (port 7979), never
            // straight to Twitch's CDN: that's the choke point where every
            // ad-block strategy in `core` gets applied uniformly (Section 4).
            localStreamProxy.start()

            val channel = runCatching { channelRepository.getChannel(channelLogin) }.getOrNull()
            val liveInfo = runCatching {
                streamRepository.streamsForChannels(listOf(channelLogin)).firstOrNull()
            }.getOrNull()
            val preferredQuality = runCatching {
                StreamQuality.valueOf(settingsStore.settings.value.preferredQuality.uppercase())
            }.getOrDefault(StreamQuality.AUTO)

            _state.update {
                it.copy(channel = channel, streamInfo = liveInfo, currentQuality = preferredQuality, isLoading = false)
            }

            playAt(preferredQuality)

            runCatching { emoteRepository.loadGlobalEmotes() }
            channel?.let { runCatching { emoteRepository.loadChannelEmotes(it.id, it.login) } }
        }
        scope.launch {
            adBlockEngine.status.collect { status -> _state.update { it.copy(adBlockStatus = status) } }
        }
        scope.launch {
            // Anonymous (read-only) IRC identity unless we have BOTH a token
            // and the resolved login of the token's owner — Twitch IRC will
            // drop the connection if PASS/NICK don't match.
            val saved = settingsStore.loadTokens()
            chatClient.connect(channelLogin, saved?.accessToken, saved?.login)
            chatClient.events.collect { event ->
                if (event is ChatEvent.Message) {
                    _state.update { current ->
                        current.copy(chatMessages = (current.chatMessages + event.message).takeLast(200))
                    }
                }
            }
        }
    }

    private fun playAt(quality: StreamQuality) {
        val url = LocalStreamProxy.streamUrl(channelLogin, quality)
        _state.update { it.copy(playableUrl = url, currentQuality = quality) }
        vlcPlayer.play(url)
    }

    fun setQuality(quality: StreamQuality) = playAt(quality)

    fun togglePlayPause() = vlcPlayer.togglePlayPause()

    fun setVolume(volume: Int) = vlcPlayer.setVolume(volume)

    fun toggleMute() = vlcPlayer.toggleMute()

    fun sendChatMessage(text: String) = scope.launch {
        chatClient.sendMessage(channelLogin, text)
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
        vlcPlayer.stop()
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

class SettingsViewModel(private val settingsStore: DesktopSettingsStore) : DesktopViewModel() {
    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    init {
        scope.launch {
            settingsStore.settings.collect { settings ->
                val tokens = settingsStore.loadTokens()
                _state.update {
                    it.copy(
                        settings = settings,
                        isLoggedIn = tokens != null,
                        loginUsername = tokens?.login,
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
            // Best-effort pre-fill; the activate page works with or without it.
            oauthManager.openInBrowser("${device.verificationUri}?public_code=${device.userCode}")
            _state.update { it.copy(userCode = device.userCode, verificationUri = device.verificationUri) }
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
        runCatching {
            // ORDERING IS LOAD-BEARING: push the token before the first authed call.
            tokenHolder.update(token.accessToken)
            val me = apiClient.getUsers().firstOrNull()
            val expiresAt = System.currentTimeMillis() / 1000 + token.expiresInSeconds
            settingsStore.saveTokens(
                accessToken = token.accessToken,
                refreshToken = token.refreshToken,
                expiresAtEpochSeconds = expiresAt,
                userId = me?.id,
                login = me?.login,
            )
        }.onSuccess {
            _state.update { it.copy(isAuthenticating = false, userCode = null, isLoggedIn = true) }
        }.onFailure { e ->
            _state.update { it.copy(isAuthenticating = false, userCode = null, error = e.message ?: "Login failed") }
        }
    }
}
