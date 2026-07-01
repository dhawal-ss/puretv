package com.puretv.twitch.android.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.puretv.twitch.android.ui.BrowseViewModel
import com.puretv.twitch.android.ui.components.EmptyState
import com.puretv.twitch.android.ui.components.ErrorState
import com.puretv.twitch.android.ui.components.GameTileSkeleton
import com.puretv.twitch.android.ui.theme.PureTvColors
import com.puretv.twitch.core.model.GameInfo
import org.koin.androidx.compose.koinViewModel

/** SECTION 06.4 — category/game browse grid (Helix `Get Top Games`). */
@Composable
fun BrowseScreen(onOpenCategory: (String) -> Unit) {
    val viewModel: BrowseViewModel = koinViewModel()
    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Browse", color = PureTvColors.TextPrimary) },
            )
        },
        containerColor = PureTvColors.Background,
    ) { padding ->
        when {
            state.isLoading && state.games.isEmpty() -> LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 140.dp),
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(12) { GameTileSkeleton() }
            }
            state.error != null && state.games.isEmpty() -> ErrorState(
                message = state.error!!,
                onRetry = viewModel::retry,
                modifier = Modifier.padding(padding),
            )
            state.games.isEmpty() -> EmptyState(
                title = "No categories",
                subtitle = "Couldn't find anything to browse right now.",
                modifier = Modifier.padding(padding),
            )
            else -> LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 140.dp),
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(state.games, key = { it.id }) { game ->
                    GameCard(game, onClick = { onOpenCategory(game.id) })
                }
            }
        }
    }
}

@Composable
private fun GameCard(game: GameInfo, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .background(PureTvColors.Surface)
            .padding(8.dp),
    ) {
        AsyncImage(
            model = game.boxArtUrl.replace("{width}", "285").replace("{height}", "380"),
            contentDescription = game.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxWidth().aspectRatio(3f / 4f).clip(RoundedCornerShape(6.dp)),
        )
        Text(
            text = game.name,
            style = MaterialTheme.typography.bodyLarge,
            color = PureTvColors.TextPrimary,
            maxLines = 1,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}
