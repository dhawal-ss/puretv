package com.puretv.twitch.desktop.ui

import com.puretv.twitch.core.adblock.AdBlockEngine
import com.puretv.twitch.core.adblock.AdBlockStatus
import com.puretv.twitch.core.api.TwitchApiClient
import com.puretv.twitch.core.chat.BadgeRepository
import com.puretv.twitch.core.chat.TwitchChatClient
import com.puretv.twitch.core.emotes.EmoteRepository
import com.puretv.twitch.core.model.ChatMessage
import com.puretv.twitch.core.model.StreamQuality
import com.puretv.twitch.core.repository.ChannelRepository
import com.puretv.twitch.core.stream.PlayableStreamCache
import com.puretv.twitch.core.stream.Failure
import com.puretv.twitch.core.stream.FailureKind
import com.puretv.twitch.desktop.data.DesktopSettingsStore
import com.puretv.twitch.desktop.notify.DesktopLiveNotifier
import com.puretv.twitch.desktop.player.ChannelClaims
import com.puretv.twitch.desktop.player.DEFAULT_VOLUME
import com.puretv.twitch.desktop.player.DesktopPlayerProvider
import com.puretv.twitch.desktop.player.LocalStreamProxy
import com.puretv.twitch.desktop.player.PlaybackStallWatchdog
import com.puretv.twitch.desktop.player.ProxyUnavailableException
import com.puretv.twitch.desktop.player.TILE_AUDIO_OUTPUT
import com.puretv.twitch.desktop.player.TileQuality
import com.puretv.twitch.desktop.player.VlcEngines
import com.puretv.twitch.desktop.player.VlcPlayer
import com.puretv.twitch.core.repository.VodRepository
import com.puretv.twitch.core.stream.MasterPlaylistResult
import com.puretv.twitch.core.vod.VodChatReplayController
import com.puretv.twitch.core.vod.VodFailures
import com.puretv.twitch.core.vod.WatchProgress
import com.puretv.twitch.core.vod.loadVodEmoteIndex
import com.puretv.twitch.desktop.data.WatchProgressStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What a tile shows besides its video. */
data class TileInfo(
    val displayName: String,
    val avatarUrl: String? = null,
    /** Null until the tile's player exists (libVLC is built off the UI thread). */
    val player: VlcPlayer? = null,
    val adBlockStatus: AdBlockStatus = AdBlockStatus.UNKNOWN,
    val isBuffering: Boolean = true,
    /** Why this tile cannot play, in words; null while it plays or is still trying. */
    val message: String? = null,
)

/** The chat replay of the selected past video: [videoId] says which video the [messages] belong to. */
data class VodChatView(val videoId: String? = null, val messages: List<ChatMessage> = emptyList())

data class MultiViewUiState(
    val layout: MultiViewState = MultiViewState(),
    val info: Map<Int, TileInfo> = emptyMap(),
    /** Set when the local proxy cannot start, which no tile can work around. */
    val fatalError: String? = null,
)

/**
 * The multi-stream session: up to four live channels at once, one of them active. It is
 * owned by [MultiStreamSession], not by the screen, so tiles that were popped out into
 * their own windows keep playing when the screen is left.
 *
 * Every tile is its own [VlcPlayer] made from the shared [VlcEngines], never the
 * single-stream player, so leaving the normal stream screen can never stop a tile
 * (and the mpv engine is not involved). All tiles keep playing, each with its own
 * volume and mute. By default only the active tile is heard; the viewer can choose to
 * hear every unmuted tile instead. Either way only the players' volume changes, so
 * moving the sound never reloads a stream. A tile's quality depends
 * on how many tiles there are (see [TileQuality]); when that changes, only the tiles
 * whose target changed reload.
 *
 * Each tile gets the same recovery as the single stream screen: a failure is
 * classified, final ones are explained, the others retry after the Wave 2 delays,
 * and a stalled picture is reloaded with a fresh session.
 */
class MultiViewViewModel(
    initial: TileSource,
    private val engines: VlcEngines,
    private val adBlockEngine: AdBlockEngine,
    private val localStreamProxy: LocalStreamProxy,
    private val playableStreamCache: PlayableStreamCache,
    private val settingsStore: DesktopSettingsStore,
    private val channelRepository: ChannelRepository,
    chatClient: TwitchChatClient,
    private val emoteRepository: EmoteRepository,
    badgeRepository: BadgeRepository,
    apiClient: TwitchApiClient,
    private val players: DesktopPlayerProvider,
    private val notifier: DesktopLiveNotifier,
    private val vodRepository: VodRepository,
    private val progressStore: WatchProgressStore,
    // Which windows and tiles watch which channel; a channel is only forgotten by the last one.
    private val claims: ChannelClaims = ChannelClaims(),
    // Asked before a tile is added: false when the app is already playing as much video as it allows.
    private val admit: () -> Boolean = { true },
) : DesktopViewModel(), MultiTileHost {

    // New tiles start at the volume the viewer last had on the normal stream screen, when
    // that player exists and is not at zero; the settings hold no volume of their own.
    private val startVolume: Int = run {
        var volume = DEFAULT_VOLUME
        players.ifCreated { p -> p.status.value.volume.takeIf { it > 0 }?.let { volume = it } }
        volume
    }

    private val _state = MutableStateFlow(
        MultiViewUiState(
            layout = MultiViewState(listenAll = rememberedListenAll).let {
                when (initial) {
                    is TileSource.Live -> it.add(initial.login, startVolume)
                    is TileSource.Vod -> it.addVod(initial, startVolume)
                }
            },
        ),
    )
    override val state: StateFlow<MultiViewUiState> = _state.asStateFlow()

    // Chat replay of the selected past video (see the collector in init).
    private val _vodChat = MutableStateFlow(VodChatView())
    val vodChatState: StateFlow<VodChatView> = _vodChat.asStateFlow()

    // One chat connection for the whole screen, following the active tile.
    private val chat = MultiChatSession(
        chatClient, emoteRepository, badgeRepository, channelRepository, apiClient, settingsStore, scope,
    )
    val chatState: StateFlow<MultiChatState> = chat.state
    fun startReply(message: ChatMessage) = chat.startReply(message)
    fun cancelReply() = chat.cancelReply()
    fun sendChat(text: String) = chat.send(text)

    /** What runs behind one tile. Touched on several coroutines, so only under [lock]. */
    private class Runtime(
        val id: Int,
        val login: String,
        /** Non-null for a past video: it never touches the live proxy, ad block or stall watchdog. */
        vod: TileSource.Vod? = null,
        val watchdog: PlaybackStallWatchdog = PlaybackStallWatchdog(),
    ) {
        // Replaced when the lookup of a pasted link fills in its title and channel.
        @Volatile var vod: TileSource.Vod? = vod
        @Volatile var player: VlcPlayer? = null
        @Volatile var master: MasterPlaylistResult? = null
        @Volatile var startMs = 0L
        @Volatile var loadJob: Job? = null
        @Volatile var autoRetries = 0
        @Volatile var playingHeight = 0
        @Volatile var closed = false
        private val freed = java.util.concurrent.atomic.AtomicBoolean(false)

        /** Stops and releases [player] exactly once, whichever of close and setup gets here first. */
        fun freeOnce(player: VlcPlayer) {
            if (!freed.compareAndSet(false, true)) return
            player.stop()
            player.release()
        }
        val jobs = ArrayList<Job>()
    }

    private val lock = Any()
    private val audioLock = Any()
    // Guards the go-live skip list so a late update cannot land after the screen closed.
    private val watchLock = Any()
    private var cleared = false
    private val runtimes = HashMap<Int, Runtime>()

    // The proxy start is shared: every tile waits for the same attempt.
    private val proxyReady = MutableStateFlow<Boolean?>(null)

    init {
        scope.launch { startProxy() }
        _state.value.layout.tiles.forEach { openRuntime(it) }
        // Go-live toasts skip every channel on screen, not only the one App knows about.
        scope.launch {
            // Only live tiles: a past video of a channel does not make its going live uninteresting.
            _state.map { s -> s.layout.tiles.mapNotNullTo(HashSet()) { it.liveLogin } }.distinctUntilChanged()
                .collect { tiles -> synchronized(watchLock) { if (!cleared) notifier.alsoWatching = tiles } }
        }
        // Chat belongs to the active tile: when the sound moves, so does the chat. A past
        // video has no live room; its replay is shown instead (see below).
        scope.launch {
            _state.map { it.layout.active?.liveLogin }.distinctUntilChanged().collect { chat.switchTo(it) }
        }
        scope.launch { followVodChat() }
        // Places of the playing past videos are saved for Continue Watching.
        scope.launch {
            while (true) {
                delay(VodTileLogic.SAVE_EVERY_MS)
                val vods = synchronized(lock) { runtimes.values.filter { it.vod != null && !it.closed } }
                for (rt in vods) if (rt.player?.status?.value?.isPlaying == true) runCatching { saveProgress(rt) }
            }
        }
        // One loop watches every tile for a picture that froze while still "playing".
        scope.launch {
            while (true) {
                delay(STALL_CHECK_MS)
                val now = System.currentTimeMillis()
                val live = synchronized(lock) { runtimes.values.toList() }
                for (rt in live) {
                    // A past video that is paused or seeking is not stalled.
                    if (rt.vod != null) continue
                    val player = rt.player ?: continue
                    if (rt.watchdog.sample(player.status.value, now)) {
                        // A stalled session should not be reused: mint a fresh one.
                        playableStreamCache.invalidate(rt.login)
                        startPlayback(rt)
                    }
                }
            }
        }
    }

    private suspend fun startProxy() {
        val started = runCatching { localStreamProxy.start() }
        if (started.isFailure) {
            _state.update {
                it.copy(
                    fatalError = (started.exceptionOrNull() as? ProxyUnavailableException)?.message
                        ?: "PureTV's local video proxy could not start. Try closing other PureTV windows and reopening multi-stream.",
                )
            }
        }
        proxyReady.value = started.isSuccess
    }

    // ---- Tiles ------------------------------------------------------------------

    /** Adds [login] as a tile; false when it is already shown, blank, or the screen is full. */
    override fun addChannel(login: String): Boolean {
        val before = _state.value.layout
        val after = before.add(login, startVolume)
        if (after === before || !admit()) return false
        _state.update { it.copy(layout = after) }
        val tile = after.tiles.last()
        openRuntime(tile)
        retargetQuality()
        applyAudio()
        return true
    }

    /** Adds the past video [source] as a tile; false when it is already shown or the screen is full. */
    override fun addVod(source: TileSource.Vod): Boolean {
        val before = _state.value.layout
        val after = before.addVod(source, startVolume)
        if (after === before || !admit()) return false
        _state.update { it.copy(layout = after) }
        openRuntime(after.tiles.last())
        retargetQuality()
        applyAudio()
        return true
    }

    // ---- Past video controls ------------------------------------------------------

    private fun vodPlayer(id: Int): VlcPlayer? =
        synchronized(lock) { runtimes[id]?.takeIf { it.vod != null && !it.closed }?.player }

    private fun activeVodId(): Int? = _state.value.layout.active?.takeIf { it.isVod }?.id

    /** Play or pause the past video of tile [id]. */
    fun togglePlay(id: Int) {
        vodPlayer(id)?.togglePlayPause()
    }

    /** Seek the past video of tile [id] to [positionMs], kept inside the video. */
    fun seekTo(id: Int, positionMs: Long) {
        val player = vodPlayer(id) ?: return
        player.seekTo(VodTileLogic.clampSeek(positionMs, player.status.value.durationMs))
    }

    /** Skips [deltaMs] in the past video of tile [id]; false while it cannot seek yet. */
    fun seekBy(id: Int, deltaMs: Long): Boolean {
        val player = vodPlayer(id) ?: return false
        val s = player.status.value
        val target = VodTileLogic.seekTarget(s.positionMs, deltaMs, s.durationMs, s.isSeekable) ?: return false
        player.seekTo(target)
        return true
    }

    /** Space on the keyboard: play or pause the selected tile when it is a past video. */
    fun toggleActivePlay() {
        activeVodId()?.let(::togglePlay)
    }

    /** The arrow keys, J and L: skip in the selected tile when it is a past video. */
    fun seekActiveBy(deltaMs: Long): Boolean = activeVodId()?.let { seekBy(it, deltaMs) } ?: false

    /** Where the past video of tile [id] is now, for handing it to another surface. */
    fun positionOf(id: Int): Long = vodPlayer(id)?.status?.value?.positionMs ?: 0L

    /** Closes tile [id], stopping and freeing its player. The tiles left are returned. */
    override fun closeTile(id: Int): List<MultiTile> {
        val after = _state.value.layout.remove(id)
        _state.update { it.copy(layout = after, info = it.info - id) }
        closeRuntime(id)
        retargetQuality()
        applyAudio()
        return after.tiles
    }

    /** Moves tile [id] out of the grid into a window of its own. Only the video surface moves. */
    override fun popOut(id: Int) = changeTiles { it.popOut(id) }

    /** Puts tile [id] back in the grid. */
    override fun dock(id: Int) = changeTiles { it.dock(id) }

    override fun popOutAll() = changeTiles { it.popOutAll() }

    override fun dockAll() = changeTiles { it.dockAll() }

    /**
     * Leaving the multi-stream screen: closes the tiles shown in the grid and leaves the
     * ones in their own windows playing. Returns the tiles left.
     */
    override fun closeDocked(): List<MultiTile> {
        _state.value.layout.docked.forEach { closeTile(it.id) }
        return _state.value.layout.tiles
    }

    private fun changeTiles(change: (MultiViewState) -> MultiViewState) {
        _state.update { s ->
            val after = change(s.layout)
            if (after === s.layout) s else s.copy(layout = after)
        }
    }

    /** Selects tile [id]: the one you hear (unless listening to all) and the keys act on. Nothing reloads. */
    fun setActive(id: Int) {
        val before = _state.value.layout
        val after = before.setActive(id)
        if (after === before) return
        _state.update { it.copy(layout = after) }
        applyAudio()
    }

    fun swap(a: Int, b: Int) = _state.update { it.copy(layout = it.layout.swap(a, b)) }

    /** Moves tile [id] one place earlier (-1) or later (1) in the grid. Only the order changes: no player is touched. */
    fun move(id: Int, delta: Int) = _state.update { it.copy(layout = it.layout.move(id, delta)) }

    /** Up or Down on the keyboard: volume for the selected tile, as on the normal stream screen. */
    fun nudgeVolume(up: Boolean) = changeLayout { l -> l.activeId?.let { l.nudge(it, up) } ?: l }

    /** Up or Down on the keyboard in the window of tile [id]. */
    fun nudgeVolume(id: Int, up: Boolean) = changeLayout { it.selecting(id).nudge(id, up) }

    /** The volume bar of tile [id]. */
    fun setVolume(id: Int, volume: Int) = changeLayout { it.selecting(id).withVolume(id, volume) }

    /** The speaker button of tile [id]. */
    fun toggleMute(id: Int) = changeLayout { it.selecting(id).toggleMute(id) }

    /** M on the keyboard: mutes or unmutes the selected tile. */
    fun toggleActiveMute() = changeLayout { l -> l.activeId?.let { l.toggleMute(it) } ?: l }

    /** "Listen to": only the selected tile, or every tile at its own volume. Remembered for this session. */
    fun setListenAll(all: Boolean) {
        rememberedListenAll = all
        changeLayout { it.withListenAll(all) }
    }

    private fun changeLayout(change: (MultiViewState) -> MultiViewState) {
        val before = _state.value.layout
        val after = change(before)
        if (after === before) return
        _state.update { it.copy(layout = after) }
        applyAudio()
    }

    // ---- Runtimes ---------------------------------------------------------------

    private fun openRuntime(tile: MultiTile) {
        val rt = Runtime(tile.id, tile.login, tile.vod)
        synchronized(lock) { runtimes[tile.id] = rt }
        if (rt.vod == null) claims.acquire(rt.login)
        _state.update { it.copy(info = it.info + (tile.id to TileInfo(displayName = tile.login.ifBlank { "Past video" }))) }
        val setup = scope.launch {
            // libVLC is built on first use and its plugin scan is slow: not on the UI thread.
            // On the instance the audio normalizer setting asks for, and it follows later changes.
            val player = VlcPlayer(engines, audioOutput = TILE_AUDIO_OUTPUT, replayOnNewSurface = true)
            if (rt.closed) {
                rt.freeOnce(player)
                return@launch
            }
            rt.player = player
            // A close that landed between the check above and this assignment saw no
            // player to free, so free it here (a no-op if the close got there first).
            if (rt.closed) {
                rt.freeOnce(player)
                return@launch
            }
            updateInfo(rt.id) {
                it.copy(
                    player = player,
                    message = if (player.isAvailable) null else player.status.value.error,
                )
            }
            if (!player.isAvailable) return@launch
            applyAudio()
            val vod = rt.vod
            if (vod != null) {
                // A past video plays its own playlist directly: no proxy, no ad pipeline.
                rt.startMs = VodTileLogic.initialStartMs(vod.startPositionMs, progressStore.get(vod.videoId))
                launchVodWatchers(rt, player)
                startVod(rt, rt.startMs)
                return@launch
            }
            if (!rt.closed) adBlockEngine.resetForNewStream(rt.login)
            // Wait for the shared proxy start before the first request goes out.
            proxyReady.first { it != null }
            if (proxyReady.value != true || rt.closed) return@launch
            launchWatchers(rt, player)
            startPlayback(rt)
        }
        synchronized(lock) { rt.jobs += setup }
        // Names and avatars are cosmetic: load them without holding up the picture.
        synchronized(lock) {
            rt.jobs += scope.launch {
                // A video named only by a pasted link: ask who it is from and what it is called.
                val named = rt.vod
                if (named != null && (named.channelLogin.isBlank() || named.title.isBlank())) fillVodDetails(rt, named)
                val login = rt.vod?.channelLogin ?: tile.login
                val channel = if (login.isBlank()) null else runCatching { channelRepository.getChannel(login) }.getOrNull()
                if (channel != null) {
                    updateInfo(rt.id) {
                        it.copy(displayName = channel.displayName.ifBlank { login }, avatarUrl = channel.profileImageUrl)
                    }
                }
            }
        }
    }

    /** Looks the video up by id and fills the tile in with its title, channel, kind and art. */
    private suspend fun fillVodDetails(rt: Runtime, vod: TileSource.Vod) {
        val video = runCatching { vodRepository.videoById(vod.videoId) }.getOrNull() ?: return
        val details = video.toTileSource(vod.channelLogin)
        if (rt.closed) return
        changeTiles { it.withVodDetails(vod.videoId, details) }
        rt.vod = _state.value.layout.tiles.firstOrNull { it.id == rt.id }?.vod ?: return
        updateInfo(rt.id) { it.copy(displayName = rt.vod!!.channelLogin.ifBlank { it.displayName }) }
    }

    private fun launchWatchers(rt: Runtime, player: VlcPlayer) {
        synchronized(lock) {
            rt.jobs += scope.launch {
                adBlockEngine.statusFor(rt.login).collect { status -> updateInfo(rt.id) { it.copy(adBlockStatus = status) } }
            }
            rt.jobs += scope.launch {
                player.status.map { it.isBuffering to it.isPlaying }.distinctUntilChanged().collect { (buffering, playing) ->
                    updateInfo(rt.id) { it.copy(isBuffering = buffering && !playing, message = if (playing) null else it.message) }
                }
            }
            rt.jobs += scope.launch {
                // Same recovery as the single stream screen: ask the proxy what really
                // happened, explain a stream that cannot play, and retry the others with
                // a fresh session after a growing pause.
                player.status.map { it.error to it.isPlaying }.distinctUntilChanged().collect { (error, playing) ->
                    if (playing) rt.autoRetries = 0
                    if (error == null || rt.closed) return@collect
                    val failure = localStreamProxy.failureFor(rt.login)
                    when {
                        failure != null && failure.kind in FINAL_FAILURES ->
                            updateInfo(rt.id) { it.copy(message = failure.message()) }
                        rt.autoRetries < AUTO_RETRY_DELAYS_MS.size -> {
                            delay(AUTO_RETRY_DELAYS_MS[rt.autoRetries])
                            rt.autoRetries++
                            playableStreamCache.invalidate(rt.login)
                            rt.watchdog.reset()
                            if (!rt.closed) startPlayback(rt)
                        }
                        else -> updateInfo(rt.id) {
                            it.copy(message = (failure ?: Failure(FailureKind.UNKNOWN, error)).message())
                        }
                    }
                }
            }
        }
    }

    private fun launchVodWatchers(rt: Runtime, player: VlcPlayer) {
        synchronized(lock) {
            rt.jobs += scope.launch {
                player.status.map { it.isBuffering to it.isPlaying }.distinctUntilChanged().collect { (buffering, playing) ->
                    updateInfo(rt.id) { it.copy(isBuffering = buffering && !playing, message = if (playing) null else it.message) }
                }
            }
            rt.jobs += scope.launch {
                // A player fault reloads the video at the place it had reached, with a fresh
                // playlist, after a growing pause. It never starts over from the beginning.
                player.status.map { it.error to it.isPlaying }.distinctUntilChanged().collect { (error, playing) ->
                    if (playing) rt.autoRetries = 0
                    if (error == null || rt.closed) return@collect
                    if (rt.autoRetries < AUTO_RETRY_DELAYS_MS.size) {
                        delay(AUTO_RETRY_DELAYS_MS[rt.autoRetries])
                        rt.autoRetries++
                        rt.master = null
                        if (!rt.closed) reloadVod(rt, retry = true)
                    } else {
                        updateInfo(rt.id) { it.copy(message = VodFailures.COULD_NOT_LOAD) }
                    }
                }
            }
        }
    }

    /**
     * Where a reload of a past video picks up: the last place it was really at, else where it
     * was started. Null when it had ended, which stays ended until the viewer presses play.
     */
    private fun reloadPoint(rt: Runtime): Long? {
        val player = rt.player ?: return null
        return VodTileLogic.reloadStartMs(player.lastGoodMs, rt.startMs, player.status.value.durationMs)
    }

    /** Reloads the past video of [rt] where it was, keeping it paused if it was paused. */
    private fun reloadVod(rt: Runtime, retry: Boolean = false) {
        val at = reloadPoint(rt) ?: return
        startVod(rt, at, keepPaused = rt.player?.pausedByUser == true, retry = retry)
    }

    /**
     * Every (re)start of a past video: resolves its playlist (kept for reloads), picks the
     * rendition the tile count allows and plays it from [atMs]. A video that cannot be
     * resolved is explained, or retried after a pause when the failure may pass. A new start
     * replaces the one before it; a start that is not itself a [retry] clears the retries.
     */
    private fun startVod(rt: Runtime, atMs: Long, keepPaused: Boolean = false, retry: Boolean = false) {
        val vod = rt.vod ?: return
        val player = rt.player ?: return
        if (!retry) rt.autoRetries = 0
        synchronized(lock) {
            if (rt.closed) return
            rt.loadJob?.cancel()
            rt.loadJob = scope.launch {
                var next = atMs
                while (!rt.closed) {
                    val resolved = runCatching { rt.master ?: vodRepository.resolvePlayableVod(vod.videoId) }
                    // A start that was replaced meanwhile must not play.
                    currentCoroutineContext().ensureActive()
                    val master = resolved.getOrNull()
                    if (master != null) {
                        rt.master = master
                        val height = targetHeight()
                        val url = TileQuality.vodUrl(master.masterUrl, master.variants, height)
                        synchronized(lock) {
                            if (rt.closed) return@launch
                            rt.playingHeight = height
                            player.playVod(url, next, keepPaused)
                        }
                        applyAudio()
                        return@launch
                    }
                    val error = resolved.exceptionOrNull()
                    if (VodFailures.isPermanent(error) || rt.autoRetries >= AUTO_RETRY_DELAYS_MS.size) {
                        updateInfo(rt.id) { it.copy(message = VodFailures.message(error), isBuffering = false) }
                        return@launch
                    }
                    delay(AUTO_RETRY_DELAYS_MS[rt.autoRetries])
                    rt.autoRetries++
                    next = reloadPoint(rt) ?: next
                }
            }
        }
    }

    /** Saves where the past video of [rt] is, for Continue Watching. Nothing is saved this early in a video. */
    private fun saveProgress(rt: Runtime) {
        val vod = rt.vod ?: return
        val s = rt.player?.status?.value ?: return
        if (!VodTileLogic.shouldSave(s.positionMs, s.durationMs)) return
        // A tile that does not know its title or channel yet keeps what Continue Watching already has.
        progressStore.save(
            VodTileLogic.mergedProgress(
                existing = progressStore.get(vod.videoId),
                videoId = vod.videoId,
                title = vod.title,
                channelLogin = vod.channelLogin,
                thumbnailUrl = vod.thumbnailUrl,
                positionMs = s.positionMs,
                durationMs = s.durationMs,
                now = System.currentTimeMillis(),
            ),
        )
    }

    /** Chat replay follows the selected tile when it is a past video, and fades out otherwise. */
    private suspend fun followVodChat() {
        _state.map { s -> s.layout.active?.vod?.let { it to s.info[s.layout.activeId]?.player } }
            .distinctUntilChanged()
            .collectLatest { target ->
                if (target == null) {
                    _vodChat.value = VodChatView()
                    return@collectLatest
                }
                val (vod, player) = target
                val replayScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
                try {
                    val controller = VodChatReplayController(
                        scope = replayScope,
                        fetch = { offset -> vodRepository.videoComments(vod.videoId, offset) },
                        loadEmoteIndex = { loadVodEmoteIndex(vod.channelLogin, channelRepository, emoteRepository) },
                    )
                    _vodChat.value = VodChatView(vod.videoId)
                    replayScope.launch { controller.messages.collect { _vodChat.value = VodChatView(vod.videoId, it) } }
                    if (player == null) awaitCancellation()
                    player.status.map { it.positionMs / 1000 }.distinctUntilChanged().collect { controller.onPosition(it) }
                } finally {
                    replayScope.cancel()
                }
            }
    }

    /** Every (re)start of a tile goes through here, so retries keep the tile's quality and low latency. */
    private fun startPlayback(rt: Runtime) {
        if (rt.vod != null) {
            reloadVod(rt)
            applyAudio()
            return
        }
        val player = rt.player ?: return
        val height = targetHeight()
        val lowLatency = settingsStore.settings.value.lowLatencyMode
        // Under the lock so a close cannot slip in between the check and the play:
        // play() only queues work on the UI thread, and close queues stop and release
        // after it, so a closed tile is never played again.
        synchronized(lock) {
            if (rt.closed) return
            rt.playingHeight = height
            player.play(LocalStreamProxy.tileStreamUrl(rt.login, height), lowLatency = lowLatency)
        }
        applyAudio()
    }

    private fun targetHeight(): Int {
        val preference = StreamQuality.fromSetting(settingsStore.settings.value.preferredQuality)
        return TileQuality.maxHeight(_state.value.layout.qualityCap(preference))
    }

    /** After a tile joins or leaves: reload only the tiles whose quality target changed. */
    private fun retargetQuality() {
        val height = targetHeight()
        val changed = synchronized(lock) { runtimes.values.filter { it.player != null && it.playingHeight != 0 && it.playingHeight != height } }
        changed.forEach {
            it.watchdog.reset()
            // A past video asks for a fresh playlist, so a long pause cannot leave it with a stale one.
            it.master = null
            startPlayback(it)
        }
    }

    /**
     * Gives every tile's player the sound its tile asks for: an audible tile plays at its
     * own volume, every other one is muted. Safe to repeat: the player keeps the setting
     * across reloads and re-asserts it once its audio output exists (see VlcPlayer).
     */
    private fun applyAudio() {
        // One pass at a time, reading the layout inside it, so overlapping calls from the
        // UI and the tile coroutines always end on the newest layout.
        synchronized(audioLock) {
            val layout = _state.value.layout
            val players = synchronized(lock) {
                runtimes.values.filterNot { it.closed }.mapNotNull { rt -> rt.player?.let { rt.id to it } }
            }
            for ((id, player) in players) {
                val tile = layout.tiles.firstOrNull { it.id == id } ?: continue
                if (layout.isAudible(id)) {
                    // Volume first: it also lifts the mute, so there is no blip at the old level.
                    player.setVolume(tile.volume)
                    player.setMuted(false)
                } else {
                    player.setMuted(true)
                }
            }
        }
    }

    private fun updateInfo(id: Int, change: (TileInfo) -> TileInfo) {
        _state.update { s ->
            val current = s.info[id] ?: return@update s
            s.copy(info = s.info + (id to change(current)))
        }
    }

    private fun closeRuntime(id: Int) {
        val rt = synchronized(lock) {
            runtimes.remove(id)?.also { it.closed = true }
        } ?: return
        // Saved before the player is stopped: a stopped player reads position 0.
        runCatching { saveProgress(rt) }
        rt.loadJob?.cancel()
        synchronized(lock) { rt.jobs.toList() }.forEach { it.cancel() }
        rt.player?.let { rt.freeOnce(it) }
        // A past video has no ad-block state, and a live tile of the same channel must keep its own.
        if (rt.vod == null && claims.release(rt.login)) adBlockEngine.forgetChannel(rt.login)
    }

    override fun shutDown() = dispose()

    /** Stops and frees every tile, so leaving the screen leaves nothing playing or decoding. */
    override fun onCleared() {
        chat.close()
        synchronized(watchLock) {
            cleared = true
            notifier.alsoWatching = emptySet()
        }
        val ids = synchronized(lock) { runtimes.keys.toList() }
        ids.forEach { closeRuntime(it) }
    }

    private companion object {
        const val STALL_CHECK_MS = 2_000L

        // The "Listen to" choice outlives one visit to the screen, but not the app.
        @Volatile var rememberedListenAll = false
    }
}
