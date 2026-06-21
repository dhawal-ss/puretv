package com.puretv.twitch.android.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.puretv.twitch.android.ui.theme.PureTvColors
import com.puretv.twitch.android.ui.theme.ViewerCountStyle
import java.util.Locale

/**
 * Small red "LIVE" pill + abbreviated viewer count, used on stream cards and the
 * player overlay (Section 10.3). The dot pulses (animated alpha) so the badge
 * reads as a live signal, and the count is set in monospace so it never jitters
 * the layout as viewers tick up and down.
 */
@Composable
fun LiveBadge(viewerCount: Long, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "live-pulse")
    val dotAlpha by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "live-dot-alpha",
    )

    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .background(PureTvColors.Live, RoundedCornerShape(4.dp))
                .padding(horizontal = 6.dp, vertical = 3.dp),
        ) {
            Spacer(
                Modifier
                    .size(5.dp)
                    .alpha(dotAlpha)
                    .background(Color.White, CircleShape),
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = "LIVE",
                style = ViewerCountStyle,
                color = Color.White,
            )
        }
        Spacer(Modifier.width(6.dp))
        Text(
            text = "${formatViewerCount(viewerCount)} viewers",
            style = ViewerCountStyle,
            color = PureTvColors.TextSecondary,
        )
    }
}

/**
 * Abbreviates a viewer count: 950 -> "950", 1000 -> "1K", 12_345 -> "12.3K",
 * 1_200_000 -> "1.2M". A trailing ".0" is dropped so a round thousand reads as
 * "1K" rather than "1.0K".
 */
internal fun formatViewerCount(count: Long): String = when {
    count >= 1_000_000 -> trimZero(String.format(Locale.US, "%.1f", count / 1_000_000.0)) + "M"
    count >= 1_000 -> trimZero(String.format(Locale.US, "%.1f", count / 1_000.0)) + "K"
    else -> count.toString()
}

private fun trimZero(s: String): String = if (s.endsWith(".0")) s.dropLast(2) else s
