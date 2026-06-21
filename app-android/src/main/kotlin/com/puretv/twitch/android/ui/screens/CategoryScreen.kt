package com.puretv.twitch.android.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.puretv.twitch.android.ui.CategoryViewModel
import com.puretv.twitch.android.ui.components.EmptyState
import com.puretv.twitch.android.ui.components.ErrorState
import com.puretv.twitch.android.ui.components.FullScreenLoading
import com.puretv.twitch.android.ui.components.StreamCard
import com.puretv.twitch.android.ui.theme.PureTvColors
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * SECTION 06.4 — a single category (game): the channels live in it right now.
 * Reached by tapping a game tile on Home or Browse (previously a dead click).
 */
@Composable
fun CategoryScreen(gameId: String, onOpenStream: (String) -> Unit, onBack: () -> Unit) {
    val viewModel: CategoryViewModel = koinViewModel(parameters = { parametersOf(gameId) })
    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.gameName.ifBlank { "Category" }, color = PureTvColors.TextPrimary) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = PureTvColors.TextPrimary)
                    }
                },
            )
        },
        containerColor = PureTvColors.Background,
    ) { padding ->
        when {
            state.isLoading && state.streams.isEmpty() -> FullScreenLoading(Modifier.padding(padding))
            state.error != null && state.streams.isEmpty() -> ErrorState(
                message = state.error!!,
                onRetry = viewModel::retry,
                modifier = Modifier.padding(padding),
            )
            state.streams.isEmpty() -> EmptyState(
                title = "No one live",
                subtitle = "No channels are streaming this category right now.",
                modifier = Modifier.padding(padding),
            )
            else -> LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 300.dp),
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(state.streams, key = { it.id }) { stream ->
                    StreamCard(stream = stream, onClick = { onOpenStream(stream.userLogin) })
                }
            }
        }
    }
}
