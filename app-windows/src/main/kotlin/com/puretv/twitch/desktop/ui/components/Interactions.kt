package com.puretv.twitch.desktop.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.puretv.twitch.desktop.ui.theme.PureTvMotion
import com.puretv.twitch.desktop.ui.theme.PureTvTheme

/**
 * Reusable interaction modifiers — the substrate every premium component composes.
 *
 * The convention: each interactive component creates ONE
 * `remember { MutableInteractionSource() }`, threads it into
 * `.clickable(interactionSource = it, indication = null)` / `.hoverable(it)`, and
 * layers these modifiers off it. One source → consistent hover/press/focus
 * everywhere, and it replaces the hand-rolled `pointerInput { Enter/Exit }` loops
 * that were duplicated across StreamCard / NavItem / WinButton.
 */

/** The tactile press-in cue. Spring so it settles with a little weight. */
fun Modifier.pressScale(
    interaction: MutableInteractionSource,
    pressed: Float = 0.96f,
): Modifier = composed {
    val isPressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) pressed else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium),
        label = "pressScale",
    )
    graphicsLayer { scaleX = scale; scaleY = scale }
}

/** Hover lift for cards: rise + slight scale. Runs in graphicsLayer (no relayout). */
fun Modifier.hoverLift(
    interaction: MutableInteractionSource,
    lift: Dp = 4.dp,
    scaleTo: Float = 1.03f,
): Modifier = composed {
    val hovered by interaction.collectIsHoveredAsState()
    val l by animateDpAsState(if (hovered) -lift else 0.dp, tween(PureTvMotion.Fast), label = "lift")
    val s by animateFloatAsState(if (hovered) scaleTo else 1f, tween(PureTvMotion.Fast), label = "hoverScale")
    graphicsLayer { translationY = l.toPx(); scaleX = s; scaleY = s }
}

/** Desktop-only premium cue: show the hand cursor over a clickable. */
fun Modifier.handCursor(): Modifier = pointerHoverIcon(PointerIcon.Hand)

/**
 * Animated accent focus ring, offset just outside the bounds. Drive [focused] from
 * `interaction.collectIsFocusedAsState()` so keyboard users always see focus.
 */
fun Modifier.focusRing(
    focused: Boolean,
    color: Color? = null,
    cornerRadius: Dp = 10.dp,
    width: Dp = 2.dp,
): Modifier = composed {
    val ringColor = color ?: PureTvTheme.colors.twitchPurple
    val alpha by animateFloatAsState(if (focused) 1f else 0f, tween(PureTvMotion.Fast), label = "focusRing")
    drawWithContent {
        drawContent()
        if (alpha > 0f) {
            val inset = (-3).dp.toPx()
            val stroke = width.toPx()
            drawRoundRect(
                color = ringColor.copy(alpha = alpha),
                topLeft = Offset(inset + stroke / 2, inset + stroke / 2),
                size = Size(size.width - 2 * inset - stroke, size.height - 2 * inset - stroke),
                cornerRadius = CornerRadius(cornerRadius.toPx()),
                style = Stroke(width = stroke),
            )
        }
    }
}

/** Convenience: hover state as a boolean, for components that want to branch on it. */
@Composable
fun MutableInteractionSource.isHovered(): Boolean = collectIsHoveredAsState().value
