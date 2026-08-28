package com.puretv.twitch.desktop.ui.components

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.puretv.twitch.desktop.ui.theme.PureTvTheme

/**
 * The pre-Expressive button API, kept so existing call sites keep working, and
 * re-expressed on top of [ExpressiveButton] so they get the shape morph, the
 * press compression and the tonal roles for free.
 *
 * New code should call [ExpressiveButton] / [ExpressiveIconButton] directly:
 * those expose the full M3 emphasis ladder, where this exposes only four
 * variants inherited from the old design.
 */
enum class ButtonVariant { Primary, Secondary, Ghost, Destructive }

enum class ButtonSize(val height: Dp, val hPad: Dp) {
    Sm(40.dp, 18.dp),
    Md(48.dp, 22.dp),
    Lg(56.dp, 26.dp),
}

private fun ButtonSize.expressive() = when (this) {
    ButtonSize.Sm -> ExpressiveButtonSize.Small
    ButtonSize.Md -> ExpressiveButtonSize.Medium
    ButtonSize.Lg -> ExpressiveButtonSize.Large
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
    // Loading is the one state ExpressiveButton has no slot for, because a spinner
    // inside a morphing pill fights the morph. It gets its own minimal surface that
    // keeps the button's footprint while the action is in flight.
    if (loading) {
        val c = PureTvTheme.colors
        val interaction = remember { MutableInteractionSource() }
        Box(
            contentAlignment = Alignment.Center,
            modifier = modifier
                .height(size.height)
                .expressiveSurface(
                    interaction = interaction,
                    restRadius = size.height / 2,
                    hoverRadius = size.height / 2,
                    color = when (variant) {
                        ButtonVariant.Primary -> c.primary
                        ButtonVariant.Destructive -> c.errorContainer
                        ButtonVariant.Secondary -> c.secondaryContainer
                        ButtonVariant.Ghost -> Color.Transparent
                    },
                )
                .padding(horizontal = size.hPad),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    color = if (variant == ButtonVariant.Primary) c.onPrimary else c.onSurface,
                    strokeWidth = 2.dp,
                )
                Text(
                    text,
                    color = if (variant == ButtonVariant.Primary) c.onPrimary else c.onSurface,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
            }
        }
        return
    }

    ExpressiveButton(
        text = text,
        onClick = onClick,
        modifier = modifier,
        style = when (variant) {
            ButtonVariant.Primary -> ExpressiveButtonStyle.Filled
            ButtonVariant.Secondary -> ExpressiveButtonStyle.Outlined
            ButtonVariant.Ghost -> ExpressiveButtonStyle.Text
            // The destructive voice is the error container, the same fill the LIVE
            // badge and the close button use, so "irreversible" reads consistently.
            ButtonVariant.Destructive -> ExpressiveButtonStyle.FilledTertiary
        },
        size = size.expressive(),
        icon = leadingIcon,
        enabled = enabled,
    )
}

@Composable
fun PureIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    boxSize: Dp = 48.dp,
    iconSize: Dp = 22.dp,
    enabled: Boolean = true,
    tint: Color? = null,
) {
    ExpressiveIconButton(
        icon = icon,
        contentDescription = contentDescription,
        onClick = onClick,
        modifier = modifier,
        boxSize = boxSize,
        iconSize = iconSize,
        enabled = enabled,
        tint = tint,
    )
}
