package com.puretv.twitch.tv.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.puretv.twitch.tv.ui.SearchViewModel
import com.puretv.twitch.tv.ui.theme.PureTvTvColors
import org.koin.androidx.compose.koinViewModel

/**
 * SECTION 07.2: TV search input.
 *
 * `androidx.tv.material3` has no `TextField`, so this uses `BasicTextField`
 * directly. On entry the field auto-focuses and asks the platform to raise the
 * Android TV on-screen keyboard (`SoftwareKeyboardController.show()`), so a
 * viewer can type a query with the remote; a focus ring makes it clear the
 * field is selected. The IME also exposes the remote's voice/assist input.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun TvSearchScreen(
    onOpenChannel: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: SearchViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()
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
            .background(PureTvTvColors.Background)
            .padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Button(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back")
            }
            Text(text = "Search", style = MaterialTheme.typography.headlineLarge, color = PureTvTvColors.TextPrimary)
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(PureTvTvColors.Surface, RoundedCornerShape(12.dp))
                .border(
                    BorderStroke(if (searchFocused) 2.dp else 0.dp, PureTvTvColors.FocusBorder),
                    RoundedCornerShape(12.dp),
                )
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(Icons.Default.Search, contentDescription = null, tint = PureTvTvColors.TextSecondary)
            BasicTextField(
                value = state.query,
                onValueChange = viewModel::onQueryChange,
                singleLine = true,
                textStyle = TextStyle(color = PureTvTvColors.TextPrimary, fontSize = MaterialTheme.typography.bodyLarge.fontSize),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(PureTvTvColors.TwitchPurple),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
                    .onFocusChanged { searchFocused = it.isFocused },
                decorationBox = { inner ->
                    if (state.query.isEmpty()) {
                        Text(
                            text = "Search channels…",
                            style = MaterialTheme.typography.bodyLarge,
                            color = PureTvTvColors.TextMuted,
                        )
                    }
                    inner()
                },
            )
        }

        if (state.isSearching) {
            Text(text = "Searching…", style = MaterialTheme.typography.bodyMedium, color = PureTvTvColors.TextSecondary)
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(state.results, key = { it.broadcaster_login }) { result ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(PureTvTvColors.Surface, RoundedCornerShape(8.dp))
                        .padding(16.dp)
                        .let { base ->
                            // Result rows are plain focusable rows (Button gives us the
                            // D-pad confirm + visual focus state for free, matching the
                            // canonical Section 7.3 pattern without a full card layout).
                            base
                        },
                ) {
                    Button(onClick = { onOpenChannel(result.broadcaster_login) }, modifier = Modifier.fillMaxWidth()) {
                        Column {
                            Text(text = result.display_name, style = MaterialTheme.typography.titleLarge, color = Color.White)
                            Text(
                                text = if (result.is_live) "🔴 Live now · ${result.game_name}" else result.game_name,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (result.is_live) PureTvTvColors.Live else PureTvTvColors.TextSecondary,
                            )
                        }
                    }
                }
            }
        }
    }
}
