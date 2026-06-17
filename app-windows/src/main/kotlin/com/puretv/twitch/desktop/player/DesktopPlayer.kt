package com.puretv.twitch.desktop.player

import com.puretv.twitch.core.model.UpscalingMode
import kotlinx.coroutines.flow.StateFlow
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
    fun play(streamUrl: String)
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
}

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
