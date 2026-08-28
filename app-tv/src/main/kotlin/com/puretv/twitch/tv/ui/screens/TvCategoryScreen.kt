package com.puretv.twitch.tv.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.puretv.twitch.core.model.StreamInfo
import com.puretv.twitch.tv.ui.CategoryViewModel
import com.puretv.twitch.tv.ui.components.ExpressiveIcons
import com.puretv.twitch.tv.ui.components.TvButtonStyle
import com.puretv.twitch.tv.ui.components.TvExpressiveButton
import com.puretv.twitch.tv.ui.components.TvExpressiveIconButton
import com.puretv.twitch.tv.ui.components.TvStreamCard
import com.puretv.twitch.tv.ui.components.formatTvViewerCount
import com.puretv.twitch.tv.ui.theme.PureTvTvTheme
import com.puretv.twitch.tv.ui.theme.PureTvTvType
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * SECTION 07.2 / 06.4: a single category (game): the channels live in it right
 * now, as a D-pad-navigable grid of [TvStreamCard]s. Reached by tapping a game
 * tile in [TvBrowseScreen].
 *
 * [CategoryUiState][com.puretv.twitch.tv.ui.CategoryUiState] carries no box art
 * for the game itself (only its id/name and the live streams in it), so the
 * header's backdrop uses the first live stream's own thumbnail — real data
 * rather than an invented placeholder — fading into [PureTvTvTheme.colors.surface].
 */
@Composable
fun TvCategoryScreen(
    gameId: String,
    onOpenStream: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: CategoryViewModel = koinViewModel(parameters = { parametersOf(gameId) }),
) {
    val state by viewModel.state.collectAsState()
    val c = PureTvTvTheme.colors
    val displayName = state.gameName.ifBlank { "Category" }

    Column(modifier = Modifier.fillMaxSize().background(c.surface)) {
        CategoryHeader(displayName = displayName, streams = state.streams, onBack = onBack)

        when {
            state.isLoading && state.streams.isEmpty() ->
                Box(Modifier.fillMaxSize().padding(32.dp)) {
                    Text("Loading…", style = MaterialTheme.typography.bodyLarge, color = c.onSurfaceVariant)
                }

            state.error != null && state.streams.isEmpty() ->
                Column(modifier = Modifier.padding(32.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(state.error!!, style = MaterialTheme.typography.bodyLarge, color = c.error)
                    TvExpressiveButton(text = "Try again", onClick = viewModel::retry, style = TvButtonStyle.Tonal)
                }

            state.streams.isEmpty() ->
                Box(Modifier.fillMaxSize().padding(32.dp)) {
                    Text(
                        "No channels are streaming this category right now.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = c.onSurfaceVariant,
                    )
                }

            else -> LazyVerticalGrid(
                modifier = Modifier.fillMaxSize(),
                columns = GridCells.Adaptive(minSize = 220.dp),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
                contentPadding = PaddingValues(start = 32.dp, end = 32.dp, top = 24.dp, bottom = 40.dp),
            ) {
                items(state.streams, key = { it.userLogin }) { stream ->
                    TvStreamCard(stream = stream, onClick = { onOpenStream(stream.userLogin) })
                }
            }
        }
    }
}

/**
 * The full-bleed header band: the top live stream's art (when there is one)
 * fading down into [PureTvTvTheme.colors.surface], a floating back button, the
 * category name in `displayMedium`, then a single mono stats line.
 */
@Composable
private fun CategoryHeader(displayName: String, streams: List<StreamInfo>, onBack: () -> Unit) {
    val c = PureTvTvTheme.colors
    val totalViewers = remember(streams) { streams.sumOf { it.viewerCount } }
    val artUrl = remember(streams) { streams.firstOrNull { it.thumbnailUrl.isNotBlank() }?.thumbnailUrl }

    Box(modifier = Modifier.fillMaxWidth().height(300.dp)) {
        Box(Modifier.fillMaxSize().background(c.surfaceHigh))
        artUrl?.let { url ->
            AsyncImage(
                model = templatedUrl(url, 1280, 720),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0f to c.surface.copy(alpha = 0f),
                    0.6f to c.surface.copy(alpha = 0.75f),
                    1f to c.surface,
                ),
            ),
        )
        TvExpressiveIconButton(
            icon = ExpressiveIcons.Back,
            contentDescription = "Back",
            onClick = onBack,
            modifier = Modifier.align(Alignment.TopStart).padding(24.dp),
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(horizontal = 40.dp, vertical = 32.dp),
        ) {
            Text(
                text = displayName,
                style = MaterialTheme.typography.displayMedium,
                color = c.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = "${formatTvViewerCount(totalViewers.toLong())} watching · ${streams.size} live channels",
                style = PureTvTvType.data,
                color = c.onSurfaceVariant,
            )
        }
    }
}

/** Twitch thumbnail URLs are templates carrying `{width}`/`{height}`. */
private fun templatedUrl(url: String, width: Int, height: Int): String =
    url.replace("{width}", width.toString()).replace("{height}", height.toString())
