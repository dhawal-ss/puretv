package com.puretv.twitch.desktop.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.puretv.twitch.desktop.ui.theme.PureTvMotion
import com.puretv.twitch.desktop.ui.theme.PureTvTheme
import com.puretv.twitch.desktop.ui.theme.PureTvType

/**
 * The Material 3 Expressive component vocabulary.
 *
 * The organising idea: **shape is the primary feedback channel**. A resting pill
 * squares off into a soft rectangle on hover; a resting card rounds further out.
 * Colour shifts a little, position shifts a little, but it is the corner
 * animation, on a spring that overshoots, that makes the UI feel alive.
 *
 * Everything here is built on [Modifier.expressiveSurface], so the timing and
 * curve are identical across buttons, chips, cards, nav items and pills. That
 * consistency is what separates a design system from a pile of animations.
 */

// ── The morph primitive ────────────────────────────────────────────────────────

/**
 * Clip + fill a surface whose corner radius animates between [restRadius] and
 * [hoverRadius] (and optionally [pressRadius]) on a spring with overshoot.
 *
 * Pass the SAME [interaction] you give `clickable`/`hoverable` so hover, press
 * and focus all read from one source.
 *
 * @param restRadius corner radius at rest.
 * @param hoverRadius corner radius while hovered. Deliberately allowed to be
 *   either larger or smaller than [restRadius]: pills square off, cards round out.
 * @param pressRadius corner radius while pressed; defaults to [hoverRadius].
 * @param color resting fill.
 * @param hoverColor fill while hovered; defaults to [color].
 * @param borderWidth outline width, or 0.dp for none.
 * @param lift how far the surface rises on hover. Runs in a graphics layer, so it
 *   never triggers relayout of anything around it.
 */
fun Modifier.expressiveSurface(
    interaction: MutableInteractionSource,
    restRadius: Dp,
    hoverRadius: Dp,
    pressRadius: Dp = hoverRadius,
    color: Color = Color.Transparent,
    hoverColor: Color = color,
    borderWidth: Dp = 0.dp,
    borderColor: Color = Color.Transparent,
    hoverBorderColor: Color = borderColor,
    lift: Dp = 0.dp,
    pressScale: Float = 1f,
): Modifier = composed {
    val hovered by interaction.collectIsHoveredAsState()
    val pressed by interaction.collectIsPressedAsState()

    val radius by animateDpAsState(
        targetValue = when {
            pressed -> pressRadius
            hovered -> hoverRadius
            else -> restRadius
        },
        animationSpec = PureTvMotion.MorphSpring,
        label = "morphRadius",
    )
    val fill by animateColorAsState(
        targetValue = if (hovered) hoverColor else color,
        animationSpec = tween(PureTvMotion.Medium, easing = PureTvMotion.Standard),
        label = "morphFill",
    )
    val stroke by animateColorAsState(
        targetValue = if (hovered) hoverBorderColor else borderColor,
        animationSpec = tween(PureTvMotion.Medium, easing = PureTvMotion.Standard),
        label = "morphStroke",
    )
    val rise by animateDpAsState(
        targetValue = if (hovered) -lift else 0.dp,
        animationSpec = PureTvMotion.MorphSpring,
        label = "morphLift",
    )
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressScale else 1f,
        animationSpec = PureTvMotion.MorphSpringFloat,
        label = "morphScale",
    )

    // Radii can animate below zero on an overshooting spring; RoundedCornerShape
    // rejects negatives, so clamp before it reaches the shape.
    val shape = RoundedCornerShape(radius.coerceAtLeast(0.dp))

    this
        .graphicsLayer {
            translationY = rise.toPx()
            scaleX = scale
            scaleY = scale
        }
        .clip(shape)
        .background(fill)
        .then(if (borderWidth > 0.dp) Modifier.border(borderWidth, stroke, shape) else Modifier)
}

/**
 * The whole interactive package: [expressiveSurface] plus hover tracking, the
 * hand cursor, a focus ring and a click handler, wired to one interaction source.
 * Most components below are a thin layer over this.
 */
@Composable
fun Modifier.expressiveClickable(
    interaction: MutableInteractionSource,
    onClick: () -> Unit,
    restRadius: Dp,
    hoverRadius: Dp,
    pressRadius: Dp = hoverRadius,
    color: Color = Color.Transparent,
    hoverColor: Color = color,
    borderWidth: Dp = 0.dp,
    borderColor: Color = Color.Transparent,
    hoverBorderColor: Color = borderColor,
    lift: Dp = 0.dp,
    pressScale: Float = 1f,
    enabled: Boolean = true,
): Modifier {
    val focused by interaction.collectIsFocusedAsState()
    return this
        .graphicsLayer { alpha = if (enabled) 1f else 0.38f }
        .focusRing(focused, cornerRadius = restRadius.coerceAtMost(20.dp))
        .expressiveSurface(
            interaction = interaction,
            restRadius = restRadius,
            hoverRadius = hoverRadius,
            pressRadius = pressRadius,
            color = color,
            hoverColor = hoverColor,
            borderWidth = borderWidth,
            borderColor = borderColor,
            hoverBorderColor = hoverBorderColor,
            lift = lift,
            pressScale = pressScale,
        )
        .hoverable(interaction, enabled = enabled)
        .handCursor()
        .clickable(interactionSource = interaction, indication = null, enabled = enabled, onClick = onClick)
}

// ── Buttons ────────────────────────────────────────────────────────────────────

/**
 * The five M3 button emphases. `Filled` is the one call to action per region;
 * `Tonal` is the "selected but not shouting" state; `Outlined` and `Text` recede;
 * `FilledTertiary` is reserved for the ad-block/shield voice.
 */
enum class ExpressiveButtonStyle { Filled, Tonal, Outlined, Text, FilledTertiary }

/** Heights follow the M3 Expressive button sizes, which run taller than baseline. */
enum class ExpressiveButtonSize(val height: Dp, val hPad: Dp, val iconSize: Dp) {
    Small(40.dp, 18.dp, 18.dp),
    Medium(48.dp, 22.dp, 20.dp),
    Large(56.dp, 26.dp, 24.dp),
    XLarge(64.dp, 34.dp, 26.dp),
}

@Composable
fun ExpressiveButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: ExpressiveButtonStyle = ExpressiveButtonStyle.Filled,
    size: ExpressiveButtonSize = ExpressiveButtonSize.Medium,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    val c = PureTvTheme.colors
    val shapes = PureTvTheme.shapes
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()

    val fg = when (style) {
        ExpressiveButtonStyle.Filled -> c.onPrimary
        ExpressiveButtonStyle.Tonal -> c.onSecondaryContainer
        ExpressiveButtonStyle.FilledTertiary -> c.onTertiaryContainer
        ExpressiveButtonStyle.Outlined, ExpressiveButtonStyle.Text -> c.onSurface
    }
    val bg = when (style) {
        ExpressiveButtonStyle.Filled -> c.primary
        ExpressiveButtonStyle.Tonal -> c.secondaryContainer
        ExpressiveButtonStyle.FilledTertiary -> c.tertiaryContainer
        ExpressiveButtonStyle.Outlined, ExpressiveButtonStyle.Text -> Color.Transparent
    }
    val bgHover = when (style) {
        ExpressiveButtonStyle.Filled -> c.primary
        ExpressiveButtonStyle.Tonal -> c.primary
        ExpressiveButtonStyle.FilledTertiary -> c.tertiaryContainer
        ExpressiveButtonStyle.Outlined, ExpressiveButtonStyle.Text -> c.surfaceHigh
    }
    val fgHover = if (style == ExpressiveButtonStyle.Tonal) c.onPrimary else fg

    // Pressing squeezes the horizontal padding in. Combined with the corner morph
    // this reads as the button physically compressing under the cursor.
    val hPad by animateDpAsState(
        targetValue = if (pressed) size.hPad - 4.dp else size.hPad,
        animationSpec = PureTvMotion.MorphSpring,
        label = "buttonPad",
    )
    val hovered by interaction.collectIsHoveredAsState()
    val ink by animateColorAsState(if (hovered) fgHover else fg, tween(PureTvMotion.Medium), label = "buttonInk")

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier
            .height(size.height)
            .expressiveClickable(
                interaction = interaction,
                onClick = onClick,
                restRadius = size.height / 2,
                hoverRadius = shapes.pillMorph,
                color = bg,
                hoverColor = bgHover,
                borderWidth = if (style == ExpressiveButtonStyle.Outlined) 1.5.dp else 0.dp,
                borderColor = c.outline,
                enabled = enabled,
            )
            .padding(horizontal = hPad.coerceAtLeast(0.dp)),
    ) {
        icon?.let { Icon(it, contentDescription = null, tint = ink, modifier = Modifier.size(size.iconSize)) }
        Text(
            text,
            color = ink,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (style == ExpressiveButtonStyle.Filled) FontWeight.Bold else FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}

/**
 * Icon-only button. Round at rest, squircle on hover: the smallest possible
 * expression of the morph, and the one the user meets most often.
 */
@Composable
fun ExpressiveIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: ExpressiveButtonStyle = ExpressiveButtonStyle.Text,
    boxSize: Dp = 48.dp,
    iconSize: Dp = 24.dp,
    enabled: Boolean = true,
    tint: Color? = null,
) {
    val c = PureTvTheme.colors
    val shapes = PureTvTheme.shapes
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()

    val bg = when (style) {
        ExpressiveButtonStyle.Filled -> c.primary
        ExpressiveButtonStyle.Tonal -> c.secondaryContainer
        ExpressiveButtonStyle.FilledTertiary -> c.tertiaryContainer
        ExpressiveButtonStyle.Outlined, ExpressiveButtonStyle.Text -> Color.Transparent
    }
    val bgHover = when (style) {
        ExpressiveButtonStyle.Filled -> c.primary
        ExpressiveButtonStyle.Tonal -> c.primary
        ExpressiveButtonStyle.FilledTertiary -> c.tertiaryContainer
        ExpressiveButtonStyle.Outlined, ExpressiveButtonStyle.Text -> c.surfaceHighest
    }
    val restingTint = tint ?: when (style) {
        ExpressiveButtonStyle.Filled -> c.onPrimary
        ExpressiveButtonStyle.Tonal -> c.onSecondaryContainer
        ExpressiveButtonStyle.FilledTertiary -> c.onTertiaryContainer
        ExpressiveButtonStyle.Outlined, ExpressiveButtonStyle.Text -> c.onSurfaceVariant
    }
    val hoverTint = when (style) {
        ExpressiveButtonStyle.Tonal -> c.onPrimary
        ExpressiveButtonStyle.Outlined, ExpressiveButtonStyle.Text -> tint ?: c.onSurface
        else -> restingTint
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(boxSize)
            .expressiveClickable(
                interaction = interaction,
                onClick = onClick,
                restRadius = boxSize / 2,
                hoverRadius = shapes.pillMorph,
                color = bg,
                hoverColor = bgHover,
                borderWidth = if (style == ExpressiveButtonStyle.Outlined) 1.5.dp else 0.dp,
                borderColor = c.outline,
                enabled = enabled,
            ),
    ) {
        Icon(
            icon,
            contentDescription = contentDescription,
            tint = if (hovered) hoverTint else restingTint,
            modifier = Modifier.size(iconSize),
        )
    }
}

/**
 * A connected button group: a wide primary action welded to a narrow secondary
 * one, sharing a silhouette. The inner corners stay tight while the outer corners
 * morph, so the pair reads as a single object that flexes.
 */
@Composable
fun SplitButton(
    text: String,
    icon: ImageVector?,
    onClick: () -> Unit,
    trailingIcon: ImageVector,
    onTrailingClick: () -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 56.dp,
) {
    val c = PureTvTheme.colors
    val shapes = PureTvTheme.shapes
    val leadInteraction = remember { MutableInteractionSource() }
    val trailInteraction = remember { MutableInteractionSource() }
    val leadHovered by leadInteraction.collectIsHoveredAsState()
    val trailHovered by trailInteraction.collectIsHoveredAsState()
    val inner = 6.dp

    val leadOuter by animateDpAsState(
        if (leadHovered) shapes.pillMorph else height / 2,
        PureTvMotion.MorphSpring,
        label = "splitLead",
    )
    val trailOuter by animateDpAsState(
        if (trailHovered) shapes.pillMorph else height / 2,
        PureTvMotion.MorphSpring,
        label = "splitTrail",
    )

    Row(modifier.height(height), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .fillMaxHeight()
                .clip(
                    RoundedCornerShape(
                        topStart = leadOuter.coerceAtLeast(0.dp), bottomStart = leadOuter.coerceAtLeast(0.dp),
                        topEnd = inner, bottomEnd = inner,
                    ),
                )
                .background(c.primary)
                .hoverable(leadInteraction)
                .handCursor()
                .clickable(interactionSource = leadInteraction, indication = null, onClick = onClick)
                .padding(horizontal = 28.dp),
        ) {
            icon?.let { Icon(it, contentDescription = null, tint = c.onPrimary, modifier = Modifier.size(24.dp)) }
            Text(text, color = c.onPrimary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1)
        }
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxHeight()
                .width(52.dp)
                .clip(
                    RoundedCornerShape(
                        topStart = inner, bottomStart = inner,
                        topEnd = trailOuter.coerceAtLeast(0.dp), bottomEnd = trailOuter.coerceAtLeast(0.dp),
                    ),
                )
                .background(c.primary)
                .hoverable(trailInteraction)
                .handCursor()
                .clickable(interactionSource = trailInteraction, indication = null, onClick = onTrailingClick),
        ) {
            Icon(trailingIcon, contentDescription = null, tint = c.onPrimary, modifier = Modifier.size(22.dp))
        }
    }
}

// ── Chips ──────────────────────────────────────────────────────────────────────

/**
 * Filter chip. Unselected it is an outline; selected it fills with the secondary
 * container AND grows a check mark, so selection survives both a colour-blind
 * reading and a greyscale screenshot.
 */
@Composable
fun ExpressiveFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = PureTvTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val fg by animateColorAsState(
        if (selected) c.onSecondaryContainer else c.onSurfaceVariant,
        tween(PureTvMotion.Medium),
        label = "chipFg",
    )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
            .height(40.dp)
            .expressiveClickable(
                interaction = interaction,
                onClick = onClick,
                restRadius = 20.dp,
                hoverRadius = 12.dp,
                color = if (selected) c.secondaryContainer else Color.Transparent,
                hoverColor = if (selected) c.secondaryContainer else c.surfaceHigh,
                borderWidth = 1.5.dp,
                borderColor = if (selected) Color.Transparent else c.outlineVariant,
            )
            .padding(horizontal = 18.dp),
    ) {
        if (selected) {
            Icon(ExpressiveIcons.Check, contentDescription = null, tint = fg, modifier = Modifier.size(18.dp))
        }
        Text(label, color = fg, style = MaterialTheme.typography.labelLarge, maxLines = 1)
    }
}

/** A horizontal run of filter chips. The one row shape every browse surface uses. */
@Composable
fun <T> ExpressiveChipRow(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { option ->
            ExpressiveFilterChip(
                label = label(option),
                selected = option == selected,
                onClick = { onSelect(option) },
            )
        }
    }
}

/**
 * Segmented toggle: a pill track holding pill segments, the selected one filled.
 * Use where the options are mutually exclusive views of the same thing (Grid/List,
 * quality, expressiveness) rather than filters over a set.
 */
@Composable
fun <T> SegmentedToggle(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 48.dp,
) {
    val c = PureTvTheme.colors
    val shapes = PureTvTheme.shapes
    Row(
        modifier
            .height(height)
            .clip(shapes.pillShape)
            .background(c.surfaceHigh)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        options.forEach { option ->
            val isSelected = option == selected
            val interaction = remember(option) { MutableInteractionSource() }
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxHeight()
                    .expressiveClickable(
                        interaction = interaction,
                        onClick = { onSelect(option) },
                        restRadius = height / 2,
                        hoverRadius = shapes.pillMorph,
                        color = if (isSelected) c.secondaryContainer else Color.Transparent,
                        hoverColor = if (isSelected) c.secondaryContainer else c.surfaceHighest,
                    )
                    .padding(horizontal = 20.dp),
            ) {
                Text(
                    label(option),
                    color = if (isSelected) c.onSecondaryContainer else c.onSurfaceVariant,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                )
            }
        }
    }
}

// ── Status pills ───────────────────────────────────────────────────────────────

/**
 * The LIVE badge. Error-container red with a pulsing dot. It is the one place in the
 * app where colour alone carries meaning, so the dot's motion backs it up.
 */
@Composable
fun LivePill(
    modifier: Modifier = Modifier,
    trailing: String? = null,
    height: Dp = 28.dp,
) {
    val c = PureTvTheme.colors
    Row(
        modifier
            .height(height)
            .clip(CircleShape)
            .background(c.live)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        LiveDot(size = 7.dp, color = c.onLive)
        Text(
            if (trailing != null) "LIVE · $trailing" else "LIVE",
            style = PureTvType.badge,
            color = c.onLive,
            maxLines = 1,
        )
    }
}

/**
 * The ad-block status pill. Deliberately tertiary, not primary: it is a standing
 * fact about the app, not an action, so it must not compete with the CTA.
 */
@Composable
fun ShieldPill(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector = ExpressiveIcons.Shield,
    height: Dp = 28.dp,
) {
    val c = PureTvTheme.colors
    Row(
        modifier
            .height(height)
            .clip(CircleShape)
            .background(c.tertiaryContainer)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Icon(icon, contentDescription = null, tint = c.onTertiaryContainer, modifier = Modifier.size(height * 0.5f))
        Text(text, style = PureTvType.badge, color = c.onTertiaryContainer, maxLines = 1)
    }
}

/** Small numeric badge: unread mentions, live-follow count on the nav rail. */
@Composable
fun CountBadge(text: String, modifier: Modifier = Modifier) {
    val c = PureTvTheme.colors
    Text(
        text,
        style = PureTvType.dataSmall,
        color = c.onErrorContainer,
        maxLines = 1,
        modifier = modifier
            .clip(CircleShape)
            .background(c.errorContainer)
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

// ── Cards & containers ─────────────────────────────────────────────────────────

/**
 * The standard content card: a tonal container that rounds out and lifts on
 * hover. Content goes inside the padding; artwork should be clipped to
 * `PureTvTheme.shapes.thumbShape` by the caller.
 */
@Composable
fun ExpressiveCard(
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    color: Color? = null,
    contentPadding: Dp = 10.dp,
    content: @Composable () -> Unit,
) {
    val c = PureTvTheme.colors
    val shapes = PureTvTheme.shapes
    val interaction = remember { MutableInteractionSource() }
    val fill = color ?: c.surfaceLow

    Box(
        modifier
            .then(
                if (onClick != null) {
                    Modifier.expressiveClickable(
                        interaction = interaction,
                        onClick = onClick,
                        restRadius = shapes.card,
                        hoverRadius = shapes.cardMorph,
                        color = fill,
                        hoverColor = c.surfaceHigh,
                        lift = 4.dp,
                    )
                } else {
                    Modifier.clip(shapes.cardShape).background(fill)
                },
            )
            .padding(contentPadding),
    ) { content() }
}

/** A non-interactive panel: settings sections, About boxes, stat tiles. */
@Composable
fun ExpressivePanel(
    modifier: Modifier = Modifier,
    color: Color? = null,
    padding: Dp = 28.dp,
    content: @Composable () -> Unit,
) {
    val c = PureTvTheme.colors
    Box(
        modifier
            .clip(PureTvTheme.shapes.cardShape)
            .background(color ?: c.surfaceLow)
            .padding(padding),
    ) { content() }
}

// ── Headings ───────────────────────────────────────────────────────────────────

/** The page-level masthead. One per screen, in the display voice. */
@Composable
fun PageTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.displayMedium,
        color = PureTvTheme.colors.onSurface,
        modifier = modifier,
    )
}

/** Section heading with an optional trailing action, as on Home's shelves. */
@Composable
fun SectionHeading(
    title: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val c = PureTvTheme.colors
    Row(
        modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            title,
            style = MaterialTheme.typography.headlineMedium,
            color = c.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.width(16.dp))
            ExpressiveButton(
                text = actionLabel,
                onClick = onAction,
                style = ExpressiveButtonStyle.Tonal,
                size = ExpressiveButtonSize.Small,
            )
        }
    }
}

// ── Switch ─────────────────────────────────────────────────────────────────────

/**
 * M3 switch. Both the track colour and the thumb SIZE change with state: the
 * thumb grows as it slides on, so the control still reads as on or off in a
 * greyscale screenshot or to a colour-blind viewer.
 */
@Composable
fun ExpressiveSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = PureTvTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val track by animateColorAsState(if (checked) c.primary else c.surfaceHighest, tween(PureTvMotion.Medium), label = "switchTrack")
    // Resting positions inside a 56x32 track: 4dp inset on the off side for the
    // small thumb, 28dp on the on side for the large one.
    val thumbStart by animateDpAsState(if (checked) 28.dp else 8.dp, PureTvMotion.MorphSpring, label = "switchThumbStart")
    val thumbSize by animateDpAsState(if (checked) 24.dp else 16.dp, PureTvMotion.MorphSpring, label = "switchThumbSize")

    Box(
        modifier
            .size(width = 56.dp, height = 32.dp)
            .clip(CircleShape)
            .background(track)
            .then(if (checked) Modifier else Modifier.border(2.dp, c.outline, CircleShape))
            .hoverable(interaction)
            .handCursor()
            .clickable(interactionSource = interaction, indication = null) { onCheckedChange(!checked) },
    ) {
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .padding(start = thumbStart.coerceAtLeast(0.dp))
                .size(thumbSize.coerceAtLeast(0.dp))
                .clip(CircleShape)
                .background(if (checked) c.onPrimary else c.outline),
        )
    }
}

// ── Divider ────────────────────────────────────────────────────────────────────

@Composable
fun ExpressiveDivider(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(1.dp).background(PureTvTheme.colors.outlineVariant))
}
