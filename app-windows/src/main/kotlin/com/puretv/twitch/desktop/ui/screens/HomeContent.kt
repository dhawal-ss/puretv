package com.puretv.twitch.desktop.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.puretv.twitch.core.model.StreamInfo
import com.puretv.twitch.desktop.data.FollowStore
import com.puretv.twitch.desktop.data.FollowedChannel
import com.puretv.twitch.desktop.data.WatchProgress
import com.puretv.twitch.desktop.data.WatchProgressStore
import com.puretv.twitch.desktop.ui.FollowCardState
import com.puretv.twitch.desktop.ui.HomeViewModel
import com.puretv.twitch.desktop.ui.VodLaunch
import com.puretv.twitch.desktop.ui.components.Avatar
import com.puretv.twitch.desktop.ui.components.CoverImage
import com.puretv.twitch.desktop.ui.components.EditorialEmptyState
import com.puretv.twitch.desktop.ui.components.ExpressiveButton
import com.puretv.twitch.desktop.ui.components.ExpressiveButtonSize
import com.puretv.twitch.desktop.ui.components.ExpressiveButtonStyle
import com.puretv.twitch.desktop.ui.components.ExpressiveCard
import com.puretv.twitch.desktop.ui.components.ExpressiveIcons
import com.puretv.twitch.desktop.ui.components.LivePill
import com.puretv.twitch.desktop.ui.components.SectionHeading
import com.puretv.twitch.desktop.ui.components.ShieldPill
import com.puretv.twitch.desktop.ui.components.SplitButton
import com.puretv.twitch.desktop.ui.components.StreamCardSkeleton
import com.puretv.twitch.desktop.ui.components.expressiveClickable
import com.puretv.twitch.desktop.ui.components.formatViewerCount
import com.puretv.twitch.desktop.ui.rememberDesktopViewModel
import com.puretv.twitch.desktop.ui.theme.PureTvTheme
import com.puretv.twitch.desktop.ui.theme.PureTvType
import org.koin.core.Koin

/**
 * Home in the Material 3 Expressive language, one scroll container throughout:
 *
 *  1. [HomeHero] spotlights the one most relevant live stream: the first followed
 *     channel that's live, else the top live stream. Skipped when nothing is live,
 *     never invented.
 *  2. "Continue watching": a row of resume cards that tapers width by index (400 /
 *     250 / 150 / 88dp), the mockup's signature move, each carrying its own
 *     progress bar.
 *  3. "From channels you follow": the local follow list has no destination screen
 *     of its own in this app (unlike the mockup, which gives it one), so it keeps
 *     living here, restyled to the same card language as "Live now".
 *  4. "Live now": a 4-column grid of [StreamGridCard]s.
 *
 * Everything lives in ONE [LazyVerticalGrid] (`GridCells.Fixed(4)`) rather than a
 * LazyColumn wrapping a nested LazyVerticalGrid: a full-span `item` carries the
 * hero, each heading and the continue-watching shelf, while the stream cards are
 * ordinary grid cells. That keeps the whole page one scroll container that stays
 * cheap with hundreds of streams, since only the grid cells on screen compose.
 */
@Composable
fun HomeContent(koin: Koin, onOpenChannel: (String) -> Unit, onResumeVod: (VodLaunch) -> Unit) {
    val viewModel = rememberDesktopViewModel { koin.get<HomeViewModel>() }
    val state by viewModel.state.collectAsState()

    val watchStore = remember { koin.get<WatchProgressStore>() }
    val progressMap by watchStore.progress.collectAsState()
    val continueItems = remember(progressMap) { watchStore.continueWatching() }

    // Read/write the same local follow list ChannelContent uses, so the hero's
    // Follow/Following button is real rather than a decorative copy of ViewModel
    // state that goes stale the moment it's clicked.
    val followStore = remember { koin.get<FollowStore>() }
    val followedChannels by followStore.followed.collectAsState()

    LazyVerticalGrid(
        columns = GridCells.Fixed(4),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 32.dp, end = 32.dp, top = 36.dp, bottom = 40.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        when {
            !state.isLoggedIn -> item(span = { GridItemSpan(maxLineSpan) }) {
                EditorialEmptyState(
                    kicker = "Not signed in",
                    title = "Sign in to see live channels",
                    message = "Connect your Twitch account from the Account tab to follow channels and watch live.",
                )
            }

            state.isLoading -> {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    SectionHeading(title = "Live now")
                }
                gridItems(List(8) { it }, key = { it }) { StreamCardSkeleton() }
            }

            else -> {
                val hero = featuredStream(state.following, state.topStreams)
                if (hero != null) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        val isFollowed = followedChannels.any { it.login.equals(hero.login, ignoreCase = true) }
                        HomeHero(
                            hero = hero,
                            isFollowed = isFollowed,
                            onWatch = { onOpenChannel(hero.login) },
                            onToggleFollow = {
                                if (isFollowed) {
                                    followStore.unfollow(hero.login)
                                } else {
                                    followStore.follow(
                                        FollowedChannel(id = hero.userId, login = hero.login, displayName = hero.userName),
                                    )
                                }
                            },
                        )
                    }
                }

                if (continueItems.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Column(Modifier.padding(top = 20.dp)) {
                            SectionHeading(title = "Continue watching")
                            Spacer(Modifier.height(16.dp))
                            LazyRow(
                                modifier = Modifier.fillMaxWidth().height(200.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                itemsIndexed(continueItems, key = { _, p -> p.vodId }) { index, p ->
                                    ContinueWatchingCard(
                                        progress = p,
                                        width = continueCardWidth(index),
                                        onClick = { onResumeVod(VodLaunch(p.vodId, p.channelLogin, p.title, p.thumbnailUrl)) },
                                        onRemove = { watchStore.remove(p.vodId) },
                                    )
                                }
                            }
                        }
                    }
                }

                if (state.following.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Column(Modifier.padding(top = 20.dp)) {
                            SectionHeading(title = "From channels you follow")
                            Spacer(Modifier.height(16.dp))
                            LazyRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                            ) {
                                items(state.following, key = { "fav_${it.login}" }) { ch ->
                                    StreamGridCard(
                                        channelName = ch.displayName,
                                        avatarUrl = ch.avatarUrl.takeIf { it.isNotBlank() },
                                        isLive = ch.isLive,
                                        thumbnailUrl = if (ch.isLive) sizedThumbUrl(ch.thumbnailUrl, 440, 248) else null,
                                        viewerCount = ch.viewerCount,
                                        game = ch.gameName,
                                        title = ch.title,
                                        onClick = { onOpenChannel(ch.login) },
                                        modifier = Modifier.width(240.dp),
                                    )
                                }
                            }
                        }
                    }
                }

                if (state.topStreams.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        SectionHeading(title = "Live now", modifier = Modifier.padding(top = 20.dp))
                    }
                    gridItems(state.topStreams, key = { "live_${it.id}" }) { stream ->
                        StreamGridCard(
                            channelName = stream.userName,
                            avatarUrl = null,
                            isLive = true,
                            thumbnailUrl = sizedThumbUrl(stream.thumbnailUrl, 440, 248),
                            viewerCount = stream.viewerCount,
                            game = stream.gameName,
                            title = stream.title,
                            onClick = { onOpenChannel(stream.userLogin) },
                        )
                    }
                } else if (state.following.isEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        EditorialEmptyState(
                            kicker = "Nothing live",
                            title = "No live streams right now",
                            message = "Your session may have expired. Try signing in again from the Account tab.",
                        )
                    }
                }
            }
        }
    }
}

/** The single stream to spotlight in the hero, with its display copy resolved. */
private data class Featured(
    val login: String,
    // Real Twitch channel id, only populated (and only needed) for a top-stream
    // hero the user isn't following yet, so Follow can write a correct entry.
    // A followed-live hero only ever unfollows, which needs just the login.
    val userId: String,
    val userName: String,
    val title: String,
    val gameName: String,
    val viewerCount: Int,
    val imageUrl: String?,
    val followed: Boolean,
)

/** Prefer the first followed channel that's live; else the top live stream. */
private fun featuredStream(following: List<FollowCardState>, top: List<StreamInfo>): Featured? {
    following.firstOrNull { it.isLive }?.let { f ->
        return Featured(
            login = f.login,
            userId = "",
            userName = f.displayName,
            title = f.title.ifBlank { f.displayName },
            gameName = f.gameName,
            viewerCount = f.viewerCount,
            imageUrl = sizedThumbUrl(f.thumbnailUrl, 1280, 720),
            followed = true,
        )
    }
    top.firstOrNull()?.let { s ->
        return Featured(
            login = s.userLogin,
            userId = s.userId,
            userName = s.userName,
            title = s.title.ifBlank { s.userName },
            gameName = s.gameName,
            viewerCount = s.viewerCount,
            imageUrl = sizedThumbUrl(s.thumbnailUrl, 1280, 720),
            followed = false,
        )
    }
    return null
}

/**
 * 340dp full-bleed art with a horizontal scrim (readable text needs less than
 * two thirds of the width), status pills, the display title, a mono meta line,
 * then Watch now (a [SplitButton], since the caret is a placeholder for a
 * future quality/source picker) beside a live Follow/Following toggle.
 */
@Composable
private fun HomeHero(
    hero: Featured,
    isFollowed: Boolean,
    onWatch: () -> Unit,
    onToggleFollow: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = PureTvTheme.colors
    val shapes = PureTvTheme.shapes
    // The height follows the copy rather than being pinned. A long stream title
    // wraps to a second line at narrower window widths, and against a fixed height
    // that pushed the buttons out through the bottom edge of the card.
    Box(
        modifier
            .fillMaxWidth()
            .heightIn(min = 340.dp)
            .clip(shapes.heroShape),
    ) {
        CoverImage(hero.imageUrl, hero.userName, hero.title, Modifier.matchParentSize())
        Box(Modifier.matchParentSize().background(c.heroScrim))
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth(0.64f)
                .padding(40.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                LivePill()
                ShieldPill("ADS BLOCKED")
                Text(
                    (if (hero.followed) "From a channel you follow" else "Live now").uppercase(),
                    style = PureTvType.kicker,
                    color = c.onSurface.copy(alpha = 0.72f),
                )
            }
            Spacer(Modifier.height(20.dp))
            Text(
                hero.title,
                style = MaterialTheme.typography.displayLarge,
                color = c.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    hero.userName,
                    style = MaterialTheme.typography.bodyLarge,
                    color = c.onSurface,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (hero.gameName.isNotBlank()) {
                    Text("·", color = c.onSurface.copy(alpha = 0.5f))
                    Text(
                        hero.gameName,
                        style = MaterialTheme.typography.bodyLarge,
                        color = c.onSurface.copy(alpha = 0.82f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text("·", color = c.onSurface.copy(alpha = 0.5f))
                Text(
                    "${formatViewerCount(hero.viewerCount)} watching",
                    style = PureTvType.data,
                    color = c.onSurface.copy(alpha = 0.82f),
                )
            }
            Spacer(Modifier.height(28.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SplitButton(
                    text = "Watch now",
                    icon = ExpressiveIcons.Play,
                    onClick = onWatch,
                    trailingIcon = ExpressiveIcons.ExpandMore,
                    onTrailingClick = onWatch,
                )
                ExpressiveButton(
                    text = if (isFollowed) "Following" else "Follow",
                    onClick = onToggleFollow,
                    style = ExpressiveButtonStyle.Outlined,
                    size = ExpressiveButtonSize.XLarge,
                    icon = if (isFollowed) ExpressiveIcons.Check else ExpressiveIcons.Add,
                )
            }
        }
    }
}

/**
 * A resume card whose [width] tapers by shelf position (see [continueCardWidth]):
 * the widest carries a title, meta line and progress bar; the narrowest is art
 * and a bare progress sliver. The remove affordance stays on every tier so no
 * entry becomes un-removable just because it scrolled into a thin slot.
 */
@Composable
private fun ContinueWatchingCard(
    progress: WatchProgress,
    width: Dp,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = PureTvTheme.colors
    val shapes = PureTvTheme.shapes
    val interaction = remember { MutableInteractionSource() }
    val fraction = (progress.positionMs.toFloat() / progress.durationMs.coerceAtLeast(1).toFloat()).coerceIn(0f, 1f)
    val showText = width >= 150.dp
    val showMeta = width >= 250.dp
    val wide = width >= 400.dp

    Box(
        modifier
            .width(width)
            .fillMaxHeight()
            .expressiveClickable(
                interaction = interaction,
                onClick = onClick,
                restRadius = shapes.card,
                hoverRadius = shapes.cardMorph,
            ),
    ) {
        CoverImage(vodThumbUrl(progress.thumbnailUrl), progress.channelLogin, progress.title, Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().background(c.cardScrim))

        if (showText) {
            Column(Modifier.align(Alignment.BottomStart).padding(if (wide) 20.dp else 16.dp)) {
                Text(
                    progress.title.ifBlank { "Past broadcast" },
                    style = if (wide) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium,
                    color = c.onSurface,
                    maxLines = if (wide) 2 else 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (showMeta) {
                    Spacer(Modifier.height(6.dp))
                    val remaining = (progress.durationMs - progress.positionMs).coerceAtLeast(0)
                    val meta = listOfNotNull(
                        progress.channelLogin.takeIf { it.isNotBlank() },
                        formatTimeLeft(remaining),
                    ).joinToString(" · ")
                    Text(meta, style = PureTvType.data, color = c.onSurface.copy(alpha = 0.7f))
                }
                Spacer(Modifier.height(if (wide) 14.dp else 12.dp))
                ProgressTrack(fraction, Modifier.fillMaxWidth())
            }
        } else {
            ProgressTrack(fraction, Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(10.dp))
        }

        val removeSize = if (showText) 26.dp else 20.dp
        IconButton(
            onClick = onRemove,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(6.dp)
                .size(removeSize)
                .clip(CircleShape)
                .background(c.surfaceLowest.copy(alpha = 0.6f)),
        ) {
            Icon(
                Icons.Filled.Close,
                contentDescription = "Remove from Continue watching",
                tint = c.onSurface,
                modifier = Modifier.size(if (showText) 15.dp else 12.dp),
            )
        }
    }
}

/** Progress fill in [PureTvTheme.colors.primary] over a translucent track. */
@Composable
private fun ProgressTrack(fraction: Float, modifier: Modifier = Modifier) {
    val c = PureTvTheme.colors
    Box(
        modifier
            .height(6.dp)
            .clip(PureTvTheme.shapes.pillShape)
            .background(c.onSurface.copy(alpha = 0.24f)),
    ) {
        Box(
            Modifier
                .fillMaxWidth(fraction)
                .fillMaxHeight()
                .clip(PureTvTheme.shapes.pillShape)
                .background(c.primary),
        )
    }
}

/**
 * The "Live now" grid card, also reused for the followed-channels shelf: a 16:9
 * [CoverImage] with a [LivePill] and mono viewer count when live, or a dimmed
 * cover plus centered [Avatar] and an OFFLINE label when not, then the channel
 * row and stream title below the art.
 */
@Composable
private fun StreamGridCard(
    channelName: String,
    avatarUrl: String?,
    isLive: Boolean,
    thumbnailUrl: String?,
    viewerCount: Int,
    game: String,
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = PureTvTheme.colors
    val shapes = PureTvTheme.shapes
    ExpressiveCard(onClick = onClick, modifier = modifier) {
        Column {
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(shapes.thumbShape),
            ) {
                if (isLive) {
                    CoverImage(thumbnailUrl, channelName, title, Modifier.fillMaxSize())
                    Box(Modifier.fillMaxSize().background(c.cardScrim))
                    LivePill(modifier = Modifier.align(Alignment.TopStart).padding(10.dp))
                    Text(
                        formatViewerCount(viewerCount),
                        style = PureTvType.data,
                        color = c.onSurface,
                        modifier = Modifier.align(Alignment.BottomEnd).padding(10.dp),
                    )
                } else {
                    CoverImage(null, channelName, null, Modifier.fillMaxSize().alpha(0.4f))
                    Avatar(channelName, avatarUrl, size = 44, modifier = Modifier.align(Alignment.Center))
                    Text(
                        "OFFLINE",
                        style = PureTvType.dataSmall,
                        color = c.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.BottomStart).padding(10.dp),
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Avatar(channelName, avatarUrl, size = 32)
                Column(Modifier.fillMaxWidth()) {
                    Text(
                        channelName,
                        style = MaterialTheme.typography.titleMedium,
                        color = c.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val subtitle = if (isLive) game else "Offline"
                    if (subtitle.isNotBlank()) {
                        Text(
                            subtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (isLive) c.primary else c.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            if (isLive && title.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = c.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** Width by shelf position: 400 / 250 / 150dp, then a constant 88dp sliver. */
private fun continueCardWidth(index: Int): Dp = when (index) {
    0 -> 400.dp
    1 -> 250.dp
    2 -> 150.dp
    else -> 88.dp
}

/** "2:41:12 left" past the hour mark, else "41:12 left". */
private fun formatTimeLeft(remainingMs: Long): String {
    val totalSeconds = remainingMs / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "%d:%02d:%02d left".format(hours, minutes, seconds)
    } else {
        "%d:%02d left".format(minutes, seconds)
    }
}

/** Fill Twitch's stream/game thumbnail template, which uses {width}/{height}. Null when blank. */
private fun sizedThumbUrl(raw: String, width: Int, height: Int): String? =
    raw.takeIf { it.isNotBlank() }
        ?.replace("{width}", width.toString())
        ?.replace("{height}", height.toString())

/**
 * Fill Twitch's VOD thumbnail template, which uses %{width}/%{height} (percent-brace),
 * distinct from stream/game thumbnails which use {width}/{height} (see [sizedThumbUrl]).
 * Null when blank.
 */
private fun vodThumbUrl(raw: String): String? =
    raw.takeIf { it.isNotBlank() }?.replace("%{width}", "320")?.replace("%{height}", "180")
