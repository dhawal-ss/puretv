package com.puretv.twitch.android.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.puretv.twitch.android.ui.theme.PureTvTheme
import com.puretv.twitch.android.ui.theme.PureTvType
import com.puretv.twitch.core.adblock.AdBlockStatus
import kotlinx.coroutines.delay

/**
 * SECTION 10.3: small unobtrusive pill in the top-right of the stream view.
 * Shown for 3 seconds whenever [status] changes, then fades out. Tapping it
 * opens ad-block settings (wire [onClick] to navigate there).
 *
 * Built on [ShieldPill]'s grammar so it reads as the same shield vocabulary as
 * the rest of the app: the active states (blocked, filtered) get the shield's
 * tertiary-container fill, the inactive states drop to a plain outline with no
 * fill at all, so "on" is the only state this pill ever colours.
 */
@Composable
fun AdBlockPill(status: AdBlockStatus, onClick: () -> Unit, modifier: Modifier = Modifier) {
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(status) {
        if (status == AdBlockStatus.UNKNOWN) return@LaunchedEffect
        visible = true
        delay(3_000)
        visible = false
    }

    AnimatedVisibility(visible = visible, enter = fadeIn(), exit = fadeOut(), modifier = modifier) {
        val c = PureTvTheme.colors
        val label = when (status) {
            AdBlockStatus.AD_BLOCKED -> "AD BLOCKED"
            AdBlockStatus.AD_FILTERED -> "AD FILTERED"
            AdBlockStatus.AD_BLOCK_OFF -> "AD BLOCK OFF"
            AdBlockStatus.DISABLED -> "AD BLOCK OFF"
            AdBlockStatus.UNKNOWN -> ""
        }
        val active = status == AdBlockStatus.AD_BLOCKED || status == AdBlockStatus.AD_FILTERED
        val fg = if (active) c.onTertiaryContainer else c.onSurfaceVariant

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .height(26.dp)
                .clip(CircleShape)
                .then(
                    if (active) {
                        Modifier.background(c.tertiaryContainer)
                    } else {
                        Modifier.border(1.dp, c.outline, CircleShape)
                    },
                )
                .clickable(onClick = onClick)
                .semantics { contentDescription = "Ad block status: $label" }
                .padding(horizontal = 10.dp),
        ) {
            Icon(ExpressiveIcons.Shield, contentDescription = null, tint = fg, modifier = Modifier.size(14.dp))
            Text(label, style = PureTvType.badge, color = fg, maxLines = 1)
        }
    }
}

/**
 * A small persistent "AD-FREE" badge of honor for the Home top bar and the
 * Welcome screen: this product's promise made visible. Just [ShieldPill] under
 * its own name, so the app carries one shield pill rather than a near-duplicate.
 */
@Composable
fun AdFreeChip(modifier: Modifier = Modifier) {
    ShieldPill(text = "AD-FREE", modifier = modifier, height = 24.dp)
}
