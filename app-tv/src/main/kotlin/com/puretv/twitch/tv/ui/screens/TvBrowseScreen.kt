package com.puretv.twitch.tv.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.puretv.twitch.tv.ui.BrowseViewModel
import com.puretv.twitch.tv.ui.components.TvGameCard
import com.puretv.twitch.tv.ui.theme.PureTvTvColors
import org.koin.androidx.compose.koinViewModel

/**
 * SECTION 07.2 — game/category grid (Section 5.4's `topGames`/`getGamesByName`
 * surfaced for browsing). D-pad navigates the [LazyVerticalGrid] natively;
 * each [TvGameCard] carries its own focus/scale treatment (Section 7.3).
 */
@Composable
fun TvBrowseScreen(
    onOpenChannel: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: BrowseViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PureTvTvColors.Background)
            .padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Button(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back")
            }
            Text(text = "Categories", style = MaterialTheme.typography.headlineLarge, color = PureTvTvColors.TextPrimary)
        }

        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 180.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(bottom = 32.dp),
        ) {
            items(state.games, key = { it.id }) { game ->
                // Browsing a category currently routes to channel search results
                // scoped by game name — `onOpenChannel` here is a placeholder hook
                // until a dedicated "streams for game" list view is wired (the
                // `core` `ChannelRepository`/`StreamRepository` already expose the
                // Helix `game_id` filter needed for that follow-up screen).
                TvGameCard(game = game, onClick = { onOpenChannel(game.name) })
            }
        }
    }
}
