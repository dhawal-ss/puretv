package com.puretv.twitch.desktop.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.puretv.twitch.desktop.ui.BrowseViewModel
import com.puretv.twitch.desktop.ui.rememberDesktopViewModel
import com.puretv.twitch.desktop.ui.theme.PureTvTheme
import org.koin.core.Koin

/**
 * Browse = a grid of category cover art. Tapping a cover opens the Category
 * screen ([onOpenCategory]) listing that game's live streams.
 *
 * Covers come from [com.puretv.twitch.core.model.GameInfo.boxArtUrl], a
 * `{width}x{height}` template (same shape as stream thumbnails). Box art is
 * portrait, so the 3:4 tiles match Twitch's native aspect ratio.
 */
@Composable
fun BrowseContent(koin: Koin, onOpenCategory: (gameId: String, gameName: String) -> Unit) {
    val viewModel = rememberDesktopViewModel { koin.get<BrowseViewModel>() }
    val state by viewModel.state.collectAsState()
    val c = PureTvTheme.colors

    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Text("Browse", style = MaterialTheme.typography.headlineMedium, color = c.textPrimary)
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 160.dp),
            modifier = Modifier.fillMaxSize().padding(top = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            items(state.games, key = { it.id }) { game ->
                Column(modifier = Modifier.clickable { onOpenCategory(game.id, game.name) }) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(3f / 4f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(c.surfaceVariant),
                        contentAlignment = Alignment.Center,
                    ) {
                        val art = game.boxArtUrl
                            .replace("{width}", "285")
                            .replace("{height}", "380")
                        if (art.isNotBlank()) {
                            AsyncImage(
                                model = art,
                                contentDescription = game.name,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                        } else {
                            // Fallback while box art is missing — the old initials tile.
                            Text(
                                game.name.take(2).uppercase(),
                                color = c.textMuted,
                                style = MaterialTheme.typography.headlineMedium,
                            )
                        }
                    }
                    Text(
                        game.name,
                        style = MaterialTheme.typography.bodyMedium,
                        color = c.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        }
    }
}
