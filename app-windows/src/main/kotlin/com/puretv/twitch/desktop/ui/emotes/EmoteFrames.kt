package com.puretv.twitch.desktop.ui.emotes

import androidx.compose.ui.graphics.ImageBitmap

/** Decoded animated emote: one ImageBitmap per frame plus each frame's display duration. */
data class AnimatedEmoteFrames(
    val frames: List<ImageBitmap>,
    val durationsMs: List<Int>,
)

/**
 * Frame index to show at [elapsedMs] given per-frame [durationsMs], looping.
 * Zero/negative durations are floored to 100ms so a codec reporting 0 never
 * divides by zero or pins on frame 0.
 */
fun frameIndexAt(elapsedMs: Long, durationsMs: List<Int>): Int {
    if (durationsMs.size <= 1) return 0
    val safe = durationsMs.map { if (it <= 0) 100 else it }
    val total = safe.sum().toLong()
    if (total <= 0) return 0
    var t = elapsedMs % total
    for (i in safe.indices) {
        t -= safe[i]
        if (t < 0) return i
    }
    return safe.lastIndex
}
