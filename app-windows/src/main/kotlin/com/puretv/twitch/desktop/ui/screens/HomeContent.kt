package com.puretv.twitch.desktop.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.puretv.twitch.core.model.StreamInfo
import com.puretv.twitch.desktop.data.WatchProgress
import com.puretv.twitch.desktop.data.WatchProgressStore
import com.puretv.twitch.desktop.ui.FollowCardState
import com.puretv.twitch.desktop.ui.HomeViewModel
import com.puretv.twitch.desktop.ui.VodLaunch
import com.puretv.twitch.desktop.ui.components.CinematicHero
import com.puretv.twitch.desktop.ui.components.CoverImage
import com.puretv.twitch.desktop.ui.components.EditorialEmptyState
import com.puretv.twitch.desktop.ui.components.FollowCard
import com.puretv.twitch.desktop.ui.components.Kicker
import com.puretv.twitch.desktop.ui.components.StreamCard
import com.puretv.twitch.desktop.ui.components.StreamCardSkeleton
import com.puretv.twitch.desktop.ui.components.formatViewerCount
import com.puretv.twitch.desktop.ui.rememberDesktopViewModel
import com.puretv.twitch.desktop.ui.theme.PureTvShape
import com.puretv.twitch.desktop.ui.theme.PureTvTheme
import org.koin.core.Koin

/**
 * Home in the Cinémathèque language: a single editorial column.
 *
 *  1. A [CinematicHero] spotlights the one most relevant live stream — the first
 *     followed channel that's live, else the top live stream. Skipped when nothing
 *     is live.
 *  2. Beneath it, hairline-ruled [Kicker] sections, each a horizontal shelf of
 *     fixed-width cards (poster shelves read most "cinema" and stay performant):
 *     "From channels you follow" (the followed list) and "Live now" (top streams).
 *  3. Followed channels render via [FollowCard]; top streams via [StreamCard].
 *
 * A LazyColumn is its own scroll container, so it must NOT be nested in a
 * verticalScroll.
 */
@Composable
fun HomeContent(koin: Koin, onOpenChannel: (String) -> Unit, onResumeVod: (VodLaunch) -> Unit) {
    val viewModel = rememberDesktopViewModel { koin.get<HomeViewModel>() }
    val state by viewModel.state.collectAsState()

    val watchStore = remember { koin.get<WatchProgressStore>() }
    val progressMap by watchStore.progress.collectAsState()
    val continueItems = remember(progressMap) { watchStore.continueWatching() }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(32.dp),
    ) {
        when {
            !state.isLoggedIn -> item {
                EditorialEmptyState(
                    kicker = "Not signed in",
                    title = "Sign in to see live channels",
                    message = "Connect your Twitch account from the Account tab to follow channels and watch live.",
                    modifier = Modifier.padding(horizontal = SIDE),
                )
            }

            state.isLoading -> {
                item {
                    Kicker("From channels you follow", rule = true, modifier = Modifier.padding(horizontal = SIDE))
                }
                item { SkeletonShelf() }
                item {
                    Kicker("Live now", rule = true, modifier = Modifier.padding(horizontal = SIDE))
                }
                item { SkeletonShelf() }
            }

            else -> {
                val hero = featuredStream(state.following, state.topStreams)
                if (hero != null) {
                    item {
                        CinematicHero(
                            seed = hero.userName,
                            imageUrl = hero.imageUrl,
                            kicker = if (hero.followed) "Live from a channel you follow" else "Live now",
                            title = hero.title,
                            meta = hero.meta,
                            onWatch = { onOpenChannel(hero.login) },
                            modifier = Modifier.padding(horizontal = SIDE),
                        )
                    }
                }

                if (continueItems.isNotEmpty()) {
                    item {
                        Kicker("Continue watching", rule = true, modifier = Modifier.padding(horizontal = SIDE))
                    }
                    item {
                        LazyRow(
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(horizontal = SIDE),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            items(continueItems, key = { it.vodId }) { p ->
                                ContinueWatchingCard(
                                    progress = p,
                                    onClick = { onResumeVod(VodLaunch(p.vodId, p.channelLogin, p.title, p.thumbnailUrl)) },
                                    onRemove = { watchStore.remove(p.vodId) },
                                    modifier = Modifier.width(CARD_WIDTH),
                                )
                            }
                        }
                    }
                }

                if (state.following.isNotEmpty()) {
                    item {
                        Kicker(
                            "From channels you follow",
                            rule = true,
                            modifier = Modifier.padding(horizontal = SIDE),
                        )
                    }
                    item {
                        Shelf(state.following, key = { "fav_${it.login}" }) { ch ->
                            FollowCard(
                                state = ch,
                                onClick = { onOpenChannel(ch.login) },
                                modifier = Modifier.width(CARD_WIDTH),
                            )
                        }
                    }
                }

                if (state.topStreams.isNotEmpty()) {
                    item {
                        Kicker("Live now", rule = true, modifier = Modifier.padding(horizontal = SIDE))
                    }
                    item {
                        Shelf(state.topStreams, key = { "live_${it.id}" }) { stream ->
                            StreamCard(
                                stream = stream,
                                onClick = { onOpenChannel(stream.userLogin) },
                                modifier = Modifier.width(CARD_WIDTH),
                            )
                        }
                    }
                } else if (state.following.isEmpty()) {
                    item {
                        EditorialEmptyState(
                            kicker = "Nothing live",
                            title = "No live streams right now",
                            message = "Your session may have expired. Try signing in again from the Account tab.",
                            modifier = Modifier.padding(horizontal = SIDE),
                        )
                    }
                }
            }
        }
    }
}

/** A horizontal shelf of fixed-width cards — the cinema-shelf primitive. */
@Composable
private fun <T> Shelf(items: List<T>, key: (T) -> Any, card: @Composable (T) -> Unit) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = SIDE),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        items(items, key = key) { card(it) }
    }
}

/** Loading shelf: a row of poster-shaped skeletons. */
@Composable
private fun SkeletonShelf() {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = SIDE),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        items(5) { StreamCardSkeleton(modifier = Modifier.width(CARD_WIDTH)) }
    }
}

/** The single stream to spotlight in the hero, with its display copy resolved. */
private data class Featured(
    val login: String,
    val userName: String,
    val title: String,
    val meta: String,
    val imageUrl: String?,
    val followed: Boolean,
)

/** Prefer the first followed channel that's live; else the top live stream. */
private fun featuredStream(following: List<FollowCardState>, top: List<StreamInfo>): Featured? {
    following.firstOrNull { it.isLive }?.let { f ->
        return Featured(
            login = f.login,
            userName = f.displayName,
            title = f.title.ifBlank { f.displayName },
            meta = heroMeta(f.displayName, f.gameName, f.viewerCount),
            imageUrl = heroImage(f.thumbnailUrl),
            followed = true,
        )
    }
    top.firstOrNull()?.let { s ->
        return Featured(
            login = s.userLogin,
            userName = s.userName,
            title = s.title.ifBlank { s.userName },
            meta = heroMeta(s.userName, s.gameName, s.viewerCount),
            imageUrl = heroImage(s.thumbnailUrl),
            followed = false,
        )
    }
    return null
}

private fun heroMeta(name: String, game: String, viewers: Int): String =
    buildList {
        if (name.isNotBlank()) add(name)
        if (game.isNotBlank()) add(game)
        add("${formatViewerCount(viewers)} watching")
    }.joinToString(" · ")

private fun heroImage(thumbnailUrl: String): String? =
    thumbnailUrl.takeIf { it.isNotBlank() }
        ?.replace("{width}", "1280")
        ?.replace("{height}", "720")

@Composable
private fun ContinueWatchingCard(
    progress: WatchProgress,
    onClick: () -> Unit,
    onRemove: () -> Unit,           // the ✕ remove affordance
    modifier: Modifier = Modifier,
) {
    val c = PureTvTheme.colors
    Column(modifier = modifier.clickable(onClick = onClick)) {
        Box(Modifier.fillMaxWidth().height(118.dp).clip(PureTvShape.lg)) {
            CoverImage(
                imageUrl = vodThumbUrl(progress.thumbnailUrl),
                seed = progress.channelLogin,
                contentDescription = progress.title,
                modifier = Modifier.fillMaxSize(),
            )
            val frac = (progress.positionMs.toFloat() /
                progress.durationMs.coerceAtLeast(1).toFloat()).coerceIn(0f, 1f)
            Box(
                Modifier.align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .height(3.dp)
                    .background(c.surfaceVariant),
            ) {
                Box(Modifier.fillMaxWidth(frac).fillMaxHeight().background(c.twitchPurple))
            }
            IconButton(
                onClick = onRemove,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .size(26.dp)
                    .background(c.background.copy(alpha = 0.6f), CircleShape),
            ) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = "Remove from Continue watching",
                    tint = c.textPrimary,
                    modifier = Modifier.size(15.dp),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            progress.title.ifBlank { "Past broadcast" },
            style = MaterialTheme.typography.bodyMedium,
            color = c.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Fill Twitch's VOD thumbnail template, which uses %{width}/%{height} (percent-brace)
 * — distinct from stream/game thumbnails which use {width}/{height} (see heroImage).
 * Null when blank.
 */
private fun vodThumbUrl(raw: String): String? =
    raw.takeIf { it.isNotBlank() }?.replace("%{width}", "320")?.replace("%{height}", "180")

private val SIDE = 28.dp
private val CARD_WIDTH = 210.dp
