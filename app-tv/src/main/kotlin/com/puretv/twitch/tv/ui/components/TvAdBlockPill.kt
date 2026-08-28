package com.puretv.twitch.tv.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import com.puretv.twitch.core.adblock.AdBlockStatus
import com.puretv.twitch.tv.ui.theme.PureTvTvTheme
import com.puretv.twitch.tv.ui.theme.PureTvTvType
import kotlinx.coroutines.delay

/**
 * SECTION 10.3 / 07.4 — TV counterpart of the phone app's `AdBlockPill`,
 * surfaced inside [TvControlsOverlay] (top-right corner of the immersive
 * player) rather than as a standalone tap target — TV remotes have no
 * pointer, so it's purely informational here (no `onClick`).
 *
 * Built on [TvShieldPill]'s grammar (shape, padding, icon size, mono badge
 * type) rather than the composable itself: [TvShieldPill] is always a filled
 * tertiary pill, and here the fill has to be able to drop out entirely. Active
 * states (blocked, filtered) get the shield's tertiary-container fill; the
 * inactive states fall back to a plain outline with no fill at all, so "on" is
 * the only state this pill ever colours.
 */
@Composable
fun TvAdBlockPill(status: AdBlockStatus, modifier: Modifier = Modifier) {
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(status) {
        if (status == AdBlockStatus.UNKNOWN) return@LaunchedEffect
        visible = true
        delay(3_000)
        visible = false
    }

    AnimatedVisibility(visible = visible, enter = fadeIn(), exit = fadeOut(), modifier = modifier) {
        val c = PureTvTvTheme.colors
        val label = when (status) {
            AdBlockStatus.AD_BLOCKED -> "AD BLOCKED"
            AdBlockStatus.AD_FILTERED -> "AD FILTERED"
            AdBlockStatus.AD_BLOCK_OFF -> "AD BLOCK OFF"
            AdBlockStatus.DISABLED -> "AD BLOCK OFF"
            AdBlockStatus.UNKNOWN -> ""
        }
        val active = status == AdBlockStatus.AD_BLOCKED || status == AdBlockStatus.AD_FILTERED
        val fg = if (active) c.onTertiaryContainer else c.onSurfaceVariant
        val height = 34.dp

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .height(height)
                .clip(CircleShape)
                .then(
                    if (active) {
                        Modifier.background(c.tertiaryContainer)
                    } else {
                        Modifier.border(1.dp, c.outline, CircleShape)
                    },
                )
                .padding(horizontal = 14.dp),
        ) {
            Icon(ExpressiveIcons.Shield, contentDescription = null, tint = fg, modifier = Modifier.size(height * 0.55f))
            Text(label, style = PureTvTvType.badge, color = fg, maxLines = 1)
        }
    }
}
