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
import com.puretv.twitch.core.chat.BadgeIndex
import com.puretv.twitch.core.chat.BadgeRepository
import com.puretv.twitch.core.chat.TwitchChatClient
import com.puretv.twitch.core.di.TokenHolder
import com.puretv.twitch.core.emotes.EmoteRepository
import com.puretv.twitch.core.emotes.PickableEmote
import com.puretv.twitch.core.emotes.ResolvedEmote
import com.puretv.twitch.core.emotes.SevenTvEmoteDelta
import com.puretv.twitch.core.emotes.SevenTvEventClient
import com.puretv.twitch.core.emotes.buildEmoteIndex
import com.puretv.twitch.core.emotes.applyThirdPartyEmotes
import com.puretv.twitch.core.emotes.buildPickableEmotes
import com.puretv.twitch.core.emotes.prioritizeThirdParty
import com.puretv.twitch.core.model.ChannelEmote
import com.puretv.twitch.core.model.EmoteProvider
import com.puretv.twitch.core.model.AppSettings
import com.puretv.twitch.core.model.Badge
import com.puretv.twitch.core.model.ChannelInfo
import com.puretv.twitch.core.model.ChatEvent
import com.puretv.twitch.core.model.ChatMessage
import com.puretv.twitch.core.model.MessagePart
import com.puretv.twitch.core.model.GameInfo
import com.puretv.twitch.core.model.StreamInfo
import com.puretv.twitch.core.model.StreamQuality
import com.puretv.twitch.core.model.PlaybackBackend
import com.puretv.twitch.core.model.UpscalingMode
import com.puretv.twitch.core.repository.ChannelRepository
import com.puretv.twitch.core.repository.StreamRepository
import com.puretv.twitch.desktop.auth.DesktopOAuthManager
import com.puretv.twitch.desktop.data.DesktopSettingsStore
import com.puretv.twitch.desktop.data.FollowStore
import com.puretv.twitch.desktop.data.FollowedChannel
import com.puretv.twitch.desktop.player.LocalStreamProxy
import com.puretv.twitch.desktop.player.ProxyUnavailableException
import com.puretv.twitch.desktop.player.DesktopPlayer
import com.puretv.twitch.desktop.ui.chat.ChatModeration
import com.puretv.twitch.desktop.ui.chat.buildSelfEcho
import io.ktor.client.HttpClient
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
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
                            // Audit U9: in-memory flag, not a per-emission token decrypt.
                            isLoggedIn = settingsStore.isLoggedIn,
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
                .onFailure { _state.update { it.copy(isLoading = false, error = "Couldn't load categories. Check your connection and try again.") } }
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
                .onFailure { _state.update { it.copy(isLoading = false, error = "Couldn't load this category. Check your connection and try again.") } }
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
                .onFailure { _state.update { it.copy(results = emptyList(), isSearching = false, error = "Search failed. Check your connection and try again.") } }
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
    val emotes: List<PickableEmote> = emptyList(),
    /** Resolved chat badge art (global ∪ channel) for rendering real badge icons. */
    val badges: BadgeIndex = BadgeIndex.EMPTY,
    /** True only when we hold both a token and the token-owner's login (can speak). */
    val canChat: Boolean = false,
    /** The message the composer is currently replying to, if any. */
    val replyingTo: ChatMessage? = null,
    val isLoading: Boolean = true,
    /** A stream-level fatal error (e.g. the local proxy port is in use). Distinct from
     *  a transient player-engine error; shown in place of the video with priority. */
    val fatalError: String? = null,
)

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
) : DesktopViewModel() {
    private val _state = MutableStateFlow(StreamUiState())
    val state: StateFlow<StreamUiState> = _state.asStateFlow()

    // Identity for the optimistic local echo of our own sent messages — Twitch
    // never echoes your own PRIVMSG back. Captured from USERSTATE/GLOBALUSERSTATE.
    private var selfLogin: String? = null
    private var selfDisplayName: String? = null
    private var selfColor: String? = null
    private var selfBadges: List<Badge> = emptyList()
    private var echoCounter = 0
    private var systemCounter = 0

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

            // Assemble the unified pickable-emote list: third-party (BTTV/FFZ/7TV)
            // globals + first-party Twitch globals, then the channel-specific sets.
            // Each fetch is best-effort so one provider failing doesn't sink the rest.
            tpGlobalEmotes = runCatching { emoteRepository.loadGlobalEmotes() }.getOrDefault(emptyList())
            twitchGlobalEmotes = runCatching { apiClient.getGlobalEmotes() }.getOrDefault(emptyList())
            // Resolve globals immediately (before channel emotes load): incoming gets
            // third-party globals; self-echo additionally gets first-party Twitch
            // globals so a typed emote in your own message renders right away. Honor the
            // provider toggles/priority here too so a disabled provider never flashes in.
            val globEnabled = enabledThirdPartyProviders()
            val tpGlobFiltered = prioritizeThirdParty(tpGlobalEmotes, globEnabled)
            emoteIndex = buildEmoteIndex(emptyList(), tpGlobFiltered)
            selfEchoIndex = buildEmoteIndex(emptyList(), tpGlobFiltered, emptyList(), twitchGlobalEmotes)
            channel?.let { ch ->
                tpChannelEmotes = runCatching { emoteRepository.loadChannelEmotes(ch.id, ch.login) }.getOrDefault(emptyList())
                twitchChannelEmotes = runCatching { apiClient.getChannelTwitchEmotes(ch.id) }.getOrDefault(emptyList())
                // Build indices + picker from the now-complete source lists and re-map
                // any messages that arrived before emotes finished loading.
                rebuildEmoteIndicesAndPublish()
                // Subscribe to live 7TV emote add/remove for this channel (EventAPI).
                sevenTvEventClient.connect(ch.id)
                // Real badge art (global ∪ channel). Best-effort: failure leaves text-free rows.
                val badgeIndex = runCatching { badgeRepository.loadForChannel(ch.id) }.getOrDefault(BadgeIndex.EMPTY)
                _state.update { it.copy(badges = badgeIndex) }
            }
        }
        // Live 7TV emote updates: fold each delta into the channel set and rebuild.
        scope.launch {
            sevenTvEventClient.updates.collect { applyEmoteDelta(it) }
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
            adBlockEngine.status.collect { status -> _state.update { it.copy(adBlockStatus = status) } }
        }
        scope.launch {
            // Anonymous (read-only) IRC identity unless we have BOTH a token
            // and the resolved login of the token's owner — Twitch IRC will
            // drop the connection if PASS/NICK don't match. Guard the decrypt so
            // a token-store read error degrades to anonymous chat instead of
            // silently killing the whole chat coroutine (audit U5).
            val saved = runCatching { settingsStore.loadTokens() }.getOrNull()
            selfLogin = saved?.login
            _state.update { it.copy(canChat = saved?.accessToken != null && saved.login != null) }
            chatClient.connect(channelLogin, saved?.accessToken, saved?.login)
            chatClient.events.collect { event ->
                when (event) {
                    is ChatEvent.Message -> _state.update { current ->
                        val mapped = event.message.copy(
                            parsedParts = applyThirdPartyEmotes(event.message.parsedParts, emoteIndex),
                            mentionsSelf = mentionsSelf(event.message.message),
                        )
                        current.copy(
                            chatMessages = current.chatMessages.appendCapped(mapped),
                            mentionMessages = if (mapped.mentionsSelf) {
                                current.mentionMessages.appendCapped(mapped)
                            } else current.mentionMessages,
                        )
                    }
                    is ChatEvent.SelfState -> {
                        event.displayName.ifBlank { null }?.let { selfDisplayName = it }
                        selfColor = event.color
                        selfBadges = event.badges
                    }
                    // Moderator timed out/banned a user (targetUser set) or cleared the
                    // whole room (targetUser null). Twitch removes the affected history,
                    // so tombstone matching messages rather than deleting the rows —
                    // Chatterino-style "<message deleted>" keeps the conversation legible.
                    is ChatEvent.ClearChat -> _state.update { current ->
                        val target = event.targetUser
                        if (target == null) {
                            current.copy(
                                chatMessages = current.chatMessages
                                    .appendCapped(systemMessage("Chat cleared by a moderator")),
                            )
                        } else {
                            current.copy(chatMessages = ChatModeration.markUserDeleted(current.chatMessages, target))
                        }
                    }
                    is ChatEvent.ClearMessage -> _state.update { current ->
                        current.copy(chatMessages = ChatModeration.markMessageDeleted(current.chatMessages, event.targetMessageId))
                    }
                    // Subs, resubs, raids, gifts: Twitch hands us a ready-made
                    // human-readable system line. Render it as a centred system row.
                    is ChatEvent.UserNotice -> event.systemMessage.ifBlank { null }?.let { sys ->
                        _state.update { it.copy(chatMessages = it.chatMessages.appendCapped(systemMessage(sys))) }
                    }
                    // RoomState/ConnectionState are parsed in core but not surfaced in
                    // this panel yet (no slow-mode / connection banner). Intentionally ignored.
                    is ChatEvent.RoomState, is ChatEvent.ConnectionState -> {}
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

    /** Live-apply the scaler to the running player AND persist it. The whole point:
     *  it changes the picture mid-stream, not just on restart. */
    fun setUpscaling(mode: UpscalingMode) {
        vlcPlayer.setUpscaling(mode)
        settingsStore.updateSettings { it.copy(upscalingMode = mode) }
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
        // Optimistic local echo — Twitch never sends our own message back to us,
        // so render it ourselves or the sender never sees what they typed.
        // Echo ONLY what actually went on the wire: buildPrivmsgLine truncates the
        // body to MAX_CHAT_MESSAGE_LENGTH, so clamp the echo to the same limit or the
        // sender sees more than was sent (M8). CRLF is neutralised to spaces on the
        // wire too; the composer sends on Enter so newlines don't reach here, but
        // mirror it so the echo can never diverge from the delivered text.
        val wireText = text.replace('\r', ' ').replace('\n', ' ').take(MAX_CHAT_MESSAGE_LENGTH)
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
        )
        _state.update {
            it.copy(chatMessages = it.chatMessages.appendCapped(echo), replyingTo = null)
        }
    }

    /**
     * Append [msg] keeping the newest [max] messages. At steady state (list already at cap)
     * this allocates ONE list of [max] rather than the `(list + msg).takeLast(max)` pattern's
     * two (an n+1 temp, then the capped copy) — less per-message GC churn in a fast chat.
     */
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
     * Fold a live 7TV EventAPI delta into the channel emote set, then rebuild.
     * Removals match 7TV emotes by name; additions are de-duped against existing 7TV
     * names so a repeated push can't double-insert. No-ops (no rebuild) when the delta
     * changes nothing. Note: a removed emote already rendered in an OLD message stays
     * as-is — re-tokenizing only re-scans text, it doesn't revert existing emote parts.
     */
    private fun applyEmoteDelta(delta: SevenTvEmoteDelta) {
        val removedNames = delta.removed.mapTo(HashSet()) { it.name }
        val afterRemoval =
            if (removedNames.isEmpty()) tpChannelEmotes
            else tpChannelEmotes.filterNot { it.provider == EmoteProvider.SEVENTV && it.name in removedNames }
        val present7tv = afterRemoval.filterTo(HashSet()) { it.provider == EmoteProvider.SEVENTV }.mapTo(HashSet()) { it.name }
        val additions = delta.added.filter { it.name.isNotBlank() && present7tv.add(it.name) }
        val changed = afterRemoval.size != tpChannelEmotes.size || additions.isNotEmpty()
        if (!changed) return
        tpChannelEmotes = afterRemoval + additions
        rebuildEmoteIndicesAndPublish()
    }

    /** True when [body] @-mentions the local viewer. Viewer-relative, so it is
     *  evaluated here where [selfLogin] is known rather than in the row. */
    private fun mentionsSelf(body: String): Boolean = ChatModeration.mentionsSelf(body, selfLogin)

    /** Build a synthetic, non-user system row (sub/raid notices, "chat cleared"). */
    private fun systemMessage(text: String): ChatMessage = ChatMessage(
        id = "sys-" + (systemCounter++).toString(),
        channel = channelLogin,
        username = "",
        displayName = "",
        color = "",
        message = text,
        parsedParts = listOf(MessagePart.Text(text)),
        badges = emptyList(),
        timestamp = System.currentTimeMillis(),
        isSubscriber = false,
        isModerator = false,
        isBroadcaster = false,
        isSystem = true,
    )

    private fun List<ChatMessage>.appendCapped(msg: ChatMessage, max: Int = 200): List<ChatMessage> =
        if (size < max) this + msg
        else ArrayList<ChatMessage>(max).also {
            it.addAll(subList(size - (max - 1), size))
            it.add(msg)
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
        vlcPlayer.stop()
    }

    private companion object {
        // Twitch (and core's buildPrivmsgLine) hard-cap the wire body at 500 chars.
        // Mirrored here so the optimistic echo never shows more than was sent (M8);
        // kept in sync with core's internal MAX_CHAT_MESSAGE_LENGTH, which isn't
        // visible across the module boundary.
        private const val MAX_CHAT_MESSAGE_LENGTH = 500
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
