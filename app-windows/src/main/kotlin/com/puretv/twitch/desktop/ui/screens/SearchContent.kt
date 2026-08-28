package com.puretv.twitch.desktop.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.puretv.twitch.core.api.ChannelSearchResult
import com.puretv.twitch.desktop.ui.SearchViewModel
import com.puretv.twitch.desktop.ui.components.Avatar
import com.puretv.twitch.desktop.ui.components.EditorialEmptyState
import com.puretv.twitch.desktop.ui.components.ExpressiveIconButton
import com.puretv.twitch.desktop.ui.components.ExpressiveIcons
import com.puretv.twitch.desktop.ui.components.LivePill
import com.puretv.twitch.desktop.ui.components.handCursor
import com.puretv.twitch.desktop.ui.rememberDesktopViewModel
import com.puretv.twitch.desktop.ui.theme.PureTvMotion
import com.puretv.twitch.desktop.ui.theme.PureTvTheme
import com.puretv.twitch.desktop.ui.theme.PureTvType
import org.koin.core.Koin

@Composable
fun SearchContent(koin: Koin, onOpenChannel: (String) -> Unit) {
    val viewModel = rememberDesktopViewModel { koin.get<SearchViewModel>() }
    val state by viewModel.state.collectAsState()
    val c = PureTvTheme.colors

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 36.dp, start = 32.dp, end = 32.dp, bottom = 40.dp),
    ) {
        SearchField(query = state.query, onQueryChange = viewModel::onQueryChange)

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
                        color = c.primary,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(18.dp),
                    )
                    Text("Searching...", style = PureTvType.data, color = c.onSurfaceVariant)
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
                Spacer(Modifier.height(24.dp))
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(PureTvTheme.shapes.card))
                        .background(c.surfaceLow),
                ) {
                    // Container clips its children, so the first and last row inherit
                    // its rounding without each row needing its own corner logic.
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(state.results, key = { it.id }) { result ->
                            SearchResultRow(result = result, onClick = { onOpenChannel(result.broadcaster_login) })
                        }
                    }
                }
            }
        }
    }
}

/**
 * The morphing search bar: a real editable [BasicTextField] wearing the pill's
 * clothes. It shares one interaction source between the outer hover and the
 * field's own focus, so the corner squares off on either.
 */
@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit, modifier: Modifier = Modifier) {
    val c = PureTvTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val focused by interaction.collectIsFocusedAsState()
    val radius by animateDpAsState(
        targetValue = if (hovered || focused) 20.dp else 32.dp,
        animationSpec = PureTvMotion.MorphSpring,
        label = "searchFieldRadius",
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp)
            .hoverable(interaction)
            .clip(RoundedCornerShape(radius))
            .background(c.surfaceHigh)
            .padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(
            imageVector = ExpressiveIcons.Search,
            contentDescription = null,
            tint = c.onSurfaceVariant,
            modifier = Modifier.size(26.dp),
        )

        Box(modifier = Modifier.weight(1f)) {
            if (query.isEmpty()) {
                Text(
                    "Search channels and categories",
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp),
                    color = c.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                interactionSource = interaction,
                textStyle = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp, color = c.onSurface),
                cursorBrush = SolidColor(c.primary),
                modifier = Modifier.fillMaxWidth(),
            )
        }

        if (query.isNotEmpty()) {
            ExpressiveIconButton(
                icon = ExpressiveIcons.Close,
                contentDescription = "Clear search",
                onClick = { onQueryChange("") },
                boxSize = 44.dp,
                iconSize = 22.dp,
            )
        }
    }
}

@Composable
private fun SearchResultRow(result: ChannelSearchResult, onClick: () -> Unit) {
    val c = PureTvTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val rowColor by animateColorAsState(
        targetValue = if (hovered) c.surfaceHigh else Color.Transparent,
        animationSpec = tween(PureTvMotion.Fast),
        label = "rowHover",
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .hoverable(interaction)
            .handCursor()
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .background(rowColor)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // The search/channels result carries a profile image, not a stream still,
        // so the channel reads best as an Avatar rather than a 16:10 cover.
        Avatar(
            displayName = result.display_name,
            imageUrl = result.thumbnail_url.takeIf { it.isNotBlank() },
            size = 52,
        )

        Column(modifier = Modifier.weight(1f)) {
            Text(
                result.display_name,
                style = MaterialTheme.typography.titleMedium,
                color = c.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                if (result.is_live) result.game_name.ifBlank { result.title.ifBlank { "Live" } } else "Offline",
                style = MaterialTheme.typography.bodyMedium,
                color = c.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        // No viewer count exists on the search model, so live results show a bare
        // LIVE pill rather than LivePill's trailing-count form.
        if (result.is_live) {
            LivePill()
        }
    }
}
