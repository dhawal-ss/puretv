package com.puretv.twitch.android.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.puretv.twitch.android.ui.theme.PureTvColors
import com.puretv.twitch.core.adblock.AdBlockStatus
import kotlinx.coroutines.delay

/**
 * SECTION 10.3: small unobtrusive pill in the top-right of the stream view.
 * Shown for 3 seconds whenever [status] changes, then fades out. Tapping it
 * opens ad-block settings (wire [onClick] to navigate there).
 *
 * Visual grammar: the calm states (blocked / filtered) wear a TINTED look (a
 * low-alpha fill, a 1dp accent border, accent-colored text) so they whisper.
 * Only AD_BLOCK_OFF gets a solid alarming fill, because that one needs to
 * shout. A semantics contentDescription carries the meaning to TalkBack and to
 * color-blind users, who otherwise only see "a colored pill".
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
        val (label, accent, alarming) = when (status) {
            AdBlockStatus.AD_BLOCKED -> Triple("AD BLOCKED", PureTvColors.AdBlockGreen, false)
            AdBlockStatus.AD_FILTERED -> Triple("AD FILTERED", PureTvColors.Warning, false)
            AdBlockStatus.AD_BLOCK_OFF -> Triple("AD BLOCK OFF", PureTvColors.Live, true)
            AdBlockStatus.DISABLED -> Triple("AD BLOCK OFF", PureTvColors.TextSecondary, false)
            AdBlockStatus.UNKNOWN -> Triple("", Color.Transparent, false)
        }

        val shape = RoundedCornerShape(50)
        val base = Modifier
            .clip(shape)
            .clickable(onClick = onClick)
            .semantics { contentDescription = "Ad block status: $label" }

        val styled = if (alarming) {
            // Shout: solid fill, white text.
            base.background(accent, shape)
        } else {
            // Whisper: tinted fill + hairline accent border.
            base
                .background(accent.copy(alpha = 0.14f), shape)
                .border(BorderStroke(1.dp, accent.copy(alpha = 0.55f)), shape)
        }

        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (alarming) Color.White else accent,
            modifier = styled.padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

/**
 * A small persistent "AD-FREE" badge of honor for the Home top bar: a shield
 * glyph + a monospace label in a tasteful green tint. This is the product's
 * promise made visible. Tasteful, not gaudy: tinted fill, hairline border, no
 * solid block of color.
 *
 * FOLLOW-UP: adopt in the HomeScreen top bar (it is not wired anywhere yet).
 */
@Composable
fun AdFreeChip(modifier: Modifier = Modifier) {
    val accent = PureTvColors.AdBlockGreen
    val shape = RoundedCornerShape(50)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(shape)
            .background(accent.copy(alpha = 0.12f), shape)
            .border(BorderStroke(1.dp, accent.copy(alpha = 0.45f)), shape)
            .semantics { contentDescription = "Ad free" }
            .padding(horizontal = 10.dp, vertical = 5.dp),
    ) {
        Icon(
            imageVector = Icons.Filled.Shield,
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(13.dp),
        )
        Spacer(Modifier.width(5.dp))
        Text(
            text = "AD-FREE",
            style = MaterialTheme.typography.labelSmall,
            color = accent,
        )
    }
}
