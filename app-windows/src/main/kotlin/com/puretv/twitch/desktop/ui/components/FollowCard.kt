package com.puretv.twitch.desktop.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.puretv.twitch.desktop.ui.FollowCardState
import com.puretv.twitch.desktop.ui.theme.PureTvMotion
import com.puretv.twitch.desktop.ui.theme.PureTvShape
import com.puretv.twitch.desktop.ui.theme.PureTvTheme
import com.puretv.twitch.desktop.ui.theme.PureTvType

/**
 * A saved-channel card for the Home "From channels you follow" shelf, in the
 * Cinémathèque language so it reads as the same magazine as [StreamCard]:
 *  - LIVE    → duotone [CoverImage] + [BoxScrim] + [LiveChip] + [ViewerChip], with
 *    a poster lift on hover and the title shifting to the violet accent.
 *  - OFFLINE → a dimmed duotone cover with a centered [Avatar], an "OFFLINE" mono
 *    label, and the name beneath — still present so reopening a channel is one click.
 */
@Composable
fun FollowCard(state: FollowCardState, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = PureTvTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val titleColor by animateColorAsState(
        if (hovered && state.isLive) c.twitchPurpleLight else if (state.isLive) c.textPrimary else c.textSecondary,
        tween(PureTvMotion.Fast),
        label = "followTitle",
    )

    Column(
        modifier = modifier
            .hoverLift(interaction, lift = 6.dp, scaleTo = 1f)
            .hoverable(interaction)
            .handCursor()
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(PureTvShape.md),
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
                BoxScrim(Modifier.fillMaxSize())
                LiveChip(Modifier.align(Alignment.TopStart).padding(8.dp))
                ViewerChip(formatViewerCount(state.viewerCount), Modifier.align(Alignment.BottomEnd).padding(8.dp))
            } else {
                // Offline — dimmed duotone cover with the channel's avatar centered on it.
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
                    style = PureTvType.dataSmall,
                    color = c.textMuted,
                    modifier = Modifier.align(Alignment.BottomStart).padding(8.dp),
                )
            }
        }

        Spacer(Modifier.height(10.dp))
        Text(
            state.displayName,
            style = MaterialTheme.typography.titleMedium,
            color = titleColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            if (state.isLive) state.gameName.ifBlank { state.title } else "Offline",
            style = MaterialTheme.typography.bodyMedium,
            color = c.textMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
