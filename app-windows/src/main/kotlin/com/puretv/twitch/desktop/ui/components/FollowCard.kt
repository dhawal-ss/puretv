package com.puretv.twitch.desktop.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.puretv.twitch.desktop.ui.FollowCardState
import com.puretv.twitch.desktop.ui.theme.PureTvMotion
import com.puretv.twitch.desktop.ui.theme.PureTvTheme

/**
 * A saved-channel card for the Home "Following" rail. Renders two states from
 * one [FollowCardState]:
 *  - LIVE  → thumbnail + LIVE badge + viewer count (same language as StreamCard).
 *  - OFFLINE → dimmed avatar + name + "Offline" — still present so the user
 *    never has to search; one click reopens the channel.
 */
@Composable
fun FollowCard(state: FollowCardState, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = PureTvTheme.colors
    var hovered by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (hovered) 1.04f else 1.0f,
        animationSpec = tween(PureTvMotion.Fast, easing = FastOutSlowInEasing),
        label = "followCardScale",
    )

    Column(
        modifier = modifier
            .width(220.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        hovered = when (event.type) {
                            PointerEventType.Enter -> true
                            PointerEventType.Exit -> false
                            else -> hovered
                        }
                    }
                }
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
    ) {
        val borderBrush = if (hovered) c.accentGradient else Brush.linearGradient(listOf(c.hairline, c.hairline))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .border(width = if (hovered) 2.dp else 1.dp, brush = borderBrush, shape = RoundedCornerShape(10.dp))
                .clip(RoundedCornerShape(10.dp))
                .background(c.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            if (state.isLive && state.thumbnailUrl.isNotBlank()) {
                val thumbUrl = state.thumbnailUrl
                    .replace("{width}", "440")
                    .replace("{height}", "248")
                AsyncImage(
                    model = thumbUrl,
                    contentDescription = state.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .align(Alignment.BottomCenter)
                        .background(c.bottomScrim),
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(c.live)
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                ) {
                    Text("LIVE", style = MaterialTheme.typography.labelSmall, color = c.textPrimary, fontWeight = FontWeight.Bold)
                }
                Box(Modifier.align(Alignment.BottomEnd).padding(8.dp)) {
                    Text(formatViewerCount(state.viewerCount), style = MaterialTheme.typography.labelSmall, color = c.textPrimary)
                }
            } else {
                // Offline — dimmed avatar centered on the card.
                Avatar(
                    displayName = state.displayName,
                    imageUrl = state.avatarUrl.takeIf { it.isNotBlank() },
                    size = 56,
                    modifier = Modifier.alpha(0.7f),
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(c.surfaceHover)
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                ) {
                    Text("OFFLINE", style = MaterialTheme.typography.labelSmall, color = c.textMuted, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        Text(
            state.displayName,
            style = MaterialTheme.typography.titleMedium,
            color = if (state.isLive) c.textPrimary else c.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            if (state.isLive) state.title.ifBlank { state.gameName } else "Offline",
            style = MaterialTheme.typography.bodyMedium,
            color = c.textMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
