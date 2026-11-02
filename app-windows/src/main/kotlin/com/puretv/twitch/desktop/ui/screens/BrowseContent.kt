package com.puretv.twitch.desktop.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.puretv.twitch.core.model.CategorySort
import com.puretv.twitch.core.model.formatCategoryViewers
import com.puretv.twitch.desktop.ui.BrowseViewModel
import com.puretv.twitch.desktop.ui.components.ExpressiveFilterChip
import com.puretv.twitch.desktop.ui.theme.PureTvType
import com.puretv.twitch.desktop.ui.rememberDesktopViewModel
import com.puretv.twitch.desktop.ui.WindowDestination
import com.puretv.twitch.desktop.ui.components.openInNewWindowMenu
import com.puretv.twitch.desktop.ui.components.CoverImage
import com.puretv.twitch.desktop.ui.components.EditorialEmptyState
import com.puretv.twitch.desktop.ui.components.PageTitle
import com.puretv.twitch.desktop.ui.components.expressiveClickable
import com.puretv.twitch.desktop.ui.components.expressiveSurface
import com.puretv.twitch.desktop.ui.theme.PureTvMotion
import com.puretv.twitch.desktop.ui.theme.PureTvTheme
import kotlinx.coroutines.flow.distinctUntilChanged
import org.koin.core.Koin

/**
 * Browse = a grid of category cover art, presented as a magazine contact sheet.
 * Tapping a cover opens the Category screen ([onOpenCategory]) listing that
 * game's live streams.
 *
 * Covers come from [com.puretv.twitch.core.model.GameInfo.boxArtUrl], a
 * `{width}x{height}` template (same shape as stream thumbnails). Box art is
 * portrait, so the 3:4 tiles match Twitch's native aspect ratio. GameInfo
 * exposes only id/name/boxArtUrl, no viewer count and no genre/category facet
 * to filter by, so this screen has no chip row and no per-card viewer count:
 * there is nothing real to put in either.
 */
@Composable
fun BrowseContent(koin: Koin, onOpenCategory: (gameId: String, gameName: String) -> Unit) {
    val viewModel = rememberDesktopViewModel { koin.get<BrowseViewModel>() }
    val state by viewModel.state.collectAsState()
    val gridState = rememberLazyGridState()
    // Two rows from the end, ask for the next page (signed in; see BrowseViewModel).
    LaunchedEffect(gridState) {
        snapshotFlow {
            val info = gridState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            info.totalItemsCount > 0 && last >= info.totalItemsCount - 12
        }.distinctUntilChanged().collect { nearEnd -> if (nearEnd) viewModel.loadMore() }
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(6),
        state = gridState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 32.dp, end = 32.dp, top = 36.dp, bottom = 40.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        fullSpan {
            Column {
                PageTitle("Browse")
                Spacer(Modifier.height(20.dp))
                // How to order the categories: three plain choices, always in view at the top.
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CategorySort.entries.forEach { sort ->
                        ExpressiveFilterChip(
                            label = sort.label,
                            selected = state.sort == sort,
                            onClick = { viewModel.setSort(sort) },
                        )
                    }
                }
                Spacer(Modifier.height(24.dp))
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
            else -> {
                items(state.games, key = { it.id }) { game ->
                    GameTile(
                        name = game.name,
                        boxArtUrl = game.boxArtUrl,
                        onClick = { onOpenCategory(game.id, game.name) },
                        gameId = game.id,
                        viewers = game.viewers,
                    )
                }
                if (state.isLoadingMore) {
                    fullSpan {
                        Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = PureTvTheme.colors.primary, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
                        }
                    }
                }
            }
        }
    }
}

/**
 * A single box-art poster: 3:4 cover that rounds out and lifts on hover, name
 * below in [titleMedium][MaterialTheme.typography]. Also used for Home's
 * signed-out category shelf. One interaction source
 * drives both the card's lift and the art's radius morph, so hovering
 * anywhere on the tile, art or title, animates the whole thing together.
 */
@Composable
internal fun GameTile(
    name: String,
    boxArtUrl: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    gameId: String? = null,
    // People watching, when Twitch said (signed out); shown under the name like twitch.tv.
    viewers: Long? = null,
) {
    val c = PureTvTheme.colors
    val shapes = PureTvTheme.shapes
    val interaction = remember { MutableInteractionSource() }

    Column(
        modifier = modifier.then(
            if (gameId == null) Modifier
            else Modifier.openInNewWindowMenu(WindowDestination.Category(gameId, name), "Open category in new window"),
        ).expressiveClickable(
            interaction = interaction,
            onClick = onClick,
            restRadius = 0.dp,
            hoverRadius = 0.dp,
            lift = 6.dp,
            pressScale = PureTvMotion.CardPressScale,
        ),
    ) {
        val art = boxArtUrl
            .replace("{width}", "285")
            .replace("{height}", "380")
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(3f / 4f)
                .expressiveSurface(
                    interaction = interaction,
                    restRadius = shapes.card,
                    hoverRadius = shapes.cardMorph,
                ),
        ) {
            CoverImage(
                imageUrl = art.ifBlank { null },
                seed = name,
                contentDescription = name,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Text(
            name,
            style = MaterialTheme.typography.titleMedium,
            color = c.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 12.dp),
        )
        if (viewers != null) {
            Text(
                formatCategoryViewers(viewers),
                style = PureTvType.dataSmall,
                color = c.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

/** A full-width row inside the grid (page header, empty state). */
private fun LazyGridScope.fullSpan(content: @Composable () -> Unit) {
    item(span = { GridItemSpan(maxLineSpan) }) { content() }
}
