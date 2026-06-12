package com.puretv.twitch.desktop.player

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import uk.co.caprica.vlcj.factory.MediaPlayerFactory
import uk.co.caprica.vlcj.player.base.MediaPlayer
import uk.co.caprica.vlcj.player.base.MediaPlayerEventAdapter
import uk.co.caprica.vlcj.player.base.State
import uk.co.caprica.vlcj.player.embedded.EmbeddedMediaPlayer
import java.awt.Component
import javax.swing.SwingUtilities

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
 */
class VlcPlayer {

    // Must be initialised before `factory` so jna.library.path is set before
    // VLCJ's JNA binding loads libvlc.dll.  Kotlin initialises properties in
    // declaration order within the class body.
    @Suppress("unused")
    private val vlcBundledDir: String? = detectBundledVlc()

    /**
     * `--no-video-title-show`     — suppress VLC's on-video filename overlay.
     * `--network-caching=2000`    — 2s network buffer; smooths over Twitch's
     *                               variable segment delivery without adding
     *                               noticeable latency to a live stream.
     * `--live-caching=1000`       — separate, shorter buffer specifically for
     *                               live sources.
     * `--sout-mux-caching=500`    — output muxer cache; keeps A/V sync tight.
     */
    private val factory: MediaPlayerFactory? = runCatching {
        MediaPlayerFactory(
            "--no-video-title-show",
            "--network-caching=2000",
            "--live-caching=1000",
            "--sout-mux-caching=500",
        )
    }.getOrNull()

    private val mediaPlayer: EmbeddedMediaPlayer? = factory?.mediaPlayers()?.newEmbeddedMediaPlayer()

    private val _status = MutableStateFlow(
        if (mediaPlayer == null) PlayerStatus(
            error = when {
                System.getProperty("compose.application.resources.dir") != null ->
                    "VLC bundle missing — please reinstall PureTV for Twitch."
                else ->
                    "VLC not found. Install VLC from https://videolan.org/vlc/ and restart PureTV."
            },
        ) else PlayerStatus(),
    )
    val status: StateFlow<PlayerStatus> = _status.asStateFlow()

    val isAvailable: Boolean get() = mediaPlayer != null

    private var currentUrl: String? = null

    init {
        mediaPlayer?.events()?.addMediaPlayerEventListener(object : MediaPlayerEventAdapter() {
            override fun playing(mediaPlayer: MediaPlayer) {
                _status.update { it.copy(isPlaying = true, isBuffering = false, error = null) }
            }

            override fun paused(mediaPlayer: MediaPlayer) {
                _status.update { it.copy(isPlaying = false) }
            }

            override fun stopped(mediaPlayer: MediaPlayer) {
                _status.update { it.copy(isPlaying = false, isBuffering = false) }
            }

            override fun buffering(mediaPlayer: MediaPlayer, newCache: Float) {
                _status.update { it.copy(isBuffering = newCache < 100f) }
            }

            override fun error(mediaPlayer: MediaPlayer) {
                _status.update { it.copy(isPlaying = false, isBuffering = false, error = "Playback error — VLC reported a fault") }
            }

            override fun mediaPlayerReady(mediaPlayer: MediaPlayer) {
                _status.update { it.copy(isReady = true) }
            }
        })
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

    fun attachToPanel(panel: Component) {
        check(SwingUtilities.isEventDispatchThread()) { "attachToPanel must be called on the Swing EDT" }
        if (attachedComponent === panel) return
        val mp = mediaPlayer ?: return
        val f = factory ?: return
        // VLCJ 4.x: ComponentIdVideoSurface takes a native window handle (long),
        // not an AWT Component. Use the factory's videoSurfaces() factory to mint
        // a platform-appropriate surface (Win32/X11/macOS) bound to this Canvas.
        //
        // CRITICAL: VLCJ doesn't actually bind to the native handle here — it
        // does so lazily inside media().play() via onBeforePlay → attachVideoSurface,
        // which re-checks isDisplayable at THAT moment. So we still gate on
        // isDisplayable here to avoid setting a stale surface, AND the play()
        // call below re-checks just before VLC dereferences the handle.
        if (!panel.isDisplayable) return
        mp.videoSurface().set(f.videoSurfaces().newVideoSurface(panel))
        attachedComponent = panel
        // Drain any URL that was queued by `play()` before we had a surface to
        // render into — common on first stream open, where ViewModel.init
        // calls play() one EDT-tick before SwingPanel's HierarchyListener fires.
        currentUrl?.let { url ->
            if (mp.status().state() != State.PLAYING) {
                runCatching { mp.media().play(url) }
                    .onFailure { e -> _status.update { it.copy(error = "Player attach race: ${e.message}") } }
            }
        }
    }

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
    fun play(streamUrl: String) {
        val mp = mediaPlayer ?: return
        currentUrl = streamUrl
        _status.update { it.copy(isBuffering = true, error = null) }
        val component = attachedComponent ?: return
        // else: attachToPanel will pick up currentUrl and start playback once
        // the surface is bound. See its tail.
        SwingUtilities.invokeLater {
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
            runCatching { mp.media().play(streamUrl) }
                .onFailure { e ->
                    _status.update {
                        it.copy(isPlaying = false, isBuffering = false, error = "Playback start failed: ${e.message}")
                    }
                }
        }
    }

    fun pause() {
        val mp = mediaPlayer ?: return
        SwingUtilities.invokeLater {
            if (mp.status().state() == State.PLAYING) mp.controls().pause()
        }
    }

    fun resume() {
        val mp = mediaPlayer ?: return
        SwingUtilities.invokeLater {
            if (mp.status().state() == State.PAUSED) mp.controls().play()
            else currentUrl?.let { mp.media().play(it) }
        }
    }

    fun togglePlayPause() {
        if (_status.value.isPlaying) pause() else resume()
    }

    fun stop() {
        val mp = mediaPlayer ?: return
        SwingUtilities.invokeLater {
            mp.controls().stop()
            currentUrl = null
        }
    }

    /** [volume] is 0–100, matching VLC's native scale (Section 8.4 volume slider). */
    fun setVolume(volume: Int) {
        val mp = mediaPlayer ?: return
        SwingUtilities.invokeLater {
            mp.audio().setVolume(volume.coerceIn(0, 100))
            _status.update { it.copy(volume = volume.coerceIn(0, 100)) }
        }
    }

    fun release() {
        val mp = mediaPlayer ?: return
        val f = factory ?: return
        SwingUtilities.invokeLater {
            mp.controls().stop()
            mp.release()
            f.release()
        }
    }

    private fun MutableStateFlow<PlayerStatus>.update(transform: (PlayerStatus) -> PlayerStatus) {
        value = transform(value)
    }

    companion object {
        // Checks for a VLC copy bundled inside the distributable's resources dir.
        // When found, prepends the directory to jna.library.path so JNA locates
        // libvlc.dll before scanning system paths. VLC then finds its plugins/
        // subdirectory relative to its own DLL location automatically.
        private fun detectBundledVlc(): String? {
            val resourcesDir = System.getProperty("compose.application.resources.dir")
                ?: return null
            val vlcDir = java.io.File(resourcesDir, "vlc")
            if (!vlcDir.exists() || !java.io.File(vlcDir, "libvlc.dll").exists()) return null
            val existing = System.getProperty("jna.library.path")
            System.setProperty(
                "jna.library.path",
                if (existing.isNullOrBlank()) vlcDir.absolutePath
                else "${vlcDir.absolutePath}${java.io.File.pathSeparator}$existing",
            )
            return vlcDir.absolutePath
        }
    }
}

data class PlayerStatus(
    val isReady: Boolean = false,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val volume: Int = 100,
    val error: String? = null,
)
