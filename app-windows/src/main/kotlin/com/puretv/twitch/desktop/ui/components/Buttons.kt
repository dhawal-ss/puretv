package com.puretv.twitch.desktop.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.puretv.twitch.desktop.ui.theme.PureTvMotion
import com.puretv.twitch.desktop.ui.theme.PureTvShape
import com.puretv.twitch.desktop.ui.theme.PureTvTheme

/**
 * The PureTV button system. One composable, all six states wired through a single
 * [MutableInteractionSource]: rest / hover / pressed / focused / disabled / loading.
 *
 * Tactility comes from a spring press-scale (0.96) + a hover fill shift + an accent
 * focus ring — Material's ripple is deliberately disabled (`indication = null`)
 * because ripple reads "Android," not desktop-premium.
 */
enum class ButtonVariant { Primary, Secondary, Ghost, Destructive }

enum class ButtonSize(val height: Dp, val hPad: Dp) {
    Sm(30.dp, 12.dp),
    Md(38.dp, 18.dp),
    Lg(46.dp, 24.dp),
}

@Composable
fun PureButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: ButtonVariant = ButtonVariant.Primary,
    size: ButtonSize = ButtonSize.Md,
    enabled: Boolean = true,
    loading: Boolean = false,
    leadingIcon: ImageVector? = null,
) {
    val c = PureTvTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val focused by interaction.collectIsFocusedAsState()
    val active = enabled && !loading

    val fg = when (variant) {
        ButtonVariant.Primary, ButtonVariant.Destructive -> Color.White
        ButtonVariant.Secondary -> c.textPrimary
        ButtonVariant.Ghost -> if (hovered) c.textPrimary else c.textSecondary
    }
    val bg by animateColorAsState(
        targetValue = when (variant) {
            ButtonVariant.Primary -> Color.Transparent // gradient applied separately
            ButtonVariant.Secondary -> if (hovered) c.surfaceHover else c.surfaceRaised
            ButtonVariant.Ghost -> if (hovered) c.surfaceHover else Color.Transparent
            ButtonVariant.Destructive -> if (hovered) c.live.copy(alpha = 0.88f) else c.live
        },
        animationSpec = tween(PureTvMotion.Fast),
        label = "btnBg",
    )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
            .height(size.height)
            .graphicsLayer { alpha = if (active) 1f else 0.4f }
            .pressScale(interaction)
            .focusRing(focused, cornerRadius = 8.dp)
            .clip(PureTvShape.sm)
            .then(
                if (variant == ButtonVariant.Primary && active) Modifier.background(c.accentGradient)
                else Modifier.background(if (variant == ButtonVariant.Primary) SolidColor(c.surfaceRaised) else SolidColor(bg)),
            )
            .then(
                if (variant == ButtonVariant.Secondary) Modifier.border(1.dp, c.hairlineStrong, PureTvShape.sm)
                else Modifier,
            )
            .hoverable(interaction)
            .handCursor()
            .clickable(interactionSource = interaction, indication = null, enabled = active, onClick = onClick)
            .padding(horizontal = size.hPad),
    ) {
        if (loading) {
            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = fg, strokeWidth = 2.dp)
        } else {
            leadingIcon?.let { Icon(it, contentDescription = null, tint = fg, modifier = Modifier.size(18.dp)) }
            Text(text, color = fg, style = MaterialTheme.typography.labelLarge, maxLines = 1)
        }
    }
}

/**
 * Square icon-only button with the same tactile feedback. [tintActive] is the
 * resting tint; on hover it brightens to textPrimary and fills the surface.
 */
@Composable
fun PureIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    boxSize: Dp = 38.dp,
    iconSize: Dp = 18.dp,
    enabled: Boolean = true,
    tint: Color? = null,
) {
    val c = PureTvTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val focused by interaction.collectIsFocusedAsState()
    val bg by animateColorAsState(if (hovered && enabled) c.surfaceHover else Color.Transparent, tween(PureTvMotion.Fast), label = "iconBg")
    val resting = tint ?: c.textSecondary

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(boxSize)
            .graphicsLayer { alpha = if (enabled) 1f else 0.4f }
            .pressScale(interaction)
            .focusRing(focused, cornerRadius = 8.dp)
            .clip(PureTvShape.sm)
            .background(bg)
            .hoverable(interaction)
            .handCursor()
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, onClick = onClick),
    ) {
        Icon(
            icon,
            contentDescription = contentDescription,
            tint = if (hovered && enabled) c.textPrimary else resting,
            modifier = Modifier.size(iconSize),
        )
    }
}
