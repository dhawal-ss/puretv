package com.puretv.twitch.desktop

import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.toComposeImageBitmap
import java.awt.Font
import java.awt.GradientPaint
import java.awt.RenderingHints
import java.awt.geom.RoundRectangle2D
import java.awt.image.BufferedImage

/**
 * Generates the app icon at runtime using Java2D so we avoid shipping a binary
 * asset in the repository.
 *
 * Design: dark rounded square (#08081A) with a top-left → bottom-right purple
 * gradient overlay, centred bold white "P".
 */
fun createAppIcon(): BitmapPainter {
    val size = 256
    val img = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
    val g = img.createGraphics()

    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
    g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
    g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)

    // Clip to a rounded rectangle so corners are transparent
    g.clip = RoundRectangle2D.Float(0f, 0f, size.toFloat(), size.toFloat(), 56f, 56f)

    // Dark base
    g.color = java.awt.Color(0x08, 0x08, 0x1A)
    g.fillRect(0, 0, size, size)

    // Purple diagonal gradient
    g.paint = GradientPaint(
        0f, 0f, java.awt.Color(0xB0, 0x60, 0xFF),
        size.toFloat(), size.toFloat(), java.awt.Color(0x45, 0x15, 0xB0),
    )
    g.fillRect(0, 0, size, size)

    // Centred bold "P" in white
    g.color = java.awt.Color(255, 255, 255, 235)
    g.font = Font("SansSerif", Font.BOLD, 152)
    val fm = g.fontMetrics
    val tx = (size - fm.stringWidth("P")) / 2
    val ty = (size - fm.height) / 2 + fm.ascent
    g.drawString("P", tx, ty)

    g.dispose()
    return BitmapPainter(img.toComposeImageBitmap())
}
