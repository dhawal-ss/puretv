package com.puretv.twitch.tv.ui

import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/**
 * SECTION 03.2 / 07: QR helper for the TV login screen. Renders a string
 * (the device-flow verification URL) into a black-on-white [ImageBitmap] using
 * ZXing's encoder, so a viewer can scan it with a phone instead of typing the
 * activation URL by hand. Encoder-only: no Android ZXing integration module,
 * we walk the BitMatrix into a Bitmap ourselves.
 */
object QrCode {
    fun generate(content: String, sizePx: Int = 480): ImageBitmap? = runCatching {
        val hints = mapOf(
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
            EncodeHintType.MARGIN to 1,
        )
        val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
        val w = matrix.width
        val h = matrix.height
        val pixels = IntArray(w * h)
        val black = 0xFF000000.toInt()
        val white = 0xFFFFFFFF.toInt()
        for (y in 0 until h) {
            val row = y * w
            for (x in 0 until w) {
                pixels[row + x] = if (matrix.get(x, y)) black else white
            }
        }
        Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).apply {
            setPixels(pixels, 0, w, 0, 0, w, h)
        }.asImageBitmap()
    }.getOrNull()
}
