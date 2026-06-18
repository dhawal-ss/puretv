package com.puretv.twitch.desktop.ui.emotes

import androidx.compose.ui.graphics.ImageBitmap
import org.jetbrains.skia.Image

/**
 * Decoded animated emote: one ImageBitmap per frame plus each frame's display duration.
 *
 * Each ImageBitmap is backed by a native (off-heap) skia [Image]; we keep those handles in
 * [nativeImages] so [EmoteFrameCache] can free them deterministically on eviction via [close].
 * Without that, the JVM only reclaims them when HEAP pressure eventually runs their Cleaner —
 * and GC is driven by heap, not native, pressure, so a long chat session of distinct animated
 * emotes bloats native memory unbounded (audit P0-5 / "worse the longer chat runs").
 */
class AnimatedEmoteFrames(
    val frames: List<ImageBitmap>,
    val durationsMs: List<Int>,
    private val nativeImages: List<Image> = emptyList(),
) {
    @Volatile private var closed = false

    /** Visible for tests: whether [close] has freed the native handles. */
    internal val isClosed: Boolean get() = closed

    /**
     * Free the off-heap pixel memory. MUST only be called once no composable still renders
     * these frames — the ImageBitmaps wrap these Images directly, so closing one that is still
     * being drawn is a use-after-free. The cache gates this behind a reference count. Idempotent.
     */
    fun close() {
        if (closed) return
        closed = true
        nativeImages.forEach { runCatching { it.close() } }
    }
}

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
