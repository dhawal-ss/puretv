package com.puretv.twitch.android.ui.screens

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import com.puretv.twitch.android.ui.SearchViewModel
import com.puretv.twitch.android.ui.components.EmptyState
import com.puretv.twitch.android.ui.components.ErrorState
import com.puretv.twitch.android.ui.components.ExpressiveButton
import com.puretv.twitch.android.ui.components.ExpressiveButtonSize
import com.puretv.twitch.android.ui.components.ExpressiveButtonStyle
import com.puretv.twitch.android.ui.components.ExpressiveIconButton
import com.puretv.twitch.android.ui.components.ExpressiveIcons
import com.puretv.twitch.android.ui.components.FullScreenLoading
import com.puretv.twitch.android.ui.components.LivePill
import com.puretv.twitch.android.ui.components.expressiveClickable
import com.puretv.twitch.android.ui.theme.PureTvMotion
import com.puretv.twitch.android.ui.theme.PureTvTheme
import com.puretv.twitch.core.api.ChannelSearchResult
import org.koin.androidx.compose.koinViewModel

/** SECTION 06.4: channel/game search with debounce-on-type (Helix Search Channels). */
@Composable
fun SearchScreen(onOpenChannel: (String) -> Unit) {
    val viewModel: SearchViewModel = koinViewModel()
    val state by viewModel.state.collectAsState()
    val focusManager = LocalFocusManager.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        SearchField(
            query = state.query,
            onQueryChange = viewModel::onQueryChange,
            onSubmit = {
                viewModel.commitSearch()
                focusManager.clearFocus()
            },
        )
        Spacer(Modifier.height(16.dp))

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
            else -> SearchResults(
                results = state.results,
                onOpenChannel = { login ->
                    viewModel.commitSearch()
                    onOpenChannel(login)
                },
            )
        }
    }
}

/**
 * The morphing search bar: a real editable [BasicTextField] wearing the pill's
 * clothes. There is no hover on a phone, so the corner square-off is driven by
 * FOCUS rather than press, the same way the field's own cursor and keyboard
 * already are.
 */
@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = PureTvTheme.colors
    val shapes = PureTvTheme.shapes
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val radius by animateDpAsState(
        targetValue = if (focused) 16.dp else shapes.pill,
        animationSpec = PureTvMotion.MorphSpring,
        label = "searchFieldRadius",
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(radius.coerceAtLeast(0.dp)))
            .background(c.surfaceHigh)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = ExpressiveIcons.Search,
            contentDescription = null,
            tint = c.onSurfaceVariant,
            modifier = Modifier.size(22.dp),
        )

        Box(modifier = Modifier.weight(1f)) {
            if (query.isEmpty()) {
                Text(
                    "Search channels & games",
                    style = MaterialTheme.typography.bodyLarge,
                    color = c.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                interactionSource = interaction,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = c.onSurface),
                cursorBrush = SolidColor(c.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                // Search is live (debounced), so the IME action just saves the term
                // to recents and dismisses the keyboard to reveal the results.
                keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
                modifier = Modifier.fillMaxWidth(),
            )
        }

        // Only present once there is something to clear, so the field does not
        // carry a dead-looking control on first open.
        if (query.isNotEmpty()) {
            ExpressiveIconButton(
                icon = ExpressiveIcons.Close,
                contentDescription = "Clear search",
                onClick = { onQueryChange("") },
                boxSize = 36.dp,
                iconSize = 18.dp,
            )
        }
    }
}

/**
 * All results in one [PureTvTheme.shapes.card] container on [surfaceLow][PureTvTheme.colors],
 * so the container clips its children and the first/last row inherit its
 * rounding without each row needing its own corner logic.
 */
@Composable
private fun SearchResults(results: List<ChannelSearchResult>, onOpenChannel: (String) -> Unit) {
    val c = PureTvTheme.colors
    val shapes = PureTvTheme.shapes
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(shapes.cardShape)
            .background(c.surfaceLow),
    ) {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(results, key = { it.id }) { result ->
                SearchResultRow(result, onClick = { onOpenChannel(result.broadcaster_login) })
            }
        }
    }
}

@Composable
private fun SearchResultRow(result: ChannelSearchResult, onClick: () -> Unit) {
    val c = PureTvTheme.colors
    val interaction = remember { MutableInteractionSource() }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .expressiveClickable(
                interaction = interaction,
                onClick = onClick,
                restRadius = 0.dp,
                pressRadius = 0.dp,
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // The search result carries a profile image, not a stream still, so it
        // reads best as a round avatar rather than a 16:9 thumbnail.
        SearchAvatar(displayName = result.display_name, imageUrl = result.thumbnail_url.takeIf { it.isNotBlank() })

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

        // No viewer count on the search model, so live results get a bare LIVE
        // pill rather than LivePill's trailing-count form.
        if (result.is_live) LivePill()
    }
}

@Composable
private fun SearchAvatar(displayName: String, imageUrl: String?, size: Dp = 48.dp) {
    val c = PureTvTheme.colors
    if (!imageUrl.isNullOrBlank()) {
        AsyncImage(
            model = imageUrl,
            contentDescription = displayName,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(size).clip(CircleShape).background(c.surfaceHigh),
        )
    } else {
        Box(
            modifier = Modifier.size(size).clip(CircleShape).background(c.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                displayName.take(1).uppercase(),
                style = MaterialTheme.typography.labelLarge,
                color = c.onPrimaryContainer,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun RecentSearches(recent: List<String>, onPick: (String) -> Unit, onClear: () -> Unit) {
    val c = PureTvTheme.colors
    val shapes = PureTvTheme.shapes

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Recent searches", style = MaterialTheme.typography.titleMedium, color = c.onSurface)
            ExpressiveButton("Clear", onClear, style = ExpressiveButtonStyle.Text, size = ExpressiveButtonSize.Small)
        }
        Spacer(Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(shapes.cardShape)
                .background(c.surfaceLow),
        ) {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(recent, key = { it }) { term ->
                    val interaction = remember(term) { MutableInteractionSource() }
                    Text(
                        term,
                        style = MaterialTheme.typography.bodyLarge,
                        color = c.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(interactionSource = interaction, indication = null) { onPick(term) }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                    )
                }
            }
        }
    }
}
