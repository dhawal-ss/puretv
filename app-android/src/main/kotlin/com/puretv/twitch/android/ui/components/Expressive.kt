package com.puretv.twitch.android.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.puretv.twitch.android.ui.theme.PureTvMotion
import com.puretv.twitch.android.ui.theme.PureTvTheme
import com.puretv.twitch.android.ui.theme.PureTvType

/**
 * The Material 3 Expressive component vocabulary, phone edition.
 *
 * Same organising idea as the desktop app: shape is the primary feedback
 * channel. The difference is what triggers it. A phone has no hover, so a
 * surface morphs while it is PRESSED and springs back on release, and selection
 * is carried by a persistent fill rather than by a hover tint.
 *
 * Everything is built on [Modifier.expressiveSurface], so the curve and timing
 * are identical across buttons, chips, cards, nav items and pills.
 */

// ---- The morph primitive -----------------------------------------------------

/**
 * Clip and fill a surface whose corner radius animates between [restRadius] and
 * [pressRadius] on a spring with overshoot.
 *
 * Pass the SAME [interaction] you give `clickable` so press state reads from one
 * source.
 *
 * @param restRadius corner radius at rest.
 * @param pressRadius corner radius while pressed. Deliberately allowed to be
 *   either larger or smaller than [restRadius]: pills square off, cards round out.
 * @param selected when true the surface holds [selectedColor] instead of [color].
 * @param pressScale slight shrink under the finger. Runs in a graphics layer, so
 *   it never triggers relayout of anything around it.
 */
fun Modifier.expressiveSurface(
    interaction: MutableInteractionSource,
    restRadius: Dp,
    pressRadius: Dp,
    color: Color = Color.Transparent,
    selected: Boolean = false,
    selectedColor: Color = color,
    borderWidth: Dp = 0.dp,
    borderColor: Color = Color.Transparent,
    pressScale: Float = 0.97f,
): Modifier = composed {
    val pressed by interaction.collectIsPressedAsState()

    val radius by animateDpAsState(
        targetValue = if (pressed) pressRadius else restRadius,
        animationSpec = PureTvMotion.MorphSpring,
        label = "morphRadius",
    )
    val fill by animateColorAsState(
        targetValue = if (selected) selectedColor else color,
        animationSpec = tween(PureTvMotion.Medium, easing = PureTvMotion.Standard),
        label = "morphFill",
    )
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressScale else 1f,
        animationSpec = PureTvMotion.MorphSpringFloat,
        label = "morphScale",
    )

    // Radii can animate below zero on an overshooting spring, and
    // RoundedCornerShape rejects negatives, so clamp before it reaches the shape.
    val shape = RoundedCornerShape(radius.coerceAtLeast(0.dp))

    this
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .clip(shape)
        .background(fill)
        .then(if (borderWidth > 0.dp) Modifier.border(borderWidth, borderColor, shape) else Modifier)
}

/**
 * The whole interactive package: [expressiveSurface] plus a click handler wired
 * to one interaction source. `indication` stays null because the morph IS the
 * feedback; a ripple on top of it reads as two competing answers to one tap.
 */
@Composable
fun Modifier.expressiveClickable(
    interaction: MutableInteractionSource,
    onClick: () -> Unit,
    restRadius: Dp,
    pressRadius: Dp,
    color: Color = Color.Transparent,
    selected: Boolean = false,
    selectedColor: Color = color,
    borderWidth: Dp = 0.dp,
    borderColor: Color = Color.Transparent,
    pressScale: Float = 0.97f,
    enabled: Boolean = true,
): Modifier = this
    .graphicsLayer { alpha = if (enabled) 1f else 0.38f }
    .expressiveSurface(
        interaction = interaction,
        restRadius = restRadius,
        pressRadius = pressRadius,
        color = color,
        selected = selected,
        selectedColor = selectedColor,
        borderWidth = borderWidth,
        borderColor = borderColor,
        pressScale = pressScale,
    )
    .clickable(interactionSource = interaction, indication = null, enabled = enabled, onClick = onClick)

// ---- Buttons -----------------------------------------------------------------

enum class ExpressiveButtonStyle { Filled, Tonal, Outlined, Text, FilledTertiary }

/**
 * Heights follow the M3 Expressive button sizes. [Small] is 48dp rather than the
 * desktop's 40dp because 48dp is the Android minimum touch target, and a button
 * below it is a bug regardless of how it looks.
 */
enum class ExpressiveButtonSize(val height: Dp, val hPad: Dp, val iconSize: Dp, val fontSize: TextUnit) {
    Small(48.dp, 20.dp, 18.dp, 14.sp),
    Medium(52.dp, 24.dp, 20.dp, 15.sp),
    Large(56.dp, 28.dp, 22.dp, 16.sp),
    XLarge(64.dp, 32.dp, 26.dp, 18.sp),
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

    // Pressing squeezes the horizontal padding in. Combined with the corner
    // morph this reads as the button compressing under the finger.
    val hPad by animateDpAsState(
        targetValue = if (pressed) size.hPad - 4.dp else size.hPad,
        animationSpec = PureTvMotion.MorphSpring,
        label = "buttonPad",
    )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier
            .height(size.height)
            .expressiveClickable(
                interaction = interaction,
                onClick = onClick,
                restRadius = size.height / 2,
                pressRadius = shapes.pillMorph,
                color = bg,
                borderWidth = if (style == ExpressiveButtonStyle.Outlined) 1.5.dp else 0.dp,
                borderColor = c.outline,
                enabled = enabled,
            )
            .padding(horizontal = hPad.coerceAtLeast(0.dp)),
    ) {
        icon?.let { Icon(it, contentDescription = null, tint = fg, modifier = Modifier.size(size.iconSize)) }
        Text(
            text,
            color = fg,
            style = MaterialTheme.typography.labelLarge,
            fontSize = size.fontSize,
            lineHeight = size.fontSize * 1.3f,
            fontWeight = if (style == ExpressiveButtonStyle.Filled) FontWeight.Bold else FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}

/** Icon-only button. Round at rest, squircle under the finger. */
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

    val bg = when (style) {
        ExpressiveButtonStyle.Filled -> c.primary
        ExpressiveButtonStyle.Tonal -> c.secondaryContainer
        ExpressiveButtonStyle.FilledTertiary -> c.tertiaryContainer
        ExpressiveButtonStyle.Outlined, ExpressiveButtonStyle.Text -> Color.Transparent
    }
    val resting = tint ?: when (style) {
        ExpressiveButtonStyle.Filled -> c.onPrimary
        ExpressiveButtonStyle.Tonal -> c.onSecondaryContainer
        ExpressiveButtonStyle.FilledTertiary -> c.onTertiaryContainer
        ExpressiveButtonStyle.Outlined, ExpressiveButtonStyle.Text -> c.onSurfaceVariant
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(boxSize)
            .expressiveClickable(
                interaction = interaction,
                onClick = onClick,
                restRadius = boxSize / 2,
                pressRadius = shapes.pillMorph,
                color = bg,
                borderWidth = if (style == ExpressiveButtonStyle.Outlined) 1.5.dp else 0.dp,
                borderColor = c.outline,
                enabled = enabled,
            ),
    ) {
        Icon(icon, contentDescription = contentDescription, tint = resting, modifier = Modifier.size(iconSize))
    }
}

// ---- Chips -------------------------------------------------------------------

/**
 * Filter chip. Unselected it is an outline, selected it fills with the secondary
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
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier
            .height(40.dp)
            .expressiveClickable(
                interaction = interaction,
                onClick = onClick,
                restRadius = 20.dp,
                pressRadius = 12.dp,
                color = Color.Transparent,
                selected = selected,
                selectedColor = c.secondaryContainer,
                borderWidth = 1.5.dp,
                borderColor = if (selected) Color.Transparent else c.outlineVariant,
            )
            .padding(horizontal = 16.dp),
    ) {
        if (selected) {
            Icon(ExpressiveIcons.Check, contentDescription = null, tint = fg, modifier = Modifier.size(18.dp))
        }
        Text(label, color = fg, style = MaterialTheme.typography.labelLarge, maxLines = 1)
    }
}

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
            ExpressiveFilterChip(label(option), option == selected, { onSelect(option) })
        }
    }
}

/**
 * Segmented toggle: a pill track holding pill segments, the selected one filled.
 * Use where the options are mutually exclusive views of the same thing rather
 * than filters over a set.
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
                        pressRadius = shapes.pillMorph,
                        color = Color.Transparent,
                        selected = isSelected,
                        selectedColor = c.secondaryContainer,
                    )
                    .padding(horizontal = 16.dp),
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

// ---- Status pills ------------------------------------------------------------

/** The LIVE badge. Error-container red with a pulsing dot. */
@Composable
fun LivePill(
    modifier: Modifier = Modifier,
    trailing: String? = null,
    height: Dp = 24.dp,
) {
    val c = PureTvTheme.colors
    Row(
        modifier
            .height(height)
            .clip(CircleShape)
            .background(c.live)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        LiveDot(size = 6.dp, color = c.onLive)
        Text(
            if (trailing != null) "LIVE · $trailing" else "LIVE",
            style = PureTvType.badge,
            color = c.onLive,
            maxLines = 1,
        )
    }
}

/**
 * The ad-block status pill. Deliberately tertiary rather than primary: it is a
 * standing fact about the app, not an action, so it must not compete with the
 * play control next to it.
 */
@Composable
fun ShieldPill(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector = ExpressiveIcons.Shield,
    height: Dp = 26.dp,
) {
    val c = PureTvTheme.colors
    Row(
        modifier
            .height(height)
            .clip(CircleShape)
            .background(c.tertiaryContainer)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(icon, contentDescription = null, tint = c.onTertiaryContainer, modifier = Modifier.size(height * 0.55f))
        Text(text, style = PureTvType.badge, color = c.onTertiaryContainer, maxLines = 1)
    }
}

/** Small numeric badge: unread mentions, live-follow count on a nav item. */
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
            .padding(horizontal = 7.dp, vertical = 2.dp),
    )
}

// ---- Cards and containers ----------------------------------------------------

/**
 * The standard content card: a tonal container that rounds out under the finger.
 * Artwork inside should be clipped to `PureTvTheme.shapes.thumbShape`.
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
                        pressRadius = shapes.cardMorph,
                        color = fill,
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
    padding: Dp = 20.dp,
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

// ---- Headings ----------------------------------------------------------------

/** The screen-level masthead. One per screen, in the display voice. */
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
    Row(
        modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            title,
            style = MaterialTheme.typography.headlineMedium,
            color = PureTvTheme.colors.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.width(12.dp))
            ExpressiveButton(actionLabel, onAction, style = ExpressiveButtonStyle.Tonal, size = ExpressiveButtonSize.Small)
        }
    }
}

// ---- Switch ------------------------------------------------------------------

/**
 * M3 switch. Both the track colour and the thumb SIZE change with state, so the
 * control still reads as on or off in greyscale.
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
    val thumbStart by animateDpAsState(if (checked) 28.dp else 8.dp, PureTvMotion.MorphSpring, label = "switchThumbStart")
    val thumbSize by animateDpAsState(if (checked) 24.dp else 16.dp, PureTvMotion.MorphSpring, label = "switchThumbSize")

    Box(
        modifier
            .size(width = 56.dp, height = 32.dp)
            .clip(CircleShape)
            .background(track)
            .then(if (checked) Modifier else Modifier.border(2.dp, c.outline, CircleShape))
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

// ---- Divider -----------------------------------------------------------------

@Composable
fun ExpressiveDivider(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(1.dp).background(PureTvTheme.colors.outlineVariant))
}

// ---- Live dot ----------------------------------------------------------------

/**
 * The pulsing dot inside a LIVE badge. Motion backs up the colour, which matters
 * because red-on-dark is the one place in the app where a single hue carries the
 * meaning on its own.
 */
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
