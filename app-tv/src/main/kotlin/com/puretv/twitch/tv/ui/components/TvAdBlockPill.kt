package com.puretv.twitch.tv.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.puretv.twitch.core.adblock.AdBlockStatus
import com.puretv.twitch.tv.ui.theme.PureTvTvColors
import kotlinx.coroutines.delay

/**
 * SECTION 10.3 / 07.4 — TV counterpart of the phone app's `AdBlockPill`,
 * surfaced inside [TvControlsOverlay] (top-right corner of the immersive
 * player) rather than as a standalone tap target — TV remotes have no
 * pointer, so it's purely informational here (no `onClick`).
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
        val (label, color) = when (status) {
            AdBlockStatus.AD_BLOCKED -> "AD BLOCKED" to PureTvTvColors.AdBlockGreen
            AdBlockStatus.AD_FILTERED -> "AD FILTERED" to PureTvTvColors.Warning
            AdBlockStatus.AD_BLOCK_OFF -> "AD BLOCK OFF" to PureTvTvColors.Live
            AdBlockStatus.DISABLED -> "AD BLOCK OFF" to PureTvTvColors.TextMuted
            AdBlockStatus.UNKNOWN -> "" to Color.Transparent
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
            modifier = Modifier
                .background(color, RoundedCornerShape(50))
                .padding(horizontal = 14.dp, vertical = 7.dp),
        )
    }
}
