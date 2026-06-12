package com.puretv.twitch.tv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.puretv.twitch.tv.ui.theme.PureTvTvColors
import java.util.Locale

/** TV counterpart of the phone app's `LiveBadge` — same red "LIVE" pill + abbreviated viewer count. */
@Composable
fun TvLiveBadge(viewerCount: Long, modifier: Modifier = Modifier) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "LIVE",
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
            modifier = modifier
                .background(PureTvTvColors.Live, RoundedCornerShape(4.dp))
                .padding(horizontal = 8.dp, vertical = 3.dp),
        )
        Text(
            text = "  ${formatTvViewerCount(viewerCount)} viewers",
            style = MaterialTheme.typography.bodyMedium,
            color = PureTvTvColors.TextSecondary,
        )
    }
}

internal fun formatTvViewerCount(count: Long): String = when {
    count >= 1_000_000 -> String.format(Locale.US, "%.1fM", count / 1_000_000.0)
    count >= 1_000 -> String.format(Locale.US, "%.1fK", count / 1_000.0)
    else -> count.toString()
}
