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
import com.puretv.twitch.tv.ui.CategoryViewModel
import com.puretv.twitch.tv.ui.components.TvStreamCard
import com.puretv.twitch.tv.ui.theme.PureTvTvColors
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * SECTION 07.2 / 06.4: a single category (game): the channels live in it right
 * now, as a D-pad-navigable grid of [TvStreamCard]s. Reached by clicking a game
 * tile in [TvBrowseScreen] (previously a dead placeholder route).
 */
@Composable
fun TvCategoryScreen(
    gameId: String,
    onOpenStream: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: CategoryViewModel = koinViewModel(parameters = { parametersOf(gameId) }),
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
            Text(
                text = state.gameName.ifBlank { "Category" },
                style = MaterialTheme.typography.headlineLarge,
                color = PureTvTvColors.TextPrimary,
            )
        }

        when {
            state.isLoading && state.streams.isEmpty() ->
                Text("Loading…", style = MaterialTheme.typography.bodyLarge, color = PureTvTvColors.TextSecondary)

            state.error != null && state.streams.isEmpty() ->
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(state.error!!, style = MaterialTheme.typography.bodyLarge, color = PureTvTvColors.Live)
                    Button(onClick = viewModel::retry) { Text("Try again") }
                }

            state.streams.isEmpty() ->
                Text(
                    "No channels are streaming this category right now.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = PureTvTvColors.TextSecondary,
                )

            else -> LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 220.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(bottom = 32.dp),
            ) {
                items(state.streams, key = { it.userLogin }) { stream ->
                    TvStreamCard(stream = stream, onClick = { onOpenStream(stream.userLogin) })
                }
            }
        }
    }
}
