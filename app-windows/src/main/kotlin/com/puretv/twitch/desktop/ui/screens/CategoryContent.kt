package com.puretv.twitch.desktop.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.puretv.twitch.desktop.ui.CategoryViewModel
import com.puretv.twitch.desktop.ui.rememberDesktopViewModel
import com.puretv.twitch.desktop.ui.components.EditorialEmptyState
import com.puretv.twitch.desktop.ui.components.Kicker
import com.puretv.twitch.desktop.ui.components.PureIconButton
import com.puretv.twitch.desktop.ui.components.StreamCard
import com.puretv.twitch.desktop.ui.components.StreamCardSkeleton
import com.puretv.twitch.desktop.ui.theme.PureTvTheme
import org.koin.core.Koin
import org.koin.core.parameter.parametersOf

/**
 * Browse → category drill-down: the live streams in one game, built on the same
 * [StreamCard] grid Home uses. Tapping a stream routes to the channel page via
 * [onOpenChannel], exactly like Home and Search.
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
    val c = PureTvTheme.colors

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 40.dp, end = 40.dp, top = 40.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PureIconButton(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                onClick = onBack,
            )
            Spacer(Modifier.width(16.dp))
            Column {
                Kicker("Category")
                Spacer(Modifier.height(6.dp))
                Text(
                    state.gameName.ifBlank { gameName },
                    style = MaterialTheme.typography.headlineMedium,
                    color = c.textPrimary,
                )
            }
        }

        when {
            state.isLoading -> LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 260.dp),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(40.dp),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                items(8) { StreamCardSkeleton(modifier = Modifier.fillMaxWidth()) }
            }

            state.streams.isEmpty() -> EditorialEmptyState(
                kicker = "Category",
                title = "Nobody's live here",
                message = "No one is streaming this right now.",
                modifier = Modifier.fillMaxWidth().padding(top = 32.dp),
            )

            else -> LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 260.dp),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(40.dp),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                items(state.streams, key = { it.id }) { stream ->
                    StreamCard(
                        stream = stream,
                        onClick = { onOpenChannel(stream.userLogin) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}
