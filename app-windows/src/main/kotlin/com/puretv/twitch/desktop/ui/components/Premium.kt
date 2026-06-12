package com.puretv.twitch.desktop.ui.components

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
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import com.puretv.twitch.desktop.ui.theme.PureTvTheme

// ── Stream card ────────────────────────────────────────────────────────────────

@Composable
fun StreamCard(stream: StreamInfo, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = PureTvTheme.colors
    var hovered by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (hovered) 1.04f else 1.0f,
        animationSpec = tween(PureTvMotion.Fast, easing = FastOutSlowInEasing),
        label = "cardScale",
    )

    Column(
        modifier = modifier
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
        val borderBrush = if (hovered) {
            c.accentGradient
        } else {
            Brush.linearGradient(listOf(c.hairline, c.hairline))
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .border(width = if (hovered) 2.dp else 1.dp, brush = borderBrush, shape = RoundedCornerShape(10.dp))
                .clip(RoundedCornerShape(10.dp))
                .background(c.surfaceVariant),
        ) {
            val thumbUrl = stream.thumbnailUrl
                .replace("{width}", "440")
                .replace("{height}", "248")
            if (thumbUrl.isNotBlank()) {
                AsyncImage(
                    model = thumbUrl,
                    contentDescription = stream.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
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
                Text(formatViewerCount(stream.viewerCount), style = MaterialTheme.typography.labelSmall, color = c.textPrimary)
            }
        }

        Spacer(Modifier.height(8.dp))
        Text(stream.userName, style = MaterialTheme.typography.titleMedium, color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(stream.title, style = MaterialTheme.typography.bodyMedium, color = c.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        // Viewer count under the card — explicit "live now + how many watching".
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
            Box(Modifier.size(6.dp).clip(CircleShape).background(c.live))
            Spacer(Modifier.width(6.dp))
            Text("${formatViewerCount(stream.viewerCount)} viewers", style = MaterialTheme.typography.bodyMedium, color = c.textSecondary, maxLines = 1)
            if (stream.gameName.isNotBlank()) {
                Text(
                    " · ${stream.gameName}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = c.textMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
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
        Text(title, style = MaterialTheme.typography.titleLarge, color = c.textPrimary)
        if (onSeeAll != null) {
            Text("See all", style = MaterialTheme.typography.bodyMedium, color = c.twitchPurpleLight, modifier = Modifier.clickable(onClick = onSeeAll))
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
            Text(displayName.take(1).uppercase(), style = MaterialTheme.typography.labelSmall, color = c.textPrimary, fontWeight = FontWeight.Bold)
        }
    }
}
