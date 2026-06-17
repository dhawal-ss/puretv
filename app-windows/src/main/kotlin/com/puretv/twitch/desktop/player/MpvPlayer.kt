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
 * libmpv-backed [DesktopPlayer] (opt-in; VLC remains default).
 *
 * SURFACE LIFECYCLE — mpv needs the native window handle (`wid`) BEFORE
 * `mpv_initialize`, and `wid` is a SET-ONCE option: a libmpv context is bound to
 * exactly one Canvas/HWND for its lifetime. Compose creates a fresh Canvas (new
 * HWND) for every stream screen and destroys it on navigation, so the native
 * context is created PER SURFACE in [attachToPanel] and torn down in
 * [detachFromPanel] — never reused across surfaces. Leaving mpv bound to a
 * destroyed HWND keeps its VO rendering into a dead window and wedges the GPU/
 * render path (a whole-window freeze); [detachFromPanel] is driven from
 * VlcPlayerView's `Canvas.removeNotify`, so it runs BEFORE the HWND is destroyed.
 *
 * A [play] issued before a surface is attached is cached in [pendingUrl] and
 * drained post-init (mirrors VlcPlayer). The mpv client API is thread-safe, so
 * transport calls don't marshal onto the EDT (unlike VLCJ).
 */
class MpvPlayer(private val settingsStore: DesktopSettingsStore) : DesktopPlayer {

    // Order matters: detectBundledMpv() sets jna.library.path BEFORE Native.load.
    private val mpvDir: String? = detectBundledMpv()
    private val lib: MpvLibrary? = runCatching { Native.load("libmpv-2", MpvLibrary::class.java) }.getOrNull()

    // Verify mpv can create a context at all (library loads, no missing runtime
    // deps) WITHOUT binding a surface — the real, surface-bound contexts are made
    // per-attach. Keeps the "engine unavailable" signal available before the user
    // ever opens a stream, like the original eager mpv_create did.
    private val available: Boolean = lib?.let { l ->
        runCatching { l.mpv_create()?.also { l.mpv_terminate_destroy(it) } != null }.getOrDefault(false)
    } ?: false

    // The live, surface-bound context: null until attachToPanel, null again after
    // detachFromPanel. attach/detach run on the EDT; transport reads tolerate null
    // (no-op when not attached). `ctx != null` ⇔ `initialized`.
    @Volatile private var ctx: Pointer? = null

    private val _status = MutableStateFlow(
        if (!available) PlayerStatus(error = "mpv engine unavailable — switch back to VLC in Settings.")
        else PlayerStatus(),
    )
    override val status: StateFlow<PlayerStatus> = _status.asStateFlow()
    override val isAvailable: Boolean get() = available
    override val supportsUpscaling: Boolean get() = available

    @Volatile private var initialized = false
    @Volatile private var pendingUrl: String? = null
    @Volatile private var preMuteVolume = DEFAULT_VOLUME

    // Serializes ALL native access (attach / detach / transport / property reads)
    // so a teardown can never run mpv_terminate_destroy concurrently with a
    // transport call or property read on the same handle (audit P0-9 transport-UAF).
    // detach is reachable off-EDT too (release() from the JVM shutdown hook), so a
    // plain "it's all on the EDT" assumption is not enough.
    private val nativeLock = Any()

    // How long detach waits for the mpv-events daemon thread to exit. The prior
    // 2000ms ran on the EDT (via Canvas.removeNotify) and froze the WHOLE window
    // for up to 2s on every navigation away from a stream. terminate_destroy
    // already unblocks mpv_wait_event, so the thread exits near-instantly — a tight
    // bound is enough, and a straggler dies with the JVM (it's a daemon).
    private val detachJoinTimeoutMs = 250L

    // Per-context liveness flag, captured by THAT context's event loop. detach sets
    // it stopped and NEVER resets it; the next attach mints a fresh handle. This is
    // what kills the previous join-timeout re-arm bug: a torn-down loop can never be
    // reactivated to call mpv_wait_event on a freed handle.
    private class EventLoopHandle { @Volatile var stopped = false }
    private var loopHandle: EventLoopHandle? = null
    private var eventThread: Thread? = null
    // Read in attachToPanel (sameSurface check) OUTSIDE nativeLock, but written
    // under the lock. detach is reachable off-EDT (release() shutdown hook), so
    // mark volatile to give that cross-thread read a happens-before edge.
    @Volatile private var attachedCanvas: Component? = null

    private val observedProps = listOf("time-pos", "duration", "pause", "core-idle", "paused-for-cache", "seekable")

    // Resolve a bundled shader by filename to a forward-slashed absolute path (mpv
    // wants forward slashes on Windows), or "" if the file isn't present so the mode
    // degrades to the base scaler instead of feeding mpv a broken path.
    private fun resolveShader(name: String): String =
        mpvDir?.let { File(it, "shaders/$name") }?.takeIf { it.exists() }
            ?.absolutePath?.replace('\\', '/') ?: ""

    // Bundled upscaling shaders, resolved once; used at init AND for live re-apply.
    // STANDARD ("Sharp") adds CAS sharpening over ewa_lanczossharp; ANIME runs the
    // Anime4K Mode-A (Fast, M-tier) pipeline in order.
    private val shaderPaths: ShaderPaths = ShaderPaths(
        cas = resolveShader("CAS.glsl"),
        animePipeline = listOf(
            "Anime4K_Clamp_Highlights.glsl",
            "Anime4K_Restore_CNN_M.glsl",
            "Anime4K_Upscale_CNN_x2_M.glsl",
            "Anime4K_AutoDownscalePre_x2.glsl",
            "Anime4K_AutoDownscalePre_x4.glsl",
            "Anime4K_Upscale_CNN_x2_S.glsl",
        ).map { resolveShader(it) }.filter { it.isNotBlank() },
    )

    /** Pre-`mpv_initialize` options, re-applied to every freshly-created context. */
    private fun applyPreInitOptions(l: MpvLibrary, c: Pointer) {
        val mode = settingsStore.settings.value.upscalingMode
        // vo is a priority list: prefer gpu-next (libplacebo), fall back to the
        // older, broadly-compatible `gpu` VO so machines that can't bring up a
        // gpu-next context get video instead of a silent black pane.
        l.mpv_set_option_string(c, "vo", "gpu-next,gpu")
        l.mpv_set_option_string(c, "hwdec", "auto-safe")
        l.mpv_set_option_string(c, "keep-open", "yes")
        l.mpv_set_option_string(c, "network-timeout", "10")
        mpvScalerProps(mode, shaderPaths).forEach { (k, v) -> l.mpv_set_option_string(c, k, v) }
        // OSD styling for the upscaling stats overlay (show-text): a restrained
        // top-left mono readout, not a loud HUD. Consolas ships on Windows. These
        // only take effect when we actually push OSD text (the F3 overlay).
        l.mpv_set_option_string(c, "osd-font", "Consolas")
        l.mpv_set_option_string(c, "osd-font-size", "26")
        l.mpv_set_option_string(c, "osd-align-x", "left")
        l.mpv_set_option_string(c, "osd-align-y", "top")
        l.mpv_set_option_string(c, "osd-margin-x", "34")
        l.mpv_set_option_string(c, "osd-margin-y", "30")
        l.mpv_set_option_string(c, "osd-border-size", "1")
    }

    override fun attachToPanel(panel: Component) {
        check(SwingUtilities.isEventDispatchThread()) { "attachToPanel must be called on the Swing EDT" }
        val l = lib ?: return
        when (mpvAttachAction(initialized, sameSurface = panel === attachedCanvas)) {
            AttachAction.NOOP -> return
            // Tear down the dead-surface context FIRST — outside the lock, since
            // detach manages its own lock + thread join (nesting would deadlock).
            AttachAction.REATTACH -> detachFromPanel()
            AttachAction.ATTACH -> {}
        }
        if (!panel.isDisplayable) return

        synchronized(nativeLock) {
            // Fresh native context bound to THIS surface (wid is set-once pre-init).
            val c = runCatching { l.mpv_create() }.getOrNull()
            if (c == null) {
                _status.update { it.copy(error = "mpv init failed: could not create player context") }
                return
            }
            applyPreInitOptions(l, c)
            val hwnd = Native.getComponentID(panel)
            l.mpv_set_option_string(c, "wid", hwnd.toString())
            // Assert the UI's volume as a pre-init option so mpv doesn't start at its
            // own default (100) — this app has a documented "volume desync at startup"
            // bug class; reading _status here captures any pre-init slider change.
            l.mpv_set_option_string(c, "volume", _status.value.volume.toString())
            val rc = l.mpv_initialize(c)
            if (rc < 0) {
                _status.update { it.copy(error = "mpv init failed: ${l.mpv_error_string(rc)}") }
                runCatching { l.mpv_terminate_destroy(c) }
                return
            }
            ctx = c
            initialized = true
            attachedCanvas = panel
            observedProps.forEach { l.mpv_observe_property(c, 0L, it, MpvConst.MPV_FORMAT_NONE) }
            val handle = EventLoopHandle()
            loopHandle = handle
            startEventLoop(l, c, handle)
            pendingUrl?.let { doPlay(l, c, it) }
        }
    }

    /**
     * Tear the live context down BEFORE its Canvas/HWND is destroyed. Order
     * mirrors [release]: `quit` → join the event loop → `terminate_destroy`, so
     * nothing runs on `ctx` concurrently with the destroy (a use-after-free).
     * Idempotent — safe to call repeatedly or when not attached.
     */
    override fun detachFromPanel() {
        val l = lib ?: return
        var threadToJoin: Thread? = null
        synchronized(nativeLock) {
            val c = ctx ?: return
            threadToJoin = eventThread
            loopHandle?.stopped = true // stop THIS generation's loop forever — never reset
            // Null ctx BEFORE the destroy so any concurrent transport call (which
            // reads ctx under nativeLock) sees null and no-ops instead of using a
            // handle that's being freed.
            ctx = null
            initialized = false
            loopHandle = null
            eventThread = null
            attachedCanvas = null
            runCatching { l.mpv_command(c, arrayOf("quit", null)) } // nudge mpv_wait_event to return
            // terminate_destroy unblocks a stuck mpv_wait_event AND blocks until the
            // VO releases the HWND, so it is safe to call before the Canvas dies.
            // Opt-in timing (PURETV_MPV_TIMING) to confirm whether THIS call (GPU
            // release), not the join below, is the residual freeze cost.
            val timing = System.getenv("PURETV_MPV_TIMING") != null
            val t0 = if (timing) System.nanoTime() else 0L
            runCatching { l.mpv_terminate_destroy(c) }
            if (timing) System.err.println("mpv terminate_destroy took ${(System.nanoTime() - t0) / 1_000_000}ms")
            // Playback ended with the surface; reflect "not playing" but keep pendingUrl
            // so the re-opened screen (fresh ViewModel re-issues play) drains cleanly.
            _status.update { it.copy(isPlaying = false, isBuffering = false, isReady = false) }
        }
        // Join OUTSIDE the lock: the loop may grab the lock for a final stopped
        // recheck, so joining while holding it would deadlock. The stopped flag
        // guarantees the joined thread never touches the freed handle again.
        runCatching { threadToJoin?.join(detachJoinTimeoutMs) }
    }

    private fun startEventLoop(l: MpvLibrary, c: Pointer, handle: EventLoopHandle) {
        eventThread = Thread {
            while (!handle.stopped) {
                // Blocking wait is OUTSIDE nativeLock (it can take up to 1s). Running
                // it concurrently with detach's mpv_terminate_destroy is the
                // documented-safe libmpv shutdown path: destroy makes wait_event return.
                val evPtr = runCatching { l.mpv_wait_event(c, 1.0) }.getOrNull()
                if (handle.stopped) break // context torn down — must NOT touch c again
                if (evPtr == null) continue
                // Read the two ints we use directly from libmpv's event buffer
                // (mpv_event layout: event_id@0, error@4) rather than aliasing the
                // libmpv-owned, reused buffer with a JNA Structure whose auto-write
                // could clobber the next event. Also avoids a per-event allocation.
                val eventId = evPtr.getInt(0)
                when (eventId) {
                    MpvConst.MPV_EVENT_NONE -> {}  // timeout tick — loop re-checks stopped
                    MpvConst.MPV_EVENT_SHUTDOWN -> break
                    MpvConst.MPV_EVENT_FILE_LOADED -> _status.update { it.copy(isReady = true, error = null) }
                    MpvConst.MPV_EVENT_END_FILE ->
                        if (evPtr.getInt(4) != 0) _status.update { it.copy(error = "Playback error", isPlaying = false, isBuffering = false) }
                    MpvConst.MPV_EVENT_PROPERTY_CHANGE -> synchronized(nativeLock) {
                        // Re-check under the lock: a detach may have freed c while we
                        // were dispatching this event outside the lock.
                        if (handle.stopped) return@synchronized
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

    /** Run [block] with the live context under [nativeLock], or no-op when detached. */
    private inline fun withCtx(block: (MpvLibrary, Pointer) -> Unit) {
        val l = lib ?: return
        synchronized(nativeLock) {
            val c = ctx ?: return
            block(l, c)
        }
    }

    private fun doPlay(l: MpvLibrary, c: Pointer, url: String) {
        _status.update { it.copy(isBuffering = true, error = null) }
        runCatching { l.mpv_command(c, arrayOf("loadfile", url, null)) }
            .onFailure { e -> _status.update { it.copy(error = "Play failed: ${e.message}", isBuffering = false) } }
    }

    // Transport — `ctx != null` ⇔ attached & initialized; withCtx no-ops when not
    // attached (play() caches in pendingUrl instead) and serializes every native
    // call against detach's terminate_destroy via nativeLock (audit P0-9).

    override fun play(streamUrl: String) {
        pendingUrl = streamUrl
        withCtx { l, c -> doPlay(l, c, streamUrl) } // else attachToPanel drains pendingUrl
    }

    override fun pause() = withCtx { l, c -> runCatching { l.mpv_set_property_string(c, "pause", "yes") }; Unit }

    override fun resume() = withCtx { l, c -> runCatching { l.mpv_set_property_string(c, "pause", "no") }; Unit }

    override fun togglePlayPause() { if (_status.value.isPlaying) pause() else resume() }

    override fun stop() {
        withCtx { l, c -> runCatching { l.mpv_command(c, arrayOf("stop", null)) } }
        pendingUrl = null
    }

    override fun seekTo(positionMs: Long) {
        if (!_status.value.isSeekable) return
        withCtx { l, c -> runCatching { l.mpv_command(c, arrayOf("seek", (positionMs.coerceAtLeast(0) / 1000.0).toString(), "absolute", null)) }; Unit }
    }

    override fun setVolume(volume: Int) {
        val next = applyVolumeChange(VolumeState(_status.value.volume, _status.value.isMuted, preMuteVolume), volume)
        preMuteVolume = next.preMute
        _status.update { it.copy(volume = next.volume, isMuted = next.isMuted) }  // publish UI first (no lag)
        withCtx { l, c -> runCatching { l.mpv_set_property_string(c, "volume", next.volume.toString()) }; Unit }
    }

    override fun toggleMute() {
        val next = applyMuteToggle(VolumeState(_status.value.volume, _status.value.isMuted, preMuteVolume))
        preMuteVolume = next.preMute
        _status.update { it.copy(volume = next.volume, isMuted = next.isMuted) }
        withCtx { l, c -> runCatching { l.mpv_set_property_string(c, "volume", next.volume.toString()) }; Unit }
    }

    /**
     * Live-apply a scaler change to the running context (the fix for "changing the
     * scaling did nothing mid-stream"): scale/cscale/dscale/glsl-shaders are all
     * runtime-settable in mpv. We push the COMPLETE property set so switching out
     * of Anime clears the shader. No-op when not attached — `applyPreInitOptions`
     * applies the persisted mode on the next stream instead.
     */
    override fun setUpscaling(mode: UpscalingMode) = withCtx { l, c ->
        mpvScalerProps(mode, shaderPaths).forEach { (k, v) ->
            runCatching { l.mpv_set_property_string(c, k, v) }
        }
    }

    override fun release() {
        // App shutdown — tear the live context down exactly like a surface detach.
        detachFromPanel()
    }

    /**
     * Draw (or clear) the upscaling stats on mpv's OSD — the only way to overlay
     * the heavyweight AWT video Canvas, and proof drawn by the engine itself. The
     * caller re-invokes with `show=true` ~1×/sec to refresh; the 2s text duration
     * covers the gap so it never flickers. Thread-safety comes from [withCtx]'s
     * [nativeLock] — that lock, not a "same thread as detach" assumption (detach
     * is also reachable off-EDT via release()), is what keeps `ctx` from being
     * freed mid-read.
     */
    override fun renderStatsOverlay(show: Boolean) = withCtx { l, c ->
        if (!show) {
            runCatching { l.mpv_command(c, arrayOf("show-text", "", "0", null)) }
            return@withCtx
        }
        val stats = videoStatsLocked(l, c) ?: return@withCtx  // nothing decoded yet — caller retries
        val label = upscaleModeLabel(settingsStore.settings.value.upscalingMode)
        runCatching { l.mpv_command(c, arrayOf("show-text", formatStatsOsd(stats, label), "2000", null)) }
        Unit
    }

    /**
     * Read live render diagnostics straight from mpv (no cached/derived values, so
     * the overlay can't show stale "mock" data). Must be called while holding
     * [nativeLock] with a live [c] (via [withCtx]) so `ctx` can't be freed mid-read.
     * Returns null before the first frame decodes.
     */
    private fun videoStatsLocked(l: MpvLibrary, c: Pointer): VideoStats? {
        fun prop(name: String): String? {
            val ptr = runCatching { l.mpv_get_property_string(c, name) }.getOrNull() ?: return null
            val v = ptr.getString(0)
            runCatching { l.mpv_free(ptr) }  // heap string — free it (JNA won't)
            return v?.takeIf { it.isNotBlank() }
        }
        val srcW = prop("width")?.toIntOrNull() ?: 0
        val srcH = prop("height")?.toIntOrNull() ?: 0
        val outW = prop("osd-width")?.toIntOrNull() ?: 0
        val outH = prop("osd-height")?.toIntOrNull() ?: 0
        if (srcW <= 0 || srcH <= 0) return null  // no decoded frame yet — caller retries next tick
        // glsl-shaders holds the active shader path(s) IFF mpv actually loaded them
        // (our forward-slashed path); show just the file name. Null ⇒ no shader.
        val shaderName = prop("glsl-shaders")?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
        return VideoStats(
            sourceWidth = srcW,
            sourceHeight = srcH,
            outputWidth = outW,
            outputHeight = outH,
            scaler = prop("scale"),
            shaderName = shaderName,
            hwdec = prop("hwdec-current"),
            vo = prop("current-vo"),
            fps = prop("container-fps")?.toDoubleOrNull() ?: prop("estimated-vf-fps")?.toDoubleOrNull(),
        )
    }

    // Mirrors detectBundledVlc: packaged build exposes compose.application.resources.dir
    // (we look in its `mpv/` subdir); dev falls back to the MPV_DIR env var pointing
    // straight at the libmpv dir. Sets jna.library.path so Native.load finds libmpv-2.dll.
    private fun detectBundledMpv(): String? {
        // mpv strings (stream URLs, OSD text) are UTF-8; make JNA encode to match.
        // Set before Native.load (this runs first, during property init).
        System.setProperty("jna.encoding", "UTF-8")
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
