package com.puretv.twitch.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.puretv.twitch.android.ui.theme.PureTvColors
import com.puretv.twitch.core.model.StreamInfo

/**
 * Twitch thumbnail/box-art URLs are templates carrying `{width}x{height}`.
 * Substitute a concrete size before loading.
 */
fun streamThumbUrl(template: String, width: Int = 640, height: Int = 360): String =
    template.replace("{width}", width.toString()).replace("{height}", height.toString())

/**
 * The core discovery unit, reused on Home, Search, and Channel. A 16:9 Coil
 * thumbnail under a bottom gradient scrim (so the LIVE badge always reads, even
 * over a bright frame), then a clean type hierarchy: channel name, game, title.
 *
 * StreamInfo carries no avatar, so the card leans entirely on the thumbnail. The
 * thumbnail sits on a Surface2 placeholder so it is never a black box while it
 * loads, and the whole card is clipped to the medium shape BEFORE .clickable, so
 * the touch ripple is contained to the rounded silhouette.
 */
@Composable
fun StreamCard(stream: StreamInfo, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .background(PureTvColors.Surface1),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .background(PureTvColors.Surface2),
        ) {
            AsyncImage(
                model = streamThumbUrl(stream.thumbnailUrl),
                contentDescription = stream.title.ifBlank { stream.userName },
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
            // Bottom scrim: keeps the badge legible over a busy lower frame.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0.55f to Color.Transparent,
                            1f to Color.Black.copy(alpha = 0.65f),
                        ),
                    ),
            )
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(6.dp),
            ) {
                LiveBadge(viewerCount = stream.viewerCount.toLong())
            }
        }
        Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                stream.userName,
                style = MaterialTheme.typography.titleMedium,
                color = PureTvColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                stream.gameName,
                style = MaterialTheme.typography.bodySmall,
                color = PureTvColors.TwitchPurpleLight,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                stream.title,
                style = MaterialTheme.typography.bodySmall,
                // AA-contrast tertiary, not the near-invisible TextMuted.
                color = PureTvColors.TextTertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
