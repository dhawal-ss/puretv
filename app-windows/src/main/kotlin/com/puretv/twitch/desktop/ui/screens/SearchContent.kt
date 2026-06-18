package com.puretv.twitch.desktop.ui.screens

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.puretv.twitch.core.api.ChannelSearchResult
import com.puretv.twitch.desktop.ui.SearchViewModel
import com.puretv.twitch.desktop.ui.components.Avatar
import com.puretv.twitch.desktop.ui.components.EditorialEmptyState
import com.puretv.twitch.desktop.ui.components.Kicker
import com.puretv.twitch.desktop.ui.components.LiveDot
import com.puretv.twitch.desktop.ui.components.handCursor
import com.puretv.twitch.desktop.ui.rememberDesktopViewModel
import com.puretv.twitch.desktop.ui.theme.PureTvMotion
import com.puretv.twitch.desktop.ui.theme.PureTvShape
import com.puretv.twitch.desktop.ui.theme.PureTvTheme
import com.puretv.twitch.desktop.ui.theme.PureTvType
import org.koin.core.Koin

@Composable
fun SearchContent(koin: Koin, onOpenChannel: (String) -> Unit) {
    val viewModel = rememberDesktopViewModel { koin.get<SearchViewModel>() }
    val state by viewModel.state.collectAsState()
    val c = PureTvTheme.colors

    Column(modifier = Modifier.fillMaxSize().padding(32.dp)) {
        Kicker("Search")
        Spacer(Modifier.height(14.dp))

        // Large editorial search field — surface plate, hairline border, accent cursor.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(PureTvShape.lg)
                .background(c.surface)
                .border(1.dp, c.hairline, PureTvShape.lg)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.Search,
                contentDescription = null,
                tint = c.textSecondary,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(12.dp))
            Box(modifier = Modifier.weight(1f)) {
                if (state.query.isEmpty()) {
                    Text(
                        "Search channels and categories…",
                        color = c.textMuted,
                        style = MaterialTheme.typography.titleLarge.copy(fontFamily = PureTvType.display),
                    )
                }
                BasicTextField(
                    value = state.query,
                    onValueChange = viewModel::onQueryChange,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.titleLarge.copy(
                        fontFamily = PureTvType.display,
                        color = c.textPrimary,
                    ),
                    cursorBrush = SolidColor(c.twitchPurple),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        when {
            state.error != null -> {
                EditorialEmptyState(
                    kicker = "Search",
                    title = "Search failed",
                    message = state.error!!,
                    actionLabel = "Retry",
                    onAction = { viewModel.retry() },
                    modifier = Modifier.padding(top = 24.dp),
                )
            }

            state.isSearching -> {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 32.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(
                        color = c.twitchPurple,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(18.dp),
                    )
                    Text("Searching…", style = PureTvType.data, color = c.textTertiary)
                }
            }

            state.results.isEmpty() -> {
                EditorialEmptyState(
                    kicker = "Search",
                    title = "Find a channel",
                    message = "Search for streamers and categories.",
                    modifier = Modifier.padding(top = 24.dp),
                )
            }

            else -> {
                Spacer(Modifier.height(28.dp))
                Kicker("Channels", rule = true)
                Spacer(Modifier.height(8.dp))
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(state.results, key = { it.id }) { result ->
                        SearchResultRow(result = result, onClick = { onOpenChannel(result.broadcaster_login) })
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchResultRow(result: ChannelSearchResult, onClick: () -> Unit) {
    val c = PureTvTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val indent by animateDpAsState(
        if (hovered) 8.dp else 0.dp,
        tween(PureTvMotion.Fast),
        label = "rowIndent",
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .hoverable(interaction)
            .handCursor()
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .background(if (hovered) c.surfaceHover else c.background)
            .padding(start = 8.dp + indent, end = 8.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The search/channels result carries a profile image, not a stream still,
        // so the channel reads best as an Avatar rather than a 16:10 cover.
        Avatar(
            displayName = result.display_name,
            imageUrl = result.thumbnail_url.takeIf { it.isNotBlank() },
            size = 48,
        )

        Column(modifier = Modifier.weight(1f).padding(start = 16.dp)) {
            Text(
                result.display_name,
                style = MaterialTheme.typography.titleMedium,
                color = c.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                if (result.is_live) result.game_name.ifBlank { result.title.ifBlank { "Live" } } else "Offline",
                style = MaterialTheme.typography.bodyMedium,
                color = c.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        // Right-aligned status. No viewer count exists on the search model,
        // so live shows a LIVE marker and offline shows a muted dot.
        if (result.is_live) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(start = 16.dp),
            ) {
                LiveDot(size = 6.dp)
                Text("LIVE", style = PureTvType.dataSmall, color = c.live)
            }
        } else {
            Text(
                "·",
                style = PureTvType.data,
                color = c.textMuted,
                modifier = Modifier.padding(start = 16.dp),
            )
        }
    }

    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(c.hairline))
}
