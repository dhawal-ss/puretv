package com.puretv.twitch.tv.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import java.util.Locale

/**
 * TV counterpart of the phone app's `LiveBadge`. Thin wrapper over [TvLivePill]
 * so the app carries exactly one LIVE badge implementation: this one folds the
 * viewer count into the pill's `trailing` slot ("LIVE · 12.3K"), which already
 * renders in [com.puretv.twitch.tv.ui.theme.PureTvTvType.badge]'s mono face.
 */
@Composable
fun TvLiveBadge(viewerCount: Long, modifier: Modifier = Modifier) {
    TvLivePill(modifier = modifier, trailing = formatTvViewerCount(viewerCount))
}

internal fun formatTvViewerCount(count: Long): String = when {
    count >= 1_000_000 -> String.format(Locale.US, "%.1fM", count / 1_000_000.0)
    count >= 1_000 -> String.format(Locale.US, "%.1fK", count / 1_000.0)
    else -> count.toString()
}
