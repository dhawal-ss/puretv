package com.puretv.twitch.desktop.player

import com.puretv.twitch.core.audio.AudioLevelling
import com.puretv.twitch.core.audio.AudioNormalizerProfile
import com.puretv.twitch.core.model.UpscalingMode
import com.puretv.twitch.core.perf.TimingLog
import com.puretv.twitch.desktop.data.DesktopSettingsStore
import com.puretv.twitch.desktop.platform.RtxGpu
import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.awt.Component
import java.io.File
import java.util.concurrent.Executors
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
    private val lib: MpvLibrary? = if (!NativeLibs.mpvSupported()) null else NativeLibs.mpvLoadNames().firstNotNullOfOrNull { name ->
        runCatching { Native.load(name, MpvLibrary::class.java) }.getOrNull()
    }

    // mpv_request_log_messages, bound here so the RTX Video code stays in this file:
    // the d3d11vpp failures are only in mpv's log (setting `vf` still returns success).
    private interface MpvLogApi : Library {
        fun mpv_request_log_messages(ctx: Pointer, minLevel: String): Int
    }
    private val logApi: MpvLogApi? = if (lib == null) null else NativeLibs.mpvLoadNames().firstNotNullOfOrNull { name ->
        runCatching { Native.load(name, MpvLogApi::class.java) }.getOrNull()
    }

    // Verify mpv can create a context at all (library loads, no missing runtime
    // deps) WITHOUT binding a surface — the real, surface-bound contexts are made
    // per-attach. Keeps the "engine unavailable" signal available before the user
    // ever opens a stream, like the original eager mpv_create did.
    private val available: Boolean = lib?.let { l ->
        runCatching { l.mpv_create()?.also { l.mpv_terminate_destroy(it) } != null }.getOrDefault(false)
    } ?: false

    // The live, surface-bound context: null until an attach completes, null again
    // after detach. The heavyweight create/initialize/terminate_destroy now run on
    // [lifecycleExecutor] (NOT the EDT — see A1), so this is read/written on that
    // thread under [nativeLock]; transport reads tolerate null (no-op when not
    // attached). `ctx != null` ⇔ `initialized`.
    @Volatile private var ctx: Pointer? = null

    private val _status = MutableStateFlow(
        if (!available) PlayerStatus(error = "mpv engine unavailable — switch back to VLC in Settings.")
        else PlayerStatus(),
    )
    override val status: StateFlow<PlayerStatus> = _status.asStateFlow()
    override val isAvailable: Boolean get() = available
    override val supportsUpscaling: Boolean get() = available

    // RTX Video needs Windows and an RTX card (RtxGpu.present is false elsewhere); the
    // bundled libmpv has d3d11vpp. Whether it really runs shows in rtxStatus.
    override val supportsRtxVideo: Boolean get() = available && rtxGpuPresent()

    // RTX Video HDR through d3d11vpp ran stable on live Twitch here (RTX 4070, 3+
    // minutes, no crash), unlike VLC's d3d11 HDR path, so mpv offers it.
    override val supportsRtxHdr: Boolean get() = supportsRtxVideo

    private val _rtxStatus = MutableStateFlow(RtxVideoStatus())
    override val rtxStatus: StateFlow<RtxVideoStatus> = _rtxStatus.asStateFlow()

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
    // The NATIVE-bound surface: written/read only on [lifecycleExecutor] under
    // [nativeLock], alongside ctx. No longer consulted for the attach decision (the
    // EDT can't see it promptly now that attach is async) — that uses desiredCanvas.
    @Volatile private var attachedCanvas: Component? = null

    // The surface we INTEND to be bound to, set/cleared SYNCHRONOUSLY on the EDT in
    // attach/detach. Because mpv_create/initialize now run async on
    // [lifecycleExecutor], the EDT can't read the real attachedCanvas in time, so
    // the attach decision (mpvAttachAction) reads this instead: a duplicate
    // HierarchyListener/invokeLater attach for the same Canvas still resolves to
    // NOOP. The submitted attach task also re-checks it under the lock and bails if
    // a detach has since cleared/changed it — that is what stops an in-flight attach
    // from binding a context to a Canvas that is about to be destroyed (HWND wedge).
    @Volatile private var desiredCanvas: Component? = null

    // Serializes the BLOCKING native lifecycle calls off the EDT (A1): mpv_create +
    // mpv_initialize bring up the gpu-next/libplacebo GPU context, and
    // mpv_terminate_destroy blocks until the VO releases the HWND. These were run
    // inline from VlcPlayerView's EDT callbacks (HierarchyListener attach,
    // Canvas.removeNotify detach), freezing the UI thread on every stream open/close.
    // Single-threaded + FIFO so an attach and the detach that tears it down can never
    // run concurrently or out of order on the same handle. Daemon so it never blocks
    // JVM exit.
    private val lifecycleExecutor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "mpv-lifecycle").apply { isDaemon = true }
    }

    private val observedProps = listOf("time-pos", "duration", "pause", "core-idle", "paused-for-cache", "seekable")

    // RTX Video state. rtxChoice is what the settings ask for (volatile, read anywhere);
    // the rest belongs to the live context and is read/written under [nativeLock] only.
    // rtxProps are observed with their own reply_userdata so they are read only when
    // they change, not on every time-pos tick.
    @Volatile private var rtxChoice = RtxVideoChoice.NONE
    private val rtxProps = listOf("video-params", "video-out-params", "video-target-params", "osd-dimensions")
    private var rtxFilter = ""            // the vf value set on the context
    private var rtxFilterSetAt = 0L       // when it was set (or a new file loaded)
    private var rtxSettleChecked = true   // the settled check ran for the current filter
    private var rtxScaleNow = 1.0
    private var rtxSeen = MpvRtxObserved()
    private var rtxHealth = MpvRtxHealth()
    private var rtxGaveUp = false         // fell back to normal playback on this context
    private var rtxHwdec = MPV_HWDEC_DEFAULT

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
        // RTX Video starts fresh with every context. Read here, on the lifecycle
        // thread, because the first RtxGpu read can block briefly.
        val rtxGpu = rtxGpuPresent()
        val choice = rtxChoiceNow()
        rtxChoice = choice
        rtxFilter = ""
        rtxSettleChecked = true
        rtxScaleNow = 1.0
        rtxSeen = MpvRtxObserved()
        rtxHealth = MpvRtxHealth()
        rtxGaveUp = false
        rtxHwdec = mpvRtxHwdec(choice)
        publishRtxLocked(settled = false)
        // vo is a priority list: prefer gpu-next (libplacebo), fall back to the
        // older, broadly-compatible `gpu` VO so machines that can't bring up a
        // gpu-next context get video instead of a silent black pane.
        l.mpv_set_option_string(c, "vo", NativeLibs.mpvVideoOutputs())
        // d3d11vpp only works in the Direct3D 11 GPU context, and gpu-api is fixed at
        // init, so an RTX machine always gets it (even with RTX Video off) to let
        // applyRtxVideo turn it on live. mpv on Windows picks d3d11 first anyway.
        if (rtxGpu) l.mpv_set_option_string(c, "gpu-api", "d3d11")
        l.mpv_set_option_string(c, "hwdec", rtxHwdec)
        l.mpv_set_option_string(c, "keep-open", "yes")
        l.mpv_set_option_string(c, "network-timeout", "10")
        mpvScalerPropsWithRtx(mode, shaderPaths, rtxSuperResolutionLocked()).forEach { (k, v) -> l.mpv_set_option_string(c, k, v) }
        // Audio normalizer: a fresh context per surface, so the filter is set here
        // as well as live in setAudioLevelling.
        settingsStore.settings.value.audioLevellingInUse()?.let { level ->
            l.mpv_set_option_string(c, "af", AudioNormalizerProfile.mpvAudioFilter(level))
        }
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
        // Decide on the EDT against the synchronously-maintained desiredCanvas marker
        // (NOT the native attachedCanvas, which is now set later on the lifecycle
        // thread). desiredCanvas != null ⇔ "an attach for some surface is live or in
        // flight", which is exactly the property mpvAttachAction's `initialized`
        // models, so a re-entrant attach for the same Canvas still resolves to NOOP.
        when (mpvAttachAction(initialized = desiredCanvas != null, sameSurface = panel === desiredCanvas)) {
            AttachAction.NOOP -> return
            // Different surface: tear the old context down FIRST. detachFromPanel
            // blocks the EDT until the GPU/HWND release completes (mandatory
            // ordering) and clears desiredCanvas before we re-mark it below.
            AttachAction.REATTACH -> detachFromPanel()
            AttachAction.ATTACH -> {}
        }
        if (!panel.isDisplayable) return

        // Capture the HWND on the EDT (Native.getComponentID reads the AWT native
        // peer) and mark the intent synchronously, THEN hand the blocking GPU-context
        // bringup (mpv_create/applyPreInitOptions/mpv_initialize) to the lifecycle
        // thread so stream-open never stalls the EDT (A1). The mpv client API is
        // thread-safe, so binding `wid` and initializing off-EDT is safe; the HWND
        // stays alive because its Canvas is still mounted (detach, driven from
        // Canvas.removeNotify, clears desiredCanvas which makes the task below bail).
        val hwnd = Native.getComponentID(panel)
        desiredCanvas = panel
        lifecycleExecutor.execute {
            synchronized(nativeLock) {
                // A detach (or a REATTACH to a different surface) may have changed the
                // intent while this task waited in the FIFO queue. If THIS surface is
                // no longer wanted, do NOT bring up a context — it would bind to a
                // Canvas that is about to be (or already) destroyed and wedge the GPU.
                if (desiredCanvas !== panel) return@synchronized
                // Fresh native context bound to THIS surface (wid is set-once pre-init).
                val c = runCatching { l.mpv_create() }.getOrNull()
                if (c == null) {
                    _status.update { it.copy(error = "mpv init failed: could not create player context") }
                    return@synchronized
                }
                applyPreInitOptions(l, c)
                l.mpv_set_option_string(c, "wid", hwnd.toString())
                // Assert the UI's volume as a pre-init option so mpv doesn't start at its
                // own default (100) — this app has a documented "volume desync at startup"
                // bug class; reading _status here captures any pre-init slider change.
                l.mpv_set_option_string(c, "volume", _status.value.volume.toString())
                val rc = l.mpv_initialize(c)
                if (rc < 0) {
                    _status.update { it.copy(error = "mpv init failed: ${l.mpv_error_string(rc)}") }
                    runCatching { l.mpv_terminate_destroy(c) }
                    return@synchronized
                }
                ctx = c
                initialized = true
                attachedCanvas = panel
                normalizerOverride?.let { applyNormalizer(l, c, it.level) }
                observedProps.forEach { l.mpv_observe_property(c, 0L, it, MpvConst.MPV_FORMAT_NONE) }
                if (rtxGpuPresent()) {
                    rtxProps.forEach { l.mpv_observe_property(c, RTX_USERDATA, it, MpvConst.MPV_FORMAT_NONE) }
                    // Warnings and errors only: the d3d11vpp failures, and little else.
                    logApi?.let { api -> runCatching { api.mpv_request_log_messages(c, "warn") } }
                }
                val handle = EventLoopHandle()
                loopHandle = handle
                startEventLoop(l, c, handle)
                pendingUrl?.let { doPlay(l, c, it) }
            }
        }
    }

    /**
     * Tear the live context down BEFORE its Canvas/HWND is destroyed.
     *
     * ORDERING (the subtle part of A1): this is driven from Canvas.removeNotify,
     * and the HWND is destroyed the instant removeNotify returns. mpv's VO must
     * release the HWND first (rendering into a dead window wedges the GPU/render
     * path — a whole-window freeze), and mpv_terminate_destroy is what blocks until
     * that release. So the teardown CANNOT be fire-and-forget — it must complete
     * before this call returns. We run it on [lifecycleExecutor] (FIFO behind any
     * in-flight attach, so we never destroy a half-built context) and BLOCK the
     * caller on the result. That keeps the heavyweight work serialized off the EDT
     * for the create/initialize side while still guaranteeing release-before-
     * destruction here; the EDT block is unavoidable and bounded by the GPU release
     * (the same cost the inline version paid), not by any newly-introduced wait.
     *
     * Idempotent — safe to call repeatedly or when not attached.
     */
    override fun detachFromPanel() {
        val l = lib ?: return
        // Clear the EDT-visible intent FIRST (synchronously) so any attach task still
        // queued on the lifecycle thread early-outs instead of binding a context to a
        // Canvas about to be destroyed, and so a subsequent re-attach isn't NOOP'd.
        desiredCanvas = null
        // Submit the teardown and WAIT for it: removeNotify must not return (peer
        // destruction) until terminate_destroy has released the HWND. No timeout — a
        // bounded wait that let removeNotify proceed early would re-introduce the
        // HWND wedge; the inline version also blocked unboundedly here. The event-loop
        // join inside [teardown] stays bounded by detachJoinTimeoutMs.
        // Explicit Runnable so submit() doesn't hit the Runnable/Callable overload
        // ambiguity; we only need the Future to await completion, not a result.
        runCatching { lifecycleExecutor.submit(Runnable { teardown(l) }).get() }
    }

    /**
     * The actual context teardown, run on [lifecycleExecutor]. Order mirrors
     * [release]: `quit` → `terminate_destroy` → join the event loop, so nothing runs
     * on `ctx` concurrently with the destroy (a use-after-free). No-op when not
     * attached.
     */
    private fun teardown(l: MpvLibrary) {
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
            _rtxStatus.value = mpvRtxStatus(rtxChoice, "", MpvRtxObserved(), MpvRtxHealth(), settled = false)
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
                    MpvConst.MPV_EVENT_FILE_LOADED -> {
                        _status.update { it.copy(isReady = true, error = null) }
                        // A new video: give the filter its settling time again.
                        synchronized(nativeLock) {
                            if (!handle.stopped && rtxFilter.isNotEmpty()) {
                                rtxFilterSetAt = System.currentTimeMillis()
                                rtxSettleChecked = false
                            }
                        }
                    }
                    MPV_EVENT_LOG_MESSAGE -> {
                        // mpv_event.data@16 is an mpv_event_log_message { prefix@0,
                        // level@8, text@16 }; read before the next wait_event reuses it.
                        val msg = runCatching { evPtr.getPointer(16) }.getOrNull() ?: continue
                        val prefix = runCatching { msg.getPointer(0)?.getString(0) }.getOrNull()
                        val text = runCatching { msg.getPointer(16)?.getString(0) }.getOrNull() ?: continue
                        val verdict = mpvRtxLog(prefix, text) ?: continue
                        synchronized(nativeLock) {
                            if (handle.stopped) return@synchronized
                            val srBefore = rtxSuperResolutionLocked()
                            rtxHealth = rtxHealth.with(verdict)
                            // Super Resolution gone: the Scaling choice applies again.
                            if (srBefore != rtxSuperResolutionLocked()) pushScalerLocked(l, c)
                            updateRtxLocked(l, c)
                        }
                    }
                    MpvConst.MPV_EVENT_END_FILE ->
                        if (evPtr.getInt(4) != 0) _status.update { it.copy(error = "Playback error", isPlaying = false, isBuffering = false) }
                    MpvConst.MPV_EVENT_PROPERTY_CHANGE -> synchronized(nativeLock) {
                        // Re-check under the lock: a detach may have freed c while we
                        // were dispatching this event outside the lock.
                        if (handle.stopped) return@synchronized
                        // mpv_event.reply_userdata@8 says which group changed.
                        if (evPtr.getLong(8) == RTX_USERDATA) {
                            refreshRtxLocked(l, c)
                            return@synchronized
                        }
                        // mpv can drop the filter without any property changing; the
                        // regular ticks give the settled check its chance.
                        if (!rtxSettleChecked && System.currentTimeMillis() - rtxFilterSetAt >= RTX_SETTLE_MS) {
                            updateRtxLocked(l, c)
                        }
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

    // lowLatency needs nothing here: mpv's HLS demuxer already starts three
    // segments from the end of the playlist, and with low latency on the proxy
    // has turned Twitch's prefetch hints into segments, so that end is nearer live.
    override fun play(streamUrl: String, lowLatency: Boolean) {
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
        // Through the RTX rule: while Super Resolution is on, the shaders stay out.
        mpvScalerPropsWithRtx(mode, shaderPaths, rtxSuperResolutionLocked()).forEach { (k, v) ->
            runCatching { l.mpv_set_property_string(c, k, v) }
        }
    }

    /**
     * Re-read the RTX Video settings and apply them to the running context: hwdec, the
     * Scaling shaders (see [mpvScalerPropsWithRtx]) and the d3d11vpp filter. A new try
     * also clears a failure seen earlier on this context. When not attached the status
     * just follows the settings; applyPreInitOptions reads them for the next stream.
     *
     * Runs on [lifecycleExecutor], not the caller's (UI) thread: the first RtxGpu read
     * can block on `reg query`, and a vf or hwdec change waits for mpv to rebuild its
     * chain. FIFO with attach/detach there, so it never meets a half-built context.
     */
    override fun applyRtxVideo() {
        runCatching {
            lifecycleExecutor.execute {
                val choice = rtxChoiceNow()
                rtxChoice = choice
                var live = false
                withCtx { l, c ->
                    live = true
                    rtxHealth = MpvRtxHealth()
                    rtxGaveUp = false
                    setHwdecLocked(l, c, mpvRtxHwdec(choice))
                    pushScalerLocked(l, c)
                    refreshRtxLocked(l, c)
                }
                if (!live) _rtxStatus.value = mpvRtxStatus(choice, "", MpvRtxObserved(), MpvRtxHealth(), settled = false)
            }
        }
    }

    private fun rtxGpuPresent(): Boolean = runCatching { RtxGpu.present }.getOrDefault(false)

    private fun rtxChoiceNow(): RtxVideoChoice = RtxVideoChoice.from(settingsStore.settings.value, rtxGpuPresent())

    /** Super Resolution is asked for and has not failed on this context. Under [nativeLock]. */
    private fun rtxSuperResolutionLocked(): Boolean =
        rtxChoice.superResolution && !rtxGaveUp && !rtxHealth.filterFailed && !rtxHealth.superResolutionFailed

    private fun pushScalerLocked(l: MpvLibrary, c: Pointer) {
        val mode = settingsStore.settings.value.upscalingMode
        mpvScalerPropsWithRtx(mode, shaderPaths, rtxSuperResolutionLocked()).forEach { (k, v) ->
            runCatching { l.mpv_set_property_string(c, k, v) }
        }
    }

    // Only when it differs: setting hwdec makes mpv look at the decoder again.
    private fun setHwdecLocked(l: MpvLibrary, c: Pointer, hwdec: String) {
        if (hwdec == rtxHwdec) return
        if (runCatching { l.mpv_set_property_string(c, "hwdec", hwdec) }.getOrDefault(-1) >= 0) rtxHwdec = hwdec
    }

    /** Read the RTX properties again, then bring the filter and status up to date. Under [nativeLock]. */
    private fun refreshRtxLocked(l: MpvLibrary, c: Pointer) {
        fun prop(name: String): String? {
            val ptr = runCatching { l.mpv_get_property_string(c, name) }.getOrNull() ?: return null
            val v = ptr.getString(0)
            runCatching { l.mpv_free(ptr) }
            return v?.takeIf { it.isNotBlank() }
        }
        rtxSeen = mpvRtxObserve(prop("video-params"), prop("video-out-params"), prop("video-target-params"), prop("osd-dimensions"))
        // d3d11vpp cannot be created outside the Direct3D 11 context (e.g. mpv fell
        // back to Vulkan), so there is no point setting it there.
        if (rtxChoice.any && !rtxGaveUp) {
            val gpuContext = prop("current-gpu-context")
            if (gpuContext != null && gpuContext != "d3d11") rtxHealth = rtxHealth.copy(filterFailed = true)
        }
        updateRtxLocked(l, c)
    }

    /**
     * Set (or clear) the d3d11vpp filter to match the choice and what mpv reports, fall
     * back when it failed, and publish the status. Under [nativeLock].
     */
    private fun updateRtxLocked(l: MpvLibrary, c: Pointer) {
        val choice = rtxChoice
        if (!rtxGaveUp && !rtxHealth.filterFailed) {
            rtxScaleNow = rtxScaleNext(rtxScaleNow, rtxSeen.scale)
            val working = RtxVideoChoice(
                superResolution = choice.superResolution && !rtxHealth.superResolutionFailed,
                hdr = choice.hdr && !rtxHealth.hdrFailed,
            )
            val wanted = when {
                !choice.any -> ""
                // Before the video opens the scale and the display are not known yet.
                !rtxSeen.videoOpen -> rtxFilter
                else -> mpvRtxFilter(working, rtxScaleNow, rtxSeen.displayHdr)
            }
            if (wanted != rtxFilter) {
                val rc = runCatching { l.mpv_set_property_string(c, "vf", wanted) }.getOrDefault(-1)
                if (rc >= 0) {
                    rtxFilter = wanted
                    rtxFilterSetAt = System.currentTimeMillis()
                    rtxSettleChecked = wanted.isEmpty()
                } else if (wanted.isNotEmpty()) {
                    rtxHealth = rtxHealth.copy(filterFailed = true)
                }
            }
        }
        val settled = rtxFilter.isNotEmpty() && System.currentTimeMillis() - rtxFilterSetAt >= RTX_SETTLE_MS
        if (settled) rtxSettleChecked = true
        if (!rtxGaveUp && (rtxHealth.filterFailed || mpvRtxFilterIneffective(rtxFilter, rtxSeen, settled))) {
            fallBackLocked(l, c)
        }
        publishRtxLocked(settled)
    }

    /**
     * RTX Video did not work on this context: back to exactly what plays without it (no
     * filter, the normal hwdec, the Scaling choice), so the viewer keeps their video.
     * The status says UNSUPPORTED until applyRtxVideo tries again or a new surface.
     */
    private fun fallBackLocked(l: MpvLibrary, c: Pointer) {
        rtxGaveUp = true
        rtxHealth = rtxHealth.copy(filterFailed = true)
        if (rtxFilter.isNotEmpty()) runCatching { l.mpv_set_property_string(c, "vf", "") }
        rtxFilter = ""
        rtxSettleChecked = true
        setHwdecLocked(l, c, MPV_HWDEC_DEFAULT)
        pushScalerLocked(l, c)
    }

    private fun publishRtxLocked(settled: Boolean) {
        val next = mpvRtxStatus(rtxChoice, rtxFilter, rtxSeen, rtxHealth, settled)
        val was = _rtxStatus.value
        if (next.superResolution != was.superResolution || next.hdr != was.hdr) {
            // Diagnostics, as VlcPlayer logs them: state changes only, not every resize.
            TimingLog.log(
                "player.rtx_video", 0,
                "engine=mpv sr=${next.superResolution} hdr=${next.hdr} filter=${rtxFilter.ifEmpty { "none" }} " +
                    rtxSizeLabel(next),
            )
        }
        _rtxStatus.value = next
    }

    /**
     * Swap the audio normalizer in, out, or to another strength on the running
     * context: `af` is runtime-settable, and an empty list clears it. No-op when not
     * attached; `applyPreInitOptions` reads the saved settings for the next stream.
     */
    override fun setAudioLevelling(level: AudioLevelling?) {
        // Remembered first: a context still coming up (ctx is null until its attach
        // finishes) applies it as soon as it is live, so a switch flipped
        // mid-bring-up is never lost.
        normalizerOverride = LevellingOverride(level)
        withCtx { l, c -> applyNormalizer(l, c, level) }
    }

    private fun applyNormalizer(l: MpvLibrary, c: Pointer, level: AudioLevelling?) {
        runCatching { l.mpv_set_property_string(c, "af", level?.let { AudioNormalizerProfile.mpvAudioFilter(it) } ?: "") }
    }

    // The newest live switch, for a context that was being created when it came in. A
    // holder, so "switched off" (level null) differs from "never switched" (no holder).
    private class LevellingOverride(val level: AudioLevelling?)
    @Volatile private var normalizerOverride: LevellingOverride? = null

    override fun release() {
        // App shutdown — tear the live context down exactly like a surface detach
        // (detachFromPanel runs + awaits teardown on the lifecycle thread), then stop
        // the executor since no further attach/detach can occur. shutdown() lets the
        // already-submitted teardown finish; the thread is a daemon so it can't hold
        // up JVM exit regardless.
        detachFromPanel()
        lifecycleExecutor.shutdown()
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
        val label = mpvScaleLabel(
            settingsStore.settings.value.upscalingMode,
            _rtxStatus.value.superResolution == SuperResolutionState.ACTIVE,
        )
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
        if (!File(dir, NativeLibs.mpvFileName()).exists()) return null
        val existing = System.getProperty("jna.library.path")
        System.setProperty(
            "jna.library.path",
            if (existing.isNullOrBlank()) dir.absolutePath else "${dir.absolutePath}${File.pathSeparator}$existing",
        )
        return dir.absolutePath
    }

    private companion object {
        // reply_userdata for the RTX properties (the transport ones use 0).
        const val RTX_USERDATA = 1L

        // MPV_EVENT_LOG_MESSAGE (not in MpvConst).
        const val MPV_EVENT_LOG_MESSAGE = 2

        // How long a new filter gets to show up in video-out-params before a missing
        // effect counts as a failure. The chain is rebuilt on the next frame, so this
        // is generous even for a stream that is still buffering.
        const val RTX_SETTLE_MS = 3000L
    }
}
