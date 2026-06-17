package com.puretv.twitch.desktop.ui.emotes

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Codec
import org.jetbrains.skia.Data
import org.jetbrains.skia.Image

/**
 * Decodes animated GIF/WebP [bytes] into per-frame ImageBitmaps + durations using Skiko.
 * Returns null for single-frame/static images (caller renders them via Coil) and for any
 * decode failure — never throws to the UI.
 */
fun decodeAnimatedFrames(bytes: ByteArray): AnimatedEmoteFrames? = runCatching {
    // Codec, Data and the per-frame Bitmaps are native-backed Skia objects
    // (org.jetbrains.skia.impl.Managed). Without explicit close() their off-heap
    // memory lingers until the JVM GC runs their Cleaner — and GC is driven by
    // HEAP pressure, not native pressure, so under many emote decodes native
    // memory bloats unbounded (audit P0-5). They are pure decode intermediates:
    // the per-frame `Image` (held by the returned ImageBitmap) keeps its own
    // refcounted pixelref, so closing the source Bitmap/Codec/Data here is safe.
    val data = Data.makeFromBytes(bytes)
    val codec = Codec.makeFromData(data)
    try {
        val count = codec.frameCount
        if (count <= 1) {
            null // static -> use Coil path
        } else {
            val info = codec.imageInfo
            val frames = ArrayList<ImageBitmap>(count)
            val durations = ArrayList<Int>(count)
            for (i in 0 until count) {
                val bmp = Bitmap()
                try {
                    bmp.allocPixels(info)
                    // The 2-arg readPixels uses priorFrame = NoFrame, so SkCodec recursively
                    // decodes this frame's required-frame chain and composites it (correct
                    // GIF/WebP disposal). The 3-arg priorFrame overload is ONLY a perf
                    // optimization — do not switch to it without seeding the dst with the
                    // prior frame's pixels, or compositing breaks.
                    codec.readPixels(bmp, i)
                    frames += Image.makeFromBitmap(bmp).toComposeImageBitmap()
                    durations += codec.framesInfo.getOrNull(i)?.duration ?: 100
                } finally {
                    bmp.close()
                }
            }
            AnimatedEmoteFrames(frames, durations)
        }
    } finally {
        codec.close()
        data.close()
    }
}.getOrNull()
