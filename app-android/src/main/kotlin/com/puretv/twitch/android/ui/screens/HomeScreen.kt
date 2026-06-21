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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.puretv.twitch.android.data.db.WatchHistoryEntry
import com.puretv.twitch.android.ui.HomeViewModel
import com.puretv.twitch.android.ui.components.AdFreeChip
import com.puretv.twitch.android.ui.components.EmptyState
import com.puretv.twitch.android.ui.components.ErrorState
import com.puretv.twitch.android.ui.components.GameTile
import com.puretv.twitch.android.ui.components.StreamCard
import com.puretv.twitch.android.ui.components.StreamCardSkeleton
import com.puretv.twitch.android.ui.theme.PureTvColors
import org.koin.androidx.compose.koinViewModel

/**
 * SECTION 06.4: Home. A single adaptive grid (no overlapping scrollables): the
 * followed "Live now" rail, a categories rail, then the top-streams grid. Cards
 * carry real thumbnails. Tapping a card opens the stream.
 */
@Composable
fun HomeScreen(
    onOpenStream: (String) -> Unit,
    onOpenChannel: (String) -> Unit,
    onOpenBrowse: () -> Unit,
    onOpenCategory: (String) -> Unit,
    onOpenSearch: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenLogin: () -> Unit,
    viewModel: HomeViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("PureTV", color = PureTvColors.TextPrimary, style = MaterialTheme.typography.titleLarge)
                        // The brand badge of honor, always visible on the home screen.
                        AdFreeChip()
                    }
                },
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
        val isEmpty = state.followedLive.isEmpty() && state.games.isEmpty() &&
            state.topStreams.isEmpty() && state.continueWatching.isEmpty()
        when {
            state.isLoading && isEmpty -> LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 300.dp),
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(8) { StreamCardSkeleton() }
            }
            state.error != null && isEmpty -> ErrorState(
                message = state.error!!,
                onRetry = viewModel::refresh,
                modifier = Modifier.padding(padding),
            )
            isEmpty -> EmptyState(
                title = "Nothing to watch yet",
                subtitle = "We couldn't load any streams. Check your connection and try again.",
                modifier = Modifier.padding(padding),
            )
            else -> LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 300.dp),
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (state.continueWatching.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) { SectionHeader("Continue watching") }
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(state.continueWatching, key = { it.channelLogin }) { entry ->
                                ContinueWatchingCard(entry = entry, onClick = { onOpenStream(entry.channelLogin) })
                            }
                        }
                    }
                }

                if (state.followedLive.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) { SectionHeader("Live now") }
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(state.followedLive, key = { it.userLogin }) { s ->
                                StreamCard(
                                    stream = s,
                                    onClick = { onOpenStream(s.userLogin) },
                                    modifier = Modifier.width(300.dp),
                                )
                            }
                        }
                    }
                }

                if (state.games.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        SectionHeaderRow("Browse categories", actionLabel = "See all", onAction = onOpenBrowse)
                    }
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(state.games, key = { it.id }) { g ->
                                GameTile(game = g, onClick = { onOpenCategory(g.id) })
                            }
                        }
                    }
                }

                if (state.topStreams.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) { SectionHeader("Top streams") }
                    items(state.topStreams, key = { it.userLogin }) { s ->
                        StreamCard(stream = s, onClick = { onOpenStream(s.userLogin) })
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleLarge,
        color = PureTvColors.TextPrimary,
        modifier = Modifier.padding(top = 4.dp),
    )
}

@Composable
private fun SectionHeaderRow(title: String, actionLabel: String, onAction: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge, color = PureTvColors.TextPrimary)
        Text(
            actionLabel,
            style = MaterialTheme.typography.bodyMedium,
            color = PureTvColors.TwitchPurpleLight,
            modifier = Modifier.clickable(onClick = onAction),
        )
    }
}

@Composable
private fun ContinueWatchingCard(entry: WatchHistoryEntry, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(170.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .background(PureTvColors.Surface)
            .padding(14.dp),
    ) {
        Text(
            entry.channelDisplayName,
            style = MaterialTheme.typography.titleMedium,
            color = PureTvColors.TextPrimary,
            maxLines = 1,
        )
        Text(
            "Recently watched",
            style = MaterialTheme.typography.bodySmall,
            color = PureTvColors.TextSecondary,
            maxLines = 1,
        )
    }
}
