package com.puretv.twitch.tv.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.puretv.twitch.core.adblock.AdBlockStatus
import com.puretv.twitch.core.model.StreamQuality
import com.puretv.twitch.tv.ui.theme.PureTvTvTheme
import com.puretv.twitch.tv.ui.theme.PureTvTvType

/**
 * SECTION 07.4 [CRITICAL] — auto-hiding immersive playback chrome.
 *
 * Caller (`TvStreamScreen`) owns the visibility/auto-hide timer (3s of D-pad
 * inactivity per spec) and passes it in as [visible]; this composable is
 * purely presentational so the key-event plumbing stays in one place, and
 * both bars below live inside the SAME [AnimatedVisibility] as the timer, so
 * neither can end up shown while the other is hidden.
 *
 * Layout: header panel = back + title/viewer-count + a standing ad-block
 *         status pill; footer panel = play/pause + LIVE pill + the transient
 *         ad-block toast ([TvAdBlockPill]) + the quality row FAST_FORWARD/
 *         REWIND step through (Section 7.4), surfaced here as focusable
 *         chips so the same action is reachable without those dedicated keys.
 *
 * There is no settings action wired anywhere in [com.puretv.twitch.tv.ui.StreamViewModel]
 * or this composable's parameter list, so no settings control is rendered —
 * the quality row IS the playback settings surface this screen has to offer.
 */
@Composable
fun TvControlsOverlay(
    visible: Boolean,
    title: String,
    viewerCount: Long,
    isPlaying: Boolean,
    currentQuality: StreamQuality,
    adBlockStatus: AdBlockStatus,
    onBack: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onSelectQuality: (StreamQuality) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = PureTvTvTheme.colors
    val shapes = PureTvTvTheme.shapes

    AnimatedVisibility(visible = visible, enter = fadeIn(), exit = fadeOut(), modifier = modifier) {
        Box(modifier = Modifier.fillMaxSize().padding(32.dp)) {
            // Header: back, title/viewer-count. heightIn (not height) because a long
            // stream title must be free to wrap without pushing the pill out of the bar.
            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .heightIn(min = 88.dp)
                    .clip(shapes.paneShape)
                    .background(c.surfaceContainer)
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                TvExpressiveIconButton(
                    icon = ExpressiveIcons.Back,
                    contentDescription = "Back",
                    onClick = onBack,
                    style = TvButtonStyle.Tonal,
                    boxSize = 56.dp,
                    iconSize = 26.dp,
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        color = c.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "${formatTvViewerCount(viewerCount)} watching",
                        style = PureTvTvType.dataSmall,
                        color = c.onSurfaceVariant,
                    )
                }
                // A standing fact about the app (mirrors TvShieldPill's own intent),
                // so it stays up for the whole time the chrome is visible rather than
                // fading after 3s like the toast below — the two answer different
                // questions ("is ad-block on right now" vs "did it just do something").
                adBlockStandingLabel(adBlockStatus)?.let { label -> TvShieldPill(text = label) }
            }

            // Footer: transport controls + quality.
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .clip(shapes.paneShape)
                    .background(c.surfaceContainer)
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    TvExpressiveIconButton(
                        icon = if (isPlaying) ExpressiveIcons.Pause else ExpressiveIcons.Play,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        onClick = onTogglePlayPause,
                        style = TvButtonStyle.Filled,
                        boxSize = 72.dp,
                        iconSize = 34.dp,
                    )
                    TvLivePill()
                    Spacer(modifier = Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    // Same order stepQuality() in TvStreamScreen walks via FF/RW, so the
                    // row the viewer sees always agrees with what those keys do.
                    StreamQuality.entries.sortedBy { it.sortOrder }.forEach { quality ->
                        TvFilterChip(
                            label = quality.label,
                            selected = quality == currentQuality,
                            onClick = { onSelectQuality(quality) },
                        )
                    }
                }
            }
        }
    }
}

/** Maps ad-block state to the header's standing-fact label; null suppresses the
 *  pill entirely while the engine hasn't reported a state yet. */
private fun adBlockStandingLabel(status: AdBlockStatus): String? = when (status) {
    AdBlockStatus.AD_BLOCKED -> "AD BLOCKED"
    AdBlockStatus.AD_FILTERED -> "AD FILTERED"
    AdBlockStatus.AD_BLOCK_OFF -> "AD BLOCK OFF"
    AdBlockStatus.DISABLED -> "AD BLOCK OFF"
    AdBlockStatus.UNKNOWN -> null
}
