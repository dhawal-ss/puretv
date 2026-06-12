package com.puretv.twitch.android.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.puretv.twitch.android.ui.components.LiveBadge
import com.puretv.twitch.android.ui.theme.PureTvColors
import com.puretv.twitch.core.model.StreamInfo
import org.koin.androidx.compose.koinViewModel

/**
 * SECTION 06.4 — Home: followed channels rail (if logged in) + featured/top
 * streams grid. Tapping a card opens [Routes.STREAM] for that channel.
 */
@Composable
fun HomeScreen(
    onOpenStream: (String) -> Unit,
    onOpenChannel: (String) -> Unit,
    onOpenBrowse: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenLogin: () -> Unit,
    viewModel: HomeViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("PureTV for Twitch", color = PureTvColors.TextPrimary) },
                actions = {
                    IconButton(onClick = onOpenSearch) {
                        Icon(Icons.Filled.Search, contentDescription = "Search", tint = PureTvColors.TextPrimary)
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings", tint = PureTvColors.TextPrimary)
                    }
                    IconButton(onClick = if (state.isLoggedIn) onOpenSettings else onOpenLogin) {
                        Icon(Icons.Filled.AccountCircle, contentDescription = "Account", tint = PureTvColors.TextPrimary)
                    }
                },
            )
        },
        containerColor = PureTvColors.Background,
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            if (state.followedLive.isNotEmpty()) {
                item {
                    Text("Live now", style = MaterialTheme.typography.titleLarge, color = PureTvColors.TextPrimary)
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(state.followedLive) { stream ->
                            StreamCard(stream = stream, onClick = { onOpenStream(stream.userLogin) })
                        }
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Browse categories", style = MaterialTheme.typography.titleLarge, color = PureTvColors.TextPrimary)
                    Text(
                        "See all",
                        style = MaterialTheme.typography.bodyMedium,
                        color = PureTvColors.TwitchPurpleLight,
                        modifier = Modifier.clickable(onClick = onOpenBrowse),
                    )
                }
            }

            item {
                Text("Top streams", style = MaterialTheme.typography.titleLarge, color = PureTvColors.TextPrimary)
            }
        }

        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 160.dp),
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            contentPadding = PaddingValues(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(state.topStreams) { stream ->
                StreamCard(stream = stream, onClick = { onOpenStream(stream.userLogin) })
            }
        }
    }
}

@Composable
private fun StreamCard(stream: StreamInfo, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .clickable(onClick = onClick)
            .background(PureTvColors.Surface, RoundedCornerShape(10.dp))
            .padding(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            LiveBadge(viewerCount = stream.viewerCount.toLong())
        }
        Text(
            text = stream.userName,
            style = MaterialTheme.typography.titleLarge,
            color = PureTvColors.TextPrimary,
            maxLines = 1,
        )
        Text(
            text = stream.gameName.ifBlank { "—" },
            style = MaterialTheme.typography.bodyMedium,
            color = PureTvColors.TextSecondary,
            maxLines = 1,
        )
        Text(
            text = stream.title,
            style = MaterialTheme.typography.bodyMedium,
            color = PureTvColors.TextMuted,
            maxLines = 2,
        )
    }
}
