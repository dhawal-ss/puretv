package com.puretv.twitch.tv.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.puretv.twitch.tv.ui.theme.LocalReducedMotion
import com.puretv.twitch.tv.ui.theme.PureTvTvMotion
import com.puretv.twitch.tv.ui.theme.PureTvTvTheme
import com.puretv.twitch.tv.ui.theme.PureTvTvType

/**
 * The Material 3 Expressive component vocabulary, 10-foot edition.
 *
 * On a television the entire interaction model is D-pad focus, so focus is what
 * drives the morph here rather than hover or press. A focused surface does three
 * things at once, because at three metres one signal is not enough:
 *
 *  1. its corners morph,
 *  2. it scales up slightly,
 *  3. it takes a primary-coloured ring.
 *
 * Everything is built on [Modifier.tvFocusSurface], so every focusable object in
 * the app answers the remote in exactly the same way.
 */

// ---- The focus-morph primitive -----------------------------------------------

/**
 * Clip, fill and ring a surface that reacts to D-pad focus.
 *
 * Pass the SAME [interaction] you give `focusable`/`clickable` so all three cues
 * read from one source.
 *
 * @param restRadius corner radius when not focused.
 * @param focusRadius corner radius while focused. Allowed to be either larger or
 *   smaller: pills square off, cards round out.
 * @param scale how far the surface grows on focus. Keep neighbours in mind, a
 *   row of cards needs spacing to absorb it.
 * @param ring draw the primary focus ring. Leave on for anything selectable.
 */
fun Modifier.tvFocusSurface(
    interaction: MutableInteractionSource,
    restRadius: Dp,
    focusRadius: Dp,
    color: Color = Color.Transparent,
    focusColor: Color = color,
    scale: Float = PureTvTvMotion.FocusScale,
    ring: Boolean = true,
    ringColor: Color? = null,
    ringWidth: Dp = 3.dp,
    idleBorderColor: Color = Color.Transparent,
): Modifier = composed {
    val focused by interaction.collectIsFocusedAsState()
    val accent = ringColor ?: PureTvTvTheme.colors.focusRing

    // One progress value (0 resting, 1 focused) drives every cue, and it is read
    // only inside the layer and draw lambdas, so a focus change animates without
    // recomposing the card or anything under it on any frame.
    val progress = animateFloatAsState(
        targetValue = if (focused) 1f else 0f,
        animationSpec = PureTvTvMotion.FocusSpring,
        label = "tvFocus",
    )

    this
        // Outer layer: scale only, unclipped, so the halo can draw outside the card.
        .graphicsLayer {
            val grow = 1f + (scale - 1f) * progress.value
            scaleX = grow
            scaleY = grow
        }
        .drawBehind {
            val p = progress.value.coerceIn(0f, 1f)
            if (ring && p > 0f) {
                // A soft halo: one wide, faint stroke just outside the ring. Cheaper
                // than a blurred shadow, which a Fire TV stick would pay for per frame.
                val halo = 6.dp.toPx()
                val r = (lerpDp(restRadius, focusRadius, p).toPx() + halo / 2f).coerceAtLeast(0f)
                drawRoundRect(
                    color = accent.copy(alpha = PureTvTvMotion.HaloAlpha * p),
                    topLeft = Offset(-halo / 2f, -halo / 2f),
                    size = Size(size.width + halo, size.height + halo),
                    cornerRadius = CornerRadius(r, r),
                    style = Stroke(halo),
                )
            }
        }
        .graphicsLayer {
            // An overshooting spring can drive the radius below zero, and
            // RoundedCornerShape rejects negatives, so clamp before the shape.
            shape = RoundedCornerShape(lerpDp(restRadius, focusRadius, progress.value).coerceAtLeast(0.dp))
            clip = true
        }
        .drawBehind {
            val p = progress.value
            drawRect(lerp(color, focusColor, p.coerceIn(0f, 1f)))
            val stroke = lerp(idleBorderColor, if (ring) accent else idleBorderColor, p.coerceIn(0f, 1f))
            if (stroke.alpha > 0f) {
                // Inset by half the width so the whole line sits inside the clip.
                val w = ringWidth.toPx()
                val r = (lerpDp(restRadius, focusRadius, p).toPx() - w / 2f).coerceAtLeast(0f)
                drawRoundRect(
                    color = stroke,
                    topLeft = Offset(w / 2f, w / 2f),
                    size = Size(size.width - w, size.height - w),
                    cornerRadius = CornerRadius(r, r),
                    style = Stroke(w),
                )
            }
        }
}

private fun lerpDp(from: Dp, to: Dp, fraction: Float): Dp = from + (to - from) * fraction

/**
 * The whole remote-driven package: [tvFocusSurface] plus `focusable` and a click
 * handler, wired to one interaction source. Select on the remote fires onClick.
 */
@Composable
fun Modifier.tvFocusClickable(
    interaction: MutableInteractionSource,
    onClick: () -> Unit,
    restRadius: Dp,
    focusRadius: Dp,
    color: Color = Color.Transparent,
    focusColor: Color = color,
    scale: Float = PureTvTvMotion.FocusScale,
    ring: Boolean = true,
    idleBorderColor: Color = Color.Transparent,
    enabled: Boolean = true,
): Modifier = this
    .graphicsLayer { alpha = if (enabled) 1f else 0.38f }
    .tvFocusSurface(
        interaction = interaction,
        restRadius = restRadius,
        focusRadius = focusRadius,
        color = color,
        focusColor = focusColor,
        scale = scale,
        ring = ring,
        idleBorderColor = idleBorderColor,
    )
    .clickable(interactionSource = interaction, indication = null, enabled = enabled, onClick = onClick)
    .focusable(enabled = enabled, interactionSource = interaction)

// ---- Buttons -----------------------------------------------------------------

enum class TvButtonStyle { Filled, Tonal, Outlined, Text, FilledTertiary }

/** Sized for a remote and a couch: nothing under 56dp tall, nothing under 18sp. */
enum class TvButtonSize(val height: Dp, val hPad: Dp, val iconSize: Dp, val fontSize: TextUnit) {
    Medium(56.dp, 28.dp, 24.dp, 18.sp),
    Large(64.dp, 34.dp, 28.dp, 20.sp),
    XLarge(76.dp, 40.dp, 32.dp, 22.sp),
}

@Composable
fun TvExpressiveButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: TvButtonStyle = TvButtonStyle.Filled,
    size: TvButtonSize = TvButtonSize.Medium,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    val c = PureTvTvTheme.colors
    val shapes = PureTvTvTheme.shapes
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()

    val restBg = when (style) {
        TvButtonStyle.Filled -> c.primary
        TvButtonStyle.Tonal -> c.secondaryContainer
        TvButtonStyle.FilledTertiary -> c.tertiaryContainer
        TvButtonStyle.Outlined, TvButtonStyle.Text -> Color.Transparent
    }
    // Focus flips low-emphasis buttons to a filled primary. On TV the focused
    // control has to be obvious from across the room, so it takes the strongest
    // treatment available rather than a subtle tint.
    val focusBg = when (style) {
        TvButtonStyle.Filled -> c.primary
        TvButtonStyle.FilledTertiary -> c.tertiaryContainer
        else -> c.primary
    }
    val ink = when {
        style == TvButtonStyle.Filled -> c.onPrimary
        style == TvButtonStyle.FilledTertiary -> c.onTertiaryContainer
        focused -> c.onPrimary
        style == TvButtonStyle.Tonal -> c.onSecondaryContainer
        else -> c.onSurface
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        // Centred, not merely spaced: a button stretched to fillMaxWidth would
        // otherwise pin its icon and label to the left edge.
        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
        modifier = modifier
            .height(size.height)
            .tvFocusClickable(
                interaction = interaction,
                onClick = onClick,
                restRadius = size.height / 2,
                focusRadius = shapes.pillFocus,
                color = restBg,
                focusColor = focusBg,
                scale = 1.05f,
                enabled = enabled,
            )
            .padding(horizontal = size.hPad),
    ) {
        icon?.let { Icon(it, contentDescription = null, tint = ink, modifier = Modifier.size(size.iconSize)) }
        Text(
            text,
            color = ink,
            style = MaterialTheme.typography.labelLarge,
            fontSize = size.fontSize,
            lineHeight = size.fontSize * 1.3f,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}

/** Icon-only button. Round at rest, squircle on focus. */
@Composable
fun TvExpressiveIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: TvButtonStyle = TvButtonStyle.Tonal,
    boxSize: Dp = 64.dp,
    iconSize: Dp = 30.dp,
    enabled: Boolean = true,
) {
    val c = PureTvTvTheme.colors
    val shapes = PureTvTvTheme.shapes
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()

    val restBg = when (style) {
        TvButtonStyle.Filled -> c.primary
        TvButtonStyle.Tonal -> c.surfaceHigh
        TvButtonStyle.FilledTertiary -> c.tertiaryContainer
        TvButtonStyle.Outlined, TvButtonStyle.Text -> Color.Transparent
    }
    val ink = when {
        style == TvButtonStyle.Filled -> c.onPrimary
        focused -> c.onPrimary
        else -> c.onSurface
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(boxSize)
            .tvFocusClickable(
                interaction = interaction,
                onClick = onClick,
                restRadius = boxSize / 2,
                focusRadius = shapes.pillFocus,
                color = restBg,
                focusColor = c.primary,
                scale = 1.08f,
                enabled = enabled,
            ),
    ) {
        Icon(icon, contentDescription = contentDescription, tint = ink, modifier = Modifier.size(iconSize))
    }
}

// ---- Chips -------------------------------------------------------------------

/**
 * Filter chip. Selection is a persistent fill plus a check mark; focus is the
 * ring and the morph. The two states are deliberately different signals so a
 * focused-but-unselected chip is never mistaken for a selected one.
 */
@Composable
fun TvFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = PureTvTvTheme.colors
    val shapes = PureTvTvTheme.shapes
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
            .height(52.dp)
            .tvFocusClickable(
                interaction = interaction,
                onClick = onClick,
                restRadius = 26.dp,
                focusRadius = shapes.pillFocus,
                color = if (selected) c.secondaryContainer else Color.Transparent,
                focusColor = c.primary,
                scale = 1.05f,
                idleBorderColor = if (selected) Color.Transparent else c.outlineVariant,
            )
            .padding(horizontal = 22.dp),
    ) {
        if (selected) {
            Icon(
                ExpressiveIcons.Check,
                contentDescription = null,
                tint = if (focused) c.onPrimary else c.onSecondaryContainer,
                modifier = Modifier.size(22.dp),
            )
        }
        Text(
            label,
            color = when {
                focused -> c.onPrimary
                selected -> c.onSecondaryContainer
                else -> c.onSurfaceVariant
            },
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
        )
    }
}

// ---- Status pills ------------------------------------------------------------

/**
 * The LIVE badge. Error-container red with a dot that pulses when `pulse` is on.
 * Each pulsing pill is an endless animation that keeps the whole screen redrawing,
 * which TV GPUs feel during D-pad scrolling, so cards and rows pass `pulse = false`
 * and only single pills (the Home hero, a channel's header) pulse.
 */
@Composable
fun TvLivePill(
    modifier: Modifier = Modifier,
    trailing: String? = null,
    height: Dp = 34.dp,
    pulse: Boolean = true,
) {
    val c = PureTvTvTheme.colors
    Row(
        modifier
            .height(height)
            .clip(CircleShape)
            .background(c.live)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TvLiveDot(size = 9.dp, color = c.onLive, pulse = pulse)
        Text(
            if (trailing != null) "LIVE · $trailing" else "LIVE",
            style = PureTvTvType.badge,
            color = c.onLive,
            maxLines = 1,
        )
    }
}

/**
 * The ad-block status pill. Tertiary rather than primary: it is a standing fact
 * about the app, not something the remote can act on.
 */
@Composable
fun TvShieldPill(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector = ExpressiveIcons.Shield,
    height: Dp = 34.dp,
) {
    val c = PureTvTvTheme.colors
    Row(
        modifier
            .height(height)
            .clip(CircleShape)
            .background(c.tertiaryContainer)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(icon, contentDescription = null, tint = c.onTertiaryContainer, modifier = Modifier.size(height * 0.55f))
        Text(text, style = PureTvTvType.badge, color = c.onTertiaryContainer, maxLines = 1)
    }
}

/**
 * The pulsing dot inside a LIVE badge. Motion backs up the colour, which matters
 * because red-on-dark is the one place where a single hue carries the meaning.
 */
@Composable
fun TvLiveDot(modifier: Modifier = Modifier, size: Dp = 9.dp, color: Color? = null, pulse: Boolean = true) {
    val c = PureTvTvTheme.colors
    // Still when motion is off, and wherever live video plays: nothing loops over the picture.
    val alpha = if (pulse && !LocalReducedMotion.current) {
        rememberInfiniteTransition(label = "tvLiveDot").animateFloat(
            initialValue = 1f,
            targetValue = 0.35f,
            animationSpec = infiniteRepeatable(tween(1100), RepeatMode.Reverse),
            label = "tvLiveDotAlpha",
        )
    } else {
        null
    }
    Box(
        modifier
            .size(size)
            .graphicsLayer { this.alpha = alpha?.value ?: 1f }
            .clip(CircleShape)
            .background(color ?: c.live),
    )
}

// ---- Containers and headings -------------------------------------------------

/** A non-interactive panel: settings sections, About boxes, empty states. */
@Composable
fun TvPanel(
    modifier: Modifier = Modifier,
    color: Color? = null,
    padding: Dp = 32.dp,
    content: @Composable () -> Unit,
) {
    val c = PureTvTvTheme.colors
    Box(
        modifier
            // Panels stack vertically down a settings page, so they fill the
            // column by default. A content-sized panel left the stack ragged.
            .fillMaxWidth()
            .clip(PureTvTvTheme.shapes.cardShape)
            .background(color ?: c.surfaceLow)
            .padding(padding),
    ) { content() }
}

/** The screen-level masthead. One per screen, in the display voice. */
@Composable
fun TvPageTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.displayMedium,
        color = PureTvTvTheme.colors.onSurface,
        modifier = modifier,
    )
}

/** Row heading above a shelf of cards. */
@Composable
fun TvSectionHeading(title: String, modifier: Modifier = Modifier) {
    Text(
        title,
        style = MaterialTheme.typography.headlineMedium,
        color = PureTvTvTheme.colors.onSurface,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

@Composable
fun TvDivider(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(2.dp).background(PureTvTvTheme.colors.outlineVariant))
}
