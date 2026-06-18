package com.puretv.twitch.desktop.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.puretv.twitch.desktop.ui.BrowseViewModel
import com.puretv.twitch.desktop.ui.rememberDesktopViewModel
import com.puretv.twitch.desktop.ui.components.CoverImage
import com.puretv.twitch.desktop.ui.components.EditorialEmptyState
import com.puretv.twitch.desktop.ui.components.Kicker
import com.puretv.twitch.desktop.ui.components.handCursor
import com.puretv.twitch.desktop.ui.components.hoverLift
import com.puretv.twitch.desktop.ui.theme.PureTvShape
import com.puretv.twitch.desktop.ui.theme.PureTvTheme
import org.koin.core.Koin

/**
 * Browse = a grid of category cover art, presented as a magazine contact sheet.
 * Tapping a cover opens the Category screen ([onOpenCategory]) listing that
 * game's live streams.
 *
 * Covers come from [com.puretv.twitch.core.model.GameInfo.boxArtUrl], a
 * `{width}x{height}` template (same shape as stream thumbnails). Box art is
 * portrait, so the 3:4 tiles match Twitch's native aspect ratio. GameInfo
 * exposes only id/name/boxArtUrl — no viewer count — so none is shown.
 */
@Composable
fun BrowseContent(koin: Koin, onOpenCategory: (gameId: String, gameName: String) -> Unit) {
    val viewModel = rememberDesktopViewModel { koin.get<BrowseViewModel>() }
    val state by viewModel.state.collectAsState()

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 168.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(40.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp),
        verticalArrangement = Arrangement.spacedBy(28.dp),
    ) {
        fullSpan {
            Column {
                Kicker("Categories")
                Spacer(Modifier.height(10.dp))
                Text(
                    "Browse",
                    style = MaterialTheme.typography.displayLarge,
                    color = PureTvTheme.colors.textPrimary,
                )
                Spacer(Modifier.height(28.dp))
            }
        }

        when {
            state.error != null -> fullSpan {
                EditorialEmptyState(
                    kicker = "Categories",
                    title = "Couldn't load categories",
                    message = state.error!!,
                    actionLabel = "Retry",
                    onAction = { viewModel.load() },
                )
            }
            state.games.isEmpty() -> fullSpan {
                EditorialEmptyState(
                    kicker = "Categories",
                    title = if (state.isLoading) "Loading categories…" else "Nothing to browse yet",
                    message = if (state.isLoading) "Fetching the top categories." else "Top categories will appear here in a moment.",
                )
            }
            else -> items(state.games, key = { it.id }) { game ->
                GameTile(
                    name = game.name,
                    boxArtUrl = game.boxArtUrl,
                    onClick = { onOpenCategory(game.id, game.name) },
                )
            }
        }
    }
}

/** A single box-art poster: 3:4 cover that lifts on hover; title goes accent on hover. */
@Composable
private fun GameTile(name: String, boxArtUrl: String, onClick: () -> Unit) {
    val c = PureTvTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()

    Column(
        modifier = Modifier
            .hoverLift(interaction, lift = 6.dp, scaleTo = 1f)
            .handCursor()
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
    ) {
        val art = boxArtUrl
            .replace("{width}", "285")
            .replace("{height}", "380")
        CoverImage(
            imageUrl = art.ifBlank { null },
            seed = name,
            contentDescription = name,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(3f / 4f)
                .clip(PureTvShape.md),
        )
        Text(
            name,
            style = MaterialTheme.typography.titleMedium,
            color = if (hovered) c.twitchPurple else c.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 12.dp),
        )
    }
}

/** A full-width row inside the grid (page header, empty state). */
private fun LazyGridScope.fullSpan(content: @Composable () -> Unit) {
    item(span = { GridItemSpan(maxLineSpan) }) { content() }
}
