package com.puretv.twitch.desktop.ui.components

import androidx.compose.foundation.background
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.puretv.twitch.desktop.ui.FollowCardState
import com.puretv.twitch.desktop.ui.theme.PureTvTheme
import com.puretv.twitch.desktop.ui.theme.PureTvType

/**
 * A saved-channel card for Home's followed shelf. Built on [ExpressiveCard] so it
 * shares the exact card silhouette, hover morph and lift as [StreamCard]: the two
 * sit side by side on Home, and any difference between them would read as a bug
 * rather than as a distinction.
 */
@Composable
fun FollowCard(state: FollowCardState, onClick: () -> Unit, modifier: Modifier = Modifier) {
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
                if (state.isLive) {
                    val thumbUrl = state.thumbnailUrl
                        .takeIf { it.isNotBlank() }
                        ?.replace("{width}", "440")
                        ?.replace("{height}", "248")
                    CoverImage(
                        imageUrl = thumbUrl,
                        seed = state.displayName,
                        contentDescription = state.title,
                        modifier = Modifier.fillMaxSize(),
                    )
                    Box(Modifier.fillMaxSize().background(c.cardScrim))
                    LivePill(Modifier.align(Alignment.TopStart).padding(10.dp), height = 24.dp)
                    Text(
                        formatViewerCount(state.viewerCount),
                        style = PureTvType.dataSmall,
                        color = Color.White,
                        modifier = Modifier.align(Alignment.BottomEnd).padding(10.dp),
                    )
                } else {
                    // Offline channels stay on the shelf so reopening one is still a
                    // single click. The cover is dimmed and the identity is carried by
                    // the avatar instead, so live and offline never read alike.
                    CoverImage(
                        imageUrl = null,
                        seed = state.displayName,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize().alpha(0.45f),
                    )
                    Avatar(
                        displayName = state.displayName,
                        imageUrl = state.avatarUrl.takeIf { it.isNotBlank() },
                        size = 52,
                        modifier = Modifier.align(Alignment.Center),
                    )
                    Text(
                        "OFFLINE",
                        style = PureTvType.badge,
                        color = c.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.BottomStart).padding(10.dp),
                    )
                }
            }

            Column(Modifier.padding(start = 8.dp, end = 8.dp, top = 14.dp, bottom = 8.dp)) {
                Text(
                    state.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (state.isLive) c.onSurface else c.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    if (state.isLive) state.gameName.ifBlank { state.title } else "Offline",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (state.isLive) c.primary else c.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
