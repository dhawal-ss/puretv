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

/**
 * The shelf card: a tonal container holding 16:9 art, then the channel identity
 * below it. The whole card rounds further out and lifts on hover rather than
 * tinting its title, so the feedback is the same gesture used everywhere else.
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
                val thumbUrl = stream.thumbnailUrl
                    .replace("{width}", "440")
                    .replace("{height}", "248")
                CoverImage(
                    imageUrl = thumbUrl,
                    seed = stream.userName,
                    contentDescription = stream.title,
                    modifier = Modifier.fillMaxSize(),
                )
                Box(Modifier.fillMaxSize().background(c.cardScrim))
                LivePill(Modifier.align(Alignment.TopStart).padding(10.dp), height = 24.dp)
                Text(
                    formatViewerCount(stream.viewerCount),
                    style = PureTvType.dataSmall,
                    color = Color.White,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(10.dp),
                )
            }

            Column(Modifier.padding(start = 8.dp, end = 8.dp, top = 14.dp, bottom = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    // The Helix streams payload carries no avatar URL, so this is the
                    // deterministic initial chip rather than a fetch-per-card.
                    Avatar(stream.userName, imageUrl = null, size = 32)
                    Column(Modifier.weight(1f)) {
                        Text(stream.userName, style = MaterialTheme.typography.titleMedium, color = c.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (stream.gameName.isNotBlank()) {
                            Text(stream.gameName, style = MaterialTheme.typography.bodyMedium, color = c.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                if (stream.title.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(stream.title, style = MaterialTheme.typography.bodyMedium, color = c.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

internal fun formatViewerCount(count: Int): String = when {
    // Audit U7: without the millions branch, a 1.2M-viewer stream rendered as
    // the nonsensical "1200.0K".
    count >= 1_000_000 -> "${count / 1_000_000}.${(count % 1_000_000) / 100_000}M"
    count >= 1_000 -> "${count / 1_000}.${(count % 1_000) / 100}K"
    else -> count.toString()
}

// ── Section header ─────────────────────────────────────────────────────────────

/** Kept for call sites that predate [SectionHeading]; same thing, one voice. */
@Composable
fun SectionHeader(title: String, onSeeAll: (() -> Unit)? = null, modifier: Modifier = Modifier) {
    SectionHeading(
        title = title,
        modifier = modifier,
        actionLabel = if (onSeeAll != null) "See all" else null,
        onAction = onSeeAll,
    )
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
        colors = listOf(c.surfaceLow, c.surfaceHighest, c.surfaceLow),
        start = Offset(translateX * 600f, 0f),
        end = Offset(translateX * 600f + 600f, 0f),
    )
    Box(modifier = modifier.background(shimmerBrush))
}

@Composable
fun StreamCardSkeleton(modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Skeleton(modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(PureTvTheme.shapes.thumbShape))
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
            modifier = modifier.size(sizeDp).clip(CircleShape).border(1.dp, c.outlineVariant, CircleShape),
        )
    } else {
        Box(
            modifier = modifier.size(sizeDp).clip(CircleShape).background(c.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Text(displayName.take(1).uppercase(), style = MaterialTheme.typography.labelLarge, color = c.onPrimaryContainer, fontWeight = FontWeight.Bold)
        }
    }
}
