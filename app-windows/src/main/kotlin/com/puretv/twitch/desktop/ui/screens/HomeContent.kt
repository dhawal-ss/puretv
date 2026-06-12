package com.puretv.twitch.desktop.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.puretv.twitch.desktop.ui.HomeViewModel
import com.puretv.twitch.desktop.ui.rememberDesktopViewModel
import com.puretv.twitch.desktop.ui.components.FollowCard
import com.puretv.twitch.desktop.ui.components.SectionHeader
import com.puretv.twitch.desktop.ui.components.StreamCard
import com.puretv.twitch.desktop.ui.components.StreamCardSkeleton
import com.puretv.twitch.desktop.ui.theme.PureTvTheme
import org.koin.core.Koin

/**
 * Home is one adaptive [LazyVerticalGrid], not the old single-row rails. Cards
 * wrap into as many columns as the window is wide and keep filling DOWN the
 * page, so dozens of channels are visible without horizontal scrolling.
 *
 * Two stacked sections, favorites pinned on top:
 *  - "Favorites" — the locally-followed channels (live first, see HomeViewModel).
 *  - "Live Now"  — top live streams, each with its viewer count under the card.
 *
 * Section titles are full-width [fullSpan] items so they break the grid flow
 * cleanly; a LazyVerticalGrid is its own scroll container, so (unlike the old
 * Column) it must NOT be nested in a verticalScroll.
 */
@Composable
fun HomeContent(koin: Koin, onOpenChannel: (String) -> Unit) {
    val viewModel = rememberDesktopViewModel { koin.get<HomeViewModel>() }
    val state by viewModel.state.collectAsState()
    val c = PureTvTheme.colors

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 260.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(24.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        fullSpan { Text("Home", style = MaterialTheme.typography.headlineMedium, color = c.textPrimary) }

        when {
            !state.isLoggedIn -> fullSpan {
                Text(
                    "Sign in via the Account tab to see live channels.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = c.textSecondary,
                )
            }

            state.isLoading -> {
                fullSpan { SectionHeader(title = "Favorites") }
                items(6) { StreamCardSkeleton(modifier = Modifier.fillMaxWidth()) }
                fullSpan { SectionHeader(title = "Live Now") }
                items(6) { StreamCardSkeleton(modifier = Modifier.fillMaxWidth()) }
            }

            else -> {
                if (state.following.isNotEmpty()) {
                    fullSpan { SectionHeader(title = "Favorites") }
                    items(state.following, key = { "fav_${it.login}" }) { ch ->
                        FollowCard(
                            state = ch,
                            onClick = { onOpenChannel(ch.login) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }

                if (state.topStreams.isNotEmpty()) {
                    fullSpan { SectionHeader(title = "Live Now") }
                    items(state.topStreams, key = { "live_${it.id}" }) { stream ->
                        StreamCard(
                            stream = stream,
                            onClick = { onOpenChannel(stream.userLogin) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                } else if (state.following.isEmpty()) {
                    fullSpan {
                        Text(
                            "No live streams returned. Your session may have expired.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = c.textSecondary,
                        )
                    }
                }
            }
        }
    }
}

/** A full-width row inside the grid (section headers, empty-state text). */
private fun LazyGridScope.fullSpan(content: @Composable () -> Unit) {
    item(span = { GridItemSpan(maxLineSpan) }) { content() }
}
