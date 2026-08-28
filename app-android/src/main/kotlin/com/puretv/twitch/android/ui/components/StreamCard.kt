package com.puretv.twitch.android.ui.components

import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.puretv.twitch.android.ui.theme.PureTvTheme
import com.puretv.twitch.android.ui.theme.PureTvType
import com.puretv.twitch.core.model.StreamInfo

/**
 * Twitch thumbnail/box-art URLs are templates carrying `{width}x{height}`.
 * Substitute a concrete size before loading.
 */
fun streamThumbUrl(template: String, width: Int = 640, height: Int = 360): String =
    template.replace("{width}", width.toString()).replace("{height}", height.toString())

/**
 * The core discovery unit, reused on Home, Search, and Channel. An
 * [ExpressiveCard] holding a 16:9 thumbnail clipped to `shapes.thumbShape`, with
 * a [LivePill] pinned top-left and the mono viewer count bottom-right over
 * [PureTvAndroidColors.cardScrim] so both stay legible over a bright frame, then
 * a clean type hierarchy below the art: channel name, category, title.
 *
 * StreamInfo carries no avatar, so the card leans entirely on the thumbnail.
 * Mirrors the desktop `StreamCard` in `app-windows/.../components/Premium.kt`.
 */
@Composable
fun StreamCard(stream: StreamInfo, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = PureTvTheme.colors
    val shapes = PureTvTheme.shapes

    ExpressiveCard(onClick = onClick, modifier = modifier) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(shapes.thumbShape),
            ) {
                AsyncImage(
                    model = streamThumbUrl(stream.thumbnailUrl),
                    contentDescription = stream.title.ifBlank { stream.userName },
                    modifier = Modifier.fillMaxSize().background(c.surfaceHigh),
                    contentScale = ContentScale.Crop,
                )
                Box(Modifier.fillMaxSize().background(c.cardScrim))
                LivePill(modifier = Modifier.align(Alignment.TopStart).padding(8.dp), height = 22.dp)
                Text(
                    formatViewerCount(stream.viewerCount.toLong()),
                    style = PureTvType.dataSmall,
                    color = Color.White,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp),
                )
            }
            Column(modifier = Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    stream.userName,
                    style = MaterialTheme.typography.titleMedium,
                    color = c.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (stream.gameName.isNotBlank()) {
                    Text(
                        stream.gameName,
                        style = MaterialTheme.typography.bodySmall,
                        color = c.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (stream.title.isNotBlank()) {
                    Text(
                        stream.title,
                        style = MaterialTheme.typography.bodySmall,
                        color = c.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
