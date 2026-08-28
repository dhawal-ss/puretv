package com.puretv.twitch.android.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.puretv.twitch.android.ui.BrowseViewModel
import com.puretv.twitch.android.ui.components.EmptyState
import com.puretv.twitch.android.ui.components.ErrorState
import com.puretv.twitch.android.ui.components.GameTile
import com.puretv.twitch.android.ui.components.GameTileSkeleton
import com.puretv.twitch.android.ui.components.PageTitle
import org.koin.androidx.compose.koinViewModel

/**
 * SECTION 06.4: category/game browse grid (Helix `Get Top Games`), phone
 * Expressive edition. One scrolling grid, page title as its own full-span row,
 * same shape as [HomeScreen] and every other migrated list screen so the eye
 * never has to relearn where content starts.
 *
 * GameInfo carries no viewer count or genre facet to filter by (same gap the
 * desktop screen documents), so there is no chip row here either: nothing real
 * to wire one to.
 */
@Composable
fun BrowseScreen(onOpenCategory: (String) -> Unit) {
    val viewModel: BrowseViewModel = koinViewModel()
    val state by viewModel.state.collectAsState()

    LazyVerticalGrid(
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
                PageTitle("Browse")
                Spacer(Modifier.height(16.dp))
            }
        }

        when {
            state.isLoading && state.games.isEmpty() -> items(12) { GameTileSkeleton() }
            state.error != null && state.games.isEmpty() -> fullSpan {
                ErrorState(message = state.error!!, onRetry = viewModel::retry)
            }
            state.games.isEmpty() -> fullSpan {
                EmptyState(
                    title = "No categories",
                    subtitle = "Couldn't find anything to browse right now.",
                )
            }
            else -> items(state.games, key = { it.id }) { game ->
                GameTile(game = game, onClick = { onOpenCategory(game.id) })
            }
        }
    }
}

/** A full-width row inside the grid (page header, empty/error states). */
private fun LazyGridScope.fullSpan(content: @Composable () -> Unit) {
    item(span = { GridItemSpan(maxLineSpan) }) { content() }
}
