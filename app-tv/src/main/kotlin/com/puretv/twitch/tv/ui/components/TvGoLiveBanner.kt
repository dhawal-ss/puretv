package com.puretv.twitch.tv.ui.components

import com.puretv.twitch.tv.ui.theme.PureTvTvMotion
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.puretv.twitch.core.notify.GoLiveBanner
import com.puretv.twitch.core.notify.GoLiveBannerQueue
import com.puretv.twitch.tv.ui.theme.PureTvTvTheme
import kotlinx.coroutines.delay

/** How long each go-live banner stays up before the next one (or nothing) takes its place. */
private const val BANNER_DWELL_MS = 8_000L

/**
 * The TV go-live banner: "Alice is live" over the stream title, in the top corner,
 * for [BANNER_DWELL_MS], then the next one waiting in [queue].
 *
 * It must NEVER take D-pad focus. It is built only from plain layout and text (no
 * tv-material3 Surface or Card, which are focusable, and no clickable, focusable or
 * key handler), so the remote keeps driving whatever screen is underneath, BACK
 * included. That is also why it cannot be "pressed" to open the stream; the hint
 * line says where to find it instead.
 */
@Composable
fun TvGoLiveBanner(queue: GoLiveBannerQueue, modifier: Modifier = Modifier) {
    val pending by queue.pending.collectAsState()
    val current = pending.firstOrNull()

    // Keyed on the banner, so each one gets its own full dwell however the queue
    // around it changes.
    LaunchedEffect(current?.key) {
        val shown = current ?: return@LaunchedEffect
        delay(BANNER_DWELL_MS)
        queue.dismiss(shown)
    }

    AnimatedContent(
        targetState = current,
        transitionSpec = {
            PureTvTvMotion.HeaderEnter togetherWith PureTvTvMotion.HeaderExit
        },
        contentKey = { it?.key },
        modifier = modifier,
        label = "go-live-banner",
    ) { banner ->
        if (banner != null) BannerCard(banner)
    }
}

@Composable
private fun BannerCard(banner: GoLiveBanner) {
    val c = PureTvTvTheme.colors
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier = Modifier
            .widthIn(min = 320.dp, max = 520.dp)
            .clip(shape)
            .background(c.surfaceHigh)
            .border(1.dp, c.outlineVariant, shape)
            .padding(horizontal = 24.dp, vertical = 18.dp)
            // Read out by TalkBack when it appears, without moving focus to it.
            .semantics { liveRegion = LiveRegionMode.Polite },
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            TvLivePill(height = 28.dp, pulse = false)
            Text(
                banner.title,
                style = MaterialTheme.typography.titleLarge,
                color = c.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (banner.body.isNotBlank()) {
            Text(
                banner.body,
                style = MaterialTheme.typography.bodyLarge,
                color = c.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            "Find it in Following or Search",
            style = MaterialTheme.typography.bodyMedium,
            color = c.onSurfaceVariant,
            maxLines = 1,
        )
    }
}
