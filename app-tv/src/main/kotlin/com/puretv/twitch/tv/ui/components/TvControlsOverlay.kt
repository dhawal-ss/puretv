package com.puretv.twitch.tv.ui.components

import com.puretv.twitch.tv.ui.theme.PureTvTvMotion
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
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
    chatVisible: Boolean = false,
    onToggleChat: () -> Unit = {},
    sleepLabel: String = "Sleep: Off",
    onCycleSleep: () -> Unit = {},
    alertsOn: Boolean = false,
    /** The short confirmation after a tap on the bell, shown under the title. */
    alertsNote: String? = null,
    onToggleAlerts: () -> Unit = {},
    /**
     * The audio levelling button's label, for example "Audio levelling: Off" or
     * "Audio levelling: Balanced"; null hides the button.
     */
    audioLevellingLabel: String? = null,
    onCycleAudioLevelling: () -> Unit = {},
    /** Where the screen puts focus when the controls appear (Play/Pause). */
    playPauseFocusRequester: FocusRequester? = null,
) {
    val c = PureTvTvTheme.colors
    val shapes = PureTvTvTheme.shapes

    AnimatedVisibility(visible = visible, enter = PureTvTvMotion.ControlsEnter, exit = PureTvTvMotion.ControlsExit, modifier = modifier) {
        Box(modifier = Modifier.fillMaxSize().padding(32.dp)) {
            // Header: back, title/viewer-count. heightIn (not height) because a long
            // stream title must be free to wrap without pushing the pill out of the bar.
            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .animateEnterExit(enter = PureTvTvMotion.HeaderEnter, exit = PureTvTvMotion.HeaderExit)
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
                    alertsNote?.let { Text(text = it, style = MaterialTheme.typography.bodyMedium, color = c.primary) }
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
                    .animateEnterExit(enter = PureTvTvMotion.FooterEnter, exit = PureTvTvMotion.FooterExit)
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
                        modifier = if (playPauseFocusRequester != null) Modifier.focusRequester(playPauseFocusRequester) else Modifier,
                    )
                    TvLivePill(pulse = false)
                    // Right-aligned when they fit. With chat beside the video the bar is
                    // much narrower, so the buttons scroll instead of being cut off: D-pad
                    // focus brings the next one into view.
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                        Row(
                            modifier = Modifier
                                .horizontalScroll(rememberScrollState())
                                // Room for the focused button's grow, which the scroll clips.
                                .padding(horizontal = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            // Reachable with the D-pad like every other control, so chat and the
                            // sleep timer need no dedicated keys.
                            TvExpressiveButton(
                                text = if (chatVisible) "Hide chat" else "Chat",
                                onClick = onToggleChat,
                                style = TvButtonStyle.Tonal,
                                icon = ExpressiveIcons.Chat,
                            )
                            TvExpressiveButton(
                                text = if (alertsOn) "Alerts on" else "Notify me",
                                onClick = onToggleAlerts,
                                style = TvButtonStyle.Tonal,
                                icon = if (alertsOn) ExpressiveIcons.NotifyOn else ExpressiveIcons.Notify,
                            )
                            TvExpressiveButton(
                                text = sleepLabel,
                                onClick = onCycleSleep,
                                style = TvButtonStyle.Tonal,
                            )
                            // The same setting as Settings > Even out loud and quiet audio and
                            // its strength, stepped by one button and applied live.
                            audioLevellingLabel?.let { label ->
                                TvExpressiveButton(text = label, onClick = onCycleAudioLevelling, style = TvButtonStyle.Tonal)
                            }
                        }
                    }
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
    AdBlockStatus.AD_FILTERED -> "SKIPPING AD"
    AdBlockStatus.AD_BLOCK_OFF -> "AD BLOCK OFF"
    AdBlockStatus.DISABLED -> "AD BLOCK OFF"
    AdBlockStatus.UNKNOWN -> null
}
