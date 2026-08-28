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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.puretv.twitch.core.model.GameInfo
import com.puretv.twitch.tv.ui.BrowseViewModel
import com.puretv.twitch.tv.ui.components.ExpressiveIcons
import com.puretv.twitch.tv.ui.components.TvButtonStyle
import com.puretv.twitch.tv.ui.components.TvExpressiveButton
import com.puretv.twitch.tv.ui.components.TvExpressiveIconButton
import com.puretv.twitch.tv.ui.components.TvPageTitle
import com.puretv.twitch.tv.ui.components.tvFocusClickable
import com.puretv.twitch.tv.ui.theme.PureTvTvTheme
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
    onOpenCategory: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: BrowseViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val c = PureTvTvTheme.colors

    // Re-pull categories each time the screen returns to the foreground so a
    // grid that failed to load on a stale token (the "categories disappear"
    // bug) recovers on its own the next time the viewer opens it.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(c.surface)
            .padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(28.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            TvExpressiveIconButton(
                icon = ExpressiveIcons.Back,
                contentDescription = "Back",
                onClick = onBack,
                boxSize = 56.dp,
                iconSize = 26.dp,
            )
            TvPageTitle("Browse")
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
                columns = GridCells.Adaptive(minSize = 200.dp),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
                contentPadding = PaddingValues(top = 4.dp, bottom = 40.dp),
            ) {
                items(state.games, key = { it.id }) { game ->
                    // Clicking a category opens its live-streams grid (TvCategoryScreen),
                    // keyed by the Helix game_id.
                    GameTile(game = game, onClick = { onOpenCategory(game.id) })
                }
            }
        }
    }
}

/**
 * A single box-art poster: 3:4 cover in a [Modifier.tvFocusClickable] surface
 * that morphs, grows and rings on D-pad focus, name below in `titleMedium`.
 */
@Composable
private fun GameTile(game: GameInfo, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = PureTvTvTheme.colors
    val shapes = PureTvTvTheme.shapes
    val interaction = remember { MutableInteractionSource() }

    Column(modifier = modifier.width(200.dp)) {
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
    }
}

/** Twitch box-art URLs are templates carrying `{width}`/`{height}`. */
private fun templatedUrl(url: String, width: Int, height: Int): String =
    url.replace("{width}", width.toString()).replace("{height}", height.toString())
