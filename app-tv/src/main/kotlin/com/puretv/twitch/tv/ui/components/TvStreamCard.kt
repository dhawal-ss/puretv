package com.puretv.twitch.tv.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.puretv.twitch.core.model.GameInfo
import com.puretv.twitch.core.model.StreamInfo
import com.puretv.twitch.tv.ui.theme.PureTvTvColors

/**
 * SECTION 7.3 [CRITICAL] — canonical TV card pattern from the spec: scale up
 * 8% on focus, draw a 2dp purple focus ring, and require an explicit
 * `onFocusChanged` so the row/grid can track which card the D-pad landed on
 * (used by [TvHomeScreen]/[TvBrowseScreen] for `focusRestorer` bookkeeping).
 *
 * `Card`'s own `onClick` already wires DPAD_CENTER/ENTER as "confirm" — rule
 * #6 from Section 7.3 ("never use onClick alone without a key handler") is
 * satisfied because `androidx.tv.material3.Card` maps both pointer clicks
 * *and* the D-pad confirm key to the same `onClick` lambda.
 */
@Composable
fun TvStreamCard(stream: StreamInfo, onClick: () -> Unit, modifier: Modifier = Modifier) {
    var isFocused by remember { mutableStateOf(false) }

    Card(
        onClick = onClick,
        modifier = modifier
            .width(220.dp)
            .onFocusChanged { isFocused = it.isFocused }
            .scale(if (isFocused) 1.08f else 1.0f),
        border = CardDefaults.border(
            focusedBorder = Border(BorderStroke(2.dp, PureTvTvColors.FocusBorder)),
        ),
    ) {
        Column(modifier = Modifier.background(PureTvTvColors.Surface).padding(12.dp)) {
            // Thumbnail placeholder — wire Coil's AsyncImage to stream.thumbnailUrl once
            // a real Twitch CLIENT_ID is configured (thumbnails require template URL substitution).
            androidx.compose.foundation.layout.Box(
                modifier = Modifier.fillMaxWidth().height(120.dp).background(PureTvTvColors.SurfaceVariant),
            )
            TvLiveBadge(viewerCount = stream.viewerCount.toLong(), modifier = Modifier.padding(top = 8.dp))
            Text(
                text = stream.userName,
                style = MaterialTheme.typography.titleLarge,
                color = PureTvTvColors.TextPrimary,
                maxLines = 1,
            )
            Text(
                text = stream.gameName.ifBlank { "—" },
                style = MaterialTheme.typography.bodyMedium,
                color = PureTvTvColors.TextSecondary,
                maxLines = 1,
            )
        }
    }
}

/** Same focus pattern as [TvStreamCard], applied to game/category tiles for [TvBrowseScreen]. */
@Composable
fun TvGameCard(game: GameInfo, onClick: () -> Unit, modifier: Modifier = Modifier) {
    var isFocused by remember { mutableStateOf(false) }

    Card(
        onClick = onClick,
        modifier = modifier
            .width(180.dp)
            .onFocusChanged { isFocused = it.isFocused }
            .scale(if (isFocused) 1.08f else 1.0f),
        border = CardDefaults.border(
            focusedBorder = Border(BorderStroke(2.dp, PureTvTvColors.FocusBorder)),
        ),
    ) {
        Column(modifier = Modifier.background(PureTvTvColors.Surface).padding(10.dp)) {
            androidx.compose.foundation.layout.Box(
                modifier = Modifier.fillMaxWidth().height(220.dp).background(PureTvTvColors.SurfaceVariant),
            )
            Text(
                text = game.name,
                style = MaterialTheme.typography.bodyLarge,
                color = PureTvTvColors.TextPrimary,
                maxLines = 1,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}
