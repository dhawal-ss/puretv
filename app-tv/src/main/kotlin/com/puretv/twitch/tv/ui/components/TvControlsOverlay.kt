package com.puretv.twitch.tv.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.puretv.twitch.core.adblock.AdBlockStatus
import com.puretv.twitch.core.model.StreamQuality

/**
 * SECTION 07.4 [CRITICAL] — auto-hiding immersive playback chrome.
 *
 * Caller (`TvStreamScreen`) owns the visibility/auto-hide timer (3s of D-pad
 * inactivity per spec) and passes it in as [visible]; this composable is
 * purely presentational so the key-event plumbing stays in one place.
 *
 * Layout: top bar = back + title/viewer-count + ad-block pill;
 *         bottom bar = play/pause + quality row (FAST_FORWARD/REWIND remap
 *         to quality up/down per Section 7.4, surfaced here as focusable
 *         chips so the same action is reachable without those dedicated keys).
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
    AnimatedVisibility(visible = visible, enter = fadeIn(), exit = fadeOut(), modifier = modifier) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Top scrim + bar
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.75f), Color.Transparent)))
                    .align(Alignment.TopCenter)
                    .padding(24.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Button(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                    Column {
                        Text(text = title, style = MaterialTheme.typography.titleLarge, color = Color.White)
                        Text(
                            text = "${formatTvViewerCount(viewerCount)} watching",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.7f),
                        )
                    }
                    androidx.compose.foundation.layout.Spacer(modifier = Modifier.weight(1f))
                    TvAdBlockPill(status = adBlockStatus)
                }
            }

            // Bottom scrim + transport + quality row
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))))
                    .align(Alignment.BottomCenter)
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Button(onClick = onTogglePlayPause) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                        )
                    }
                    Text(
                        text = "PLAY_PAUSE toggles playback · FAST_FORWARD/REWIND change quality",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.6f),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StreamQuality.entries.forEach { quality ->
                        TvQualityChip(
                            quality = quality,
                            selected = quality == currentQuality,
                            onClick = { onSelectQuality(quality) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TvQualityChip(quality: StreamQuality, selected: Boolean, onClick: () -> Unit) {
    Button(onClick = onClick) {
        Text(
            text = quality.name.lowercase().replaceFirstChar { it.uppercase() },
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) Color(0xFF9B5DE5) else Color.White,
        )
    }
}
