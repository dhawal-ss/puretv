package com.puretv.twitch.desktop.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import com.puretv.twitch.desktop.ui.theme.PureTvTheme

/**
 * Minimalist viewer-trend sparkline: a soft accent-tinted area under a thin
 * accent line. Expects >= 2 points (the caller guards); fewer draws nothing.
 */
@Composable
fun Sparkline(points: List<Int>, modifier: Modifier = Modifier) {
    val accent = PureTvTheme.colors.twitchPurple
    Canvas(modifier) {
        if (points.size < 2) return@Canvas
        val maxV = (points.maxOrNull() ?: 1).coerceAtLeast(1)
        val minV = points.minOrNull() ?: 0
        val range = (maxV - minV).coerceAtLeast(1).toFloat()
        val w = size.width
        val h = size.height
        val stepX = w / (points.size - 1)
        val line = Path()
        val area = Path()
        points.forEachIndexed { i, v ->
            val x = stepX * i
            val y = (h - ((v - minV) / range) * h).coerceIn(0f, h)
            if (i == 0) {
                line.moveTo(x, y)
                area.moveTo(x, h)
                area.lineTo(x, y)
            } else {
                line.lineTo(x, y)
                area.lineTo(x, y)
            }
        }
        area.lineTo(w, h)
        area.close()
        drawPath(area, brush = Brush.verticalGradient(listOf(accent.copy(alpha = 0.28f), accent.copy(alpha = 0f))))
        drawPath(line, color = accent, style = Stroke(width = 2f, cap = StrokeCap.Round))
    }
}
