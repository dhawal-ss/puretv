package com.puretv.twitch.desktop.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.puretv.twitch.desktop.ui.ChannelViewModel
import com.puretv.twitch.desktop.ui.rememberDesktopViewModel
import com.puretv.twitch.desktop.ui.theme.PureTvTheme
import org.koin.core.Koin
import org.koin.core.parameter.parametersOf

@Composable
fun ChannelContent(koin: Koin, channelLogin: String, onWatch: () -> Unit, onBack: () -> Unit) {
    val viewModel = rememberDesktopViewModel(channelLogin) {
        koin.get<ChannelViewModel> { parametersOf(channelLogin) }
    }
    val state by viewModel.state.collectAsState()
    val isFollowed by viewModel.isFollowed.collectAsState()
    val c = PureTvTheme.colors

    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                Text(" Back")
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.size(96.dp).clip(RoundedCornerShape(48.dp)).background(c.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    (state.channel?.displayName ?: channelLogin).take(1).uppercase(),
                    style = MaterialTheme.typography.headlineLarge,
                    color = c.textSecondary,
                )
            }
            Column(modifier = Modifier.padding(start = 20.dp)) {
                Text(
                    state.channel?.displayName ?: channelLogin,
                    style = MaterialTheme.typography.headlineMedium,
                    color = c.textPrimary,
                )
                Text(
                    if (state.isLive) "LIVE NOW" else "Offline",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (state.isLive) c.live else c.textMuted,
                )
            }
        }

        state.channel?.description?.takeIf { it.isNotBlank() }?.let { description ->
            Text(
                description,
                style = MaterialTheme.typography.bodyLarge,
                color = c.textSecondary,
                modifier = Modifier.padding(top = 16.dp),
            )
        }

        Row(
            modifier = Modifier.padding(top = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(
                onClick = onWatch,
                enabled = state.isLive,
            ) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null)
                Text(if (state.isLive) " Watch now" else " Channel is offline")
            }
            Spacer(Modifier.width(12.dp))
            OutlinedButton(
                onClick = viewModel::toggleFollow,
                enabled = state.channel != null,
            ) {
                Icon(if (isFollowed) Icons.Filled.Check else Icons.Filled.Add, contentDescription = null)
                Text(if (isFollowed) " Following" else " Follow")
            }
        }
    }
}
