package com.puretv.twitch.desktop.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.puretv.twitch.core.model.StreamInfo
import com.puretv.twitch.desktop.ui.CategoryViewModel
import com.puretv.twitch.desktop.ui.rememberDesktopViewModel
import com.puretv.twitch.desktop.ui.components.CoverImage
import com.puretv.twitch.desktop.ui.components.EditorialEmptyState
import com.puretv.twitch.desktop.ui.components.ExpressiveButtonStyle
import com.puretv.twitch.desktop.ui.components.ExpressiveCard
import com.puretv.twitch.desktop.ui.components.ExpressiveIconButton
import com.puretv.twitch.desktop.ui.components.ExpressiveIcons
import com.puretv.twitch.desktop.ui.components.LivePill
import com.puretv.twitch.desktop.ui.components.Skeleton
import com.puretv.twitch.desktop.ui.components.expressiveSurface
import com.puretv.twitch.desktop.ui.components.formatViewerCount
import com.puretv.twitch.desktop.ui.theme.PureTvTheme
import com.puretv.twitch.desktop.ui.theme.PureTvType
import org.koin.core.Koin
import org.koin.core.parameter.parametersOf

/**
 * Browse -> category drill-down: the live streams in one game, in the same
 * Cinematheque Expressive card as Home. Tapping a stream routes to the channel
 * page via [onOpenChannel], exactly like Home and Search.
 *
 * The whole screen is one lazy grid: a full-bleed 260dp header (art, back
 * button, box-art tile, name and live stats) sits as a full-span row so it
 * scrolls away with everything beneath it, then the stream grid follows.
 *
 * [CategoryUiState][com.puretv.twitch.desktop.ui.CategoryUiState] carries no
 * sort option and no "is this category followed" flag, and the game's own box
 * art never reaches this screen (only its id and name do), so the mockup's
 * sort-chip row and "Follow category" button are both left out here rather
 * than wired to nothing; the header's art falls back to the same deterministic
 * duotone every other missing-thumbnail surface in the app uses.
 */
@Composable
fun CategoryContent(
    koin: Koin,
    gameId: String,
    gameName: String,
    onOpenChannel: (String) -> Unit,
    onBack: () -> Unit,
) {
    val viewModel = rememberDesktopViewModel(gameId) {
        koin.get<CategoryViewModel> { parametersOf(gameId, gameName) }
    }
    val state by viewModel.state.collectAsState()
    val displayName = state.gameName.ifBlank { gameName }

    LazyVerticalGrid(
        columns = GridCells.Fixed(4),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 32.dp, end = 32.dp, bottom = 40.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        fullSpan {
            Column {
                CategoryHeader(gameId = gameId, displayName = displayName, streams = state.streams, onBack = onBack)
                // The header's own bottom edge already carries most of the 28dp gap
                // the mockup wants before the grid; verticalArrangement.spacedBy above
                // supplies the rest (16dp), so only the remainder is added here.
                Spacer(Modifier.height(12.dp))
            }
        }

        when {
            state.error != null -> fullSpan {
                EditorialEmptyState(
                    kicker = "Category",
                    title = "Couldn't load this category",
                    message = state.error!!,
                    actionLabel = "Retry",
                    onAction = { viewModel.load() },
                )
            }

            state.isLoading -> items(8) { CategoryStreamCardSkeleton() }

            state.streams.isEmpty() -> fullSpan {
                EditorialEmptyState(
                    kicker = "Category",
                    title = "Nobody's live here",
                    message = "No one is streaming this right now.",
                )
            }

            else -> items(state.streams, key = { it.id }) { stream ->
                CategoryStreamCard(stream = stream, onClick = { onOpenChannel(stream.userLogin) })
            }
        }
    }
}

/**
 * The 260dp full-bleed band: category art fading into [PureTvTheme.colors.surface]
 * at the bottom, a floating back button, and the box-art tile + title + stats +
 * live-channel count anchored to the header's own bottom edge.
 */
@Composable
private fun CategoryHeader(gameId: String, displayName: String, streams: List<StreamInfo>, onBack: () -> Unit) {
    val c = PureTvTheme.colors
    val totalViewers = remember(streams) { streams.sumOf { it.viewerCount } }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            // Cancels the grid's own 32dp horizontal contentPadding so the header can
            // run edge-to-edge under the pane's rounded top corners, the way every
            // other row in this grid does not.
            .breakoutHorizontal(32.dp)
            .height(260.dp),
    ) {
        CoverImage(imageUrl = null, seed = gameId, contentDescription = displayName, modifier = Modifier.fillMaxSize())
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0f to c.surface.copy(alpha = 0f),
                    0.55f to c.surface.copy(alpha = 0.55f),
                    1f to c.surface,
                ),
            ),
        )
        ExpressiveIconButton(
            icon = ExpressiveIcons.Back,
            contentDescription = "Back",
            onClick = onBack,
            style = ExpressiveButtonStyle.Tonal,
            modifier = Modifier.align(Alignment.TopStart).padding(20.dp),
        )
        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(start = 32.dp, end = 32.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            CategoryBoxArt(gameId = gameId, displayName = displayName)
            Column(modifier = Modifier.padding(start = 20.dp, bottom = 6.dp)) {
                Text(displayName, style = MaterialTheme.typography.displayMedium, color = c.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${formatViewerCount(totalViewers)} watching", style = PureTvType.data, color = c.onSurfaceVariant)
                    Text(" · ", style = PureTvType.data, color = c.outline)
                    Text("${streams.size} live channels", style = PureTvType.data, color = c.onSurfaceVariant)
                }
            }
        }
    }
}

/** The 132x176 poster tile at the header's foot; its border morphs on hover, art only. */
@Composable
private fun CategoryBoxArt(gameId: String, displayName: String) {
    val c = PureTvTheme.colors
    val shapes = PureTvTheme.shapes
    val interaction = remember { MutableInteractionSource() }

    Box(
        modifier = Modifier
            .size(width = 132.dp, height = 176.dp)
            .hoverable(interaction)
            .expressiveSurface(
                interaction = interaction,
                restRadius = shapes.card,
                hoverRadius = shapes.cardMorph,
                color = c.surfaceHigh,
                borderWidth = 3.dp,
                borderColor = c.surface,
            ),
    ) {
        CoverImage(imageUrl = null, seed = gameId, contentDescription = displayName, modifier = Modifier.fillMaxSize())
    }
}

/** A live stream card: 16:9 cover art, LIVE pill and viewer count over it, then name and title. */
@Composable
private fun CategoryStreamCard(stream: StreamInfo, onClick: () -> Unit) {
    val c = PureTvTheme.colors
    val shapes = PureTvTheme.shapes

    ExpressiveCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column {
            Box(modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(shapes.thumbShape)) {
                val thumbUrl = stream.thumbnailUrl.replace("{width}", "440").replace("{height}", "248")
                CoverImage(imageUrl = thumbUrl, seed = stream.userName, contentDescription = stream.title, modifier = Modifier.fillMaxSize())
                LivePill(modifier = Modifier.align(Alignment.TopStart).padding(10.dp))
                Text(
                    formatViewerCount(stream.viewerCount),
                    style = PureTvType.data,
                    color = c.onSurface,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(10.dp),
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(stream.userName, style = MaterialTheme.typography.titleMedium, color = c.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (stream.title.isNotBlank()) {
                Text(stream.title, style = MaterialTheme.typography.bodyMedium, color = c.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** Loading placeholder shaped like [CategoryStreamCard], for the initial fetch. */
@Composable
private fun CategoryStreamCardSkeleton() {
    val shapes = PureTvTheme.shapes
    Column(modifier = Modifier.fillMaxWidth().padding(10.dp)) {
        Skeleton(modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(shapes.thumbShape))
        Spacer(Modifier.height(10.dp))
        Skeleton(modifier = Modifier.fillMaxWidth(0.6f).height(16.dp).clip(shapes.smShape))
        Spacer(Modifier.height(6.dp))
        Skeleton(modifier = Modifier.fillMaxWidth(0.85f).height(14.dp).clip(shapes.smShape))
    }
}

/** A full-width row inside the grid (the header, empty/error states). */
private fun LazyGridScope.fullSpan(content: @Composable () -> Unit) {
    item(span = { GridItemSpan(maxLineSpan) }) { content() }
}

/**
 * Expands this item past the [LazyVerticalGrid]'s own horizontal `contentPadding`
 * by [inset] on each side, so a single full-span row (the header) can bleed edge
 * to edge while every other row still respects that padding.
 */
private fun Modifier.breakoutHorizontal(inset: Dp): Modifier = layout { measurable, constraints ->
    val extra = (inset * 2).roundToPx()
    val placeable = measurable.measure(constraints.copy(maxWidth = constraints.maxWidth + extra))
    layout(placeable.width, placeable.height) {
        placeable.placeRelative(-inset.roundToPx(), 0)
    }
}
