package com.puretv.twitch.desktop.player

import com.puretv.twitch.core.model.UpscalingMode

/** Maps an [UpscalingMode] to the mpv render options to apply (scaler chain +
 *  optional GLSL shader). Pure + unit-tested. */
internal fun mpvUpscaleArgs(mode: UpscalingMode, animeShaderPath: String): Map<String, String> = when (mode) {
    UpscalingMode.OFF -> mapOf("scale" to "bilinear", "cscale" to "bilinear")
    UpscalingMode.STANDARD -> mapOf("scale" to "ewa_lanczossharp", "cscale" to "ewa_lanczossharp", "dscale" to "mitchell")
    UpscalingMode.ANIME -> mapOf("scale" to "ewa_lanczossharp", "cscale" to "ewa_lanczossharp", "dscale" to "mitchell", "glsl-shaders" to animeShaderPath)
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
