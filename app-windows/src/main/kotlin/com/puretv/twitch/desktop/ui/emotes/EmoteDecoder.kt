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
    val codec = Codec.makeFromData(Data.makeFromBytes(bytes))
    val count = codec.frameCount
    if (count <= 1) return null // static -> use Coil path
    val info = codec.imageInfo
    val frames = ArrayList<ImageBitmap>(count)
    val durations = ArrayList<Int>(count)
    for (i in 0 until count) {
        val bmp = Bitmap()
        bmp.allocPixels(info)
        codec.readPixels(bmp, i)
        frames += Image.makeFromBitmap(bmp).toComposeImageBitmap()
        durations += codec.framesInfo.getOrNull(i)?.duration ?: 100
    }
    AnimatedEmoteFrames(frames, durations)
}.getOrNull()
