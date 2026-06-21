package com.puretv.twitch.android.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import android.content.res.Configuration
import com.puretv.twitch.android.player.PlayerSurface
import com.puretv.twitch.android.ui.StreamUiState
import com.puretv.twitch.android.ui.StreamViewModel
import com.puretv.twitch.android.ui.components.AdBlockPill
import com.puretv.twitch.android.ui.components.ChatPanel
import com.puretv.twitch.android.ui.components.LiveBadge
import com.puretv.twitch.android.ui.theme.PureTvColors
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * SECTION 06.4 [CRITICAL] — full playback experience.
 *
 *  Portrait (phone):  video pinned to 16:9 at the top, chat fills the rest
 *                     in a [Column] (matches the spec's mobile-first layout).
 *  Landscape/tablet:  video and chat sit side-by-side in a [Row], video
 *                     taking ~70% of the width so chat remains legible.
 *
 * The [AdBlockPill] floats over the top-right corner of the player and the
 * [LiveBadge] over the top-left, both per Section 10.3.
 */
@Composable
fun StreamScreen(channelLogin: String, onBack: () -> Unit) {
    val viewModel: StreamViewModel = koinViewModel(parameters = { parametersOf(channelLogin) })
    val state by viewModel.state.collectAsState()
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    if (isLandscape) {
        Row(modifier = Modifier.fillMaxSize().background(PureTvColors.Background)) {
            PlayerColumn(
                state = state,
                onBack = onBack,
                modifier = Modifier.weight(0.7f).fillMaxHeight(),
            )
            ChatPanel(
                messages = state.chatMessages,
                onSend = viewModel::sendChatMessage,
                modifier = Modifier.weight(0.3f).fillMaxHeight(),
            )
        }
    } else {
        Column(modifier = Modifier.fillMaxSize().background(PureTvColors.Background)) {
            PlayerColumn(state = state, onBack = onBack, modifier = Modifier.fillMaxWidth())
            ChatPanel(
                messages = state.chatMessages,
                onSend = viewModel::sendChatMessage,
                modifier = Modifier.weight(1f).fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun PlayerColumn(state: StreamUiState, onBack: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Box(modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f)) {
            PlayerSurface(playableUrl = state.playableUrl, modifier = Modifier.fillMaxSize())

            IconButton(onClick = onBack, modifier = Modifier.align(Alignment.TopStart).padding(8.dp)) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = androidx.compose.ui.graphics.Color.White)
            }

            AdBlockPill(
                status = state.adBlockStatus,
                onClick = {},
                modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
            )

            state.streamInfo?.let { info ->
                Row(modifier = Modifier.align(Alignment.BottomStart).padding(8.dp)) {
                    LiveBadge(viewerCount = info.viewerCount.toLong())
                }
            }
        }

        state.channel?.let { channel ->
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(channel.displayName, style = MaterialTheme.typography.headlineMedium, color = PureTvColors.TextPrimary)
                state.streamInfo?.let { info ->
                    Text(info.title, style = MaterialTheme.typography.bodyLarge, color = PureTvColors.TextSecondary)
                    Text(info.gameName, style = MaterialTheme.typography.bodyMedium, color = PureTvColors.TwitchPurpleLight)
                }
            }
        }
    }
}
