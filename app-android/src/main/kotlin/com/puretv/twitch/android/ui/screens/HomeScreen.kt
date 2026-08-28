package com.puretv.twitch.android.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.puretv.twitch.android.data.db.WatchHistoryEntry
import com.puretv.twitch.android.ui.HomeViewModel
import com.puretv.twitch.android.ui.components.AdFreeChip
import com.puretv.twitch.android.ui.components.EmptyState
import com.puretv.twitch.android.ui.components.ErrorState
import com.puretv.twitch.android.ui.components.ExpressiveButton
import com.puretv.twitch.android.ui.components.ExpressiveButtonSize
import com.puretv.twitch.android.ui.components.ExpressiveButtonStyle
import com.puretv.twitch.android.ui.components.ExpressiveCard
import com.puretv.twitch.android.ui.components.ExpressiveIconButton
import com.puretv.twitch.android.ui.components.ExpressiveIcons
import com.puretv.twitch.android.ui.components.GameTile
import com.puretv.twitch.android.ui.components.LivePill
import com.puretv.twitch.android.ui.components.SectionHeading
import com.puretv.twitch.android.ui.components.ShieldPill
import com.puretv.twitch.android.ui.components.StreamCard
import com.puretv.twitch.android.ui.components.StreamCardSkeleton
import com.puretv.twitch.android.ui.components.formatViewerCount
import com.puretv.twitch.android.ui.components.streamThumbUrl
import com.puretv.twitch.android.ui.theme.PureTvTheme
import com.puretv.twitch.android.ui.theme.PureTvType
import com.puretv.twitch.android.update.AndroidUpdateManager
import com.puretv.twitch.android.update.AndroidUpdateState
import com.puretv.twitch.core.model.StreamInfo
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

/**
 * Home in the Material 3 Expressive language, adapted from the desktop's
 * hero-plus-shelves grid to a single scrolling column (a phone has no room for a
 * hero beside a 4-wide grid, so everything stacks):
 *
 *  1. [HomeHero] spotlights the one most relevant live stream: the first live
 *     followed channel, else the top live stream. Skipped when nothing is live.
 *  2. "Continue watching": [HomeUiState] only carries [WatchHistoryEntry] (login,
 *     display name, last-watched time, total watch time), not the desktop's
 *     position/duration VOD progress, so this is a recently-watched shelf rather
 *     than a resumable-progress one, restyled onto [ExpressiveCard] without a
 *     progress bar it has no data to draw.
 *  3. "Live now": followed channels currently live, restyled onto [StreamCard].
 *  4. "Browse categories": the games rail, with a "See all" action to Browse.
 *  5. "Top streams": the general discovery list, one full-width [StreamCard] per
 *     row rather than desktop's 4-column grid.
 *
 * The whole body is ONE [LazyColumn]: each section is its own lazy item (some
 * holding a nested, independently-scrolling [LazyRow]), and "Top streams" itself
 * stays item-per-card so a long list never composes more than what's on screen.
 */
@Composable
fun HomeScreen(
    onOpenStream: (String) -> Unit,
    onOpenChannel: (String) -> Unit,
    onOpenBrowse: () -> Unit,
    onOpenCategory: (String) -> Unit,
    onOpenSearch: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenLogin: () -> Unit,
    viewModel: HomeViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val updateManager = koinInject<AndroidUpdateManager>()
    val updateState by updateManager.state.collectAsState()
    val c = PureTvTheme.colors

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("PureTV", color = c.onSurface, style = MaterialTheme.typography.titleLarge)
                        // The brand badge of honor, always visible on the home screen.
                        AdFreeChip()
                    }
                },
                actions = {
                    ExpressiveIconButton(icon = ExpressiveIcons.Search, contentDescription = "Search", onClick = onOpenSearch)
                    ExpressiveIconButton(icon = ExpressiveIcons.Settings, contentDescription = "Settings", onClick = onOpenSettings)
                    ExpressiveIconButton(
                        icon = ExpressiveIcons.Account,
                        contentDescription = "Account",
                        onClick = if (state.isLoggedIn) onOpenSettings else onOpenLogin,
                    )
                },
            )
        },
        containerColor = c.surfaceLowest,
    ) { padding ->
        val isEmpty = state.followedLive.isEmpty() && state.games.isEmpty() &&
            state.topStreams.isEmpty() && state.continueWatching.isEmpty()
        when {
            state.isLoading && isEmpty -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item { SectionHeading(title = "Live now") }
                items(4) { StreamCardSkeleton(modifier = Modifier.fillMaxWidth()) }
            }
            state.error != null && isEmpty -> ErrorState(
                message = state.error!!,
                onRetry = viewModel::refresh,
                modifier = Modifier.padding(padding),
            )
            isEmpty -> EmptyState(
                title = "Nothing to watch yet",
                subtitle = "We couldn't load any streams. Check your connection and try again.",
                modifier = Modifier.padding(padding),
            )
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                (updateState as? AndroidUpdateState.Available)?.let { available ->
                    item { UpdateAvailableBanner(versionName = available.info.versionName, onClick = onOpenSettings) }
                }

                val hero = featuredStream(state.followedLive, state.topStreams)
                if (hero != null) {
                    item { HomeHero(hero = hero, onWatch = { onOpenStream(hero.login) }) }
                }

                if (state.continueWatching.isNotEmpty()) {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            SectionHeading(title = "Continue watching")
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                items(state.continueWatching, key = { it.channelLogin }) { entry ->
                                    ContinueWatchingCard(entry = entry, onClick = { onOpenStream(entry.channelLogin) })
                                }
                            }
                        }
                    }
                }

                if (state.followedLive.isNotEmpty()) {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            SectionHeading(title = "Live now")
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                items(state.followedLive, key = { it.userLogin }) { s ->
                                    StreamCard(stream = s, onClick = { onOpenStream(s.userLogin) }, modifier = Modifier.width(260.dp))
                                }
                            }
                        }
                    }
                }

                if (state.games.isNotEmpty()) {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            SectionHeading(title = "Browse categories", actionLabel = "See all", onAction = onOpenBrowse)
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                items(state.games, key = { it.id }) { g ->
                                    GameTile(game = g, onClick = { onOpenCategory(g.id) })
                                }
                            }
                        }
                    }
                }

                if (state.topStreams.isNotEmpty()) {
                    item { SectionHeading(title = "Top streams") }
                    items(state.topStreams, key = { it.userLogin }) { s ->
                        StreamCard(stream = s, onClick = { onOpenStream(s.userLogin) }, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        }
    }
}

/** The single stream to spotlight in the hero, with its display copy resolved. */
private data class Featured(
    val login: String,
    val userName: String,
    val title: String,
    val gameName: String,
    val viewerCount: Int,
    val imageUrl: String,
)

/**
 * Prefer the first live followed channel; else the top live stream. Unlike
 * desktop, [HomeUiState.followedLive] is already live-only, so there is no
 * offline-followed case to fall through here.
 */
private fun featuredStream(followedLive: List<StreamInfo>, topStreams: List<StreamInfo>): Featured? {
    val s = followedLive.firstOrNull() ?: topStreams.firstOrNull() ?: return null
    return Featured(
        login = s.userLogin,
        userName = s.userName,
        title = s.title.ifBlank { s.userName },
        gameName = s.gameName,
        viewerCount = s.viewerCount,
        imageUrl = streamThumbUrl(s.thumbnailUrl, 1280, 720),
    )
}

/**
 * Full-bleed art under a vertical [PureTvAndroidColors.heroScrim] (a phone reads
 * top-to-bottom, not left-to-right, so the scrim darkens the bottom third rather
 * than a side), status pills, the stream title, a mono meta line, then a
 * full-width Watch now. The height follows the copy rather than being pinned: a
 * long title wraps to a second line, and against a fixed height that pushes the
 * button out through the bottom edge of the card.
 */
@Composable
private fun HomeHero(hero: Featured, onWatch: () -> Unit, modifier: Modifier = Modifier) {
    val c = PureTvTheme.colors
    val shapes = PureTvTheme.shapes
    Box(
        modifier
            .fillMaxWidth()
            .heightIn(min = 220.dp)
            .clip(shapes.heroShape)
            .background(c.surfaceLow),
    ) {
        AsyncImage(
            model = hero.imageUrl,
            contentDescription = hero.title,
            modifier = Modifier.matchParentSize(),
            contentScale = ContentScale.Crop,
        )
        Box(Modifier.matchParentSize().background(c.heroScrim))
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(20.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LivePill()
                ShieldPill("ADS BLOCKED")
            }
            Spacer(Modifier.height(14.dp))
            Text(
                hero.title,
                style = MaterialTheme.typography.displaySmall,
                color = c.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                heroMetaLine(hero),
                style = PureTvType.data,
                color = c.onSurface.copy(alpha = 0.82f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(18.dp))
            ExpressiveButton(
                text = "Watch now",
                onClick = onWatch,
                style = ExpressiveButtonStyle.Filled,
                size = ExpressiveButtonSize.XLarge,
                icon = ExpressiveIcons.Play,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

private fun heroMetaLine(hero: Featured): String =
    listOfNotNull(
        hero.userName,
        hero.gameName.takeIf { it.isNotBlank() },
        "${formatViewerCount(hero.viewerCount.toLong())} watching",
    ).joinToString(" · ")

@Composable
private fun UpdateAvailableBanner(versionName: String, onClick: () -> Unit) {
    val c = PureTvTheme.colors
    ExpressiveCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(ExpressiveIcons.Download, contentDescription = null, tint = c.primary, modifier = Modifier.size(22.dp))
            Text(
                "Update available: $versionName. Open Settings to install.",
                style = MaterialTheme.typography.bodyMedium,
                color = c.onSurface,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/**
 * A recently-watched card: [WatchHistoryEntry] has no thumbnail, position or
 * duration (that is VOD-progress data the desktop's WatchProgressStore tracks
 * and this app does not), so this reads as a name and a relative time rather
 * than desktop's art-plus-progress-bar card. Tapping reopens the channel live,
 * which is the only destination this data can back.
 */
@Composable
private fun ContinueWatchingCard(entry: WatchHistoryEntry, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = PureTvTheme.colors
    ExpressiveCard(onClick = onClick, modifier = modifier.width(220.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(ExpressiveIcons.Resume, contentDescription = null, tint = c.primary, modifier = Modifier.size(20.dp))
                Text(
                    entry.channelDisplayName,
                    style = MaterialTheme.typography.titleMedium,
                    color = c.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                "Watched ${formatRecency(entry.lastWatchedEpochMs)}",
                style = PureTvType.data,
                color = c.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

/** "just now" under a minute, "N min ago" under an hour, "Nh ago" under a day, else "Nd ago". */
private fun formatRecency(epochMs: Long): String {
    val minutes = (System.currentTimeMillis() - epochMs).coerceAtLeast(0) / 60_000
    return when {
        minutes < 1 -> "just now"
        minutes < 60 -> "$minutes min ago"
        minutes < 1_440 -> "${minutes / 60}h ago"
        else -> "${minutes / 1_440}d ago"
    }
}
