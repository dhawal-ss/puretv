package com.puretv.twitch.desktop.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.animateColorAsState
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
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.puretv.twitch.core.adblock.AdBlockStatus
import com.puretv.twitch.core.model.ChatMessage
import com.puretv.twitch.core.model.EmoteLayer
import com.puretv.twitch.core.model.MessagePart
import com.puretv.twitch.desktop.ui.emotes.AnimatedEmote
import com.puretv.twitch.core.chat.BadgeIndex
import com.puretv.twitch.desktop.ui.emotes.LocalEmoteAnimation
import androidx.compose.runtime.staticCompositionLocalOf
import com.puretv.twitch.desktop.ui.theme.PureTvMotion
import com.puretv.twitch.desktop.ui.theme.PureTvTheme
import com.puretv.twitch.desktop.ui.theme.PureTvType
import java.awt.Color as AwtColor

/**
 * Shared components: the vocabulary every screen speaks.
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
            color = if (accent) c.primary else c.onSurfaceVariant,
        )
        if (rule) {
            Spacer(Modifier.width(12.dp))
            Box(Modifier.weight(1f).height(1.dp).background(c.outlineVariant))
        }
    }
}

// ── Live dot (pulsing) ───────────────────────────────────────────────────────────

@Composable
fun LiveDot(modifier: Modifier = Modifier, size: Dp = 6.dp, color: Color? = null) {
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
            .background(color ?: c.live),
    )
}

// ── Duotone cover art ────────────────────────────────────────────────────────────

/**
 * Deterministic duotone fill. Turns a missing or loading thumbnail into something that
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

/** The LIVE badge as it appears over cover art. Same badge as [LivePill]. */
@Composable
fun LiveChip(modifier: Modifier = Modifier) = LivePill(modifier = modifier, height = 24.dp)

/**
 * Viewer count over cover art. No chip behind it: the card scrim already darkens
 * that corner, and a second container there reads as clutter at card scale.
 */
@Composable
fun ViewerChip(text: String, modifier: Modifier = Modifier) {
    Text(text, style = PureTvType.dataSmall, color = Color.White, modifier = modifier)
}

// ── Segmented control ────────────────────────────────────────────────────────────

/**
 * Kept for call sites that predate [SegmentedToggle]. Same control, one voice:
 * a pill track holding pill segments, the selected one filled with the secondary
 * container.
 */
@Composable
fun <T> SegmentedControl(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) = SegmentedToggle(
    options = options,
    selected = selected,
    label = label,
    onSelect = onSelect,
    modifier = modifier,
    height = 40.dp,
)

// ── Ad-block pill ────────────────────────────────────────────────────────────────

/**
 * Playback ad-block state. Tertiary, never primary: it is a standing fact about
 * the app rather than an action, so it must not compete with the play control
 * sitting next to it. Inactive states drop to a plain outline so "on" is the only
 * state that carries colour.
 */
@Composable
fun AdBlockPill(status: AdBlockStatus, modifier: Modifier = Modifier) {
    val c = PureTvTheme.colors
    val label = when (status) {
        AdBlockStatus.AD_BLOCKED -> "ADS BLOCKED"
        AdBlockStatus.AD_FILTERED -> "ADS FILTERED"
        AdBlockStatus.AD_BLOCK_OFF -> "AD BLOCK OFF"
        AdBlockStatus.DISABLED -> "DISABLED"
        AdBlockStatus.UNKNOWN -> "CHECKING"
    }
    val active = status == AdBlockStatus.AD_BLOCKED || status == AdBlockStatus.AD_FILTERED

    if (active) {
        ShieldPill(label, modifier = modifier, height = 36.dp)
    } else {
        Row(
            modifier
                .height(36.dp)
                .clip(CircleShape)
                .border(1.5.dp, c.outlineVariant, CircleShape)
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Icon(ExpressiveIcons.Shield, contentDescription = null, tint = c.onSurfaceVariant, modifier = Modifier.size(16.dp))
            Text(label, style = PureTvType.badge, color = c.onSurfaceVariant, maxLines = 1)
        }
    }
}

// ── Cinematic hero ───────────────────────────────────────────────────────────────

/**
 * The editorial hero. Artwork fills the card, a horizontal scrim darkens the left
 * two thirds so the display type has a ground, and the copy stacks up from the
 * bottom edge. The scrim runs sideways rather than upward because the title block
 * is left-aligned: a bottom-up scrim would darken the artwork's subject and still
 * leave the top of the headline floating.
 */
@Composable
fun CinematicHero(
    seed: String,
    imageUrl: String?,
    kicker: String,
    title: String,
    meta: String,
    onWatch: () -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 340.dp,
) {
    val c = PureTvTheme.colors
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(PureTvTheme.shapes.heroShape),
    ) {
        CoverImage(imageUrl, seed, null, Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().background(c.heroScrim))
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth(0.64f)
                .padding(40.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                LivePill()
                Text(kicker.uppercase(), style = PureTvType.kicker, color = Color.White.copy(alpha = 0.72f), maxLines = 1)
            }
            Spacer(Modifier.height(20.dp))
            Text(
                title,
                style = MaterialTheme.typography.displayLarge,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(14.dp))
            Text(meta, style = PureTvType.data, color = Color.White.copy(alpha = 0.82f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(28.dp))
            ExpressiveButton(
                text = "Watch now",
                onClick = onWatch,
                icon = ExpressiveIcons.Play,
                size = ExpressiveButtonSize.Large,
            )
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
        Text(title, style = MaterialTheme.typography.headlineMedium, color = c.onSurface, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(message, style = MaterialTheme.typography.bodyLarge, color = c.onSurfaceVariant, textAlign = TextAlign.Center)
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(22.dp))
            ExpressiveButton(text = actionLabel, onClick = onAction, style = ExpressiveButtonStyle.Outlined)
        }
    }
}

/** Per-stream chat badge art, provided around the chat panel; defaults to no art
 *  (chips fall back) so rows outside a stream context still render. */
val LocalBadgeIndex = staticCompositionLocalOf { BadgeIndex.EMPTY }

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

    // System rows (subs, raids, "chat cleared") carry no user identity, so render a
    // single centred, muted line and skip the whole badge/username/emote pipeline.
    if (message.isSystem) {
        Text(
            message.message,
            style = MaterialTheme.typography.labelMedium,
            color = c.onSurfaceVariant,
            modifier = modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp),
        )
        return
    }

    val nameColor = remember(message.color) {
        runCatching { Color(AwtColor.decode(message.color).rgb or (0xFF shl 24)) }
            .getOrDefault(c.primary)
    }
    // Every row is its own rounded container rather than a flat line in a list. A
    // mention fills with the primary container so being pinged is legible at a
    // glance while scrolling; everything else stays transparent until hovered.
    val rowInteraction = remember { MutableInteractionSource() }
    val rowHovered by rowInteraction.collectIsHoveredAsState()
    val rowFill by animateColorAsState(
        targetValue = when {
            message.mentionsSelf -> c.primaryContainer
            rowHovered -> c.surfaceHigh
            else -> Color.Transparent
        },
        animationSpec = tween(PureTvMotion.Fast),
        label = "chatRowFill",
    )
    val rowModifier = modifier
        .fillMaxWidth()
        .clip(PureTvTheme.shapes.mdShape)
        .background(rowFill)
        .hoverable(rowInteraction)
        .padding(horizontal = 12.dp, vertical = 7.dp)
    Column(modifier = rowModifier) {
        val parentName = message.replyParentDisplayName
        if (parentName != null) {
            Text(
                "replying to @" + parentName,
                style = MaterialTheme.typography.labelSmall,
                color = c.onSurfaceVariant,
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
            Text(ts, style = PureTvType.dataSmall, color = c.onSurfaceVariant.copy(alpha = 0.6f), modifier = Modifier.padding(end = 2.dp))
        }
        val badgeIndex = LocalBadgeIndex.current
        val resolvedBadges = if (badgeIndex === BadgeIndex.EMPTY) emptyList()
            else message.badges.mapNotNull { badgeIndex.resolve(it.setId, it.version) }
        if (resolvedBadges.isNotEmpty()) {
            // Badge art is already square (BadgeIndex resolves the 2x CDN image),
            // so a fixed square box is correct here, no aspect-ratio bug to fix.
            resolvedBadges.forEach { EmoteImage(it.url, it.title, Modifier.size(18.dp)) }
        } else {
            // Badge art not loaded yet (or this channel returned none), so fall back to
            // the lightweight text chips so rank is still legible.
            if (message.isBroadcaster) ChatBadge("HOST", c.primary, c.onPrimary)
            else if (message.isModerator) ChatBadge("MOD", c.tertiaryContainer, c.onTertiaryContainer)
            if (message.isSubscriber) ChatBadge("SUB", c.secondaryContainer, c.onSecondaryContainer)
        }

        Text(
            message.displayName,
            color = nameColor,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
        )

        if (message.deleted) {
            // Moderator removed this message (timeout/ban/delete). Tombstone it
            // instead of dropping the row so the thread stays readable.
            Text(
                "<message deleted>",
                color = c.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
                fontStyle = FontStyle.Italic,
            )
        } else {
            val parts = message.parsedParts.ifEmpty { listOf(MessagePart.Text(message.message)) }
            parts.forEach { part ->
                when (part) {
                    is MessagePart.Text -> Text(part.content, color = c.onSurface, style = MaterialTheme.typography.bodyLarge)
                    is MessagePart.TwitchEmote -> StackedEmote(
                        "https://static-cdn.jtvnw.net/emoticons/v2/${part.id}/default/dark/2.0",
                        part.name,
                        animated = false,
                        part.overlays,
                        // Twitch enforces a square emote canvas, so the fixed square
                        // box is already correct. Skip the height-only path (and its
                        // load-time reflow) for the majority of emotes in most chats.
                        naturalAspect = false,
                    )
                    is MessagePart.ThirdPartyEmote -> StackedEmote(part.url, part.name, part.animated, part.overlays)
                }
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
                    tint = c.onSurfaceVariant,
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
            .clip(CircleShape)
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

/**
 * [naturalAspect] pins only the row height and lets width follow the emote's own
 * aspect ratio, the same fixed-line-height layout Twitch web and Chatterino use,
 * so wide/tall 7TV and BTTV emotes render at their real proportions instead of
 * being squashed into a square box. Zero-width overlay stacks pass `false`
 * because the overlay must share the base emote's exact square canvas to align.
 */
@Composable
private fun EmoteGlyph(url: String, name: String, animated: Boolean, size: Dp, naturalAspect: Boolean = true) {
    val sizing = if (naturalAspect) Modifier.height(size) else Modifier.size(size)
    if (animated && LocalEmoteAnimation.current) {
        AnimatedEmote(url, name, sizing) { u, n, m -> EmoteImage(u, n, m) }
    } else {
        EmoteImage(url, name, sizing)
    }
}

@Composable
private fun StackedEmote(
    baseUrl: String,
    name: String,
    animated: Boolean,
    overlays: List<EmoteLayer>,
    size: Dp = 28.dp,
    naturalAspect: Boolean = true,
) {
    if (overlays.isEmpty()) {
        EmoteGlyph(baseUrl, name, animated, size, naturalAspect)
        return
    }
    Box(contentAlignment = Alignment.Center) {
        EmoteGlyph(baseUrl, name, animated, size, naturalAspect = false)
        overlays.forEach { layer -> EmoteGlyph(layer.url, layer.name, layer.animated, size, naturalAspect = false) }
    }
}

private val clockFormatter = java.time.format.DateTimeFormatter.ofPattern("HH:mm")
private fun formatClock(epochMillis: Long): String =
    runCatching {
        java.time.Instant.ofEpochMilli(epochMillis)
            .atZone(java.time.ZoneId.systemDefault())
            .format(clockFormatter)
    }.getOrDefault("")
