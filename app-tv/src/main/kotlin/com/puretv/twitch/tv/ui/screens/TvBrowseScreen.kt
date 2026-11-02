package com.puretv.twitch.tv.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.puretv.twitch.core.model.CategorySort
import com.puretv.twitch.core.model.GameInfo
import com.puretv.twitch.core.model.formatCategoryViewers
import com.puretv.twitch.tv.ui.BrowseViewModel
import com.puretv.twitch.tv.ui.components.ExpressiveIcons
import com.puretv.twitch.tv.ui.components.TvButtonStyle
import com.puretv.twitch.tv.ui.components.TvExpressiveButton
import com.puretv.twitch.tv.ui.components.TvExpressiveIconButton
import com.puretv.twitch.tv.ui.components.TvPageTitle
import com.puretv.twitch.tv.ui.components.tvFocusClickable
import com.puretv.twitch.tv.ui.theme.PureTvTvTheme
import kotlinx.coroutines.flow.distinctUntilChanged
import org.koin.androidx.compose.koinViewModel

/**
 * SECTION 07.2 — game/category grid (Section 5.4's `topGames`/`getGamesByName`
 * surfaced for browsing): [TvPageTitle] over a D-pad-navigable
 * [LazyVerticalGrid] of box-art tiles. Each tile is its own
 * [Modifier.tvFocusClickable] surface (Section 7.3) rather than the plain
 * `Card`-based `TvGameCard`, since that component still animates focus the
 * pre-Expressive way (its own `onFocusChanged` + `scale`, no morph/ring) and
 * is owned by another in-flight pass, not this one.
 */
@Composable
fun TvBrowseScreen(
    onOpenCategory: (gameId: String, name: String) -> Unit,
    onBack: () -> Unit,
    viewModel: BrowseViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val c = PureTvTvTheme.colors
    val gridState = rememberLazyGridState()

    // Every category, not just the first page (issue #41): D-pad focus scrolls the
    // grid, and once the last visible tile is within a dozen of the end the next
    // page is asked for. Keyed on the game count too, so a page that lands with
    // the viewer still at the end asks again.
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

    // Re-pull categories when the screen returns to the foreground so a grid
    // that failed to load on a stale token (the "categories disappear" bug)
    // recovers on its own. Only an empty, failed or stale list starts over:
    // Back from a category keeps the viewer's place.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onResume() }

    // A new order starts at the top. Saveable, so coming back to this screen
    // (which recomposes it) is not mistaken for a change.
    var lastShownSort by rememberSaveable { mutableStateOf(state.shownSort.key) }
    LaunchedEffect(state.shownSort) {
        if (state.shownSort.key != lastShownSort) {
            lastShownSort = state.shownSort.key
            runCatching { gridState.scrollToItem(0) }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(c.surface)
            .padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(28.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            TvExpressiveIconButton(
                icon = ExpressiveIcons.Back,
                contentDescription = "Back",
                onClick = onBack,
                boxSize = 56.dp,
                iconSize = 26.dp,
            )
            TvPageTitle("Browse", modifier = Modifier.weight(1f))
            // An order that needs every category says so while the rest load.
            if (state.sort != state.shownSort && state.isLoadingMore) {
                Text("Loading every category…", style = MaterialTheme.typography.bodyLarge, color = c.onSurfaceVariant)
            }
            // One button a remote can press through: Most viewers, Fewest viewers, A to Z.
            TvExpressiveButton(
                text = "Sort: ${state.sort.label}",
                onClick = { viewModel.setSort(state.sort.next()) },
                style = TvButtonStyle.Tonal,
            )
        }

        when {
            state.isLoading && state.games.isEmpty() ->
                Text("Loading…", style = MaterialTheme.typography.bodyLarge, color = c.onSurfaceVariant)

            state.error != null && state.games.isEmpty() ->
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(state.error!!, style = MaterialTheme.typography.bodyLarge, color = c.error)
                    TvExpressiveButton(text = "Try again", onClick = viewModel::retry, style = TvButtonStyle.Tonal)
                }

            state.games.isEmpty() ->
                Text(
                    "No categories to show right now.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = c.onSurfaceVariant,
                )

            else -> LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Adaptive(minSize = 200.dp),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
                contentPadding = PaddingValues(top = 4.dp, bottom = 40.dp),
            ) {
                items(state.games, key = { it.id }) { game ->
                    // Clicking a category opens its live-streams grid (TvCategoryScreen),
                    // keyed by the Helix game_id.
                    GameTile(game = game, onClick = { onOpenCategory(game.id, game.name) })
                }
            }
        }
    }
}

/** How close (in tiles) to the end of the grid the next page is asked for. */
private const val LOAD_MORE_WITHIN = 12

/** The order after this one, wrapping round, for the sort button. */
private fun CategorySort.next(): CategorySort = CategorySort.entries[(ordinal + 1) % CategorySort.entries.size]

/**
 * A single box-art poster: 3:4 cover in a [Modifier.tvFocusClickable] surface
 * that morphs, grows and rings on D-pad focus, name below in `titleMedium`.
 * Also the tile of Search's "Categories" row, at a smaller [width].
 */
@Composable
internal fun GameTile(game: GameInfo, onClick: () -> Unit, modifier: Modifier = Modifier, width: Dp = 200.dp) {
    val c = PureTvTvTheme.colors
    val shapes = PureTvTvTheme.shapes
    val interaction = remember { MutableInteractionSource() }

    Column(modifier = modifier.width(width)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(3f / 4f)
                .tvFocusClickable(
                    interaction = interaction,
                    onClick = onClick,
                    restRadius = shapes.card,
                    focusRadius = shapes.cardFocus,
                    color = c.surfaceHigh,
                ),
        ) {
            val art = templatedUrl(game.boxArtUrl, 285, 380)
            if (art.isNotBlank()) {
                AsyncImage(
                    model = art,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }
        }
        Text(
            text = game.name,
            style = MaterialTheme.typography.titleMedium,
            color = c.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 12.dp),
        )
        // Only known signed out (the anonymous directory carries it; Helix does not).
        game.viewers?.let { viewers ->
            Text(
                text = formatCategoryViewers(viewers),
                style = MaterialTheme.typography.bodyMedium,
                color = c.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

/** Twitch box-art URLs are templates carrying `{width}`/`{height}`. */
private fun templatedUrl(url: String, width: Int, height: Int): String =
    url.replace("{width}", width.toString()).replace("{height}", height.toString())
