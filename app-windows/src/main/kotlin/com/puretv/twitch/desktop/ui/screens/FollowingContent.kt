package com.puretv.twitch.desktop.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.puretv.twitch.core.follows.FollowRow
import com.puretv.twitch.desktop.ui.FollowedRailViewModel
import com.puretv.twitch.desktop.ui.components.Avatar
import com.puretv.twitch.desktop.ui.components.CoverImage
import com.puretv.twitch.desktop.ui.components.ExpressiveButton
import com.puretv.twitch.desktop.ui.components.ExpressiveButtonSize
import com.puretv.twitch.desktop.ui.components.ExpressiveButtonStyle
import com.puretv.twitch.desktop.ui.components.ExpressiveCard
import com.puretv.twitch.desktop.ui.components.ExpressivePanel
import com.puretv.twitch.desktop.ui.components.LivePill
import com.puretv.twitch.desktop.ui.components.PageTitle
import com.puretv.twitch.desktop.ui.components.SegmentedToggle
import com.puretv.twitch.desktop.ui.components.Skeleton
import com.puretv.twitch.desktop.ui.components.expressiveClickable
import com.puretv.twitch.desktop.ui.components.formatViewerCount
import com.puretv.twitch.desktop.ui.rememberDesktopViewModel
import com.puretv.twitch.desktop.ui.theme.PureTvTheme
import com.puretv.twitch.desktop.ui.theme.PureTvType
import org.koin.core.Koin

/**
 * Following as its own destination: live follows first in a card grid or a row
 * list (the user's choice persists only for the session), everyone else below
 * in a single flat list. Built entirely on [FollowedRailViewModel] so it always
 * agrees with the nav rail's "Live now" list and its unread count.
 */
private enum class FollowingLayout(val label: String) { GRID("Grid"), LIST("List") }

@Composable
fun FollowingContent(koin: Koin, onOpenChannel: (String) -> Unit, onSignIn: () -> Unit) {
    val viewModel = rememberDesktopViewModel { koin.get<FollowedRailViewModel>() }
    val state by viewModel.state.collectAsState()
    var layout by remember { mutableStateOf(FollowingLayout.GRID) }

    LaunchedEffect(Unit) { viewModel.refresh() }

    val c = PureTvTheme.colors
    val noData = state.live.isEmpty() && state.offline.isEmpty()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 32.dp, end = 32.dp, top = 36.dp, bottom = 40.dp),
    ) {
        item { FollowingHeader() }

        when {
            !state.isLoggedIn -> item {
                Spacer(Modifier.height(32.dp))
                SignedOutPanel(onSignIn)
            }

            // isLoading is only ever true while there's no data yet (see
            // FollowedRailViewModel.loadOnce), so this never flashes on the poll.
            state.isLoading && noData -> item {
                Spacer(Modifier.height(32.dp))
                LoadingGrid()
            }

            state.errored && noData -> item {
                Spacer(Modifier.height(32.dp))
                MessagePanel(
                    title = "Couldn't load your follows",
                    message = "The last request failed. Check your connection and try again.",
                    actionLabel = "Try again",
                    onAction = { viewModel.refresh() },
                )
            }

            noData -> item {
                Spacer(Modifier.height(32.dp))
                MessagePanel(
                    title = "No follows yet",
                    message = "Channels you follow on Twitch will show up here, live ones first.",
                )
            }

            else -> {
                item {
                    Spacer(Modifier.height(32.dp))
                    LiveSectionHeader(count = state.live.size, layout = layout, onLayoutChange = { layout = it })
                    Spacer(Modifier.height(18.dp))
                    when {
                        state.live.isEmpty() -> Text(
                            "None of your follows are live right now.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = c.onSurfaceVariant,
                        )
                        layout == FollowingLayout.GRID -> Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            state.live.chunked(4).forEach { chunk ->
                                LiveGridRow(chunk, onOpenChannel)
                            }
                        }
                        else -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            state.live.forEach { row -> LiveListRow(row, onClick = { onOpenChannel(row.login) }) }
                        }
                    }
                }

                if (state.offline.isNotEmpty()) {
                    item {
                        Spacer(Modifier.height(36.dp))
                        Text("Offline · ${state.offline.size}", style = MaterialTheme.typography.headlineMedium, color = c.onSurface)
                        Spacer(Modifier.height(16.dp))
                        OfflineList(state.offline, onOpenChannel)
                    }
                }
            }
        }
    }
}

@Composable
private fun FollowingHeader() {
    val c = PureTvTheme.colors
    Text("Your follows".uppercase(), style = PureTvType.kicker, color = c.primary)
    Spacer(Modifier.height(10.dp))
    PageTitle("Following")
    Spacer(Modifier.height(8.dp))
    Text(
        "Every channel you follow lives here, live ones first so you always know who to check on next. " +
            "Everyone else sits below, ready for a click the moment they go live.",
        style = MaterialTheme.typography.bodyLarge,
        color = c.onSurfaceVariant,
        modifier = Modifier.widthIn(max = 620.dp),
    )
}

@Composable
private fun SignedOutPanel(onSignIn: () -> Unit) {
    val c = PureTvTheme.colors
    ExpressivePanel {
        Column {
            Text("Sign in to see who's live", style = MaterialTheme.typography.titleLarge, color = c.onSurface)
            Spacer(Modifier.height(8.dp))
            Text(
                "Connect your Twitch account from the Account tab to pull in your follows and their live status.",
                style = MaterialTheme.typography.bodyMedium,
                color = c.onSurfaceVariant,
            )
            Spacer(Modifier.height(20.dp))
            ExpressiveButton(text = "Sign in", onClick = onSignIn, style = ExpressiveButtonStyle.Filled, size = ExpressiveButtonSize.Medium)
        }
    }
}

@Composable
private fun MessagePanel(
    title: String,
    message: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val c = PureTvTheme.colors
    ExpressivePanel {
        Column {
            Text(title, style = MaterialTheme.typography.titleLarge, color = c.onSurface)
            Spacer(Modifier.height(8.dp))
            Text(message, style = MaterialTheme.typography.bodyMedium, color = c.onSurfaceVariant)
            if (actionLabel != null && onAction != null) {
                Spacer(Modifier.height(20.dp))
                ExpressiveButton(text = actionLabel, onClick = onAction, style = ExpressiveButtonStyle.Tonal, size = ExpressiveButtonSize.Small)
            }
        }
    }
}

/** Two rows of four card-shaped placeholders, shimmering while the first load is in flight. */
@Composable
private fun LoadingGrid() {
    val shapes = PureTvTheme.shapes
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        repeat(2) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                repeat(4) {
                    Column(modifier = Modifier.weight(1f)) {
                        Skeleton(modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(shapes.thumbShape))
                        Spacer(Modifier.height(10.dp))
                        Skeleton(modifier = Modifier.fillMaxWidth(0.7f).height(14.dp).clip(shapes.smShape))
                        Spacer(Modifier.height(6.dp))
                        Skeleton(modifier = Modifier.fillMaxWidth(0.45f).height(12.dp).clip(shapes.smShape))
                    }
                }
            }
        }
    }
}

@Composable
private fun LiveSectionHeader(count: Int, layout: FollowingLayout, onLayoutChange: (FollowingLayout) -> Unit) {
    val c = PureTvTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Live · $count", style = MaterialTheme.typography.headlineMedium, color = c.onSurface)
        Spacer(Modifier.weight(1f))
        SegmentedToggle(
            options = FollowingLayout.entries,
            selected = layout,
            label = { it.label },
            onSelect = onLayoutChange,
        )
    }
}

/** One row of up to four grid cards; short rows pad out with weighted spacers so cards keep a stable width. */
@Composable
private fun LiveGridRow(rowItems: List<FollowRow>, onOpenChannel: (String) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        rowItems.forEach { row ->
            LiveFollowCard(row, onClick = { onOpenChannel(row.login) }, modifier = Modifier.weight(1f))
        }
        repeat(4 - rowItems.size) { Spacer(Modifier.weight(1f)) }
    }
}

@Composable
private fun LiveFollowCard(row: FollowRow, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = PureTvTheme.colors
    val shapes = PureTvTheme.shapes
    ExpressiveCard(onClick = onClick, modifier = modifier) {
        Column {
            Box(modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(shapes.thumbShape)) {
                // FollowRow carries no stream thumbnail (only avatarUrl), so the art is
                // the deterministic duotone fallback rather than an invented image.
                CoverImage(imageUrl = null, seed = row.login, contentDescription = row.displayName, modifier = Modifier.fillMaxSize())
                Box(Modifier.fillMaxSize().background(c.bottomScrim))
                LivePill(modifier = Modifier.align(Alignment.TopStart).padding(8.dp))
                Text(
                    formatViewerCount(row.viewerCount),
                    style = PureTvType.dataSmall,
                    color = Color.White,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp),
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(row.displayName, style = MaterialTheme.typography.titleMedium, color = c.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (row.gameName.isNotBlank()) {
                Text(row.gameName, style = MaterialTheme.typography.bodyMedium, color = c.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** The List-mode alternative to [LiveFollowCard]: a full-width row, same facts, denser. */
@Composable
private fun LiveListRow(row: FollowRow, onClick: () -> Unit) {
    val c = PureTvTheme.colors
    val shapes = PureTvTheme.shapes
    val interaction = remember { MutableInteractionSource() }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .expressiveClickable(
                interaction = interaction,
                onClick = onClick,
                restRadius = shapes.sm,
                hoverRadius = shapes.md,
                hoverColor = c.surfaceHigh,
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Box(modifier = Modifier.size(width = 120.dp, height = 68.dp).clip(shapes.thumbShape)) {
            CoverImage(imageUrl = null, seed = row.login, contentDescription = row.displayName, modifier = Modifier.fillMaxSize())
            Box(Modifier.fillMaxSize().background(c.bottomScrim))
            LivePill(modifier = Modifier.align(Alignment.TopStart).padding(6.dp), height = 22.dp)
        }
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(row.displayName, style = MaterialTheme.typography.titleMedium, color = c.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (row.gameName.isNotBlank()) {
                Text(row.gameName, style = MaterialTheme.typography.bodyMedium, color = c.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Spacer(Modifier.width(16.dp))
        Text("${formatViewerCount(row.viewerCount)} watching", style = PureTvType.data, color = c.onSurfaceVariant)
    }
}

/** The single surfaceLow container holding every offline follow as a flat row list. */
@Composable
private fun OfflineList(rows: List<FollowRow>, onOpenChannel: (String) -> Unit) {
    val c = PureTvTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(PureTvTheme.shapes.cardShape)
            .background(c.surfaceLow),
    ) {
        rows.forEach { row -> OfflineRow(row, onClick = { onOpenChannel(row.login) }) }
    }
}

/**
 * FollowRow has no last-seen or last-played field for an offline channel (gameName
 * is only ever populated from a live stream), so this row carries just the name,
 * not an invented secondary line. The trailing button opens the channel page: there
 * is no per-channel notification system in this app to bind a "Notify me" action to.
 */
@Composable
private fun OfflineRow(row: FollowRow, onClick: () -> Unit) {
    val c = PureTvTheme.colors
    val interaction = remember { MutableInteractionSource() }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .expressiveClickable(
                interaction = interaction,
                onClick = onClick,
                restRadius = 0.dp,
                hoverRadius = 0.dp,
                hoverColor = c.surfaceHigh,
            )
            .padding(horizontal = 20.dp, vertical = 14.dp),
    ) {
        Avatar(displayName = row.displayName, imageUrl = row.avatarUrl, size = 44)
        Text(
            row.displayName,
            style = MaterialTheme.typography.titleMedium,
            color = c.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        ExpressiveButton(
            text = "View channel",
            onClick = onClick,
            style = ExpressiveButtonStyle.Outlined,
            size = ExpressiveButtonSize.Small,
        )
    }
}
