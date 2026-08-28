package com.puretv.twitch.android.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.puretv.twitch.android.ui.FollowingViewModel
import com.puretv.twitch.android.ui.components.ExpressiveButton
import com.puretv.twitch.android.ui.components.ExpressiveButtonSize
import com.puretv.twitch.android.ui.components.ExpressiveButtonStyle
import com.puretv.twitch.android.ui.components.ExpressiveCard
import com.puretv.twitch.android.ui.components.ExpressiveIcons
import com.puretv.twitch.android.ui.components.ExpressivePanel
import com.puretv.twitch.android.ui.components.LivePill
import com.puretv.twitch.android.ui.components.PageTitle
import com.puretv.twitch.android.ui.components.SegmentedToggle
import com.puretv.twitch.android.ui.components.StreamCardSkeleton
import com.puretv.twitch.android.ui.components.expressiveClickable
import com.puretv.twitch.android.ui.components.formatViewerCount
import com.puretv.twitch.android.ui.components.streamThumbUrl
import com.puretv.twitch.android.ui.theme.PureTvTheme
import com.puretv.twitch.android.ui.theme.PureTvType
import com.puretv.twitch.core.follows.FollowRow
import org.koin.androidx.compose.koinViewModel

/**
 * Following, mirroring the desktop screen's shape on a phone: a kicker,
 * [PageTitle], a short intro, a "Live · N" heading with a [SegmentedToggle]
 * that switches the live section between a two-column card grid and
 * full-width rows, then an "Offline · N" heading over a single flat
 * container. Built entirely on [FollowingViewModel], which now drives
 * `FollowedChannelsSource` directly, so both sections agree with the data
 * desktop's followed rail shows.
 */
private enum class FollowingLayout(val label: String) { GRID("Grid"), LIST("List") }

@Composable
fun FollowingScreen(
    onOpenStream: (String) -> Unit,
    onOpenLogin: () -> Unit,
    viewModel: FollowingViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val c = PureTvTheme.colors
    var layout by remember { mutableStateOf(FollowingLayout.GRID) }
    val noData = state.live.isEmpty() && state.offline.isEmpty()

    // Top inset only: this screen has no app bar, so it owns the status bar itself,
    // while the tab bar below owns the navigation bar. The M3 default (systemBars)
    // would pad the bottom a second time on top of the tab bar.
    Scaffold(
        containerColor = c.surfaceLowest,
        contentWindowInsets = WindowInsets.statusBars,
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 20.dp),
        ) {
            item { FollowingHeader() }

            when {
                !state.isLoggedIn -> item {
                    Spacer(Modifier.height(24.dp))
                    SignedOutPanel(onSignIn = onOpenLogin)
                }

                // isLoading is only ever true while there's no data yet, so this
                // never flashes on a background refresh.
                state.isLoading && noData -> item {
                    Spacer(Modifier.height(24.dp))
                    LoadingList()
                }

                state.errored && noData -> item {
                    Spacer(Modifier.height(24.dp))
                    MessagePanel(
                        title = "Couldn't load your follows",
                        message = "The last request failed. Check your connection and try again.",
                        actionLabel = "Try again",
                        onAction = { viewModel.refresh() },
                    )
                }

                noData -> item {
                    Spacer(Modifier.height(24.dp))
                    MessagePanel(
                        title = "No follows yet",
                        message = "Channels you follow on Twitch will show up here, live ones first.",
                    )
                }

                else -> {
                    item {
                        Spacer(Modifier.height(24.dp))
                        LiveSectionHeader(count = state.live.size, layout = layout, onLayoutChange = { layout = it })
                        Spacer(Modifier.height(14.dp))
                        when {
                            state.live.isEmpty() -> Text(
                                "None of your follows are live right now.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = c.onSurfaceVariant,
                            )
                            layout == FollowingLayout.GRID -> Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                state.live.chunked(2).forEach { row -> LiveGridRow(row, onOpenStream) }
                            }
                            else -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                state.live.forEach { row -> LiveListRow(row, onClick = { onOpenStream(row.login) }) }
                            }
                        }
                    }

                    if (state.offline.isNotEmpty()) {
                        item {
                            Spacer(Modifier.height(28.dp))
                            Text("Offline · ${state.offline.size}", style = MaterialTheme.typography.headlineMedium, color = c.onSurface)
                            Spacer(Modifier.height(12.dp))
                            OfflineList(state.offline, onOpenStream)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FollowingHeader() {
    val c = PureTvTheme.colors
    Column {
        Text("Your follows".uppercase(), style = PureTvType.kicker, color = c.primary)
        Spacer(Modifier.height(8.dp))
        PageTitle("Following")
        Spacer(Modifier.height(8.dp))
        Text(
            "Every channel you follow lives here, live ones first so you always know who to check on next. " +
                "Everyone else sits below, ready the moment they go live.",
            style = MaterialTheme.typography.bodyLarge,
            color = c.onSurfaceVariant,
        )
    }
}

@Composable
private fun SignedOutPanel(onSignIn: () -> Unit) {
    val c = PureTvTheme.colors
    ExpressivePanel {
        Column {
            Text("Sign in to see who's live", style = MaterialTheme.typography.titleLarge, color = c.onSurface)
            Spacer(Modifier.height(8.dp))
            Text(
                "Connect your Twitch account to pull in your follows and their live status.",
                style = MaterialTheme.typography.bodyMedium,
                color = c.onSurfaceVariant,
            )
            Spacer(Modifier.height(20.dp))
            ExpressiveButton(
                text = "Connect with Twitch",
                onClick = onSignIn,
                style = ExpressiveButtonStyle.Filled,
                size = ExpressiveButtonSize.Medium,
                icon = ExpressiveIcons.SignIn,
            )
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

/** Card-shaped shimmer placeholders, content-sized so they are safe inside a lazy item. */
@Composable
private fun LoadingList() {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        repeat(4) { StreamCardSkeleton(modifier = Modifier.fillMaxWidth()) }
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

/** One row of up to two grid cards; a lone trailing card pads out with a weighted spacer so it keeps a stable width. */
@Composable
private fun LiveGridRow(rowItems: List<FollowRow>, onOpenChannel: (String) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        rowItems.forEach { row ->
            LiveFollowCard(row, onClick = { onOpenChannel(row.login) }, modifier = Modifier.weight(1f))
        }
        repeat(2 - rowItems.size) { Spacer(Modifier.weight(1f)) }
    }
}

@Composable
private fun LiveFollowCard(row: FollowRow, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = PureTvTheme.colors
    val shapes = PureTvTheme.shapes
    ExpressiveCard(onClick = onClick, modifier = modifier) {
        Column {
            Box(modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(shapes.thumbShape)) {
                AsyncImage(
                    model = streamThumbUrl(row.thumbnailUrl, 440, 248),
                    contentDescription = row.displayName,
                    modifier = Modifier.fillMaxSize().background(c.surfaceHigh),
                    contentScale = ContentScale.Crop,
                )
                Box(Modifier.fillMaxSize().background(c.cardScrim))
                LivePill(modifier = Modifier.align(Alignment.TopStart).padding(8.dp), height = 20.dp)
                Text(
                    formatViewerCount(row.viewerCount.toLong()),
                    style = PureTvType.dataSmall,
                    color = Color.White,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp),
                )
            }
            Column(modifier = Modifier.padding(top = 10.dp).heightIn(min = 40.dp)) {
                Text(row.displayName, style = MaterialTheme.typography.titleMedium, color = c.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (row.gameName.isNotBlank()) {
                    Text(row.gameName, style = MaterialTheme.typography.bodySmall, color = c.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
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
                pressRadius = shapes.md,
                color = c.surfaceLow,
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Box(modifier = Modifier.size(width = 104.dp, height = 58.dp).clip(shapes.thumbShape)) {
            AsyncImage(
                model = streamThumbUrl(row.thumbnailUrl, 240, 135),
                contentDescription = row.displayName,
                modifier = Modifier.fillMaxSize().background(c.surfaceHigh),
                contentScale = ContentScale.Crop,
            )
            Box(Modifier.fillMaxSize().background(c.cardScrim))
            LivePill(modifier = Modifier.align(Alignment.TopStart).padding(6.dp), height = 18.dp)
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f).heightIn(min = 40.dp)) {
            Text(row.displayName, style = MaterialTheme.typography.titleMedium, color = c.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (row.gameName.isNotBlank()) {
                Text(row.gameName, style = MaterialTheme.typography.bodySmall, color = c.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text("${formatViewerCount(row.viewerCount.toLong())} watching", style = PureTvType.dataSmall, color = c.onSurfaceVariant)
        }
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
 * FollowRow has no last-seen or last-played field for an offline channel
 * (gameName is only ever populated from a live stream), so this row carries
 * just the name, not an invented secondary line. The trailing button opens
 * the channel page: there is no per-channel notification system in this app
 * to bind a "Notify me" action to.
 */
@Composable
private fun OfflineRow(row: FollowRow, onClick: () -> Unit) {
    val c = PureTvTheme.colors
    val interaction = remember { MutableInteractionSource() }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .expressiveClickable(
                interaction = interaction,
                onClick = onClick,
                restRadius = 0.dp,
                pressRadius = 0.dp,
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        FollowAvatar(displayName = row.displayName, imageUrl = row.avatarUrl, size = 44.dp)
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

/** Circular avatar with an initial-letter fallback for a blank/unresolved [imageUrl]. */
@Composable
private fun FollowAvatar(displayName: String, imageUrl: String?, size: Dp) {
    val c = PureTvTheme.colors
    Box(
        modifier = Modifier.size(size).clip(CircleShape).background(c.surfaceHigh),
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
                displayName.take(1).uppercase(),
                style = MaterialTheme.typography.titleMedium,
                color = c.onSurfaceVariant,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}
