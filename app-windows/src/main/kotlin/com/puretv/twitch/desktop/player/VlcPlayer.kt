package com.puretv.twitch.desktop.player

import com.puretv.twitch.core.audio.AudioLevelling
import com.puretv.twitch.core.perf.TimingLog
import com.puretv.twitch.desktop.platform.DisplayHdr
import com.puretv.twitch.core.stream.LiveLatency
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import uk.co.caprica.vlcj.player.base.MediaPlayer
import uk.co.caprica.vlcj.player.base.MediaPlayerEventAdapter
import uk.co.caprica.vlcj.player.base.State
import uk.co.caprica.vlcj.log.LogEventListener
import uk.co.caprica.vlcj.log.NativeLog
import uk.co.caprica.vlcj.player.embedded.EmbeddedMediaPlayer
import java.awt.Component
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import javax.swing.SwingUtilities
import javax.swing.Timer

/**
 * SECTION 08.2 [CRITICAL] — wraps VLC via VLCJ for video playback.
 *
 * VLC resolution order:
 *   1. Bundled copy — when running as a packaged distributable, looks for
 *      `vlc/libvlc.dll` inside `compose.application.resources.dir` (populated
 *      by `gradle :app-windows:bundleVlc` before packaging).
 *   2. System installation — falls back to auto-discovery (standard VLC
 *      install paths, jna.library.path, PATH) for development builds.
 *
 * [VlcPlayer] is a Koin singleton so the same playback session persists across
 * navigation, mirroring `TwitchPlayer`/`TvPlayer` on the other platforms.
 *
 * THREADING — VLCJ callbacks fire on internal VLC threads; anything that
 * touches Swing/AWT (attaching the video surface, resizing) MUST be marshalled
 * onto the EDT via [SwingUtilities.invokeLater]. [PlayerStatus] updates are
 * safe to publish from any thread since [MutableStateFlow] is thread-safe.
 *
 * Every libVLC call this player makes runs on its own worker thread ([vlc]), never on
 * the UI thread. libVLC's stop waits for the stream's threads (100 to 900 ms measured)
 * while holding a lock that play, pause, seek, size and volume calls also take, so a
 * libVLC call on the UI thread froze the window: leaving a stream showed a white
 * rectangle until it ended. The UI thread only reads and writes this class's own
 * fields and queues work; it never waits on the worker.
 */
class VlcPlayer(
    // The app's libVLC instances (see VlcEngines), one per set of instance arguments
    // (RTX Video, the audio normalizer). This player makes its media player on the one
    // its settings ask for, and moves to another when they change (see [switchEngine]).
    private val engines: VlcEngines,
    // A named libVLC audio output for this player alone, or null for VLC's default.
    // See [TILE_AUDIO_OUTPUT]: the default output shares one volume across players.
    private val audioOutput: String? = null,
    // True for a multi-stream tile, whose video can move to another window (see
    // PoppedStreamWindow). libVLC binds its surface when playback starts, so a surface set
    // while playing is ignored: the same stream is started again on the same player
    // instead, which costs a second or two of rebuffering and nothing else.
    private val replayOnNewSurface: Boolean = false,
    // The main player only: what NVIDIA RTX Video it should run with (see [RtxVlcSupport]).
    // Without it the player always plays without RTX Video.
    private val rtx: RtxVlcSupport? = null,
) : DesktopPlayer {

    // Declared before the first media player is made, which registers it. Every media
    // player this one makes (see [switchEngine]) reports here.
    private val events: MediaPlayerEventAdapter =
        object : MediaPlayerEventAdapter() {
            override fun playing(mediaPlayer: MediaPlayer) {
                _status.update { it.copy(isPlaying = true, isBuffering = false, error = null) }
                if (pauseAfterStart) {
                    // A reload of a video the viewer had paused: show it again, paused.
                    pauseAfterStart = false
                    vlc { if (mediaPlayer === this@VlcPlayer.mediaPlayer) mediaPlayer.controls().setPause(true) }
                } else {
                    pausedByUser = false
                }
                // Assert our volume on playback start so libVLC stops adopting its
                // own default (~30-50% perceived) until the user first drags the slider.
                // The audio output usually does not exist yet at this moment, so this
                // can be dropped; timeChanged below repeats it until it sticks.
                syncAudioLater(mediaPlayer)
            }

            // libVLC opened (or closed) its video output: the evidence that video, not only
            // audio, is being drawn. Logged for diagnostics and the platform smoke tests.
            override fun videoOutput(mediaPlayer: MediaPlayer, newCount: Int) {
                if (newCount > 0) {
                    TimingLog.log("player.video_output", 0, "outputs=$newCount")
                    _videoOutputs.update { it + 1 }
                }
                // The video's size can arrive just after its output opens: look again shortly.
                if (rtxChoice.any) SwingUtilities.invokeLater {
                    refreshRtxStatus()
                    Timer(1_500) { refreshRtxStatus() }.apply { isRepeats = false }.start()
                }
            }

            override fun paused(mediaPlayer: MediaPlayer) {
                pausedByUser = true
                _status.update { it.copy(isPlaying = false) }
            }

            override fun stopped(mediaPlayer: MediaPlayer) {
                _status.update { it.copy(isPlaying = false, isBuffering = false) }
            }

            override fun buffering(mediaPlayer: MediaPlayer, newCache: Float) {
                _status.update { it.copy(isBuffering = newCache < 100f) }
            }

            override fun error(mediaPlayer: MediaPlayer) {
                pausedByUser = false
                _status.update { it.copy(isPlaying = false, isBuffering = false, error = "Playback error — VLC reported a fault") }
            }

            override fun mediaPlayerReady(mediaPlayer: MediaPlayer) {
                _status.update { it.copy(isReady = true) }
            }

            override fun timeChanged(mediaPlayer: MediaPlayer, newTime: Long) {
                if (isVod) {
                    // libVLC reports 0 while it opens new media: that is not the place the video is at.
                    lastGoodMs = VodPlayback.nextGood(lastGoodMs, newTime, _status.value.isPlaying)
                    if (VodPlayback.shouldPublishTime(lastGoodMs, newTime)) _status.update { it.copy(positionMs = newTime) }
                } else {
                    _status.update { it.copy(positionMs = newTime) }
                }
                // libVLC forgets a volume set before its audio output exists, and starts
                // that output muted: measured on a live tile, VLC read back volume 0 and
                // muted while this player held 60 and unmuted, so the tile was silent.
                // Every new media gets a new output, so this also covers each reload.
                syncAudioLater(mediaPlayer)
            }

            override fun lengthChanged(mediaPlayer: MediaPlayer, newLength: Long) {
                _status.update { it.copy(durationMs = newLength) }
            }

            override fun seekableChanged(mediaPlayer: MediaPlayer, newSeekable: Int) {
                _status.update { it.copy(isSeekable = newSeekable != 0) }
            }
        }

    // The one thread that talks to libVLC for this player (see the class comment). Daemon,
    // so it never holds the app open; [release] shuts it down after its last task.
    private val worker: ExecutorService = Executors.newSingleThreadExecutor { r ->
        Thread(r, "vlc-player").apply { isDaemon = true }
    }.also { workers.removeIf { w -> w.isTerminated }; workers.add(it) }

    /** Runs [block] on the libVLC worker, in order. Never waits; failures are swallowed. */
    private fun vlc(block: () -> Unit) {
        if (worker.isShutdown) return
        runCatching { worker.execute { runCatching(block) } }
    }

    // The audio normalizer strength wanted (null for off): the settings when this player
    // was made, then whatever [setAudioLevelling] last said. Kept here rather than read
    // from the settings each time so a switch flipped quickly (on, off, on) always ends
    // on the last word.
    @Volatile private var levellingWanted: AudioLevelling? = engines.levellingWanted()

    // The instance this player is on now (what RTX Video and normalizer it runs with), and
    // that instance. Both change only on the UI thread (see [switchEngine]).
    // Before there is a window, assume the HDR display if there is one; attachToPanel
    // corrects it for the real display.
    // Timed: it waits for the GPU lookup and reads libVLC's version (see timing.log).
    @Volatile private var key: VlcEngineKey = VlcEngineKey(
        rtx = rtx?.let {
            val started = System.nanoTime()
            it.choiceFor(displayHdr = it.wanted().hdr && DisplayHdr.anyOn()).also { choice ->
                TimingLog.log("player.rtx_check", (System.nanoTime() - started) / 1_000_000, "sr=${choice.superResolution} hdr=${choice.hdr}")
            }
        } ?: RtxVideoChoice.NONE,
        levelling = levellingWanted,
    )
    @Volatile private var engine: VlcEngine = engines.engineFor(key)

    /** What RTX Video this player runs with now (part of [key]). */
    private val rtxChoice: RtxVideoChoice get() = key.rtx

    @Volatile private var mediaPlayer: EmbeddedMediaPlayer? = newMediaPlayer(this.engine)

    // libVLC's log for an RTX instance, the only place RTX Video says what it did.
    private var rtxLog: NativeLog? = null
    private var rtxReader = RtxLogReader(rtxChoice)
    private val _rtxStatus = MutableStateFlow(RtxVideoStatus())
    override val rtxStatus: StateFlow<RtxVideoStatus> = _rtxStatus.asStateFlow()
    override val supportsRtxVideo: Boolean get() = rtx?.supported == true
    override val supportsRtxHdr: Boolean get() = supportsRtxVideo

    // Set while an instance switch is on its way, so the HDR watcher does not pile up more.
    @Volatile private var switchPending = false

    /** The choice for the display this player's window is on now. */
    private fun desiredRtx(component: Component? = attachedComponent): RtxVideoChoice {
        val support = rtx ?: return RtxVideoChoice.NONE
        val hdrWanted = support.wanted().hdr
        return support.choiceFor(displayHdr = hdrWanted && component != null && DisplayHdr.isOn(component))
    }

    /**
     * The instance this player should be on: RTX Video for its window's display, and the
     * normalizer strength as last asked for. Every check for "move to another instance" compares
     * this whole key, so a change of either one is never missed.
     */
    private fun desiredKey(component: Component? = attachedComponent): VlcEngineKey =
        VlcEngineKey(desiredRtx(component), levellingWanted)

    // Told by [VlcEngines.levellingChanged] when the settings change anywhere (Settings,
    // another window's player controls), so every VLC player follows them. One object, so
    // release() can stop following.
    private val normalizerFollower: (AudioLevelling?) -> Unit = { level -> setAudioLevelling(level) }

    // Every two seconds while RTX Video is on: refresh the status (the stream's own size
    // changes with quality and ad breaks, and nothing else would say so), and, while HDR
    // is asked for, check the display, because Windows HDR is per display, can be switched
    // at any time, and the window can move (one cheap user32 query). Moves to the right
    // instance when the answer changes.
    private val hdrWatch = Timer(2_000) {
        val support = rtx ?: return@Timer
        if (released || attachedComponent == null || !support.wanted().any && !rtxChoice.any) return@Timer
        refreshRtxStatus()
        if ((support.wanted().hdr || rtxChoice.hdr) && !switchPending && desiredKey() != key) moveToWantedEngine()
    }

    // Set by release(): an instance switch still on its way must not make a new player.
    @Volatile private var released = false

    private val _status = MutableStateFlow(
        if (mediaPlayer == null) PlayerStatus(
            error = NativeLibs.vlcMissingMessage(packaged = System.getProperty("compose.application.resources.dir") != null),
        ) else PlayerStatus(),
    )
    override val status: StateFlow<PlayerStatus> = _status.asStateFlow()

    private val _videoOutputs = MutableStateFlow(0)
    override val videoOutputs: StateFlow<Int> = _videoOutputs.asStateFlow()

    override val isAvailable: Boolean get() = mediaPlayer != null

    private var currentUrl: String? = null

    // Per-media options for currentUrl, replayed whenever it is (re)started.
    @Volatile private var currentOptions: Array<String> = emptyArray()

    // Remembers the pre-mute level so unmuting restores it. Touched from the
    // EDT (slider) and VLC callback threads, hence @Volatile.
    @Volatile private var preMuteVolume: Int = DEFAULT_VOLUME

    // Volume and mute are read-modify-write on both the status and libVLC, and arrive from
    // the UI thread, tile coroutines and VLC's event thread: one at a time, so a stale
    // write (a late timeChanged) can never undo a newer change.
    private val audioLock = Any()

    init {
        listenToRtxLog()
        engines.follow(normalizerFollower)
        // A change that landed between reading the settings above and following them here
        // would otherwise wait for the next one. A no-op when nothing changed.
        setAudioLevelling(engines.levellingWanted())
    }

    private fun newMediaPlayer(from: VlcEngine): EmbeddedMediaPlayer? = from.newEmbeddedPlayer()?.also { mp ->
        if (audioOutput != null) runCatching { mp.audio().setOutput(audioOutput) }
        mp.events().addMediaPlayerEventListener(events)
    }

    /**
     * Attaches the embedded player's video surface to an AWT [Component]
     * inside a Compose `SwingPanel` (see `VlcPlayerView.kt`). Must be called
     * on the EDT — [SwingPanel]'s factory lambda already runs there.
     */
    // The VLC surface attach can be triggered multiple times by Compose
    // recomposition or HierarchyListener fires. Re-setting the same surface
    // is benign in VLCJ, but tracking the bound component lets us short-circuit
    // and avoid spurious libvlc churn.
    @Volatile private var attachedComponent: Component? = null
    private var hadSurface = false

    override fun attachToPanel(panel: Component) {
        check(SwingUtilities.isEventDispatchThread()) { "attachToPanel must be called on the Swing EDT" }
        if (attachedComponent === panel) return
        // The display decides HDR, and the normalizer may have changed while this player
        // had no window: when it needs another instance and that one is already running,
        // move to it before anything plays.
        if (panel.isDisplayable) {
            val want = desiredKey(panel)
            if (want != key && engines.isBuilt(want)) {
                attachedComponent = null
                switchEngine(want, replay = false)
            } else if (want != key) {
                SwingUtilities.invokeLater(::moveToWantedEngine) // builds it off the UI thread, then moves
            }
            if (rtx != null && !hdrWatch.isRunning) hdrWatch.start()
        }
        val mp = mediaPlayer ?: return
        // VLCJ 4.x: ComponentIdVideoSurface takes a native window handle (long),
        // not an AWT Component. Use the engine's videoSurfaces() factory to mint
        // a platform-appropriate surface (Win32/X11/macOS) bound to this Canvas.
        //
        // CRITICAL: VLCJ doesn't actually bind to the native handle here — it
        // does so lazily inside media().play() via onBeforePlay → attachVideoSurface,
        // which re-checks isDisplayable at THAT moment. So we still gate on
        // isDisplayable here to avoid setting a stale surface, AND the play()
        // call below re-checks just before VLC dereferences the handle.
        if (!panel.isDisplayable) return
        mp.videoSurface().set(engine.newVideoSurface(panel))
        attachedComponent?.removeComponentListener(resizeListener)
        if (rtx != null) panel.addComponentListener(resizeListener)
        attachedComponent = panel
        val moved = hadSurface && replayOnNewSurface
        hadSurface = true
        // Drain any URL that was queued by `play()` before we had a surface to
        // render into — common on first stream open, where ViewModel.init
        // calls play() one EDT-tick before SwingPanel's HierarchyListener fires.
        currentUrl?.let { url ->
            // A past video that had ended stays ended; playing it again starts it over.
            if (moved && isVod && VodPlayback.isEnded(lastGoodMs, _status.value.durationMs)) return@let
            if (moved) _status.update { it.copy(isBuffering = true) }
            if (moved && isVod) pauseAfterStart = pausedByUser
            // A past video moved to a new window carries on from where it is, not from where it began.
            val options = if (moved) optionsToReplay() else currentOptions
            vlc {
                if (moved || mp.status().state() != State.PLAYING) playOnWorker(mp, url, options, "Player attach race")
            }
        }
    }

    /**
     * No-op for VLC. VLCJ tolerates its embedded surface's native peer being
     * destroyed and re-binds to a fresh Canvas on the next [attachToPanel] (a new
     * component fails the `attachedComponent` short-circuit). The interface
     * requires this hook for mpv, whose set-once `wid` cannot survive HWND teardown.
     */
    override fun detachFromPanel() { /* VLC re-attaches; nothing to release early. */ }

    /**
     * Starts playback of [streamUrl] — typically the local proxy's `/stream?...` URL (Section 8.3).
     *
     * If the video surface hasn't been attached yet (Compose hasn't laid out
     * the SwingPanel that hosts our Canvas), we cache the URL and defer the
     * actual `media().play()` call until [attachToPanel] runs. Without this,
     * VLC starts decoding into a 0-handle surface and the user sees a black
     * pane forever — even after the surface eventually binds, libvlc doesn't
     * re-target an already-running session.
     */
    override fun play(streamUrl: String, lowLatency: Boolean) {
        // VLC starts a live HLS stream 15 s back from the end of the playlist by
        // default; low latency starts it LiveLatency.VLC_LIVE_DELAY_MS back.
        val options = if (lowLatency) arrayOf(":adaptive-livedelay=${LiveLatency.VLC_LIVE_DELAY_MS}") else emptyArray()
        isVod = false
        start(streamUrl, options)
    }

    /**
     * Plays a past video from [startMs]. Unlike a live stream it keeps its place: when the
     * video moves to another window, or is played again after it stopped, it starts from
     * where it was (see [optionsToReplay]) instead of from [startMs] again.
     */
    fun playVod(videoUrl: String, startMs: Long, keepPaused: Boolean = false) {
        isVod = true
        lastGoodMs = startMs.coerceAtLeast(0)
        pauseAfterStart = keepPaused
        _status.update { it.copy(positionMs = lastGoodMs) }
        start(videoUrl, VodPlayback.startOptions(startMs))
    }

    // True once a past video was started: its reloads resume at the current position.
    @Volatile private var isVod = false

    // The last place the video was really at (see [VodPlayback.nextGood]); reloads start here.
    @Volatile var lastGoodMs = 0L
        private set

    // True while the viewer has the video paused, so a reload can show it paused again.
    @Volatile var pausedByUser = false
        private set
    @Volatile private var pauseAfterStart = false

    /** The per-media options for starting [currentUrl] again: a live stream as it was, a past video where it is now. */
    private fun optionsToReplay(): Array<String> =
        if (isVod) VodPlayback.startOptions(lastGoodMs) else currentOptions

    private fun start(streamUrl: String, options: Array<String>) {
        if (mediaPlayer == null) return
        currentUrl = streamUrl
        currentOptions = options
        _status.update { it.copy(isBuffering = true, error = null) }
        val component = attachedComponent ?: return
        // else: attachToPanel will pick up currentUrl and start playback once
        // the surface is bound. See its tail.
        SwingUtilities.invokeLater {
            // The RTX Video or normalizer settings changed since this player last played:
            // move to the instance they ask for before starting. Read the player after, not
            // before. Only when that instance is already running: starting libVLC here
            // would freeze the window for its plugin scan. Otherwise this stream starts
            // where the player is, and moves over (started again) once the other is built.
            desiredKey().let { want ->
                if (want != key) {
                    if (!switchPending && engines.isBuilt(want)) switchEngine(want, replay = false) else moveToWantedEngine()
                }
            }
            val mp = mediaPlayer ?: return@invokeLater
            // VLCJ's ComponentVideoSurface.attach() — invoked transitively by
            // media().play() via EmbeddedMediaPlayer.onBeforePlay() — re-checks
            // isDisplayable at play-time, not just when we set the surface. The
            // Canvas can briefly lose its native peer during Compose recomposition
            // (SwingPanel relayout) between attachToPanel returning and this
            // invokeLater firing. If we don't re-check we get
            // "IllegalStateException: The video surface component must be displayable"
            // thrown into the AWT-EventQueue thread, which kills the EDT.
            if (!component.isDisplayable) {
                attachedComponent = null  // force re-attach when HierarchyListener fires next
                return@invokeLater
            }
            vlc { playOnWorker(mp, streamUrl, options, "Playback start failed") }
        }
    }

    /**
     * Starts [url] on [mp], on the worker. The canvas can lose its native window between
     * the UI thread's check and this call (vlcj then refuses: "must be displayable"): hand
     * the surface back to the UI thread so the next attach starts it again.
     */
    private fun playOnWorker(mp: EmbeddedMediaPlayer, url: String, options: Array<String>, failure: String) {
        if (mp !== mediaPlayer) return
        runCatching { mp.media().play(url, *options) }.onFailure { e ->
            if (e is IllegalStateException) {
                SwingUtilities.invokeLater { if (mediaPlayer === mp) attachedComponent = null }
            } else {
                _status.update { it.copy(isPlaying = false, isBuffering = false, error = "$failure: ${e.message}") }
            }
        }
    }

    override fun pause() {
        val mp = mediaPlayer ?: return
        vlc { if (mp === mediaPlayer && mp.status().state() == State.PLAYING) mp.controls().pause() }
    }

    /** Seek to an absolute position. No-op for non-seekable (live) media. */
    override fun seekTo(positionMs: Long) {
        val mp = mediaPlayer ?: return
        val target = positionMs.coerceAtLeast(0)
        if (isVod) {
            // The target is the place now, until libVLC's clock catches up with it.
            lastGoodMs = target
            _status.update { it.copy(positionMs = target) }
        }
        val url = currentUrl
        vlc {
            if (mp !== mediaPlayer) return@vlc
            val state = mp.status().state()
            if (isVod && url != null && (state == State.ENDED || state == State.STOPPED)) {
                // Seeking after the video ended plays it again from the target.
                pauseAfterStart = false
                playOnWorker(mp, url, VodPlayback.startOptions(target), "Playback start failed")
            } else if (mp.status().isSeekable) {
                mp.controls().setTime(target)
            }
        }
    }

    override fun resume() {
        val mp = mediaPlayer ?: return
        val url = currentUrl
        // A video that ended plays again from its start; one that stopped, from where it was.
        val options = if (isVod) {
            VodPlayback.startOptions(VodPlayback.replayStartMs(lastGoodMs, _status.value.durationMs))
        } else currentOptions
        vlc {
            if (mp !== mediaPlayer) return@vlc
            if (mp.status().state() == State.PAUSED) mp.controls().play()
            else if (url != null) {
                pauseAfterStart = false
                playOnWorker(mp, url, options, "Playback start failed")
            }
        }
    }

    override fun togglePlayPause() {
        if (_status.value.isPlaying) pause() else resume()
    }

    override fun stop() {
        val mp = mediaPlayer ?: return
        currentUrl = null
        vlc {
            // Moved to another instance meanwhile: retire() already stopped and freed this one.
            if (mp !== mediaPlayer) return@vlc
            val started = System.nanoTime()
            mp.controls().stop()
            // libVLC's stop waits for the stream's threads (on this worker, not the UI thread).
            TimingLog.log("player.stop", (System.nanoTime() - started) / 1_000_000, if (rtxChoice.any) "rtx" else "standard")
        }
    }

    /** [volume] is 0–100, matching VLC's native scale (Section 8.4 volume slider). */
    override fun setVolume(volume: Int) = synchronized(audioLock) {
        val mp = mediaPlayer ?: return@synchronized
        val next = applyVolumeChange(VolumeState(_status.value.volume, _status.value.isMuted, preMuteVolume), volume)
        preMuteVolume = next.preMute
        // INSTANT volume — do NOT marshal through SwingUtilities.invokeLater.
        // The slider's onValueChange already runs on the EDT, so invokeLater only
        // *re-queued* this work behind whatever AWT repaint/resize tasks the
        // heavyweight VLC video surface had pending — a backlog that grew to the
        // ~2s lag users reported while a stream was playing. Neither call needs
        // the EDT: libvlc_audio_set_volume is thread-safe, and MutableStateFlow
        // updates are safe from any thread (see class header). Publishing the UI
        // state first keeps the slider glued to the cursor.
        _status.update { it.copy(volume = next.volume, isMuted = next.isMuted) }
        syncAudioLater(mp)
    }

    /** Toggles mute, remembering the pre-mute level so unmute restores it. */
    override fun toggleMute() = synchronized(audioLock) {
        val mp = mediaPlayer ?: return@synchronized
        val next = applyMuteToggle(VolumeState(_status.value.volume, _status.value.isMuted, preMuteVolume))
        preMuteVolume = next.preMute
        _status.update { it.copy(volume = next.volume, isMuted = next.isMuted) }
        syncAudioLater(mp)
    }

    override fun release() {
        released = true
        engines.unfollow(normalizerFollower)
        SwingUtilities.invokeLater { hdrWatch.stop(); remeasureTimer.stop() }
        val mp = mediaPlayer
        // Only this player's own media player; libVLC belongs to the engine. Queued behind
        // anything still pending for it, then the worker ends (see awaitAllReleased).
        vlc {
            mp?.controls()?.stop()
            mp?.release()
            rtxLog?.release()
            rtxLog = null
        }
        worker.shutdown()
    }

    // ---- Moving between libVLC instances (RTX Video, the audio normalizer) -----------

    /** The RTX Video settings changed (main player only): move to the instance they ask for. */
    override fun applyRtxVideo() {
        if (rtx == null) return
        moveToWantedEngine()
    }

    /**
     * Turns the audio normalizer on (at [level]), off (null) or to another strength for
     * the stream that is playing. VLC 3.0 only takes audio filters when an instance
     * starts, so this moves the player to the instance with that level's arguments (or
     * none) and starts the stream again there: live streams rejoin at the live edge, past
     * videos carry on from where they are (see [optionsToReplay]). A second or two of
     * rebuffering, no restart. With nothing playing it still moves now, so the next
     * stream starts on the right one without waiting for libVLC to load. Safe from any
     * thread.
     */
    override fun setAudioLevelling(level: AudioLevelling?) {
        if (released || levellingWanted == level) return
        levellingWanted = level
        if (SwingUtilities.isEventDispatchThread()) moveToWantedEngine()
        else SwingUtilities.invokeLater(::moveToWantedEngine)
    }

    /**
     * Moves to the instance [desiredKey] names, if this player is not on it. libVLC scans
     * its plugins when an instance starts, and the display query is a native call: both
     * happen off the UI thread, then the move happens on it. UI thread only (so the
     * [switchPending] check and set cannot race).
     */
    private fun moveToWantedEngine() {
        if (released || switchPending) return
        switchPending = true
        Thread({
            val wanted = desiredKey()
            if (wanted != key) engines.engineFor(wanted).warmUp()
            SwingUtilities.invokeLater {
                switchPending = false
                if (released) return@invokeLater
                val now = desiredKey()
                when {
                    now == wanted && wanted != key -> switchEngine(wanted, replay = true)
                    // The wish changed while that instance was loading (the A key pressed
                    // twice, another strength picked, a window moved to another display):
                    // go again for the newest.
                    now != key -> moveToWantedEngine()
                }
                refreshRtxStatus()
            }
        }, "vlc-engine-switch").apply { isDaemon = true }.start()
    }

    /**
     * Moves this player to the instance for [wanted]: a new media player there, bound to
     * the same video surface, and with [replay] the stream started again where it was.
     * The old media player is stopped and freed on the worker, before the new one plays.
     * UI thread only.
     */
    private fun switchEngine(wanted: VlcEngineKey, replay: Boolean) {
        if (released || wanted == key) return
        val next = engines.engineFor(wanted)
        val fresh = newMediaPlayer(next) ?: return // that instance would not start: stay put
        // Released while the new player was being made (release() may run off the UI
        // thread): free the new one rather than leak it, and leave the old to release().
        // It never played, so freeing it here is quick, and the worker may be gone.
        if (released) {
            runCatching { fresh.release() }
            return
        }
        val old = mediaPlayer
        key = wanted
        engine = next
        mediaPlayer = fresh
        listenToRtxLog()
        old?.let(::retire)
        val component = attachedComponent
        if (component == null || !component.isDisplayable) {
            attachedComponent = null // the next attachToPanel binds the new player
            return
        }
        fresh.videoSurface().set(next.newVideoSurface(component))
        if (!replay) return
        val url = currentUrl ?: return
        // A VOD that had played to its end stays ended rather than replaying its last moment.
        if (isVod && VodPlayback.isEnded(lastGoodMs, _status.value.durationMs)) return
        _status.update { it.copy(isBuffering = true) }
        if (isVod) pauseAfterStart = pausedByUser
        val options = optionsToReplay()
        vlc { playOnWorker(fresh, url, options, "Playback start failed") }
    }

    /**
     * Stops and frees a media player this one moved away from, on the worker: queued ahead
     * of the new player's start, and every task for a player checks it is still the current
     * one, so nothing reaches the old one once it is freed.
     */
    private fun retire(old: EmbeddedMediaPlayer) {
        old.events().removeMediaPlayerEventListener(events)
        vlc {
            runCatching { old.controls().stop() }
            runCatching { old.release() }
        }
    }

    // Diagnostics only: -Dpuretv.vlc.logfile=<path> appends the RTX instance's whole libVLC
    // debug log to that file and keeps listening (libVLC allows one log listener per
    // instance, and the bundled VLC has no file-logging plugin). Off unless set.
    private val diagnosticLog: java.io.PrintWriter? by lazy {
        System.getProperty("puretv.vlc.logfile")?.takeIf { it.isNotBlank() }
            ?.let { runCatching { java.io.PrintWriter(java.io.FileWriter(it, true), true) }.getOrNull() }
    }

    /** Reads the log of the instance this player is on, when that is an RTX one. */
    private fun listenToRtxLog() {
        val reader = RtxLogReader(rtxChoice)
        rtxReader = reader
        _rtxStatus.value = withDisplayHdr(reader.status())
        val choice = rtxChoice
        val logEngine = engine
        vlc { attachRtxLog(choice, logEngine, reader) }
    }

    /** The native log part of [listenToRtxLog], on the worker (it owns [rtxLog]). */
    private fun attachRtxLog(choice: RtxVideoChoice, logEngine: VlcEngine, reader: RtxLogReader) {
        rtxLog?.release()
        rtxLog = null
        if (!choice.any || reader !== rtxReader) return
        // libVLC started, but without the RTX argument: nothing will ever be reported.
        if (!logEngine.optionalAccepted) {
            synchronized(reader) { reader.refused() }
            _rtxStatus.value = withDisplayHdr(synchronized(reader) { reader.status() })
            return
        }
        rtxLog = logEngine.newDebugLog()?.apply {
            addLogListener(LogEventListener { _, module, _, _, _, _, _, message ->
                // libVLC's own thread, inside its logging: never call back into libVLC
                // from here, only note the line and look at sizes on the UI thread.
                diagnosticLog?.let { out -> synchronized(out) { out.println("$module: $message") } }
                if (message != null && (module == "direct3d11" || module == "avcodec")) {
                    val changed = synchronized(reader) { reader.read(module, message) }
                    if (changed) SwingUtilities.invokeLater { refreshRtxStatus() }
                }
            })
        }
    }

    // The drawn size changes the Super Resolution verdict (it only works when enlarging),
    // and VLC's RTX output needs a nudge once the size settles (see [remeasureVideo]).
    private val resizeListener = object : ComponentAdapter() {
        override fun componentResized(e: ComponentEvent) {
            refreshRtxStatus()
            if (rtxChoice.any) remeasureTimer.restart()
        }
    }

    private val remeasureTimer = Timer(300) { remeasureVideo() }.apply { isRepeats = false }

    /**
     * Makes VLC measure its window again. With Super Resolution or RTX HDR, VLC 3.0.24
     * keeps its upscaled frame at a size it already had when the window flips back to it
     * (opening and closing the player menu, after it rebuilt its output for a quality
     * change): it logs the resize but its Direct3D 11 output skips it, and the picture
     * stays small in a black area. An aspect-ratio change makes that output re-measure
     * the window and rebuild the upscaled frame (VOUT_DISPLAY_CHANGE_SOURCE_ASPECT in
     * VLC's direct3d11.c), so set the video's own shape and clear it again: nothing on
     * screen changes shape, and the frame is redrawn at the window's real size. UI thread.
     */
    private fun remeasureVideo() {
        if (released || !rtxChoice.any) return
        val mp = mediaPlayer ?: return
        vlc {
            if (mp !== mediaPlayer) return@vlc
            val size = mp.video().videoDimension() ?: return@vlc
            if (size.width <= 0 || size.height <= 0) return@vlc
            mp.video().setAspectRatio("${size.width}:${size.height}")
            mp.video().setAspectRatio(null)
        }
    }

    /**
     * HDR asked for but not on this instance: say why. Either the window's display has
     * Windows HDR off (the usual case, and what the viewer can change), or this libVLC
     * is one whose HDR mode crashes (see [RtxVideoChoice.forVlc]).
     */
    private fun withDisplayHdr(s: RtxVideoStatus): RtxVideoStatus {
        val support = rtx ?: return s
        if (!support.wanted().hdr || rtxChoice.hdr) return s
        return s.copy(hdr = if (support.vlcHdrSafe) RtxHdrState.DISPLAY_SDR else RtxHdrState.UNSUPPORTED)
    }

    /**
     * Publishes [rtxStatus] from the log and the sizes now. Called on the UI thread, which
     * reads the window's size; the video's own size is a libVLC call, so the rest runs on
     * the worker.
     */
    private fun refreshRtxStatus() {
        if (!rtxChoice.any && rtx?.wanted()?.any != true) return
        val reader = rtxReader
        val mp = mediaPlayer ?: return
        val component = attachedComponent
        val scale = component?.graphicsConfiguration?.defaultTransform?.scaleX ?: 1.0
        val areaW = (component?.width ?: 0) * scale
        val areaH = (component?.height ?: 0) * scale
        vlc { publishRtxStatus(mp, reader, areaW, areaH) }
    }

    private fun publishRtxStatus(mp: EmbeddedMediaPlayer, reader: RtxLogReader, areaW: Double, areaH: Double) {
        if (mp !== mediaPlayer || reader !== rtxReader) return
        val source = runCatching { mp.video().videoDimension() }.getOrNull()
        if (source != null && areaW > 0) {
            val (w, h) = fittedSize(source.width, source.height, areaW, areaH)
            synchronized(reader) { reader.sized(source.width, source.height, w, h) }
        }
        val next = withDisplayHdr(synchronized(reader) { reader.status() })
        // Once the verdict and the GPU are known the log has nothing more to say, and
        // at debug level it is busy: stop listening (sizes come from the component).
        if (diagnosticLog == null && synchronized(reader) { reader.settled }) {
            rtxLog?.release()
            rtxLog = null
        }
        val was = _rtxStatus.value
        if (next.superResolution != was.superResolution || next.hdr != was.hdr || next.adapter != was.adapter) {
            // Diagnostics: state changes only, not every pixel of a window resize.
            TimingLog.log(
                "player.rtx_video", 0,
                "sr=${next.superResolution} hdr=${next.hdr} gpu=${next.adapter} ${rtxSizeLabel(next)}",
            )
        }
        _rtxStatus.value = next
    }

    /**
     * The F3 stats overlay (see [vlcStatsOsd]), drawn by VLC's own marquee on the video:
     * Compose cannot paint over the native surface. Called about once a second while it
     * is shown, and once with false to clear it. UI thread.
     */
    override fun renderStatsOverlay(show: Boolean) {
        val mp = mediaPlayer ?: return
        val component = attachedComponent
        val scale = component?.graphicsConfiguration?.defaultTransform?.scaleX ?: 1.0
        val areaW = (component?.width ?: 0) * scale
        val areaH = (component?.height ?: 0) * scale
        vlc { drawStats(mp, show, areaW, areaH) }
    }

    /** [renderStatsOverlay] on the worker, which owns the overlay's state below. */
    private fun drawStats(mp: EmbeddedMediaPlayer, show: Boolean, areaW: Double, areaH: Double) {
        if (mp !== mediaPlayer) return
        run {
            val marquee = mp.marquee()
            if (!show) {
                if (statsOn === mp) marquee.enable(false)
                statsOn = null
                return
            }
            val source = mp.video().videoDimension()
            val (outW, outH) = if (source != null && areaW > 0) {
                fittedSize(source.width, source.height, areaW, areaH)
            } else 0 to 0
            val stats = mp.media().info()?.statistics()
            val text = vlcStatsOsd(
                status = _rtxStatus.value,
                sourceWidth = source?.width ?: 0,
                sourceHeight = source?.height ?: 0,
                outputWidth = outW,
                outputHeight = outH,
                picturesLost = stats?.picturesLost() ?: 0,
            )
            // Every marquee setting change makes VLC rebuild its overlay filter: change only
            // what differs, and switch it on once per player.
            val size = (outH / 40).coerceIn(16, 34)
            if (statsOn !== mp || text != statsText) {
                marquee.setText(text)
                TimingLog.log("player.stats_osd", 0, text.lines().joinToString(" | "))
            }
            if (statsOn !== mp || size != statsSize) marquee.setSize(size)
            statsText = text
            statsSize = size
            if (statsOn !== mp) {
                marquee.setPosition(uk.co.caprica.vlcj.player.base.MarqueePosition.TOP_LEFT)
                marquee.setLocation(34, 30)
                marquee.setColour(0xFFFFFF)
                marquee.setOpacity(235)
                marquee.setTimeout(0)
                marquee.enable(true)
                statsOn = mp
            }
        }
    }

    // The media player the stats overlay is switched on for (an instance switch makes a new one).
    private var statsOn: EmbeddedMediaPlayer? = null
    private var statsText = ""
    private var statsSize = 0

    /**
     * Mutes or unmutes without touching the remembered volume. Multi-stream keeps
     * every tile playing and only the active one audible, so switching which tile
     * you hear never reloads a stream.
     */
    fun setMuted(muted: Boolean) = synchronized(audioLock) {
        if (_status.value.isMuted != muted) toggleMute()
    }

    /**
     * Makes libVLC's volume and mute agree with [status]: a muted player plays at
     * volume 0, an unmuted one at its volume with libVLC's own mute flag off. Reads
     * first, so the common case (already in agreement) changes nothing.
     */
    private fun syncAudioLater(mp: MediaPlayer) = vlc { if (mp === mediaPlayer) syncAudio(mp) }

    private fun syncAudio(mp: MediaPlayer) = synchronized(audioLock) {
        val s = _status.value
        val want = if (s.isMuted) 0 else s.volume
        runCatching {
            val audio = mp.audio()
            if (audio.volume() != want) audio.setVolume(want)
            if (!s.isMuted && audio.isMute) audio.setMute(false)
        }
    }


    companion object {
        // Every player's worker, so shutdown can wait for them before libVLC itself goes.
        private val workers: MutableSet<ExecutorService> = ConcurrentHashMap.newKeySet()

        /**
         * Waits up to [timeoutMs] for every released player's queued libVLC work (its last
         * stop and release) to finish. Call from main() after the UI has gone and before the
         * engines are released; never from the UI thread.
         */
        fun awaitAllReleased(timeoutMs: Long) {
            val deadline = System.nanoTime() + timeoutMs * 1_000_000
            for (w in workers.toList()) {
                w.shutdown()
                val left = (deadline - System.nanoTime()) / 1_000_000
                if (left <= 0 || !runCatching { w.awaitTermination(left, TimeUnit.MILLISECONDS) }.getOrDefault(false)) break
                workers.remove(w)
            }
        }
    }
}
