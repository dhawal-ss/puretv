package com.puretv.twitch.desktop.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.puretv.twitch.core.perf.TimingLog
import com.puretv.twitch.desktop.player.WindowPlayback
import com.puretv.twitch.desktop.ui.theme.PureTvTheme
import com.puretv.twitch.desktop.ui.theme.PureTvType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** How long the gate stays a plain surface before it says what it is waiting for: a quick build never flashes. */
private const val STARTING_LABEL_DELAY_MS = 400L

/**
 * Shows [content] once [playback]'s player is built, and a "Starting the video player"
 * state until then.
 *
 * The main window's player is built in the background once the window is up. libVLC
 * loads its plugins then, which right after a boot has taken many seconds. A screen
 * that read the player before it was built waited for it on the UI thread, so the
 * whole window stopped responding until the stream appeared. The wait now happens
 * here, off the UI thread, and the viewer can go back while it lasts. A window's own
 * player is built before its screens exist, so there the gate opens at once.
 *
 * [whileStarting] runs once if the player is not ready yet: the stream screen uses it
 * to start resolving the stream at the same time.
 */
@Composable
fun PlayerGate(
    playback: WindowPlayback,
    onBack: () -> Unit,
    whileStarting: () -> Unit = {},
    content: @Composable () -> Unit,
) {
    var ready by remember(playback) { mutableStateOf(playback.isReady) }
    if (ready) {
        content()
        return
    }
    LaunchedEffect(playback) {
        whileStarting()
        val started = System.nanoTime()
        // A build that failed leaves the player to report it (VLC missing, and so on).
        withContext(Dispatchers.IO) { runCatching { playback.prepare() } }
        // The stream screen's own timings start after this, so the wait is logged here.
        TimingLog.log("stream.player_wait", (System.nanoTime() - started) / 1_000_000)
        ready = true
    }
    PlayerStarting(onBack)
}

@Composable
private fun PlayerStarting(onBack: () -> Unit) {
    val c = PureTvTheme.colors
    var showLabel by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(STARTING_LABEL_DELAY_MS)
        showLabel = true
    }
    Column(
        modifier = Modifier.fillMaxSize().background(c.surface),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (!showLabel) return@Column
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator(color = c.primary, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
            Text("Starting the video player...", style = PureTvType.data, color = c.onSurfaceVariant)
        }
        Spacer(Modifier.height(20.dp))
        ExpressiveButton(text = "Back", onClick = onBack, style = ExpressiveButtonStyle.Tonal, size = ExpressiveButtonSize.Medium)
    }
}
