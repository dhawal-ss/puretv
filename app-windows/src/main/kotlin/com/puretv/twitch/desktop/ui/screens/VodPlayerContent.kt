package com.puretv.twitch.desktop.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.puretv.twitch.core.model.StreamQuality
import com.puretv.twitch.desktop.player.VlcPlayerView
import com.puretv.twitch.desktop.player.formatTimecode
import com.puretv.twitch.desktop.ui.VodLaunch
import com.puretv.twitch.desktop.ui.VodPlayerViewModel
import com.puretv.twitch.desktop.ui.components.SegmentedControl
import com.puretv.twitch.desktop.ui.rememberDesktopViewModel
import com.puretv.twitch.desktop.ui.theme.PureTvTheme
import com.puretv.twitch.desktop.ui.theme.PureTvType
import org.koin.core.Koin
import org.koin.core.parameter.parametersOf

@Composable
fun VodPlayerContent(koin: Koin, launch: VodLaunch, onBack: () -> Unit) {
    val viewModel = rememberDesktopViewModel(launch.vodId) { koin.get<VodPlayerViewModel> { parametersOf(launch) } }
    val state by viewModel.state.collectAsState()
    val status by viewModel.status.collectAsState()
    val c = PureTvTheme.colors

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        Column(Modifier.fillMaxSize()) {
            // Top bar
            Row(
                modifier = Modifier.fillMaxWidth().background(c.surface).padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = c.textPrimary)
                }
                Text(
                    "Past broadcast",
                    style = MaterialTheme.typography.titleLarge,
                    color = c.textPrimary,
                    modifier = Modifier.weight(1f).padding(start = 4.dp),
                )
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(c.hairline))

            // Player surface
            Box(
                modifier = Modifier.fillMaxWidth().weight(1f).background(Color.Black),
                contentAlignment = Alignment.Center,
            ) {
                when {
                    status.error != null && !viewModel.player.isAvailable ->
                        Text(status.error!!, color = c.textSecondary, modifier = Modifier.padding(24.dp))
                    state.error != null ->
                        Text(state.error!!, color = c.textSecondary, modifier = Modifier.padding(24.dp))
                    else -> VlcPlayerView(vlcPlayer = viewModel.player, modifier = Modifier.fillMaxSize())
                }
                if (state.loading && state.error == null) Text("Loading…", color = c.textSecondary)
            }

            // Controls
            Box(Modifier.fillMaxWidth().height(1.dp).background(c.hairline))
            Column(Modifier.fillMaxWidth().background(c.surface).padding(horizontal = 12.dp, vertical = 8.dp)) {
                // Seek bar
                var dragMs by remember { mutableStateOf<Long?>(null) }
                val duration = status.durationMs.coerceAtLeast(1)
                val shown = dragMs ?: status.positionMs
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(formatTimecode(shown), style = PureTvType.data, color = c.textSecondary)
                    Slider(
                        value = (shown.toFloat() / duration.toFloat()).coerceIn(0f, 1f),
                        onValueChange = { f -> dragMs = (f * duration).toLong() },
                        onValueChangeFinished = { dragMs?.let { viewModel.seekTo(it) }; dragMs = null },
                        enabled = status.isSeekable,
                        modifier = Modifier.weight(1f).padding(horizontal = 10.dp),
                        colors = SliderDefaults.colors(
                            thumbColor = c.twitchPurple,
                            activeTrackColor = c.twitchPurple,
                            inactiveTrackColor = c.surfaceVariant,
                        ),
                    )
                    Text(formatTimecode(status.durationMs), style = PureTvType.data, color = c.textSecondary)
                }
                Spacer(Modifier.height(4.dp))
                // Transport
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = viewModel::togglePlayPause) {
                        Icon(
                            if (status.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = if (status.isPlaying) "Pause" else "Play",
                            tint = c.textPrimary,
                        )
                    }
                    Icon(Icons.AutoMirrored.Filled.VolumeUp, "Volume", tint = c.textSecondary, modifier = Modifier.size(18.dp))
                    Slider(
                        value = status.volume.toFloat(),
                        onValueChange = { viewModel.setVolume(it.toInt()) },
                        valueRange = 0f..100f,
                        modifier = Modifier.width(110.dp).padding(horizontal = 6.dp),
                        colors = SliderDefaults.colors(
                            thumbColor = c.twitchPurple,
                            activeTrackColor = c.twitchPurple,
                            inactiveTrackColor = c.surfaceVariant,
                        ),
                    )
                    Spacer(Modifier.weight(1f))
                    SegmentedControl(
                        options = StreamQuality.entries,
                        selected = state.quality,
                        label = { it.label },
                        onSelect = viewModel::setQuality,
                    )
                }
            }
        }
    }
}
