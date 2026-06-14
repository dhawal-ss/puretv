package com.puretv.twitch.desktop.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.puretv.twitch.core.model.StreamInfo
import com.puretv.twitch.desktop.ui.theme.PureTvMotion
import com.puretv.twitch.desktop.ui.theme.PureTvShape
import com.puretv.twitch.desktop.ui.theme.PureTvTheme
import com.puretv.twitch.desktop.ui.theme.PureTvType

// ── Stream card ────────────────────────────────────────────────────────────────

@Composable
fun StreamCard(stream: StreamInfo, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = PureTvTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    // Cinémathèque hover: a clean lift + the title shifting to accent. No gradient
    // border, no scale bloat — "lift, don't glow".
    val titleColor by animateColorAsState(
        if (hovered) c.twitchPurpleLight else c.textPrimary,
        tween(PureTvMotion.Fast),
        label = "cardTitle",
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
            val thumbUrl = stream.thumbnailUrl
                .replace("{width}", "440")
                .replace("{height}", "248")
            CoverImage(
                imageUrl = thumbUrl,
                seed = stream.userName,
                contentDescription = stream.title,
                modifier = Modifier.fillMaxSize(),
            )
            BoxScrim(Modifier.fillMaxSize())
            LiveChip(Modifier.align(Alignment.TopStart).padding(8.dp))
            ViewerChip(formatViewerCount(stream.viewerCount), Modifier.align(Alignment.BottomEnd).padding(8.dp))
        }

        Spacer(Modifier.height(10.dp))
        Text(stream.userName, style = MaterialTheme.typography.titleMedium, color = titleColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (stream.gameName.isNotBlank()) {
            Text(stream.gameName, style = MaterialTheme.typography.bodyMedium, color = c.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (stream.title.isNotBlank()) {
            Text(stream.title, style = MaterialTheme.typography.bodyMedium, color = c.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

internal fun formatViewerCount(count: Int): String = when {
    count >= 1_000 -> "${count / 1_000}.${(count % 1_000) / 100}K"
    else -> count.toString()
}

// ── Section header ─────────────────────────────────────────────────────────────

@Composable
fun SectionHeader(title: String, onSeeAll: (() -> Unit)? = null, modifier: Modifier = Modifier) {
    val c = PureTvTheme.colors
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Kicker(title)
        if (onSeeAll != null) {
            Text("See all", style = PureTvType.data, color = c.twitchPurpleLight, modifier = Modifier.clickable(onClick = onSeeAll).handCursor())
        }
    }
}

// ── Skeleton shimmer ───────────────────────────────────────────────────────────

@Composable
fun Skeleton(modifier: Modifier = Modifier) {
    val c = PureTvTheme.colors
    val transition = rememberInfiniteTransition(label = "shimmer")
    val translateX by transition.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1_200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "shimmerX",
    )
    val shimmerBrush = Brush.linearGradient(
        colors = listOf(c.surfaceVariant, c.surfaceHover, c.surfaceVariant),
        start = Offset(translateX * 600f, 0f),
        end = Offset(translateX * 600f + 600f, 0f),
    )
    Box(modifier = modifier.background(shimmerBrush))
}

@Composable
fun StreamCardSkeleton(modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Skeleton(modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(10.dp)))
        Spacer(Modifier.height(8.dp))
        Skeleton(modifier = Modifier.fillMaxWidth(0.6f).height(14.dp).clip(RoundedCornerShape(4.dp)))
        Spacer(Modifier.height(6.dp))
        Skeleton(modifier = Modifier.fillMaxWidth(0.85f).height(12.dp).clip(RoundedCornerShape(4.dp)))
        Spacer(Modifier.height(4.dp))
        Skeleton(modifier = Modifier.fillMaxWidth(0.45f).height(12.dp).clip(RoundedCornerShape(4.dp)))
    }
}

// ── Avatar ─────────────────────────────────────────────────────────────────────

@Composable
fun Avatar(displayName: String, imageUrl: String?, size: Int = 36, modifier: Modifier = Modifier) {
    val c = PureTvTheme.colors
    val sizeDp = size.dp
    if (!imageUrl.isNullOrBlank()) {
        AsyncImage(
            model = imageUrl,
            contentDescription = displayName,
            contentScale = ContentScale.Crop,
            modifier = modifier.size(sizeDp).clip(CircleShape).border(1.dp, c.hairline, CircleShape),
        )
    } else {
        Box(
            modifier = modifier.size(sizeDp).clip(CircleShape).background(c.twitchPurple),
            contentAlignment = Alignment.Center,
        ) {
            Text(displayName.take(1).uppercase(), style = MaterialTheme.typography.labelSmall, color = c.background, fontWeight = FontWeight.Bold)
        }
    }
}
