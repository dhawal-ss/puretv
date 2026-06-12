package com.puretv.twitch.desktop.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
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
import com.puretv.twitch.desktop.ui.components.StreamCard
import com.puretv.twitch.desktop.ui.components.StreamCardSkeleton
import com.puretv.twitch.desktop.ui.theme.PureTvTheme
import org.koin.core.Koin
import org.koin.core.parameter.parametersOf

/**
 * Browse → category drill-down: the live streams in one game, newest-built on
 * top of the same [StreamCard] grid Home uses. Tapping a stream routes to the
 * channel page via [onOpenChannel], exactly like Home and Search.
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
            modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                Text(" Back")
            }
            Spacer(Modifier.width(16.dp))
            Text(
                state.gameName.ifBlank { gameName },
                style = MaterialTheme.typography.headlineMedium,
                color = c.textPrimary,
            )
        }

        when {
            state.isLoading -> LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 260.dp),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(24.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                items(8) { StreamCardSkeleton(modifier = Modifier.fillMaxWidth()) }
            }

            state.streams.isEmpty() -> Text(
                "No one is live in this category right now.",
                style = MaterialTheme.typography.bodyLarge,
                color = c.textSecondary,
                modifier = Modifier.padding(24.dp),
            )

            else -> LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 260.dp),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(24.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
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
