package com.puretv.twitch.desktop.player

import com.puretv.twitch.core.model.AppSettings
import com.puretv.twitch.desktop.platform.RtxGpu
import java.util.concurrent.ConcurrentHashMap

/**
 * NVIDIA RTX Video for the single-stream player: Super Resolution (an AI upscaler
 * for video shown larger than it was sent) and RTX Video HDR (SDR made into HDR when
 * Windows HDR is on). VLC 3.0.21 and newer drive both through Direct3D 11, but only
 * as libVLC instance arguments (a per-media option never reaches the video output),
 * so the player that uses them runs on its own instance (see [RtxVlcEngines]).
 *
 * What the viewer asked for, after checking it can run here. [NONE] off Windows, or
 * without an RTX card.
 */
data class RtxVideoChoice(val superResolution: Boolean, val hdr: Boolean) {
    val any: Boolean get() = superResolution || hdr

    /** The libVLC instance arguments for this choice (see [forVlc] for when HDR may be in it). */
    fun vlcArgs(): List<String> = buildList {
        if (superResolution) add("--d3d11-upscale-mode=super")
        if (hdr) add("--d3d11-hdr-mode=generate")
    }

    /**
     * What VLC may be given of this choice. HDR only when the window's display has
     * Windows HDR on ([displayHdr]); elsewhere VLC tone-maps the generated HDR straight
     * back down, which blows the picture out. And only on a VLC that runs it safely
     * ([vlcHdrSafe]): VLC 3.0.22 and 3.0.23 crash a few seconds into a stream with
     * `--d3d11-hdr-mode=generate` (access violation, vlc.exe too, RTX 4070); 3.0.21 and
     * 3.0.24 ran it stable, HDR display or not.
     */
    fun forVlc(displayHdr: Boolean, vlcHdrSafe: Boolean): RtxVideoChoice = copy(hdr = hdr && displayHdr && vlcHdrSafe)

    companion object {
        val NONE = RtxVideoChoice(superResolution = false, hdr = false)

        fun from(settings: AppSettings, rtxGpu: Boolean): RtxVideoChoice =
            if (!rtxGpu) NONE else RtxVideoChoice(settings.rtxSuperResolution, settings.rtxHdr)
    }
}

/**
 * True for a libVLC version string ("3.0.24 Vetinari", "3.0.24-rc1 ...") at 3.0.24 or
 * later, or any 3.0 release before 3.0.22 that has the HDR mode at all (3.0.21). VLC
 * 3.0.22 and 3.0.23 crash with RTX Video HDR; unknown versions count as unsafe.
 */
internal fun vlcHdrSafe(version: String?): Boolean {
    val parts = version?.trim()?.split(' ')?.firstOrNull()?.substringBefore('-')?.split('.')?.mapNotNull { it.toIntOrNull() }
        ?: return false
    if (parts.size < 3) return false
    val (major, minor, patch) = parts
    return when {
        major > 3 || (major == 3 && minor > 0) -> true
        major == 3 && minor == 0 -> patch == 21 || patch >= 24
        else -> false
    }
}

/**
 * The size in pixels a [sourceWidth] x [sourceHeight] video is drawn at inside an area
 * of [areaWidth] x [areaHeight], keeping its shape (letterboxed), as VLC draws it.
 */
internal fun fittedSize(sourceWidth: Int, sourceHeight: Int, areaWidth: Double, areaHeight: Double): Pair<Int, Int> {
    if (sourceWidth <= 0 || sourceHeight <= 0 || areaWidth <= 0 || areaHeight <= 0) return 0 to 0
    val scale = minOf(areaWidth / sourceWidth, areaHeight / sourceHeight)
    return Math.round(sourceWidth * scale).toInt() to Math.round(sourceHeight * scale).toInt()
}

/** "1080p->1440p" for the timing log, or "size unknown" before the video has a size. */
internal fun rtxSizeLabel(s: RtxVideoStatus): String {
    val from = s.sourceHeight ?: return "size unknown"
    val to = s.outputHeight ?: return "size unknown"
    return "${from}p->${to}p"
}

/** What Super Resolution is doing for the video on screen. */
enum class SuperResolutionState {
    /** Not asked for (or no RTX card). */
    OFF,

    /** Asked for; the video has not opened yet. */
    WAITING,

    /** Running: the video is shown larger than it was sent. */
    ACTIVE,

    /** Ready, but the video is not shown larger than it was sent, so there is nothing to upscale. */
    STANDBY,

    /** The GPU or driver refused it; VLC fell back to its normal scaling. */
    UNSUPPORTED,
}

/** What RTX Video HDR is doing for the video on screen. */
enum class RtxHdrState {
    OFF,
    WAITING,

    /** The video goes out to the display as HDR. */
    ACTIVE,

    /** The display is in SDR mode (Windows HDR is off for it), so there is nothing to do. */
    DISPLAY_SDR,

    /** The GPU or driver refused it. */
    UNSUPPORTED,
}

/**
 * The RTX Video status of one player, built from libVLC's own log ([RtxLogReader])
 * and the size the video is drawn at. [adapter] is the GPU VLC decodes on; when it
 * is not NVIDIA, Windows is running PureTV on another graphics chip.
 */
data class RtxVideoStatus(
    val superResolution: SuperResolutionState = SuperResolutionState.OFF,
    val hdr: RtxHdrState = RtxHdrState.OFF,
    val adapter: String? = null,
    val adapterIsNvidia: Boolean? = null,
    val sourceHeight: Int? = null,
    val outputHeight: Int? = null,
)

/**
 * Folds libVLC log lines (module, message) into an [RtxVideoStatus]. Pure, so the
 * wording VLC 3.0.23 uses is pinned by tests. Lines it does not know leave the
 * status as it was.
 *
 * One reader per instance switch, not per video: libVLC can keep its video output
 * from one stream to the next without logging these lines again.
 *
 * Super Resolution is only "selected" from the log; whether it is working depends on
 * whether the output is larger than the source, which the player supplies ([sized]).
 */
class RtxLogReader(private val choice: RtxVideoChoice) {
    private var srSelected = false
    private var srRefused = false
    private var hdrOutput: Boolean? = null
    private var hdrRefused = false
    private var adapter: String? = null
    private var adapterIsNvidia: Boolean? = null
    private var sourceW = 0
    private var sourceH = 0
    private var outputW = 0
    private var outputH = 0

    /** Reads one log line; true when it changed what is known. */
    fun read(module: String?, message: String): Boolean {
        when {
            message.startsWith("Using Super Resolution scaler") -> srSelected = true
            message.startsWith("Super Resolution filter not supported") -> srRefused = true
            message.startsWith("True HDR not supported") ||
                message.startsWith("Failed to enable NVIDIA True HDR") -> hdrRefused = true
            message.startsWith("using colorspace ") -> hdrOutput = message.contains("gamma:2084")
            module == "avcodec" && message.startsWith("Using D3D11VA (") -> {
                // "Using D3D11VA (NVIDIA GeForce RTX 4070, vendor 10de(NVIDIA), device 2786, ...) for hardware decoding"
                val inside = message.removePrefix("Using D3D11VA (")
                adapter = inside.substringBefore(", vendor").trim().ifEmpty { null }
                adapterIsNvidia = inside.contains("vendor 10de", ignoreCase = true)
            }
            else -> return false
        }
        return true
    }

    /** VLC would not take the Super Resolution argument at all (a VLC older than 3.0.21). */
    fun refused() {
        srRefused = true
    }

    /** Everything the log has to say is known: the player can stop listening. */
    val settled: Boolean get() =
        (srSelected || srRefused || !choice.superResolution) &&
            (hdrOutput != null || hdrRefused || !choice.hdr) &&
            adapterIsNvidia != null

    /** The source and drawn sizes in pixels, as the player knows them now. */
    fun sized(sourceWidth: Int, sourceHeight: Int, outputWidth: Int, outputHeight: Int) {
        sourceW = sourceWidth; sourceH = sourceHeight
        outputW = outputWidth; outputH = outputHeight
    }

    fun status(): RtxVideoStatus {
        val sr = when {
            !choice.superResolution -> SuperResolutionState.OFF
            srRefused -> SuperResolutionState.UNSUPPORTED
            !srSelected -> SuperResolutionState.WAITING
            sourceW <= 0 || sourceH <= 0 || outputW <= 0 -> SuperResolutionState.WAITING
            outputW > sourceW || outputH > sourceH -> SuperResolutionState.ACTIVE
            else -> SuperResolutionState.STANDBY
        }
        val hdr = when {
            !choice.hdr -> RtxHdrState.OFF
            hdrRefused -> RtxHdrState.UNSUPPORTED
            hdrOutput == null -> RtxHdrState.WAITING
            hdrOutput == true -> RtxHdrState.ACTIVE
            else -> RtxHdrState.DISPLAY_SDR
        }
        return RtxVideoStatus(
            superResolution = sr,
            hdr = hdr,
            adapter = adapter,
            adapterIsNvidia = adapterIsNvidia,
            sourceHeight = sourceH.takeIf { it > 0 },
            outputHeight = outputH.takeIf { it > 0 },
        )
    }
}

/**
 * The libVLC instances the main player picks from. RTX Video only works as instance
 * arguments, and multi-stream tiles and extra windows should not pay for it, so the
 * main player plays on an instance of its own for each [RtxVideoChoice], built the
 * first time that choice is needed. With RTX Video off it shares [standard].
 */
class RtxVlcEngines(
    val standard: VlcEngine,
    private val extraArgs: () -> List<String>,
    private val choice: () -> RtxVideoChoice,
) {
    private val engines = ConcurrentHashMap<RtxVideoChoice, VlcEngine>()

    /** Whether RTX Video can run here at all (Windows with an RTX card). Reads the GPU list once. */
    val supported: Boolean get() = RtxGpu.present

    /** What the settings ask for, before any check of the display or VLC. */
    fun wanted(): RtxVideoChoice = choice()

    /** The choice for a window on a display with Windows HDR on ([displayHdr]) or not, as VLC can take it. */
    fun choiceFor(displayHdr: Boolean): RtxVideoChoice =
        // libVLC's version is read only when HDR could be used: it loads libVLC directly.
        choice().let { it.forVlc(displayHdr, vlcHdrSafe = it.hdr && displayHdr && vlcHdrSafe) }

    /**
     * Whether the bundled libVLC runs RTX Video HDR without crashing (see
     * [RtxVideoChoice.forVlc]). Only asked with an RTX card (so only on Windows): reading
     * the version loads libVLC directly, which on macOS breaks vlcj's own loading (it must
     * load libvlccore first) and left the Mac build with no player at all.
     */
    val vlcHdrSafe: Boolean by lazy { RtxGpu.present && vlcHdrSafe(VlcEngine.libVlcVersion()) }

    /** The instance for [wanted]: [standard] when it asks for nothing. Cheap; libVLC loads on first use. */
    fun engineFor(wanted: RtxVideoChoice): VlcEngine =
        if (!wanted.any) standard
        else engines.computeIfAbsent(wanted) { VlcEngine(extraArgs, optionalArgs = { wanted.vlcArgs() }) }

    /** Whether the instance for [wanted] already started, so moving to it is instant. */
    fun isBuilt(wanted: RtxVideoChoice): Boolean = engineFor(wanted).isBuilt

    /** Frees every RTX instance that was built. Call once at shutdown, after the players. */
    fun releaseIfBuilt() = engines.values.forEach { it.releaseIfBuilt() }
}

/**
 * The F3 stats overlay text for the VLC player, drawn by VLC itself (its marquee) since
 * nothing of Compose can paint over the native video. Leads with what is doing the
 * scaling, the way the mpv overlay does. Pure, so the wording is tested.
 */
internal fun vlcStatsOsd(
    status: RtxVideoStatus,
    sourceWidth: Int,
    sourceHeight: Int,
    outputWidth: Int,
    outputHeight: Int,
    picturesLost: Int,
): String {
    val scaler = when (status.superResolution) {
        SuperResolutionState.ACTIVE -> "RTX Super Resolution"
        SuperResolutionState.STANDBY -> "RTX Super Resolution (ready, not enlarging)"
        SuperResolutionState.WAITING -> "RTX Super Resolution (starting)"
        SuperResolutionState.UNSUPPORTED -> "standard (RTX refused)"
        SuperResolutionState.OFF -> "standard"
    }
    val hdr = when (status.hdr) {
        RtxHdrState.ACTIVE -> "RTX HDR on"
        RtxHdrState.DISPLAY_SDR -> "SDR display"
        RtxHdrState.UNSUPPORTED -> "RTX HDR refused"
        RtxHdrState.WAITING -> "RTX HDR starting"
        RtxHdrState.OFF -> "SDR"
    }
    val verdict = when (val u = upscaleSummary(sourceWidth, sourceHeight, outputWidth, outputHeight)) {
        is UpscaleStatus.Upscaling -> "x%.2f upscale".format(java.util.Locale.US, u.factor)
        is UpscaleStatus.Downscaling -> "x%.2f downscale".format(java.util.Locale.US, u.factor)
        UpscaleStatus.Native -> "native"
    }
    return buildString {
        appendLine("VLC | $scaler | $hdr")
        appendLine("${sourceWidth}x$sourceHeight -> ${outputWidth}x$outputHeight    $verdict")
        // Nothing that changes every second (a bitrate): VLC rebuilds its overlay
        // whenever the text changes.
        append("GPU ${status.adapter ?: "?"} | dropped frames $picturesLost")
    }
}
