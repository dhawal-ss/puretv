package com.puretv.twitch.desktop.ui.emotes

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Codec
import org.jetbrains.skia.Data
import org.jetbrains.skia.Image

/** Cap the frames we decode/hold for one animated emote. Real emotes are short loops
 *  (tens of frames); this bounds memory + decode time for a pathological huge animated
 *  WebP/GIF to a fixed budget — the rest of the loop is simply not played. */
private const val MAX_ANIMATED_FRAMES = 120

/**
 * Decodes animated GIF/WebP [bytes] into per-frame ImageBitmaps + durations using Skiko.
 * Returns null for single-frame/static images (caller renders them via Coil) and for any
 * decode failure — never throws to the UI.
 */
fun decodeAnimatedFrames(bytes: ByteArray): AnimatedEmoteFrames? = runCatching {
    // Codec, Data and the Bitmap are native-backed Skia objects (org.jetbrains.skia.impl.Managed).
    // Without explicit close() their off-heap memory lingers until the JVM GC runs their Cleaner —
    // and GC is HEAP- not native-pressure driven, so under many emote decodes native memory bloats
    // (audit P0-5). The per-frame `Image` (held by the returned ImageBitmap) keeps its own
    // refcounted pixelref, so closing the source Bitmap/Codec/Data here is safe.
    val data = Data.makeFromBytes(bytes)
    val codec = Codec.makeFromData(data)
    try {
        val total = codec.frameCount
        if (total <= 1) return@runCatching null // static -> use Coil path
        val count = minOf(total, MAX_ANIMATED_FRAMES)
        val info = codec.imageInfo
        val framesInfo = codec.framesInfo
        val frames = ArrayList<ImageBitmap>(count)
        val durations = ArrayList<Int>(count)
        // Keep the native skia Images alongside the ImageBitmaps that wrap them so the cache can
        // free their off-heap pixels deterministically on eviction (see AnimatedEmoteFrames.close).
        val images = ArrayList<Image>(count)
        // INCREMENTAL O(n) DECODE. The 2-arg readPixels (priorFrame = NoFrame) recursively
        // re-decodes each frame's required-frame chain, which is O(n^2) and froze the UI on large
        // emotes. Instead reuse ONE canvas bitmap: when a frame's requiredFrame is the
        // immediately-preceding frame (the common sequential, keep-disposal case), pass priorFrame
        // so SkCodec composites the new frame straight onto the canvas (which already holds that
        // prior frame) — O(1) per frame. Any frame whose requiredFrame is NOT the prior frame falls
        // back to a standalone full decode (priorFrame = -1), identical to the old path, so this is
        // never less correct than before. Image.makeFromBitmap copies a mutable bitmap's pixels
        // (the old per-frame code relied on the same: it closed the source bmp while the Image
        // lived on), so each snapshot is independent of the canvas we go on to overwrite.
        val canvas = Bitmap()
        try {
            canvas.allocPixels(info)
            var prevIndex = -1
            for (i in 0 until count) {
                val required = framesInfo.getOrNull(i)?.requiredFrame ?: -1
                val priorFrame = if (prevIndex >= 0 && required == prevIndex) prevIndex else -1
                codec.readPixels(canvas, i, priorFrame)
                val img = Image.makeFromBitmap(canvas)
                images += img
                frames += img.toComposeImageBitmap()
                durations += framesInfo.getOrNull(i)?.duration ?: 100
                prevIndex = i
            }
        } finally {
            canvas.close()
        }
        AnimatedEmoteFrames(frames, durations, images)
    } finally {
        codec.close()
        data.close()
    }
}.getOrNull()
