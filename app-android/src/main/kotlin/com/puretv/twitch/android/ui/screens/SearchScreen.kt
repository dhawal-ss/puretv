package com.puretv.twitch.android.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.puretv.twitch.android.ui.SearchViewModel
import com.puretv.twitch.android.ui.components.EmptyState
import com.puretv.twitch.android.ui.components.ErrorState
import com.puretv.twitch.android.ui.components.FullScreenLoading
import com.puretv.twitch.android.ui.components.streamThumbUrl
import com.puretv.twitch.android.ui.theme.PureTvColors
import com.puretv.twitch.core.api.ChannelSearchResult
import org.koin.androidx.compose.koinViewModel

/** SECTION 06.4: channel/game search with debounce-on-type (Helix Search Channels). */
@Composable
fun SearchScreen(onOpenChannel: (String) -> Unit) {
    val viewModel: SearchViewModel = koinViewModel()
    val state by viewModel.state.collectAsState()
    val focusManager = LocalFocusManager.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    OutlinedTextField(
                        value = state.query,
                        onValueChange = viewModel::onQueryChange,
                        placeholder = { Text("Search channels & games", color = PureTvColors.TextMuted) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        // Search is live (debounced), so the IME action saves the term
                        // to recents and dismisses the keyboard to reveal the results.
                        keyboardActions = KeyboardActions(onSearch = {
                            viewModel.commitSearch()
                            focusManager.clearFocus()
                        }),
                        modifier = Modifier.fillMaxWidth(),
                    )
                },
            )
        },
        containerColor = PureTvColors.Background,
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                state.error != null -> ErrorState(
                    message = state.error!!,
                    onRetry = { viewModel.onQueryChange(state.query) },
                )
                state.isSearching && state.results.isEmpty() -> FullScreenLoading()
                state.query.length < 2 -> if (state.recentSearches.isEmpty()) {
                    EmptyState(
                        title = "Search Twitch",
                        subtitle = "Find channels and games to watch ad-free.",
                    )
                } else {
                    RecentSearches(
                        recent = state.recentSearches,
                        onPick = viewModel::onQueryChange,
                        onClear = viewModel::clearHistory,
                    )
                }
                state.results.isEmpty() -> EmptyState(
                    title = "No results",
                    subtitle = "Nothing matches \"${state.query}\".",
                )
                else -> LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(state.results, key = { it.id }) { result ->
                        SearchResultRow(result, onClick = {
                            viewModel.commitSearch()
                            onOpenChannel(result.broadcaster_login)
                        })
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchResultRow(result: ChannelSearchResult, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(PureTvColors.Surface, RoundedCornerShape(10.dp))
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (result.is_live && result.thumbnail_url.isNotBlank()) {
            AsyncImage(
                model = streamThumbUrl(result.thumbnail_url, 320, 180),
                contentDescription = result.display_name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.width(104.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(6.dp)),
            )
        } else {
            Box(modifier = Modifier.size(48.dp).background(PureTvColors.SurfaceVariant, CircleShape))
        }
        Column {
            Text(result.display_name, style = MaterialTheme.typography.titleMedium, color = PureTvColors.TextPrimary, maxLines = 1)
            if (result.is_live) {
                Text("LIVE  ${result.game_name}", style = MaterialTheme.typography.bodyMedium, color = PureTvColors.Live, maxLines = 1)
            } else {
                Text("Offline", style = MaterialTheme.typography.bodyMedium, color = PureTvColors.TextSecondary)
            }
        }
    }
}

@Composable
private fun RecentSearches(recent: List<String>, onPick: (String) -> Unit, onClear: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Recent searches", style = MaterialTheme.typography.titleMedium, color = PureTvColors.TextPrimary)
            Text(
                "Clear",
                style = MaterialTheme.typography.bodyMedium,
                color = PureTvColors.TwitchPurpleLight,
                modifier = Modifier.clickable(onClick = onClear).padding(8.dp),
            )
        }
        LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(recent, key = { it }) { term ->
                Text(
                    term,
                    style = MaterialTheme.typography.bodyLarge,
                    color = PureTvColors.TextSecondary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onPick(term) }
                        .padding(vertical = 14.dp),
                )
            }
        }
    }
}
