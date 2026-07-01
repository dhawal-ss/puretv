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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
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
    onOpenCategory: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: BrowseViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()

    // Re-pull categories each time the screen returns to the foreground so a
    // grid that failed to load on a stale token (the "categories disappear"
    // bug) recovers on its own the next time the viewer opens it.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }

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

        when {
            state.isLoading && state.games.isEmpty() ->
                Text("Loading…", style = MaterialTheme.typography.bodyLarge, color = PureTvTvColors.TextSecondary)

            state.error != null && state.games.isEmpty() ->
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(state.error!!, style = MaterialTheme.typography.bodyLarge, color = PureTvTvColors.Live)
                    Button(onClick = viewModel::retry) { Text("Try again") }
                }

            state.games.isEmpty() ->
                Text(
                    "No categories to show right now.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = PureTvTvColors.TextSecondary,
                )

            else -> LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 180.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(bottom = 32.dp),
            ) {
                items(state.games, key = { it.id }) { game ->
                    // Clicking a category opens its live-streams grid (TvCategoryScreen),
                    // keyed by the Helix game_id.
                    TvGameCard(game = game, onClick = { onOpenCategory(game.id) })
                }
            }
        }
    }
}
