package com.puretv.twitch.desktop.player

import com.puretv.twitch.core.model.UpscalingMode
import com.puretv.twitch.desktop.data.DesktopSettingsStore
import com.sun.jna.Native
import com.sun.jna.Pointer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.awt.Component
import java.io.File
import javax.swing.SwingUtilities

/**
 * libmpv-backed [DesktopPlayer] (opt-in; VLC remains default). libmpv needs the
 * native window handle (`wid`) BEFORE `mpv_initialize`, and a displayable AWT
 * peer only exists once Compose lays out the SwingPanel — so initialization is
 * DEFERRED to [attachToPanel]. A [play] issued before then is cached in
 * [pendingUrl] and drained post-init (mirrors VlcPlayer). The mpv client API is
 * thread-safe, so transport calls don't marshal onto the EDT (unlike VLCJ).
 */
class MpvPlayer(private val settingsStore: DesktopSettingsStore) : DesktopPlayer {

    // Order matters: detectBundledMpv() sets jna.library.path BEFORE Native.load.
    private val mpvDir: String? = detectBundledMpv()
    private val lib: MpvLibrary? = runCatching { Native.load("libmpv-2", MpvLibrary::class.java) }.getOrNull()
    private val ctx: Pointer? = runCatching { lib?.mpv_create() }.getOrNull()

    private val _status = MutableStateFlow(
        if (lib == null || ctx == null)
            PlayerStatus(error = "mpv engine unavailable — switch back to VLC in Settings.")
        else PlayerStatus(),
    )
    override val status: StateFlow<PlayerStatus> = _status.asStateFlow()
    override val isAvailable: Boolean get() = lib != null && ctx != null

    @Volatile private var initialized = false
    @Volatile private var pendingUrl: String? = null
    @Volatile private var preMuteVolume = DEFAULT_VOLUME
    @Volatile private var shuttingDown = false
    private var eventThread: Thread? = null

    private val observedProps = listOf("time-pos", "duration", "pause", "core-idle", "paused-for-cache", "seekable")

    init {
        val l = lib; val c = ctx
        if (l != null && c != null) {
            val mode = settingsStore.settings.value.upscalingMode
            // mpv wants forward slashes; only include the shader if it actually exists.
            val animeShader = mpvDir
                ?.let { File(it, "shaders/Anime4K_Upscale_CNN_x2_M.glsl") }
                ?.takeIf { it.exists() }
                ?.absolutePath?.replace('\\', '/')
                ?: ""
            // Pre-init OPTIONS (mpv_set_option_string only — properties need init first).
            // vo is a priority list: prefer gpu-next (libplacebo), but fall back to the
            // older, broadly-compatible `gpu` VO so machines that can't bring up a
            // gpu-next context get video instead of a silent black pane.
            l.mpv_set_option_string(c, "vo", "gpu-next,gpu")
            l.mpv_set_option_string(c, "hwdec", "auto-safe")
            l.mpv_set_option_string(c, "keep-open", "yes")
            l.mpv_set_option_string(c, "network-timeout", "10")
            mpvUpscaleArgs(mode, animeShader).forEach { (k, v) -> l.mpv_set_option_string(c, k, v) }
        }
    }

    override fun attachToPanel(panel: Component) {
        check(SwingUtilities.isEventDispatchThread()) { "attachToPanel must be called on the Swing EDT" }
        val l = lib ?: return; val c = ctx ?: return
        if (initialized) return
        if (!panel.isDisplayable) return
        val hwnd = Native.getComponentID(panel)
        l.mpv_set_option_string(c, "wid", hwnd.toString())
        // Assert the UI's volume as a pre-init option so mpv doesn't start at its
        // own default (100) — this app has a documented "volume desync at startup"
        // bug class; reading _status here captures any pre-init slider change.
        l.mpv_set_option_string(c, "volume", _status.value.volume.toString())
        val rc = l.mpv_initialize(c)
        if (rc < 0) {
            _status.update { it.copy(error = "mpv init failed: ${l.mpv_error_string(rc)}") }
            return
        }
        initialized = true
        observedProps.forEach { l.mpv_observe_property(c, 0L, it, MpvConst.MPV_FORMAT_NONE) }
        startEventLoop(l, c)
        pendingUrl?.let { doPlay(l, c, it) }
    }

    private fun startEventLoop(l: MpvLibrary, c: Pointer) {
        eventThread = Thread {
            while (!shuttingDown) {
                // Bounded poll (not -1 block) so the loop re-checks shuttingDown and
                // can never become a busy-spin or a stuck thread if SHUTDOWN is missed.
                val evPtr = runCatching { l.mpv_wait_event(c, 1.0) }.getOrNull()
                if (evPtr == null) continue
                val ev = MpvEvent(evPtr).apply { read() }
                when (ev.event_id) {
                    MpvConst.MPV_EVENT_NONE -> {}  // timeout tick — loop re-checks shuttingDown
                    MpvConst.MPV_EVENT_SHUTDOWN -> break
                    MpvConst.MPV_EVENT_FILE_LOADED -> _status.update { it.copy(isReady = true, error = null) }
                    MpvConst.MPV_EVENT_END_FILE ->
                        if (ev.error != 0) _status.update { it.copy(error = "Playback error", isPlaying = false, isBuffering = false) }
                    MpvConst.MPV_EVENT_PROPERTY_CHANGE -> {
                        for (p in observedProps) {
                            val ptr = runCatching { l.mpv_get_property_string(c, p) }.getOrNull()
                            val v = ptr?.getString(0)
                            ptr?.let { runCatching { l.mpv_free(it) } }  // free the heap string (JNA won't)
                            _status.update { mpvApply(it, p, v) }
                        }
                    }
                }
            }
        }.apply { isDaemon = true; name = "mpv-events"; start() }
    }

    private fun doPlay(l: MpvLibrary, c: Pointer, url: String) {
        _status.update { it.copy(isBuffering = true, error = null) }
        runCatching { l.mpv_command(c, arrayOf("loadfile", url, null)) }
            .onFailure { e -> _status.update { it.copy(error = "Play failed: ${e.message}", isBuffering = false) } }
    }

    override fun play(streamUrl: String) {
        pendingUrl = streamUrl
        val l = lib ?: return; val c = ctx ?: return
        if (initialized) doPlay(l, c, streamUrl)  // else attachToPanel drains pendingUrl
    }

    override fun pause() {
        val l = lib ?: return; val c = ctx ?: return
        if (initialized) runCatching { l.mpv_set_property_string(c, "pause", "yes") }
    }

    override fun resume() {
        val l = lib ?: return; val c = ctx ?: return
        if (initialized) runCatching { l.mpv_set_property_string(c, "pause", "no") }
        else pendingUrl?.let { doPlay(l, c, it) }
    }

    override fun togglePlayPause() { if (_status.value.isPlaying) pause() else resume() }

    override fun stop() {
        val l = lib ?: return; val c = ctx ?: return
        runCatching { l.mpv_command(c, arrayOf("stop", null)) }
        pendingUrl = null
    }

    override fun seekTo(positionMs: Long) {
        if (!_status.value.isSeekable) return
        val l = lib ?: return; val c = ctx ?: return
        runCatching { l.mpv_command(c, arrayOf("seek", (positionMs.coerceAtLeast(0) / 1000.0).toString(), "absolute", null)) }
    }

    override fun setVolume(volume: Int) {
        val next = applyVolumeChange(VolumeState(_status.value.volume, _status.value.isMuted, preMuteVolume), volume)
        preMuteVolume = next.preMute
        _status.update { it.copy(volume = next.volume, isMuted = next.isMuted) }  // publish UI first (no lag)
        val l = lib ?: return; val c = ctx ?: return
        if (initialized) runCatching { l.mpv_set_property_string(c, "volume", next.volume.toString()) }
    }

    override fun toggleMute() {
        val next = applyMuteToggle(VolumeState(_status.value.volume, _status.value.isMuted, preMuteVolume))
        preMuteVolume = next.preMute
        _status.update { it.copy(volume = next.volume, isMuted = next.isMuted) }
        val l = lib ?: return; val c = ctx ?: return
        if (initialized) runCatching { l.mpv_set_property_string(c, "volume", next.volume.toString()) }
    }

    override fun release() {
        val l = lib ?: return; val c = ctx ?: return
        shuttingDown = true
        if (initialized) {
            // libmpv's documented async-shutdown: send `quit`, let the event loop
            // drain to MPV_EVENT_SHUTDOWN, and JOIN it before destroying — so no
            // function runs concurrently on `ctx` when mpv_terminate_destroy frees
            // the handle. Calling terminate_destroy while the loop is still in
            // mpv_wait_event would be a use-after-free (concurrent call on the
            // same context). The 1s-polling loop also guarantees exit via shuttingDown.
            runCatching { l.mpv_command(c, arrayOf("quit", null)) }
            runCatching { eventThread?.join(2000) }
        }
        runCatching { l.mpv_terminate_destroy(c) }
    }

    // Mirrors detectBundledVlc: packaged build exposes compose.application.resources.dir
    // (we look in its `mpv/` subdir); dev falls back to the MPV_DIR env var pointing
    // straight at the libmpv dir. Sets jna.library.path so Native.load finds libmpv-2.dll.
    private fun detectBundledMpv(): String? {
        val resourcesDir = System.getProperty("compose.application.resources.dir")
        val base = resourcesDir ?: System.getenv("MPV_DIR") ?: return null
        val dir = if (resourcesDir != null) File(base, "mpv") else File(base)
        if (!File(dir, "libmpv-2.dll").exists()) return null
        val existing = System.getProperty("jna.library.path")
        System.setProperty(
            "jna.library.path",
            if (existing.isNullOrBlank()) dir.absolutePath else "${dir.absolutePath}${File.pathSeparator}$existing",
        )
        return dir.absolutePath
    }
}
