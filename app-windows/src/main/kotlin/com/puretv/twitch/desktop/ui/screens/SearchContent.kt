package com.puretv.twitch.desktop.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.puretv.twitch.core.api.ChannelSearchResult
import com.puretv.twitch.desktop.ui.SearchViewModel
import com.puretv.twitch.desktop.ui.rememberDesktopViewModel
import com.puretv.twitch.desktop.ui.theme.PureTvTheme
import org.koin.core.Koin

@Composable
fun SearchContent(koin: Koin, onOpenChannel: (String) -> Unit) {
    val viewModel = rememberDesktopViewModel { koin.get<SearchViewModel>() }
    val state by viewModel.state.collectAsState()
    val c = PureTvTheme.colors

    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Text("Search", style = MaterialTheme.typography.headlineMedium, color = c.textPrimary)

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(c.surfaceVariant)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            if (state.query.isEmpty()) {
                Text("Search channels…", color = c.textMuted, style = MaterialTheme.typography.bodyLarge)
            }
            BasicTextField(
                value = state.query,
                onValueChange = viewModel::onQueryChange,
                singleLine = true,
                textStyle = TextStyle(color = c.textPrimary, fontSize = MaterialTheme.typography.bodyLarge.fontSize),
                cursorBrush = SolidColor(c.twitchPurple),
                modifier = Modifier.fillMaxWidth(),
            )
        }

        if (state.isSearching) {
            CircularProgressIndicator(color = c.twitchPurple, modifier = Modifier.padding(top = 24.dp))
        }

        LazyColumn(modifier = Modifier.fillMaxSize().padding(top = 16.dp)) {
            items(state.results, key = { it.id }) { result ->
                SearchResultRow(result = result, onClick = { onOpenChannel(result.broadcaster_login) })
            }
        }
    }
}

@Composable
private fun SearchResultRow(result: ChannelSearchResult, onClick: () -> Unit) {
    val c = PureTvTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(48.dp).clip(RoundedCornerShape(24.dp)).background(c.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Text(result.display_name.take(1).uppercase(), color = c.textSecondary)
        }
        Column(modifier = Modifier.padding(start = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(result.display_name, style = MaterialTheme.typography.titleMedium, color = c.textPrimary)
                if (result.is_live) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(c.live)
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    ) {
                        Text("LIVE", style = MaterialTheme.typography.labelSmall, color = c.textPrimary)
                    }
                }
            }
            if (result.game_name.isNotBlank()) {
                Text(result.game_name, style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
            }
        }
    }
    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(c.hairline))
}
