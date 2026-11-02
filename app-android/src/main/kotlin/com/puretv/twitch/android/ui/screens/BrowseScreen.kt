package com.puretv.twitch.android.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.puretv.twitch.android.ui.BrowseViewModel
import com.puretv.twitch.android.ui.components.EmptyState
import com.puretv.twitch.android.ui.components.ErrorState
import com.puretv.twitch.android.ui.components.ExpressiveButton
import com.puretv.twitch.android.ui.components.ExpressiveButtonSize
import com.puretv.twitch.android.ui.components.ExpressiveButtonStyle
import com.puretv.twitch.android.ui.components.GameTile
import com.puretv.twitch.android.ui.components.GameTileSkeleton
import com.puretv.twitch.android.ui.components.PageTitle
import com.puretv.twitch.android.ui.theme.PureTvTheme
import com.puretv.twitch.android.ui.theme.itemMotion
import com.puretv.twitch.core.model.CategorySort
import kotlinx.coroutines.flow.distinctUntilChanged
import org.koin.androidx.compose.koinViewModel

/**
 * SECTION 06.4: category/game browse grid (Helix `Get Top Games`, paged), phone
 * Expressive edition. One scrolling grid, page title as its own full-span row,
 * same shape as [HomeScreen] and every other migrated list screen so the eye
 * never has to relearn where content starts.
 *
 * A sort control sits beside the title (Most viewers, Fewest viewers, A to Z,
 * saved in Settings). GameInfo carries no genre facet, so there is no filter
 * chip row: nothing real to wire one to.
 */
@Composable
fun BrowseScreen(onOpenCategory: (gameId: String, name: String) -> Unit) {
    val viewModel: BrowseViewModel = koinViewModel()
    val state by viewModel.state.collectAsState()
    val gridState = rememberLazyGridState()

    // Every category, not just the first page (issue #41): ask for the next page
    // once the last visible tile is within a dozen of the end. Keyed on the game
    // count too, so a page that lands with the viewer still at the end asks again.
    LaunchedEffect(gridState) {
        snapshotFlow {
            // Counted in games, not grid items: placeholder tiles coming and going
            // around a failed page must not re-ask for it in a loop.
            val games = state.games.size
            val last = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            games to (games > 0 && last >= games - LOAD_MORE_WITHIN)
        }
            .distinctUntilChanged()
            .collect { (_, nearEnd) -> if (nearEnd) viewModel.loadMore() }
    }

    // A new order starts at the top. Saveable, so coming back to this screen
    // (which recomposes it) is not mistaken for a change.
    var lastShownSort by rememberSaveable { mutableStateOf(state.shownSort.key) }
    LaunchedEffect(state.shownSort) {
        if (state.shownSort.key != lastShownSort) {
            lastShownSort = state.shownSort.key
            runCatching { gridState.scrollToItem(0) }
        }
    }

    LazyVerticalGrid(
        state = gridState,
        columns = GridCells.Adaptive(minSize = 140.dp),
        // The shell's Scaffold zeroes its own content insets so each screen owns
        // its status-bar clearance; PageTitle is the first thing on screen now
        // that the TopAppBar is gone, so it carries that padding instead.
        modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        fullSpan {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    PageTitle("Browse", modifier = Modifier.weight(1f))
                    BrowseSortButton(sort = state.sort, onSelect = viewModel::setSort)
                }
                // An order that needs every category says so while the rest load.
                if (state.sort != state.shownSort && state.isLoadingMore) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Loading every category to sort them…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = PureTvTheme.colors.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(16.dp))
            }
        }

        when {
            state.isLoading && state.games.isEmpty() -> items(12) { GameTileSkeleton() }
            state.error != null && state.games.isEmpty() -> fullSpan {
                ErrorState(message = state.error.orEmpty(), onRetry = viewModel::retry)
            }
            state.games.isEmpty() -> fullSpan {
                EmptyState(
                    title = "No categories",
                    subtitle = "Couldn't find anything to browse right now.",
                )
            }
            else -> {
                items(state.games, key = { it.id }) { game ->
                    GameTile(game = game, onClick = { onOpenCategory(game.id, game.name) }, modifier = Modifier.then(itemMotion()))
                }
                if (state.isLoadingMore) items(4) { GameTileSkeleton() }
            }
        }
    }
}

/** How close (in tiles) to the end of the grid the next page is asked for. */
private const val LOAD_MORE_WITHIN = 12

/** "Sort: Most viewers", opening the three orders; the picked one is in the primary colour. */
@Composable
private fun BrowseSortButton(sort: CategorySort, onSelect: (CategorySort) -> Unit) {
    val c = PureTvTheme.colors
    var open by remember { mutableStateOf(false) }
    Box {
        ExpressiveButton(
            text = "Sort: ${sort.label}",
            onClick = { open = true },
            style = ExpressiveButtonStyle.Tonal,
            size = ExpressiveButtonSize.Small,
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, containerColor = c.surfaceHigh) {
            CategorySort.entries.forEach { option ->
                DropdownMenuItem(
                    text = {
                        Text(
                            option.label,
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (option == sort) c.primary else c.onSurface,
                        )
                    },
                    onClick = {
                        open = false
                        if (option != sort) onSelect(option)
                    },
                )
            }
        }
    }
}

/** A full-width row inside the grid (page header, empty/error states). */
private fun LazyGridScope.fullSpan(content: @Composable () -> Unit) {
    item(span = { GridItemSpan(maxLineSpan) }) { content() }
}
