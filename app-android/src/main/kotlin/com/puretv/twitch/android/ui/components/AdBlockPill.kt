package com.puretv.twitch.android.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.puretv.twitch.android.ui.theme.PureTvColors
import com.puretv.twitch.core.adblock.AdBlockStatus
import kotlinx.coroutines.delay

/**
 * SECTION 10.3 — small unobtrusive pill in the top-right of the stream view.
 * Shown for 3 seconds whenever [status] changes, then fades out. Tapping it
 * should open ad-block settings (wire [onClick] to navigate there).
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
        val (label, color) = when (status) {
            AdBlockStatus.AD_BLOCKED -> "AD BLOCKED" to PureTvColors.AdBlockGreen
            AdBlockStatus.AD_FILTERED -> "AD FILTERED" to PureTvColors.Warning
            AdBlockStatus.AD_BLOCK_OFF -> "AD BLOCK OFF" to PureTvColors.Live
            AdBlockStatus.DISABLED -> "AD BLOCK OFF" to PureTvColors.TextMuted
            AdBlockStatus.UNKNOWN -> "" to Color.Transparent
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
            modifier = Modifier
                .clickable(onClick = onClick)
                .background(color, RoundedCornerShape(50))
                .padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}
