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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.puretv.twitch.android.ui.theme.PureTvColors
import com.puretv.twitch.core.model.GameInfo
import org.koin.androidx.compose.koinViewModel

/** SECTION 06.4 — category/game browse grid (Helix `Get Top Games`). */
@Composable
fun BrowseScreen(onOpenChannel: (String) -> Unit, onBack: () -> Unit) {
    val viewModel: BrowseViewModel = koinViewModel()
    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Browse", color = PureTvColors.TextPrimary) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = PureTvColors.TextPrimary)
                    }
                },
            )
        },
        containerColor = PureTvColors.Background,
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 140.dp),
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(state.games) { game -> GameCard(game) }
        }
    }
}

@Composable
private fun GameCard(game: GameInfo) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { /* category drill-down: list channels playing this game */ }
            .background(PureTvColors.Surface, RoundedCornerShape(8.dp))
            .padding(8.dp),
    ) {
        androidx.compose.foundation.layout.Box(
            modifier = Modifier.fillMaxWidth().aspectRatio(3f / 4f).background(PureTvColors.SurfaceVariant, RoundedCornerShape(6.dp)),
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
