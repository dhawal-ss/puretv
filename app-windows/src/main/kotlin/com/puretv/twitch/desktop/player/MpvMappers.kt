package com.puretv.twitch.desktop.player

import com.puretv.twitch.core.model.UpscalingMode
import java.util.Locale

/** What [MpvPlayer.attachToPanel] should do given the current binding state. */
internal enum class AttachAction { ATTACH, REATTACH, NOOP }

/**
 * Decide how to (re)bind mpv to a surface. mpv's `wid` is set once before
 * `mpv_initialize`, so a live context belongs to one Canvas/HWND: re-attaching
 * to a *different* surface requires tearing the old context down first, never
 * reusing one bound to a now-destroyed HWND. Pure + unit-tested.
 */
internal fun mpvAttachAction(initialized: Boolean, sameSurface: Boolean): AttachAction = when {
    !initialized -> AttachAction.ATTACH
    sameSurface -> AttachAction.NOOP
    else -> AttachAction.REATTACH
}

/** Verdict for the upscaling stats overlay: is the output bigger than the source? */
internal sealed interface UpscaleStatus {
    data class Upscaling(val factor: Double) : UpscaleStatus
    object Native : UpscaleStatus
    data class Downscaling(val factor: Double) : UpscaleStatus
}

/**
 * Compare a video source size against the actual output (VO surface) size to
 * decide whether scaling is happening, and by how much. Pure + unit-tested — the
 * honest core of the upscaling proof. `factor` is the linear height ratio;
 * non-positive (unknown) dimensions are reported as [UpscaleStatus.Native].
 */
internal fun upscaleSummary(srcW: Int, srcH: Int, outW: Int, outH: Int): UpscaleStatus {
    if (srcW <= 0 || srcH <= 0 || outW <= 0 || outH <= 0) return UpscaleStatus.Native
    val factor = outH.toDouble() / srcH.toDouble()
    return when {
        factor > 1.01 -> UpscaleStatus.Upscaling(factor)
        factor < 0.99 -> UpscaleStatus.Downscaling(factor)
        else -> UpscaleStatus.Native
    }
}

/**
 * Format the upscaling stats into mpv's OSD text (rendered by `show-text`).
 * Deliberately ASCII-only (no ×/→/· — keeps it safe across JNA string encoding)
 * and restrained: three terse lines, editorial not gamer-HUD. Pure + unit-tested.
 */
internal fun formatStatsOsd(stats: VideoStats, modeLabel: String): String {
    val verdict = when (val u = upscaleSummary(stats.sourceWidth, stats.sourceHeight, stats.outputWidth, stats.outputHeight)) {
        is UpscaleStatus.Upscaling -> "x%.2f upscale".format(Locale.US, u.factor)
        is UpscaleStatus.Downscaling -> "x%.2f downscale".format(Locale.US, u.factor)
        UpscaleStatus.Native -> "native"
    }
    val vo = stats.vo ?: "?"
    val hwdec = stats.hwdec ?: "sw"
    val fps = stats.fps?.let { "%.0f fps".format(Locale.US, it) } ?: "-- fps"
    val scaler = stats.scaler ?: "?"
    val shader = stats.shaderName?.let { " + $it" } ?: ""
    // Lead with the active upscaling mode so the overlay answers "is it on, and
    // which?" at a glance — the whole point of the proof.
    return buildString {
        appendLine("mpv | $modeLabel | $vo | $hwdec | $fps")
        appendLine("${stats.sourceWidth}x${stats.sourceHeight} -> ${stats.outputWidth}x${stats.outputHeight}    $verdict")
        append("scaler $scaler$shader")
    }
}

/** UI label for an upscaling mode (also the F3 overlay tag). */
internal fun upscaleModeLabel(mode: UpscalingMode): String = when (mode) {
    UpscalingMode.OFF -> "Off"
    UpscalingMode.STANDARD -> "Sharp"
    UpscalingMode.ANIME -> "Anime"
}

/** Resolved (forward-slashed) absolute paths to the bundled upscaling shaders.
 *  Any path may be blank if its file wasn't found — the mode degrades gracefully. */
internal data class ShaderPaths(val cas: String, val animePipeline: List<String>)

/** Join shader paths for mpv's `glsl-shaders` — a PATH list, so the separator is
 *  the OS path separator (';' on Windows). Blank paths are dropped. */
internal fun joinShaders(paths: List<String>): String =
    paths.filter { it.isNotBlank() }.joinToString(java.io.File.pathSeparator)

/**
 * Maps an [UpscalingMode] to the COMPLETE set of mpv scaler properties. Every mode
 * returns the SAME four keys (scale, cscale, dscale, glsl-shaders) so applying one
 * mode over another at runtime fully overwrites the previous scaler — `glsl-shaders`
 * is cleared to "" when a mode has no shader, which is what makes live switching
 * actually remove the previous mode's shaders. Content-matched for a mixed Twitch
 * diet: STANDARD ("Sharp") = ewa_lanczossharp + CAS for live-action; ANIME = the
 * full Anime4K Mode-A pipeline. A missing shader file (blank path) degrades to the
 * base scaler rather than a broken path. Pure + unit-tested; used at init
 * (set_option) and live re-apply (set_property).
 */
internal fun mpvScalerProps(mode: UpscalingMode, shaders: ShaderPaths): Map<String, String> = when (mode) {
    UpscalingMode.OFF -> mapOf("scale" to "bilinear", "cscale" to "bilinear", "dscale" to "bilinear", "glsl-shaders" to "")
    UpscalingMode.STANDARD -> mapOf(
        "scale" to "ewa_lanczossharp", "cscale" to "ewa_lanczossharp", "dscale" to "mitchell",
        "glsl-shaders" to joinShaders(listOf(shaders.cas)),
    )
    UpscalingMode.ANIME -> mapOf(
        "scale" to "ewa_lanczossharp", "cscale" to "ewa_lanczossharp", "dscale" to "mitchell",
        "glsl-shaders" to joinShaders(shaders.animePipeline),
    )
}

/** Apply one observed mpv property change (read as a string) to a [PlayerStatus].
 *  Pure + testable; unknown property names pass through unchanged. */
internal fun mpvApply(s: PlayerStatus, name: String, value: String?): PlayerStatus = when (name) {
    "time-pos" -> s.copy(positionMs = ((value?.toDoubleOrNull() ?: 0.0) * 1000).toLong())
    "duration" -> s.copy(durationMs = ((value?.toDoubleOrNull() ?: 0.0) * 1000).toLong())
    "pause" -> s.copy(isPlaying = value == "no")
    "core-idle" -> s.copy(isBuffering = value == "yes")
    "paused-for-cache" -> s.copy(isBuffering = value == "yes")
    "seekable" -> s.copy(isSeekable = value == "yes")
    else -> s
}
