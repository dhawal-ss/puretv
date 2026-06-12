package com.puretv.twitch.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.puretv.twitch.android.ui.theme.PureTvColors
import java.util.Locale

/**
 * Small red "LIVE" pill + abbreviated viewer count, used on stream cards
 * and the player overlay (Section 10.3).
 */
@Composable
fun LiveBadge(viewerCount: Long, modifier: Modifier = Modifier) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "LIVE",
            style = MaterialTheme.typography.labelSmall,
            color = androidx.compose.ui.graphics.Color.White,
            modifier = modifier
                .background(PureTvColors.Live, RoundedCornerShape(4.dp))
                .padding(horizontal = 6.dp, vertical = 2.dp),
        )
        Text(
            text = "  ${formatViewerCount(viewerCount)} viewers",
            style = MaterialTheme.typography.bodyMedium,
            color = PureTvColors.TextSecondary,
        )
    }
}

internal fun formatViewerCount(count: Long): String = when {
    count >= 1_000_000 -> String.format(Locale.US, "%.1fM", count / 1_000_000.0)
    count >= 1_000 -> String.format(Locale.US, "%.1fK", count / 1_000.0)
    else -> count.toString()
}
