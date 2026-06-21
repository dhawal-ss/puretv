package com.puretv.twitch.android.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.puretv.twitch.android.ui.SearchViewModel
import com.puretv.twitch.android.ui.theme.PureTvColors
import com.puretv.twitch.core.api.ChannelSearchResult
import org.koin.androidx.compose.koinViewModel

/** SECTION 06.4 — channel/game search with debounce-on-type (Section 3.3 Helix `Search Channels`). */
@Composable
fun SearchScreen(onOpenChannel: (String) -> Unit, onBack: () -> Unit) {
    val viewModel: SearchViewModel = koinViewModel()
    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    OutlinedTextField(
                        value = state.query,
                        onValueChange = viewModel::onQueryChange,
                        placeholder = { Text("Search channels & games", color = PureTvColors.TextMuted) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = PureTvColors.TextPrimary)
                    }
                },
            )
        },
        containerColor = PureTvColors.Background,
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (state.isSearching) {
                Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.Center) {
                    CircularProgressIndicator(color = PureTvColors.TwitchPurple)
                }
            }
            LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(state.results, key = { it.id }) { result -> SearchResultRow(result, onClick = { onOpenChannel(result.broadcaster_login) }) }
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
            .background(PureTvColors.Surface, androidx.compose.foundation.shape.RoundedCornerShape(10.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        androidx.compose.foundation.layout.Box(
            modifier = Modifier.size(40.dp).background(PureTvColors.SurfaceVariant, androidx.compose.foundation.shape.CircleShape),
        )
        Column {
            Text(result.display_name, style = MaterialTheme.typography.titleLarge, color = PureTvColors.TextPrimary)
            if (result.is_live) {
                Text("LIVE now", style = MaterialTheme.typography.bodyMedium, color = PureTvColors.Live)
            } else {
                Text(result.game_name.ifBlank { "Offline" }, style = MaterialTheme.typography.bodyMedium, color = PureTvColors.TextSecondary)
            }
        }
    }
}
