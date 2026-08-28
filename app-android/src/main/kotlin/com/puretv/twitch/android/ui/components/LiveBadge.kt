package com.puretv.twitch.android.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.puretv.twitch.android.ui.theme.PureTvTheme
import com.puretv.twitch.android.ui.theme.PureTvType
import java.util.Locale

/**
 * The LIVE badge + abbreviated viewer count, used on stream cards and the
 * player overlay (Section 10.3). Built on [LivePill] so there is exactly one
 * LIVE pill implementation in the app; the viewer count rides alongside it in
 * monospace so it never jitters the layout as viewers tick up and down.
 */
@Composable
fun LiveBadge(viewerCount: Long, modifier: Modifier = Modifier) {
    val c = PureTvTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
        LivePill()
        Spacer(Modifier.width(6.dp))
        Text(
            text = "${formatViewerCount(viewerCount)} viewers",
            style = PureTvType.data,
            color = c.onSurfaceVariant,
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
