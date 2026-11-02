package com.puretv.twitch.desktop.ui.components

import com.puretv.twitch.core.audio.AudioLevelling
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.Alignment
import androidx.compose.foundation.background
import com.puretv.twitch.desktop.player.NativeLibs
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.puretv.twitch.core.model.PlaybackBackend
import com.puretv.twitch.core.model.StreamQuality
import com.puretv.twitch.core.model.UpscalingMode
import com.puretv.twitch.core.playback.SleepTimer
import com.puretv.twitch.core.playback.SleepTimerText
import com.puretv.twitch.desktop.player.RtxVideoStatus
import com.puretv.twitch.desktop.ui.theme.PureTvTheme

/**
 * The in-player "Playback" menu: one panel reused by the live stream and VOD
 * players (highlights are VODs). It consolidates the per-playback controls:
 * Resolution (source quality, live), Audio (the normalizer, live), Scaling (mpv GPU
 * upscaler, live), and Engine (restart-gated). Rendered in the player Column (NOT floating over the video,
 * the heavyweight AWT Canvas paints above Compose), styled to the Cinémathèque
 * system. Stateless: the caller owns current values + callbacks.
 *
 * @param scalingEnabled false on the VLC backend, which has no GPU upscaler here, so
 *   the Scaling section then shows guidance instead of a dead control.
 */
@Composable
fun PlayerSettingsMenu(
    currentQuality: StreamQuality,
    onQualitySelected: (StreamQuality) -> Unit,
    upscalingMode: UpscalingMode,
    onUpscalingSelected: (UpscalingMode) -> Unit,
    scalingEnabled: Boolean,
    backend: PlaybackBackend,
    onBackendSelected: (PlaybackBackend) -> Unit,
    modifier: Modifier = Modifier,
    // Live streams only (the Z key cycles the same presets): the sleep timer's minutes, 0 for Off.
    sleepMinutes: Int? = null,
    onSleepSelected: (Int) -> Unit = {},
    // NVIDIA RTX Video: null hides the section (no RTX card, or a player that can't use it).
    rtx: RtxMenuState? = null,
    // The engine running now differs from [backend]; with [onRestart] the menu offers to
    // restart right away instead of only saying it applies after a restart.
    engineNeedsRestart: Boolean = false,
    onRestart: (() -> Unit)? = null,
    // "Even out loud and quiet audio" (the same settings as in Settings): null hides the
    // section. [audioLevelling] is its strength, offered while it is on.
    audioNormalizer: Boolean? = null,
    onAudioNormalizerChange: (Boolean) -> Unit = {},
    audioLevelling: AudioLevelling = AudioLevelling.DEFAULT,
    onAudioLevellingSelected: (AudioLevelling) -> Unit = {},
) {
    val c = PureTvTheme.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(c.surface)
            .padding(horizontal = 24.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        PlayerMenuSection("Resolution") {
            SegmentedControl(StreamQuality.entries.toList(), currentQuality, { it.label }, onQualitySelected)
        }
        if (rtx != null) PlayerMenuSection("NVIDIA RTX Video") {
            RtxVideoMenuSection(
                status = rtx.status,
                superResolution = rtx.superResolution,
                onSuperResolutionChange = rtx.onSuperResolutionChange,
                hdr = rtx.hdr,
                onHdrChange = rtx.onHdrChange,
            )
        }
        if (audioNormalizer != null) PlayerMenuSection("Audio") {
            AudioNormalizerRow(audioNormalizer, onAudioNormalizerChange)
            if (audioNormalizer) {
                SegmentedControl(AudioLevelling.entries.toList(), audioLevelling, { it.label }, onAudioLevellingSelected)
                Text(audioLevelling.description, style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant)
            }
        }
        // Without mpv (macOS) there is no engine choice and no GPU upscaling to offer.
        if (NativeLibs.mpvSupported()) PlayerMenuSection("Scaling") {
            if (scalingEnabled) {
                SegmentedControl(UpscalingMode.entries.toList(), upscalingMode, { it.label }, onUpscalingSelected)
                Text(
                    "Sharp = general; Anime = animation. Hold X to compare against Off, F3 for live stats.",
                    style = MaterialTheme.typography.bodySmall,
                    color = c.onSurfaceVariant,
                )
            } else {
                Text(
                    "Switch engine to mpv for GPU upscaling.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = c.onSurfaceVariant,
                )
            }
        }
        if (NativeLibs.mpvSupported()) PlayerMenuSection("Engine") {
            SegmentedControl(PlaybackBackend.entries.toList(), backend, { it.label }, onBackendSelected)
            if (engineNeedsRestart && onRestart != null) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(
                        "Switching to ${backend.label} takes a quick restart.",
                        style = MaterialTheme.typography.bodySmall,
                        color = c.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    ExpressiveButton("Restart now", onClick = onRestart, size = ExpressiveButtonSize.Small)
                }
            } else {
                Text("Applies after restart.", style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant)
            }
        }
        if (sleepMinutes != null) {
            PlayerMenuSection("Sleep timer") {
                SegmentedControl(SleepTimer.PRESETS_MINUTES, sleepMinutes, { SleepTimerText.preset(it) }, onSleepSelected)
                Text("Stops playback when the time is up. Press Z to cycle.", style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant)
            }
        }
    }
}

/** What the menu's RTX Video section shows and changes. */
class RtxMenuState(
    val status: RtxVideoStatus,
    val superResolution: Boolean,
    val onSuperResolutionChange: (Boolean) -> Unit,
    // Null when the player can't do RTX Video HDR, which hides that switch.
    val hdr: Boolean?,
    val onHdrChange: (Boolean) -> Unit,
)

/**
 * The menu's audio normalizer switch. It (and the strength under it) changes the stream
 * that is playing: mpv swaps its audio filter, VLC starts the stream again on an
 * instance with that level (or without it), which takes a second or two.
 */
@Composable
private fun AudioNormalizerRow(checked: Boolean, onChange: (Boolean) -> Unit) {
    val c = PureTvTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("Even out loud and quiet audio", style = MaterialTheme.typography.titleSmall, color = c.onSurface)
            Text(
                "Turns down sudden loud moments and lifts quiet voices. Press A to switch it while watching.",
                style = MaterialTheme.typography.bodySmall,
                color = c.onSurfaceVariant,
                modifier = Modifier.widthIn(max = 640.dp),
            )
        }
        ExpressiveSwitch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun PlayerMenuSection(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Kicker(title)
        content()
    }
}
