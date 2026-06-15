package com.puretv.twitch.desktop.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.puretv.twitch.core.adblock.AdBlockStatus
import com.puretv.twitch.core.model.ChatMessage
import com.puretv.twitch.core.model.MessagePart
import com.puretv.twitch.desktop.ui.theme.PureTvShape
import com.puretv.twitch.desktop.ui.theme.PureTvTheme
import com.puretv.twitch.desktop.ui.theme.PureTvType
import java.awt.Color as AwtColor

/**
 * Cinémathèque shared components — the editorial vocabulary every screen speaks:
 * kickers, duotone cover art, poster lift, the segmented control, the ad-block
 * indicator, the cinematic hero, empty states, and rich chat rows.
 *
 * Rules of the house: type + grid + restraint. One accent moment per region.
 * Depth from the surface ladder + hairlines, never gratuitous shadow or glow.
 */

// ── Kicker / eyebrow ─────────────────────────────────────────────────────────────

/** Mono uppercase section label. [rule] draws a trailing hairline (fills width). */
@Composable
fun Kicker(
    text: String,
    modifier: Modifier = Modifier,
    accent: Boolean = false,
    rule: Boolean = false,
) {
    val c = PureTvTheme.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = if (rule) modifier.fillMaxWidth() else modifier,
    ) {
        Text(
            text.uppercase(),
            style = PureTvType.kicker,
            color = if (accent) c.twitchPurple else c.textTertiary,
        )
        if (rule) {
            Spacer(Modifier.width(12.dp))
            Box(Modifier.weight(1f).height(1.dp).background(c.hairline))
        }
    }
}

// ── Live dot (pulsing) ───────────────────────────────────────────────────────────

@Composable
fun LiveDot(modifier: Modifier = Modifier, size: Dp = 6.dp) {
    val c = PureTvTheme.colors
    val transition = rememberInfiniteTransition(label = "liveDot")
    val alpha by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0.35f,
        animationSpec = infiniteRepeatable(tween(1100), RepeatMode.Reverse),
        label = "liveDotAlpha",
    )
    Box(
        modifier
            .size(size)
            .graphicsLayer { this.alpha = alpha }
            .clip(CircleShape)
            .background(c.live),
    )
}

// ── Duotone cover art ────────────────────────────────────────────────────────────

/**
 * Deterministic duotone fill — turns a missing/loading thumbnail into something that
 * looks *art-directed* rather than an empty gray box (the audit's #1 complaint). The
 * hue is seeded from a stable string so a given channel always gets the same tint.
 */
@Composable
fun DuotoneFill(seed: String, modifier: Modifier = Modifier) {
    val hue = remember(seed) { (((seed.hashCode() % 360) + 360) % 360).toFloat() }
    val glow = Color.hsv((hue + 28f) % 360f, 0.55f, 0.34f)
    val base = Color.hsv(hue, 0.45f, 0.15f)
    val deep = Color.hsv((hue + 340f) % 360f, 0.50f, 0.06f)
    Box(
        modifier.background(
            Brush.linearGradient(
                0f to glow.copy(alpha = 0.85f),
                0.55f to base,
                1f to deep,
            ),
        ),
    )
}

/** Cover image with duotone fallback behind it; fills its parent. */
@Composable
fun CoverImage(
    imageUrl: String?,
    seed: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
) {
    Box(modifier) {
        DuotoneFill(seed, Modifier.matchParentSizeSafe())
        if (!imageUrl.isNullOrBlank()) {
            AsyncImage(
                model = imageUrl,
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

// matchParentSize is only on BoxScope; tiny shim so callers read cleanly.
private fun Modifier.matchParentSizeSafe(): Modifier = this.fillMaxSize()

// ── Bottom scrim (image legibility) ──────────────────────────────────────────────

@Composable
fun BoxScrim(modifier: Modifier = Modifier) {
    Box(
        modifier.background(
            Brush.verticalGradient(
                0f to Color.Transparent,
                0.55f to Color.Transparent,
                1f to Color.Black.copy(alpha = 0.82f),
            ),
        ),
    )
}

// ── Overlay chips (LIVE / viewer count on a cover) ───────────────────────────────

@Composable
fun LiveChip(modifier: Modifier = Modifier) {
    Row(
        modifier
            .clip(PureTvShape.xs)
            .background(Color.Black.copy(alpha = 0.5f))
            .padding(horizontal = 7.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LiveDot(size = 5.dp)
        Spacer(Modifier.width(5.dp))
        Text("LIVE", style = PureTvType.dataSmall, color = Color.White)
    }
}

@Composable
fun ViewerChip(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = PureTvType.dataSmall,
        color = Color.White,
        modifier = modifier
            .clip(PureTvShape.xs)
            .background(Color.Black.copy(alpha = 0.5f))
            .padding(horizontal = 7.dp, vertical = 4.dp),
    )
}

// ── Segmented control ────────────────────────────────────────────────────────────

/** Editorial replacement for the cramped quality-pill row. Generic over option type. */
@Composable
fun <T> SegmentedControl(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = PureTvTheme.colors
    Row(
        modifier
            .height(IntrinsicSize.Min)
            .clip(PureTvShape.sm)
            .border(1.dp, c.hairlineStrong, PureTvShape.sm),
    ) {
        options.forEachIndexed { index, option ->
            if (index > 0) Box(Modifier.width(1.dp).fillMaxHeight().background(c.hairline))
            val interaction = remember { MutableInteractionSource() }
            val hovered by interaction.collectIsHoveredAsState()
            val isSelected = option == selected
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .background(
                        when {
                            isSelected -> c.twitchPurple
                            hovered -> c.surfaceHover
                            else -> Color.Transparent
                        },
                    )
                    .hoverable(interaction)
                    .handCursor()
                    .clickable(interactionSource = interaction, indication = null) { onSelect(option) }
                    .padding(horizontal = 12.dp, vertical = 7.dp),
            ) {
                Text(
                    label(option),
                    style = PureTvType.data,
                    color = when {
                        isSelected -> c.background
                        hovered -> c.textPrimary
                        else -> c.textSecondary
                    },
                )
            }
        }
    }
}

// ── Ad-block pill ────────────────────────────────────────────────────────────────

@Composable
fun AdBlockPill(status: AdBlockStatus, modifier: Modifier = Modifier) {
    val c = PureTvTheme.colors
    val (label, dot) = when (status) {
        AdBlockStatus.AD_BLOCKED -> "Ads blocked" to c.adBlockGreen
        AdBlockStatus.AD_FILTERED -> "Ads filtered" to c.adBlockGreen
        AdBlockStatus.AD_BLOCK_OFF -> "Ad block off" to c.textMuted
        AdBlockStatus.DISABLED -> "Disabled" to c.textMuted
        AdBlockStatus.UNKNOWN -> "Checking…" to c.textMuted
    }
    Row(
        modifier
            .clip(PureTvShape.pill)
            .background(c.surfaceVariant.copy(alpha = 0.7f))
            .border(1.dp, c.hairline, PureTvShape.pill)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(dot))
        Text(label.uppercase(), style = PureTvType.dataSmall, color = c.textSecondary, modifier = Modifier.padding(start = 7.dp))
    }
}

// ── Cinematic hero ───────────────────────────────────────────────────────────────

@Composable
fun CinematicHero(
    seed: String,
    imageUrl: String?,
    kicker: String,
    title: String,
    meta: String,
    onWatch: () -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 300.dp,
) {
    val c = PureTvTheme.colors
    Box(modifier.fillMaxWidth().height(height)) {
        CoverImage(imageUrl, seed, null, Modifier.fillMaxSize())
        BoxScrim(Modifier.fillMaxSize())
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 38.dp, end = 38.dp, bottom = 30.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                LiveDot(size = 6.dp)
                Spacer(Modifier.width(8.dp))
                Text(kicker.uppercase(), style = PureTvType.kicker, color = c.textPrimary)
            }
            Spacer(Modifier.height(12.dp))
            Text(
                title,
                style = MaterialTheme.typography.displayLarge,
                color = c.textPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(0.7f),
            )
            Spacer(Modifier.height(18.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                PureButton(text = "Watch now", onClick = onWatch, leadingIcon = Icons.Filled.PlayArrow)
                Spacer(Modifier.width(16.dp))
                Text(meta, style = PureTvType.data, color = c.textSecondary)
            }
        }
    }
}

// ── Editorial empty state ────────────────────────────────────────────────────────

@Composable
fun EditorialEmptyState(
    kicker: String,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val c = PureTvTheme.colors
    Column(
        modifier = modifier.fillMaxWidth().padding(vertical = 56.dp, horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Kicker(kicker, accent = true)
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.headlineMedium, color = c.textPrimary, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(message, style = MaterialTheme.typography.bodyMedium, color = c.textSecondary, textAlign = TextAlign.Center)
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(22.dp))
            PureButton(text = actionLabel, onClick = onAction, variant = ButtonVariant.Secondary)
        }
    }
}

// ── Rich chat message row (timestamp · badges · emotes) ──────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChatMessageRow(
    message: ChatMessage,
    modifier: Modifier = Modifier,
    showTimestamps: Boolean = true,
    onReply: ((ChatMessage) -> Unit)? = null,
) {
    val c = PureTvTheme.colors
    val nameColor = remember(message.color) {
        runCatching { Color(AwtColor.decode(message.color).rgb or (0xFF shl 24)) }
            .getOrDefault(c.twitchPurpleLight)
    }
    Column(modifier = modifier.fillMaxWidth()) {
        val parentName = message.replyParentDisplayName
        if (parentName != null) {
            Text(
                "replying to @" + parentName,
                style = MaterialTheme.typography.labelSmall,
                color = c.textMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 2.dp, bottom = 1.dp),
            )
        }
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
        if (showTimestamps) {
            val ts = remember(message.timestamp) { formatClock(message.timestamp) }
            Text(ts, style = PureTvType.dataSmall, color = c.textMuted, modifier = Modifier.padding(end = 2.dp))
        }
        if (message.isBroadcaster) ChatBadge("HOST", c.twitchPurple, c.background)
        else if (message.isModerator) ChatBadge("MOD", c.online, Color.White)
        if (message.isSubscriber) ChatBadge("SUB", c.twitchPurpleLight.copy(alpha = 0.22f), c.twitchPurpleLight)

        Text(
            message.displayName,
            color = nameColor,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
        )

        val parts = message.parsedParts.ifEmpty { listOf(MessagePart.Text(message.message)) }
        parts.forEach { part ->
            when (part) {
                is MessagePart.Text -> Text(part.content, color = c.textPrimary, style = MaterialTheme.typography.bodyMedium)
                is MessagePart.TwitchEmote -> EmoteImage("https://static-cdn.jtvnw.net/emoticons/v2/${part.id}/default/dark/1.0", part.name)
                is MessagePart.ThirdPartyEmote -> EmoteImage(part.url, part.name)
            }
        }
        if (onReply != null) {
            IconButton(
                onClick = { onReply(message) },
                modifier = Modifier.size(18.dp),
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Reply,
                    contentDescription = "Reply",
                    tint = c.textMuted,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
        }
    }
}

@Composable
private fun ChatBadge(text: String, bg: Color, fg: Color) {
    Text(
        text,
        style = PureTvType.dataSmall,
        color = fg,
        modifier = Modifier
            .clip(PureTvShape.xs)
            .background(bg)
            .padding(horizontal = 4.dp, vertical = 1.dp),
    )
}

@Composable
internal fun EmoteImage(url: String, name: String, modifier: Modifier = Modifier.size(20.dp)) {
    AsyncImage(
        model = url,
        contentDescription = name,
        modifier = modifier,
    )
}

private val clockFormatter = java.time.format.DateTimeFormatter.ofPattern("HH:mm")
private fun formatClock(epochMillis: Long): String =
    runCatching {
        java.time.Instant.ofEpochMilli(epochMillis)
            .atZone(java.time.ZoneId.systemDefault())
            .format(clockFormatter)
    }.getOrDefault("")
