package com.puretv.twitch.desktop.player

import com.puretv.twitch.core.perf.TimingLog

import com.puretv.twitch.core.model.PlaybackBackend
import com.puretv.twitch.core.model.UpscalingMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.awt.Component

/**
 * Backend-agnostic playback contract. Implemented by [VlcPlayer] (default) and
 * [MpvPlayer] (opt-in); the concrete backend is chosen once at DI time from the
 * `playbackBackend` setting. A player is a Koin singleton, so the session
 * persists across navigation and [release] runs once at app shutdown.
 *
 * Lifecycle: [attachToPanel] MUST be called (on the Swing EDT, with a
 * displayable heavyweight component) before video can render. A [play] issued
 * before the surface is attached is cached and drained once [attachToPanel]
 * binds it — callers don't have to sequence the two.
 *
 * Threading: [status] is safe to collect from any thread; transport calls
 * (play/pause/seek/setVolume/…) are safe to invoke from the UI thread.
 */
interface DesktopPlayer {
    val status: StateFlow<PlayerStatus>
    val isAvailable: Boolean

    /** Whether this (running) backend can GPU-upscale — true only for an available mpv. */
    val supportsUpscaling: Boolean get() = false

    /**
     * Plays [streamUrl]. [lowLatency] (Settings > Low latency, live streams only)
     * asks the backend to start closer to the live edge; VODs leave it off.
     */
    fun play(streamUrl: String, lowLatency: Boolean = false)
    fun pause()
    fun resume()
    fun togglePlayPause()
    fun stop()
    fun seekTo(positionMs: Long)
    fun setVolume(volume: Int)
    fun toggleMute()

    /** Binds the video output to [panel]'s native surface. EDT-only; see lifecycle note above. */
    fun attachToPanel(panel: Component)

    /**
     * Releases the video output from its current surface BEFORE that surface's
     * native peer (HWND) is destroyed — driven from the Canvas's `removeNotify`.
     * Critical for backends (mpv) whose window handle is bound once and cannot
     * survive its HWND being torn down; a no-op for backends (VLC) that re-attach.
     * EDT-only. Idempotent.
     */
    fun detachFromPanel()
    fun release()

    /**
     * Show (or hide) the live upscaling stats overlay on the video surface. mpv
     * renders it via its own OSD — the only way to draw over the heavyweight AWT
     * video Canvas, and the most honest proof (drawn by the engine doing the
     * upscaling). Call repeatedly while visible to keep it refreshed; pass `false`
     * to clear. Default no-op (VLC has no equivalent). EDT-only.
     */
    fun renderStatsOverlay(show: Boolean) {}

    /**
     * Apply an [UpscalingMode] to the LIVE video, immediately — no restart. mpv
     * pushes the scaler chain to the running context so the picture changes on the
     * next frame. Default no-op (VLC has no GPU upscaler in this build). The caller
     * still persists the mode so it survives restarts. Call from the UI thread, like
     * the other transport calls (the running player's context is read, not freed).
     */
    fun setUpscaling(mode: UpscalingMode) {}

    /**
     * Turn the audio normalizer (AudioNormalizerProfile) on or off for the LIVE
     * audio. mpv swaps its audio filter on the running context. Default no-op:
     * VLC only takes audio filters when its instance is built (see VlcPlayer's
     * `extraArgs`), so there the setting applies after a restart. The caller
     * persists the setting either way.
     */
    fun setAudioNormalizer(enabled: Boolean) {}

    /**
     * What NVIDIA RTX Video is doing for this player's video (see [RtxVideoChoice]).
     * Stays at its default, everything off, for players that never use it.
     */
    val rtxStatus: StateFlow<RtxVideoStatus> get() = RTX_VIDEO_OFF

    /**
     * How many times the player has opened a video output, so a stream screen can time
     * its first picture (a stream's "playing" comes before any frame is drawn). Null for
     * a player that cannot tell.
     */
    val videoOutputs: StateFlow<Int>? get() = null

    /** Whether this player can run NVIDIA RTX Video at all, so the menu offers it. */
    val supportsRtxVideo: Boolean get() = false

    /** Whether RTX Video HDR is offered too (it then applies on displays with Windows HDR on; see RtxVideoChoice.forVlc). */
    val supportsRtxHdr: Boolean get() = false

    /**
     * Reads the RTX Video settings again and applies them to the playing video. VLC
     * takes them only when an instance starts, so a change moves the video to another
     * instance and starts it again (a second or two of rebuffering). Default no-op.
     * Call from the UI thread.
     */
    fun applyRtxVideo() {}
}

/** The [DesktopPlayer.rtxStatus] of a player that never uses RTX Video. */
val RTX_VIDEO_OFF: StateFlow<RtxVideoStatus> = MutableStateFlow(RtxVideoStatus()).asStateFlow()

/**
 * A point-in-time snapshot of what the video backend is actually rendering —
 * the honest evidence that upscaling is (or isn't) engaging. `outputWidth/Height`
 * is the VO surface size mpv scales the source UP to; compare against
 * `sourceWidth/Height` via `upscaleSummary` for the verdict.
 */
data class VideoStats(
    val sourceWidth: Int,
    val sourceHeight: Int,
    val outputWidth: Int,
    val outputHeight: Int,
    val scaler: String?,
    val shaderName: String?,
    val hwdec: String?,
    val vo: String?,
    val fps: Double?,
)

data class PlayerStatus(
    val isReady: Boolean = false,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val volume: Int = DEFAULT_VOLUME,
    val isMuted: Boolean = false,
    val error: String? = null,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val isSeekable: Boolean = false,
)

/**
 * Holds the one [DesktopPlayer], created on first use. Creating it loads libVLC
 * (and scans its plugins) or the libmpv DLL, so it stays off the path to the
 * first window. [releaseIfCreated] lets shutdown skip building a player only to
 * free it.
 */
class DesktopPlayerProvider(create: () -> DesktopPlayer) {
    private val lazyPlayer = lazy {
        // -Dpuretv.diag.playerDelayMs=<ms>: a slow build on demand, to check that nothing
        // waits for it on the UI thread (with -Dpuretv.diag.stalls=true).
        Integer.getInteger("puretv.diag.playerDelayMs")?.let { Thread.sleep(it.toLong()) }
        create()
    }

    val player: DesktopPlayer get() = lazyPlayer.value

    /** Builds the player ahead of the first stream open. Call off the UI thread. */
    fun warmUp() {
        if (lazyPlayer.isInitialized()) return
        val started = System.nanoTime()
        val built = lazyPlayer.value
        TimingLog.log(
            "app.player_built",
            (System.nanoTime() - started) / 1_000_000,
            if (built.isAvailable) "ready" else "unavailable",
        )
    }

    /** Whether the player exists, so reading [player] costs nothing. */
    val isBuilt: Boolean get() = lazyPlayer.isInitialized()

    /** Builds the player if needed, or waits for the build already running. Call off the UI thread. */
    fun awaitBuilt() {
        lazyPlayer.value
    }

    fun releaseIfCreated() {
        if (lazyPlayer.isInitialized()) lazyPlayer.value.release()
    }

    /**
     * The engine actually running this session: the player's own type once it is
     * built, otherwise [saved] (which is what it will be built from). The saved
     * engine can differ mid-session, because switching engines applies after a restart.
     */
    fun runningBackend(saved: PlaybackBackend): PlaybackBackend =
        if (!lazyPlayer.isInitialized()) saved
        else if (lazyPlayer.value is MpvPlayer) PlaybackBackend.MPV else PlaybackBackend.VLC

    /** The player's RTX Video status, or all off while there is no player yet (settings never build one). */
    fun rtxStatusIfCreated(): StateFlow<RtxVideoStatus> =
        if (lazyPlayer.isInitialized()) lazyPlayer.value.rtxStatus else RTX_VIDEO_OFF

    /** Whether the running player offers RTX Video HDR; false until there is a player. */
    fun offersRtxHdr(): Boolean = lazyPlayer.isInitialized() && lazyPlayer.value.supportsRtxHdr

    /** Runs [block] on the player only if it already exists, so settings never build one. */
    fun ifCreated(block: (DesktopPlayer) -> Unit) {
        if (lazyPlayer.isInitialized()) block(lazyPlayer.value)
    }
}
