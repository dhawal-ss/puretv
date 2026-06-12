package com.puretv.twitch.tv.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.puretv.twitch.tv.ui.ChannelViewModel
import com.puretv.twitch.tv.ui.components.TvLiveBadge
import com.puretv.twitch.tv.ui.theme.PureTvTvColors
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * SECTION 07.2 — channel profile: banner, avatar placeholder, description,
 * live status, and a prominent "Watch" CTA that's focused by default so the
 * D-pad confirm key immediately starts playback (Section 7.3's "primary
 * action gets initial focus" convention for detail screens).
 */
@Composable
fun TvChannelScreen(
    channelLogin: String,
    onWatch: () -> Unit,
    onBack: () -> Unit,
    viewModel: ChannelViewModel = koinViewModel(parameters = { parametersOf(channelLogin) }),
) {
    val state by viewModel.state.collectAsState()
    val watchFocusRequester = remember { FocusRequester() }

    LaunchedEffect(state.channel) {
        if (state.channel != null) runCatching { watchFocusRequester.requestFocus() }
    }

    Column(modifier = Modifier.fillMaxSize().background(PureTvTvColors.Background)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(280.dp)
                .background(PureTvTvColors.SurfaceVariant),
        ) {
            Button(onClick = onBack, modifier = Modifier.padding(24.dp).align(Alignment.TopStart)) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back")
            }
        }

        Column(modifier = Modifier.padding(32.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            val channel = state.channel
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    text = channel?.displayName ?: channelLogin,
                    style = MaterialTheme.typography.headlineLarge,
                    color = PureTvTvColors.TextPrimary,
                )
                if (state.isLive) TvLiveBadge(viewerCount = 0L)
            }

            if (!channel?.description.isNullOrBlank()) {
                Text(
                    text = channel!!.description,
                    style = MaterialTheme.typography.bodyLarge,
                    color = PureTvTvColors.TextSecondary,
                )
            }

            Button(
                onClick = onWatch,
                modifier = Modifier.focusRequester(watchFocusRequester),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Text(text = if (state.isLive) "Watch live" else "Watch channel")
                }
            }
        }
    }
}
