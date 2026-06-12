package com.puretv.twitch.desktop.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.puretv.twitch.core.model.StreamInfo
import com.puretv.twitch.desktop.ui.FollowCardState
import com.puretv.twitch.desktop.ui.HomeViewModel
import com.puretv.twitch.desktop.ui.rememberDesktopViewModel
import com.puretv.twitch.desktop.ui.components.FollowCard
import com.puretv.twitch.desktop.ui.components.SectionHeader
import com.puretv.twitch.desktop.ui.components.StreamCard
import com.puretv.twitch.desktop.ui.components.StreamCardSkeleton
import com.puretv.twitch.desktop.ui.theme.PureTvTheme
import org.koin.core.Koin

@Composable
fun HomeContent(koin: Koin, onOpenChannel: (String) -> Unit) {
    val viewModel = rememberDesktopViewModel { koin.get<HomeViewModel>() }
    val state by viewModel.state.collectAsState()
    val c = PureTvTheme.colors

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
    ) {
        Text("Home", style = MaterialTheme.typography.headlineMedium, color = c.textPrimary)
        Spacer(Modifier.height(24.dp))

        when {
            !state.isLoggedIn -> Text(
                "Sign in via the Account tab to see live channels.",
                style = MaterialTheme.typography.bodyLarge,
                color = c.textSecondary,
            )
            state.isLoading -> {
                SkeletonRail("Following")
                Spacer(Modifier.height(32.dp))
                SkeletonRail("Live Now")
            }
            else -> {
                if (state.following.isNotEmpty()) {
                    FollowRail("Following", state.following, onOpenChannel)
                    Spacer(Modifier.height(32.dp))
                }
                if (state.topStreams.isNotEmpty()) {
                    StreamRail("Live Now", state.topStreams, onOpenChannel)
                } else if (state.following.isEmpty()) {
                    Text(
                        "No live streams returned. Your session may have expired.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = c.textSecondary,
                    )
                }
            }
        }
    }
}

@Composable
private fun StreamRail(title: String, streams: List<StreamInfo>, onOpenChannel: (String) -> Unit) {
    Column {
        SectionHeader(title = title)
        Spacer(Modifier.height(14.dp))
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(end = 24.dp),
        ) {
            items(streams, key = { it.id }) { stream ->
                StreamCard(stream = stream, onClick = { onOpenChannel(stream.userLogin) })
            }
        }
    }
}

@Composable
private fun FollowRail(title: String, channels: List<FollowCardState>, onOpenChannel: (String) -> Unit) {
    Column {
        SectionHeader(title = title)
        Spacer(Modifier.height(14.dp))
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(end = 24.dp),
        ) {
            items(channels, key = { it.login }) { ch ->
                FollowCard(state = ch, onClick = { onOpenChannel(ch.login) })
            }
        }
    }
}

@Composable
private fun SkeletonRail(title: String) {
    Column {
        SectionHeader(title = title)
        Spacer(Modifier.height(14.dp))
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(end = 24.dp),
        ) {
            items(6) { StreamCardSkeleton() }
        }
    }
}
