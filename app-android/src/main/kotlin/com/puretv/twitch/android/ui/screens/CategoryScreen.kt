package com.puretv.twitch.android.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.puretv.twitch.android.ui.CategoryViewModel
import com.puretv.twitch.android.ui.components.EmptyState
import com.puretv.twitch.android.ui.components.ErrorState
import com.puretv.twitch.android.ui.components.ExpressiveButtonStyle
import com.puretv.twitch.android.ui.components.ExpressiveIconButton
import com.puretv.twitch.android.ui.components.ExpressiveIcons
import com.puretv.twitch.android.ui.components.StreamCard
import com.puretv.twitch.android.ui.components.StreamCardSkeleton
import com.puretv.twitch.android.ui.components.formatViewerCount
import com.puretv.twitch.android.ui.theme.PureTvTheme
import com.puretv.twitch.android.ui.theme.PureTvType
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * SECTION 06.4: a single category (game), the channels live in it right now.
 * Reached by tapping a game tile on Home or Browse.
 *
 * One scrolling grid: a full-bleed header (art, back button, name, live stats)
 * as a full-span row, then the stream grid. [CategoryUiState] carries no sort
 * option and no box art for the game itself (only id/name reach this screen),
 * so the header's art falls back to the same deterministic duotone every other
 * missing-thumbnail surface in the app would use, and there is no sort-chip row
 * here: nothing real to wire one to.
 */
@Composable
fun CategoryScreen(gameId: String, onOpenStream: (String) -> Unit, onBack: () -> Unit) {
    val viewModel: CategoryViewModel = koinViewModel(parameters = { parametersOf(gameId) })
    val state by viewModel.state.collectAsState()
    val displayName = state.gameName.ifBlank { "Category" }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 300.dp),
        modifier = Modifier.fillMaxSize().navigationBarsPadding(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        fullSpan {
            CategoryHeader(
                gameId = gameId,
                displayName = displayName,
                streamCount = state.streams.size,
                totalViewers = remember(state.streams) { state.streams.sumOf { it.viewerCount } },
                onBack = onBack,
            )
        }

        when {
            state.isLoading && state.streams.isEmpty() -> items(6) { StreamCardSkeleton() }
            state.error != null && state.streams.isEmpty() -> fullSpan {
                ErrorState(message = state.error!!, onRetry = viewModel::retry)
            }
            state.streams.isEmpty() -> fullSpan {
                EmptyState(
                    title = "No one live",
                    subtitle = "No channels are streaming this category right now.",
                )
            }
            else -> items(state.streams, key = { it.userLogin }) { stream ->
                StreamCard(stream = stream, onClick = { onOpenStream(stream.userLogin) })
            }
        }
    }
}

/**
 * The full-bleed band: duotone art fading into [PureTvTheme.colors.surface], a
 * floating back button, and the name + live stats anchored to the header's own
 * bottom edge. The art bleeds up under the status bar (edge-to-edge, targetSdk
 * 35 draws there by default); only the interactive back button gets pushed
 * clear of it. `heightIn(min = ...)` rather than a fixed height: a very short
 * game name should not leave the header looking empty, and a long one must
 * never be able to push the back button out through the top the way a fixed
 * height on desktop once let a long stream title push buttons out the bottom.
 */
@Composable
private fun CategoryHeader(
    gameId: String,
    displayName: String,
    streamCount: Int,
    totalViewers: Int,
    onBack: () -> Unit,
) {
    val c = PureTvTheme.colors

    Box(modifier = Modifier.fillMaxWidth()) {
        CategoryArt(seed = gameId, modifier = Modifier.matchParentSize())
        Box(
            Modifier.matchParentSize().background(
                Brush.verticalGradient(
                    0f to c.surface.copy(alpha = 0f),
                    0.6f to c.surface.copy(alpha = 0.6f),
                    1f to c.surface,
                ),
            ),
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 220.dp)
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            ExpressiveIconButton(
                icon = ExpressiveIcons.Back,
                contentDescription = "Back",
                onClick = onBack,
                style = ExpressiveButtonStyle.Tonal,
            )
            Column {
                Text(
                    displayName,
                    style = MaterialTheme.typography.displaySmall,
                    color = c.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(8.dp))
                Row {
                    Text("${formatViewerCount(totalViewers.toLong())} watching", style = PureTvType.data, color = c.onSurfaceVariant)
                    Text("  ·  ", style = PureTvType.data, color = c.outline)
                    Text("$streamCount live channels", style = PureTvType.data, color = c.onSurfaceVariant)
                }
            }
        }
    }
}

/**
 * Deterministic duotone fill, standing in for the game's box art which never
 * reaches this screen. Seeded from the game id so a given category always gets
 * the same tint rather than a random flash on every visit.
 */
@Composable
private fun CategoryArt(seed: String, modifier: Modifier = Modifier) {
    val hue = remember(seed) { (((seed.hashCode() % 360) + 360) % 360).toFloat() }
    val glow = Color.hsv((hue + 28f) % 360f, 0.55f, 0.34f)
    val base = Color.hsv(hue, 0.45f, 0.15f)
    val deep = Color.hsv((hue + 340f) % 360f, 0.50f, 0.06f)
    Box(
        modifier.background(
            Brush.linearGradient(
                0f to glow.copy(alpha = 0.85f),
                0.55f to base,
                1f to deep,
            ),
        ),
    )
}

/** A full-width row inside the grid (the header, empty/error states). */
private fun LazyGridScope.fullSpan(content: @Composable () -> Unit) {
    item(span = { GridItemSpan(maxLineSpan) }) { content() }
}
