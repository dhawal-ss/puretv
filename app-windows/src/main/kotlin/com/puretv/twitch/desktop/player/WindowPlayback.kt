package com.puretv.twitch.desktop.player

import com.puretv.twitch.core.model.UpscalingMode
import java.awt.Component
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The player a window's stream and video screens use, with the hand-off ticket that goes
 * with it (see [PlayTicket]).
 *
 * The main window's is the app-wide player the viewer picked in Settings (VLC or mpv) and
 * the app-wide ticket, exactly as before windows were many. Every other window has a
 * player of its own ([secondary]): always VLC, made from the shared engine with its own
 * audio output so its volume is its own, and released when the window closes. Windows
 * therefore never fight over one player, and one window's stop cannot end another's stream.
 *
 * [isolated] is true for those other windows. They report ad blocking per channel instead
 * of through the app-wide status, and give a channel back when they leave it (see
 * [ChannelClaims]).
 */
class WindowPlayback(
    val playTicket: PlayTicket,
    private val source: () -> DesktopPlayer,
    val isolated: Boolean = false,
    val claims: ChannelClaims = ChannelClaims(),
    private val onRelease: () -> Unit = {},
    // Whether [player] is already built, and the build itself. The main window's player is
    // built in the background after startup; a window's own player before its screens exist.
    private val built: () -> Boolean = { true },
    private val build: () -> Unit = {},
) {
    val player: DesktopPlayer get() = source()

    /** True when reading [player] costs nothing; until then it would build it, which can take seconds. */
    val isReady: Boolean get() = built()

    /** Builds [player] if it is not built yet. Blocking: call off the UI thread (see PlayerGate). */
    fun prepare() = build()

    /** Frees what this window's player holds. Safe to call more than once. */
    fun release() = onRelease()

    companion object {
        /**
         * A window's own player. The engine is loaded and the player made by [prepare], off
         * the UI thread (libVLC's plugin scan is slow); the screens only read [player] after.
         */
        fun secondary(engine: VlcEngine, claims: ChannelClaims): SecondaryPlayback = SecondaryPlayback(engine, claims)
    }
}

/** The own-player [WindowPlayback] of a secondary window, in two steps: [prepare], then use. */
class SecondaryPlayback(private val engine: VlcEngine, private val claims: ChannelClaims) {
    @Volatile private var built: ReleasablePlayer? = null
    private val released = AtomicBoolean(false)

    /** Loads libVLC if needed and makes the player. Call off the UI thread; true when it is usable. */
    fun prepare(): Boolean {
        if (released.get()) return false
        if (built == null) {
            val player = ReleasablePlayer(VlcPlayer(engine, audioOutput = TILE_AUDIO_OUTPUT))
            built = player
            // The window closed while libVLC was loading.
            if (released.get()) player.release()
        }
        return !released.get()
    }

    /** The window's playback; only valid once [prepare] returned true. */
    val playback: WindowPlayback by lazy {
        WindowPlayback(
            playTicket = PlayTicket(),
            source = { checkNotNull(built) { "prepare() has not finished" } },
            isolated = true,
            claims = claims,
            onRelease = ::release,
        )
    }

    fun release() {
        if (!released.compareAndSet(false, true)) return
        built?.release()
    }
}

/**
 * Wraps a player so that once it is released every call is a no-op. A screen leaving
 * composition stops its player a moment before or after the window releases it, and a
 * stop sent to a freed libVLC player is not safe.
 */
class ReleasablePlayer(private val inner: DesktopPlayer) : DesktopPlayer {
    private val gone = AtomicBoolean(false)

    override val status get() = inner.status
    override val isAvailable: Boolean get() = inner.isAvailable
    override val supportsUpscaling: Boolean get() = inner.supportsUpscaling

    override fun play(streamUrl: String, lowLatency: Boolean) { if (!gone.get()) inner.play(streamUrl, lowLatency) }
    override fun pause() { if (!gone.get()) inner.pause() }
    override fun resume() { if (!gone.get()) inner.resume() }
    override fun togglePlayPause() { if (!gone.get()) inner.togglePlayPause() }
    override fun stop() { if (!gone.get()) inner.stop() }
    override fun seekTo(positionMs: Long) { if (!gone.get()) inner.seekTo(positionMs) }
    override fun setVolume(volume: Int) { if (!gone.get()) inner.setVolume(volume) }
    override fun toggleMute() { if (!gone.get()) inner.toggleMute() }
    override fun attachToPanel(panel: Component) { if (!gone.get()) inner.attachToPanel(panel) }
    override fun detachFromPanel() { if (!gone.get()) inner.detachFromPanel() }
    override fun renderStatsOverlay(show: Boolean) { if (!gone.get()) inner.renderStatsOverlay(show) }
    override fun setUpscaling(mode: UpscalingMode) { if (!gone.get()) inner.setUpscaling(mode) }
    override fun setAudioNormalizer(enabled: Boolean) { if (!gone.get()) inner.setAudioNormalizer(enabled) }
    override val videoOutputs get() = inner.videoOutputs
    override val rtxStatus get() = inner.rtxStatus
    override val supportsRtxVideo: Boolean get() = inner.supportsRtxVideo
    override val supportsRtxHdr: Boolean get() = inner.supportsRtxHdr
    override fun applyRtxVideo() { if (!gone.get()) inner.applyRtxVideo() }

    override fun release() {
        if (!gone.compareAndSet(false, true)) return
        // Stop first, then free; both queue on the UI thread in this order.
        inner.stop()
        inner.release()
    }
}

/**
 * Which windows are watching which channel, so a window that leaves a channel only forgets
 * its ad-block status when no other window still watches it (forgetting mutes the channel's
 * reports, and would blank the other window's pill).
 */
class ChannelClaims {
    private val counts = HashMap<String, Int>()

    @Synchronized
    fun acquire(login: String) {
        val key = login.lowercase()
        counts[key] = (counts[key] ?: 0) + 1
    }

    /** True when [login] is no longer watched by anyone through these claims. */
    @Synchronized
    fun release(login: String): Boolean {
        val key = login.lowercase()
        val left = (counts[key] ?: 0) - 1
        if (left > 0) {
            counts[key] = left
            return false
        }
        counts.remove(key)
        return true
    }
}
