package com.puretv.twitch.tv.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.puretv.twitch.core.api.ChannelSearchResult
import com.puretv.twitch.tv.ui.SearchViewModel
import com.puretv.twitch.tv.ui.components.ExpressiveIcons
import com.puretv.twitch.tv.ui.components.TvExpressiveIconButton
import com.puretv.twitch.tv.ui.components.TvLivePill
import com.puretv.twitch.tv.ui.components.tvFocusClickable
import com.puretv.twitch.tv.ui.theme.PureTvTvMotion
import com.puretv.twitch.tv.ui.theme.PureTvTvTheme
import org.koin.androidx.compose.koinViewModel
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text

/**
 * SECTION 07.2: TV search input.
 *
 * `androidx.tv.material3` has no `TextField`, so this uses `BasicTextField`
 * directly. On entry the field auto-focuses and asks the platform to raise the
 * Android TV on-screen keyboard (`SoftwareKeyboardController.show()`), so a
 * viewer can type a query with the remote; the field's own morph and ring make
 * it clear it is the one listening. The IME also exposes the remote's
 * voice/assist input. None of that plumbing changed here, only the shell
 * around it.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun TvSearchScreen(
    onOpenChannel: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: SearchViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val c = PureTvTvTheme.colors
    val shapes = PureTvTvTheme.shapes
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    var searchFocused by remember { mutableStateOf(false) }

    // Land focus on the search box and raise the on-screen keyboard as soon as
    // the screen opens, so the first thing the remote drives is typing.
    LaunchedEffect(Unit) {
        runCatching { focusRequester.requestFocus() }
        keyboard?.show()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(c.surface)
            .padding(48.dp),
        verticalArrangement = Arrangement.spacedBy(28.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            TvExpressiveIconButton(icon = ExpressiveIcons.Back, contentDescription = "Back", onClick = onBack)
            Text(text = "Search", style = MaterialTheme.typography.displayMedium, color = c.onSurface)
        }

        TvSearchField(
            query = state.query,
            onQueryChange = viewModel::onQueryChange,
            focused = searchFocused,
            modifier = Modifier
                .focusRequester(focusRequester)
                .onFocusChanged { searchFocused = it.isFocused },
        )

        if (state.isSearching) {
            Text(text = "Searching…", style = MaterialTheme.typography.bodyLarge, color = c.onSurfaceVariant)
        }

        if (state.results.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(shapes.cardShape)
                    .background(c.surfaceLow),
            ) {
                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    items(state.results, key = { it.broadcaster_login }) { result ->
                        TvSearchResultRow(result = result, onClick = { onOpenChannel(result.broadcaster_login) })
                    }
                }
            }
        }
    }
}

/**
 * The pill that morphs into a squarer field and rings on D-pad focus. Built by
 * hand rather than on [tvFocusClickable] because the surface it wraps is a
 * [BasicTextField], which already owns its own focus state via
 * `onFocusChanged` above; there is no click to hand off.
 */
@Composable
private fun TvSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    focused: Boolean,
    modifier: Modifier = Modifier,
) {
    val c = PureTvTvTheme.colors
    val shapes = PureTvTvTheme.shapes

    val radius by animateDpAsState(
        targetValue = if (focused) shapes.pillFocus else shapes.pill,
        animationSpec = PureTvTvMotion.MorphSpring,
        label = "tvSearchFieldRadius",
    )
    val ring by animateColorAsState(
        targetValue = if (focused) c.focusRing else Color.Transparent,
        animationSpec = tween(PureTvTvMotion.Fast),
        label = "tvSearchFieldRing",
    )
    val shape = RoundedCornerShape(radius.coerceAtLeast(0.dp))

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(76.dp)
            .clip(shape)
            .background(c.surfaceHigh)
            .border(3.dp, ring, shape)
            .padding(horizontal = 28.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(ExpressiveIcons.Search, contentDescription = null, tint = c.onSurfaceVariant, modifier = Modifier.size(28.dp))
        Box(modifier = Modifier.fillMaxWidth()) {
            if (query.isEmpty()) {
                Text(
                    text = "Search channels",
                    style = MaterialTheme.typography.bodyLarge,
                    color = c.onSurfaceVariant,
                )
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = TextStyle(color = c.onSurface, fontSize = MaterialTheme.typography.bodyLarge.fontSize),
                cursorBrush = SolidColor(c.primary),
                modifier = modifier.fillMaxWidth(),
            )
        }
    }
}

/** One search hit: a round avatar, name + status, and a LIVE pill when live. */
@Composable
private fun TvSearchResultRow(result: ChannelSearchResult, onClick: () -> Unit) {
    val c = PureTvTvTheme.colors
    val shapes = PureTvTvTheme.shapes
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .tvFocusClickable(
                interaction = interaction,
                onClick = onClick,
                restRadius = 0.dp,
                focusRadius = shapes.md,
                color = Color.Transparent,
                focusColor = c.primary,
                scale = 1.02f,
            )
            .padding(horizontal = 28.dp, vertical = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        TvResultAvatar(displayName = result.display_name, imageUrl = result.thumbnail_url.takeIf { it.isNotBlank() })

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = result.display_name,
                style = MaterialTheme.typography.titleMedium,
                color = if (focused) c.onPrimary else c.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = if (result.is_live) result.game_name.ifBlank { "Live now" } else "Offline",
                style = MaterialTheme.typography.bodyMedium,
                color = if (focused) c.onPrimary else c.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        if (result.is_live) TvLivePill()
    }
}

@Composable
private fun TvResultAvatar(displayName: String, imageUrl: String?, modifier: Modifier = Modifier) {
    val c = PureTvTvTheme.colors
    Box(
        modifier = modifier.size(56.dp).clip(CircleShape).background(c.surfaceHigh),
        contentAlignment = Alignment.Center,
    ) {
        if (!imageUrl.isNullOrBlank()) {
            AsyncImage(
                model = imageUrl,
                contentDescription = displayName,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().clip(CircleShape),
            )
        } else {
            Text(
                text = displayName.take(1).uppercase(),
                style = MaterialTheme.typography.titleMedium,
                color = c.onSurfaceVariant,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}
