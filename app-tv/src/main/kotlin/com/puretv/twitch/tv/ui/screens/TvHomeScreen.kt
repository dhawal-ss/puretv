package com.puretv.twitch.tv.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.puretv.twitch.core.model.StreamInfo
import com.puretv.twitch.tv.ui.HomeViewModel
import com.puretv.twitch.tv.ui.components.TvNavDestination
import com.puretv.twitch.tv.ui.components.TvNavDrawer
import com.puretv.twitch.tv.ui.components.TvStreamCard
import com.puretv.twitch.tv.ui.theme.PureTvTvColors
import com.puretv.twitch.tv.update.TvUpdateManager
import com.puretv.twitch.tv.update.TvUpdateState
import kotlinx.coroutines.delay
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

/**
 * SECTION 07.2 / 07.3 [CRITICAL] — landing screen: persistent left
 * [TvNavDrawer] + horizontally-scrolling content rows ("Following" /
 * "Live Channels"), each row a [LazyRow] of [TvStreamCard]s.
 *
 * Focus handling: the first card of the first row holds a [FocusRequester]
 * that's requested on first composition; `onPreviewKeyEvent` on the content
 * column intercepts DPAD_LEFT when focus is already on the leftmost column
 * and redirects it into the nav rail — satisfying Section 7.3's rule
 * "D-pad LEFT on the leftmost item opens the nav drawer" without fighting
 * Compose's default focus-search (which would otherwise have nowhere to go).
 */
@Composable
fun TvHomeScreen(
    onOpenStream: (String) -> Unit,
    onOpenChannel: (String) -> Unit,
    onOpenBrowse: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenLogin: () -> Unit,
    viewModel: HomeViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val updateManager = koinInject<TvUpdateManager>()
    val updateState by updateManager.state.collectAsState()
    val drawerFocusRequester = remember { FocusRequester() }
    val firstCardFocusRequester = remember { FocusRequester() }
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(Unit) {
        runCatching { firstCardFocusRequester.requestFocus() }
    }

    // Keep "Live Now" current. The TV app used to load top streams exactly once
    // at ViewModel creation, so a long-lived (Fire TV never kills the process)
    // session froze on the first snapshot. Refresh on every return to the
    // foreground and then poll while the screen stays resumed; the loop is
    // cancelled the moment the app is backgrounded so it never polls unseen.
    LaunchedEffect(Unit) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                viewModel.refresh()
                delay(HOME_REFRESH_INTERVAL_MS)
            }
        }
    }

    Row(modifier = Modifier.fillMaxSize().background(PureTvTvColors.Background)) {
        TvNavDrawer(
            selected = TvNavDestination.HOME,
            isLoggedIn = state.isLoggedIn,
            onSelect = { dest ->
                when (dest) {
                    TvNavDestination.HOME -> Unit
                    TvNavDestination.BROWSE -> onOpenBrowse()
                    TvNavDestination.SEARCH -> onOpenSearch()
                    TvNavDestination.SETTINGS -> onOpenSettings()
                    TvNavDestination.ACCOUNT -> if (state.isLoggedIn) onOpenSettings() else onOpenLogin()
                }
            },
            modifier = Modifier.focusRequester(drawerFocusRequester),
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 32.dp, top = 32.dp, end = 32.dp)
                .onPreviewKeyEvent { event ->
                    // Section 7.3: DPAD LEFT on the leftmost column re-targets focus
                    // into the rail instead of being swallowed by Compose's focus search.
                    if (event.key == Key.DirectionLeft) {
                        runCatching { drawerFocusRequester.requestFocus() }
                        true
                    } else {
                        false
                    }
                },
            verticalArrangement = Arrangement.spacedBy(28.dp),
        ) {
            Text(text = "PureTV for Twitch", style = MaterialTheme.typography.displaySmall, color = PureTvTvColors.TextPrimary)

            // Quality-of-life: a newer TV APK is available — one click jumps to
            // Settings where the download & install lives. Only shows when the
            // launch/Settings check actually found a newer build.
            (updateState as? TvUpdateState.Available)?.let { available ->
                Button(onClick = onOpenSettings) {
                    Text("Update available: ${available.info.versionName} — open Settings to install")
                }
            }

            if (state.followedLive.isNotEmpty()) {
                ContentRow(
                    title = "Following — Live Now",
                    streams = state.followedLive,
                    firstCardFocusRequester = firstCardFocusRequester,
                    onOpenStream = onOpenStream,
                )
            }
            ContentRow(
                title = "Live Channels",
                streams = state.topStreams,
                firstCardFocusRequester = if (state.followedLive.isEmpty()) firstCardFocusRequester else null,
                onOpenStream = onOpenStream,
            )

            // Only surfaces when there's genuinely nothing to show (no cache, no
            // network) — the cached-first paint keeps this hidden in the common case.
            if (state.topStreams.isEmpty() && !state.isLoading) {
                Text(
                    text = state.error ?: "Nothing live right now. It'll refresh automatically.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = PureTvTvColors.TextSecondary,
                )
            }
        }
    }
}

// Poll cadence for the foreground "Live Now" refresh. 90s keeps viewer counts /
// who's-live reasonably fresh without hammering Helix on a lean-back screen.
private const val HOME_REFRESH_INTERVAL_MS = 90_000L

@Composable
private fun ContentRow(
    title: String,
    streams: List<StreamInfo>,
    firstCardFocusRequester: FocusRequester?,
    onOpenStream: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(text = title, style = MaterialTheme.typography.headlineSmall, color = PureTvTvColors.TextPrimary)
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(bottom = 8.dp),
        ) {
            itemsIndexed(streams, key = { _, s -> s.id }) { index, stream ->
                TvStreamCard(
                    stream = stream,
                    onClick = { onOpenStream(stream.userLogin) },
                    modifier = if (index == 0 && firstCardFocusRequester != null) {
                        Modifier.focusRequester(firstCardFocusRequester)
                    } else {
                        Modifier
                    },
                )
            }
        }
    }
}
