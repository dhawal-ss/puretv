package com.puretv.twitch.android.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.unit.dp
import com.puretv.twitch.android.ui.HomeViewModel
import com.puretv.twitch.android.ui.components.GameTile
import com.puretv.twitch.android.ui.components.StreamCard
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
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 300.dp),
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (state.followedLive.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) { SectionHeader("Live now") }
                item(span = { GridItemSpan(maxLineSpan) }) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(state.followedLive, key = { it.id }) { s ->
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
                            GameTile(game = g, onClick = onOpenBrowse)
                        }
                    }
                }
            }

            item(span = { GridItemSpan(maxLineSpan) }) { SectionHeader("Top streams") }
            items(state.topStreams, key = { it.id }) { s ->
                StreamCard(stream = s, onClick = { onOpenStream(s.userLogin) })
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
