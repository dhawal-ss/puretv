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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
 * thumbnail with a LIVE + viewer-count overlay, then the streamer name, game,
 * and title.
 */
@Composable
fun StreamCard(stream: StreamInfo, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clickable(onClick = onClick)
            .clip(RoundedCornerShape(10.dp))
            .background(PureTvColors.Surface),
    ) {
        Box(modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f)) {
            AsyncImage(
                model = streamThumbUrl(stream.thumbnailUrl),
                contentDescription = stream.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(6.dp)
                    .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 4.dp, vertical = 2.dp),
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
                color = PureTvColors.TextMuted,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
