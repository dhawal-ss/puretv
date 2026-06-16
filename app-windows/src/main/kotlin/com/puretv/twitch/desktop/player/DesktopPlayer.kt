package com.puretv.twitch.desktop.player

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
    fun release()
}

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
