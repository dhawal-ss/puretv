package com.puretv.twitch.desktop.player

import com.puretv.twitch.core.perf.TimingLog

import uk.co.caprica.vlcj.factory.MediaPlayerFactory
import uk.co.caprica.vlcj.log.LogLevel
import uk.co.caprica.vlcj.log.NativeLog
import uk.co.caprica.vlcj.player.embedded.EmbeddedMediaPlayer
import uk.co.caprica.vlcj.player.embedded.videosurface.VideoSurface
import java.awt.Component
import java.io.File
import javax.swing.SwingUtilities

/**
 * The audio output multi-stream tiles use (per OS, see [NativeLibs.tileAudioOutput]).
 * VLC's default Windows output (mmdevice)
 * sets volume and mute on the process's one audio session, so with several players
 * muting the quiet tiles silenced the active one too: measured with two live
 * players, setting one player's volume changed the other's. DirectSound keeps a
 * volume per player (measured the same way), which is what "only the active tile is
 * audible" needs. The single-stream player keeps VLC's default.
 */
val TILE_AUDIO_OUTPUT: String? = NativeLibs.tileAudioOutput()

/**
 * The one libVLC instance of the app. Loading libVLC scans its plugins, which is
 * slow, so it happens once and every [VlcPlayer] (the single-stream player, and
 * one per multi-stream tile) makes its own media player from it.
 *
 * VLC resolution order:
 *   1. Bundled copy: when running as a packaged distributable, looks for
 *      `vlc/libvlc.dll` inside `compose.application.resources.dir` (populated
 *      by `gradle :app-windows:bundleVlc` before packaging).
 *   2. System installation: falls back to auto-discovery (standard VLC
 *      install paths, jna.library.path, PATH) for development builds.
 *
 * Construction is cheap; libVLC loads on first use (the background warm-up, the
 * first stream open, or the first multi-stream tile), never on the way to the
 * first window. [extraArgs] is read at that moment, so the audio normalizer
 * setting is whatever it is when VLC starts (VLC 3.0.23 only loads audio filters
 * per instance, so a change applies after a restart).
 */
class VlcEngine(
    private val extraArgs: () -> List<String> = { emptyList() },
    // Arguments worth having but not worth failing over (RTX Video): when libVLC refuses
    // to start with them, it is tried again without them before the extras go too.
    private val optionalArgs: () -> List<String> = { emptyList() },
) {

    private val lazyFactory = lazy {
        // jna.library.path must be set before VLCJ's JNA binding loads libvlc.dll.
        val bundled = detectBundledVlc()
        // Writes the plugin cache when the bundle has none (see NativeLibs.vlcPluginCacheArgs).
        val cache = NativeLibs.vlcPluginCacheArgs(bundled?.let(::File))
        val extra = extraArgs()
        val optional = optionalArgs()
        // An argument libVLC refuses makes it refuse to start at all; playing without
        // the optional arguments, then without the extras, beats not playing.
        listOf(extra + optional, extra, emptyList()).distinct().withIndex().firstNotNullOfOrNull { (attempt, args) ->
            val started = System.nanoTime()
            // The first try writes the cache; a retry reads it instead of scanning again.
            val built = buildFactory(if (attempt == 0) cache + args else args)
            // Every start is timed, so a slow one in timing.log shows which step took the time.
            TimingLog.log(
                "player.vlc_init",
                (System.nanoTime() - started) / 1_000_000,
                "try=${attempt + 1} ${if (built != null) "ok" else "refused"}" +
                    (if (bundled == null) " vlc=system" else "") +
                    (if (cache.isEmpty() || attempt > 0) "" else " cache=writing"),
            )
            built?.also {
                optionalAccepted = optional.isEmpty() || args.containsAll(optional)
                if (args.size < extra.size + optional.size) {
                    System.err.println("VLC rejected ${extra + optional}, started with $args")
                }
            }
        }
    }

    /** False when libVLC started only after dropping the optional arguments (an older VLC). */
    @Volatile var optionalAccepted: Boolean = true
        private set

    private val factory: MediaPlayerFactory? get() = lazyFactory.value

    val isAvailable: Boolean get() = factory != null

    /** Whether libVLC already started for this instance (so using it costs nothing). */
    val isBuilt: Boolean get() = lazyFactory.isInitialized()

    /** Builds libVLC ahead of the first stream. Call off the UI thread. */
    fun warmUp() {
        factory
    }

    /**
     * libVLC's own log for this instance at debug level, or null when VLC is not
     * installed. RTX Video reports what it did only there. Release it when done.
     */
    fun newDebugLog(): NativeLog? = factory?.application()?.newLog()?.apply { setLevel(LogLevel.DEBUG) }

    /** A new media player on the shared instance, or null when VLC is not installed. */
    fun newEmbeddedPlayer(): EmbeddedMediaPlayer? = factory?.mediaPlayers()?.newEmbeddedMediaPlayer()

    /**
     * A video surface for [component]: its native window on Windows and Linux, or on
     * macOS frames decoded into memory that the [VideoCanvas] paints itself.
     */
    fun newVideoSurface(component: Component): VideoSurface? {
        val surfaces = factory?.videoSurfaces() ?: return null
        if (NativeLibs.callbackVideo() && component is VideoCanvas) {
            val sink = FrameSink(component)
            return surfaces.newVideoSurface(sink, sink, true)
        }
        return surfaces.newVideoSurface(component)
    }

    // main() and the shutdown hook both release the engine; a second libvlc_release would
    // free libVLC twice.
    private val released = java.util.concurrent.atomic.AtomicBoolean(false)

    /** Frees libVLC. Call at shutdown, after every player made from it is released. Runs once. */
    fun releaseIfBuilt() {
        if (!lazyFactory.isInitialized()) return
        if (!released.compareAndSet(false, true)) return
        val f = lazyFactory.value ?: return
        SwingUtilities.invokeLater { runCatching { f.release() } }
    }

    companion object {
        // `--no-video-title-show`   suppress VLC's on-video filename overlay.
        // `--network-caching=2000`  2s network buffer; smooths over Twitch's variable
        //                           segment delivery without adding noticeable latency.
        // `--live-caching=1000`     separate, shorter buffer specifically for live sources.
        // `--sout-mux-caching=500`  output muxer cache; keeps A/V sync tight.
        private fun buildFactory(extra: List<String>): MediaPlayerFactory? = runCatching {
            MediaPlayerFactory(
                "--no-video-title-show",
                "--network-caching=2000",
                "--live-caching=1000",
                "--sout-mux-caching=500",
                *extra.toTypedArray(),
            )
        }.getOrNull()

        /**
         * libVLC's version ("3.0.24 Vetinari"), read without starting an instance; null when
         * VLC is missing. Windows only: on macOS this direct load breaks vlcj's (see
         * RtxVlcEngines.vlcHdrSafe).
         */
        fun libVlcVersion(): String? = runCatching {
            detectBundledVlc()
            uk.co.caprica.vlcj.binding.lib.LibVlc.libvlc_get_version()
        }.getOrNull()

        // Checks for a VLC copy bundled inside the distributable's resources dir.
        // When found, prepends the directory to jna.library.path so JNA locates
        // libvlc.dll before scanning system paths. VLC then finds its plugins/
        // subdirectory relative to its own DLL location automatically.
        private fun detectBundledVlc(): String? {
            val resourcesDir = System.getProperty("compose.application.resources.dir")
                ?: return null
            // Windows: vlc/libvlc.dll. macOS: vlc/lib/libvlc.dylib. Linux uses the distro's libVLC.
            val vlcDir = NativeLibs.bundledVlcLibDir(java.io.File(resourcesDir)) ?: return null
            val existing = System.getProperty("jna.library.path")
            System.setProperty(
                "jna.library.path",
                if (existing.isNullOrBlank()) vlcDir.absolutePath
                else "${vlcDir.absolutePath}${java.io.File.pathSeparator}$existing",
            )
            // The engine can be built after libmpv already went through JNA (multi-stream
            // with the mpv engine selected), so name the folder to JNA directly as well.
            runCatching {
                com.sun.jna.NativeLibrary.addSearchPath("libvlc", vlcDir.absolutePath)
                com.sun.jna.NativeLibrary.addSearchPath("libvlccore", vlcDir.absolutePath)
            }
            return vlcDir.absolutePath
        }
    }
}
