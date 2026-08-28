package com.puretv.twitch.tv.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.puretv.twitch.core.follows.FollowRow
import com.puretv.twitch.tv.ui.TvFollowingViewModel
import com.puretv.twitch.tv.ui.components.ExpressiveIcons
import com.puretv.twitch.tv.ui.components.TvButtonSize
import com.puretv.twitch.tv.ui.components.TvButtonStyle
import com.puretv.twitch.tv.ui.components.TvExpressiveButton
import com.puretv.twitch.tv.ui.components.TvLivePill
import com.puretv.twitch.tv.ui.components.TvNavDestination
import com.puretv.twitch.tv.ui.components.TvNavDrawer
import com.puretv.twitch.tv.ui.components.TvPageTitle
import com.puretv.twitch.tv.ui.components.TvPanel
import com.puretv.twitch.tv.ui.components.TvSectionHeading
import com.puretv.twitch.tv.ui.components.formatTvViewerCount
import com.puretv.twitch.tv.ui.components.tvFocusClickable
import com.puretv.twitch.tv.ui.theme.PureTvTvTheme
import com.puretv.twitch.tv.ui.theme.PureTvTvType
import org.koin.androidx.compose.koinViewModel

/**
 * The Following destination: live follows first in a focusable card grid, then
 * everyone else below as focusable rows in a [PureTvTvTheme.colors.surfaceLow]
 * container. Built entirely on [TvFollowingViewModel] so it always agrees with
 * Home's "Following - Live now" shelf.
 *
 * Reachable only from [TvNavDrawer], so it carries its OWN copy of the rail
 * (mirroring [TvHomeScreen]'s layout, not the plain-back-button shell every
 * other destination uses) so the rail and its Section 7.3 D-pad rule -
 * pressing LEFT on the leftmost column moves focus into the rail - stay
 * available no matter which nav destination the viewer is on.
 */
@Composable
fun TvFollowingScreen(
    onOpenChannel: (String) -> Unit,
    onOpenHome: () -> Unit,
    onOpenBrowse: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenLogin: () -> Unit,
    viewModel: TvFollowingViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val c = PureTvTvTheme.colors
    val drawerFocusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val firstFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) { viewModel.refresh() }

    val noData = state.live.isEmpty() && state.offline.isEmpty()

    // Whichever branch below renders the screen's first interactive element -
    // "Sign in", "Try again", or the first live/offline row - claims initial
    // focus once it actually exists. A pure loading pass or a genuinely empty
    // follow list has nothing to claim, so those are left alone.
    //
    // Latched by TARGET KIND, not a one-shot flag: `isLoggedIn` starts false
    // and flips true asynchronously once TvFollowingViewModel's settings
    // collector emits, so a signed-in cold start briefly renders SignedOutBlock
    // first. A single claim-once flag would latch onto that transient button
    // and never move to the real content once it lands; re-deriving the kind
    // and only re-claiming when it changes lets focus follow the branch that's
    // actually on screen, while still never yanking focus back within a branch.
    val targetKind = when {
        !state.isLoggedIn -> "signedOut"
        state.errored && noData -> "retry"
        !noData -> "content"
        else -> null
    }
    var claimedKind by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(targetKind, state.live, state.offline, claimedKind) {
        if (targetKind == null || targetKind == claimedKind) return@LaunchedEffect
        if (runCatching { firstFocusRequester.requestFocus() }.isSuccess) claimedKind = targetKind
    }

    Row(modifier = Modifier.fillMaxSize().background(c.surface)) {
        TvNavDrawer(
            selected = TvNavDestination.FOLLOWING,
            isLoggedIn = state.isLoggedIn,
            onSelect = { dest ->
                when (dest) {
                    TvNavDestination.HOME -> onOpenHome()
                    TvNavDestination.FOLLOWING -> Unit
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
                .onPreviewKeyEvent { event ->
                    // Section 7.3: DPAD LEFT on the leftmost column re-targets focus
                    // into the rail instead of being swallowed by Compose's focus search.
                    if (event.key == Key.DirectionLeft) {
                        // Only hand focus to the rail when there is genuinely
                        // nothing further left. Swallowing every LEFT press made
                        // it impossible to walk back along a shelf or a grid row,
                        // since the rail stole the very first one.
                        if (event.type == KeyEventType.KeyDown &&
                            !focusManager.moveFocus(FocusDirection.Left)
                        ) {
                            runCatching { drawerFocusRequester.requestFocus() }
                        }
                        true
                    } else {
                        false
                    }
                }
                .verticalScroll(rememberScrollState())
                .padding(start = 32.dp, top = 32.dp, end = 32.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(32.dp),
        ) {
            TvPageTitle("Following")

            when {
                !state.isLoggedIn -> SignedOutBlock(onSignIn = onOpenLogin, focusRequester = firstFocusRequester)

                // isLoading is only ever true while there's no data yet (see
                // TvFollowingViewModel.loadOnce), so this never flashes on a refresh.
                state.isLoading && noData ->
                    Text("Loading…", style = MaterialTheme.typography.bodyLarge, color = c.onSurfaceVariant)

                state.errored && noData -> ErrorBlock(onRetry = viewModel::refresh, focusRequester = firstFocusRequester)

                noData -> TvPanel {
                    Column {
                        Text("No follows yet", style = MaterialTheme.typography.titleLarge, color = c.onSurface)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Channels you follow on Twitch will show up here, live ones first.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = c.onSurfaceVariant,
                        )
                    }
                }

                else -> {
                    if (state.live.isNotEmpty()) {
                        LiveSection(
                            rows = state.live,
                            onOpenChannel = onOpenChannel,
                            firstCardFocusRequester = firstFocusRequester,
                        )
                    }
                    if (state.offline.isNotEmpty()) {
                        OfflineSection(
                            rows = state.offline,
                            onOpenChannel = onOpenChannel,
                            firstRowFocusRequester = if (state.live.isEmpty()) firstFocusRequester else null,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SignedOutBlock(onSignIn: () -> Unit, focusRequester: FocusRequester) {
    val c = PureTvTvTheme.colors
    TvPanel {
        Column {
            Text("Sign in to see who's live", style = MaterialTheme.typography.titleLarge, color = c.onSurface)
            Spacer(Modifier.height(8.dp))
            Text(
                "Connect your Twitch account to pull in your follows and their live status.",
                style = MaterialTheme.typography.bodyLarge,
                color = c.onSurfaceVariant,
            )
            Spacer(Modifier.height(24.dp))
            TvExpressiveButton(
                text = "Sign in",
                onClick = onSignIn,
                style = TvButtonStyle.Filled,
                size = TvButtonSize.Medium,
                icon = ExpressiveIcons.SignIn,
                modifier = Modifier.focusRequester(focusRequester),
            )
        }
    }
}

@Composable
private fun ErrorBlock(onRetry: () -> Unit, focusRequester: FocusRequester) {
    val c = PureTvTvTheme.colors
    TvPanel {
        Column {
            Text("Couldn't load your follows", style = MaterialTheme.typography.titleLarge, color = c.onSurface)
            Spacer(Modifier.height(8.dp))
            Text(
                "The last request failed. Check your connection and try again.",
                style = MaterialTheme.typography.bodyLarge,
                color = c.onSurfaceVariant,
            )
            Spacer(Modifier.height(24.dp))
            TvExpressiveButton(
                text = "Try again",
                onClick = onRetry,
                style = TvButtonStyle.Tonal,
                modifier = Modifier.focusRequester(focusRequester),
            )
        }
    }
}

/**
 * The live shelf, as a wrapping grid rather than a fixed row count: [FlowRow]
 * lets card count per line follow the screen width instead of a hardcoded
 * chunk size, while still living happily inside the screen's outer
 * `verticalScroll` (a `LazyVerticalGrid` here would fight that for height).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LiveSection(
    rows: List<FollowRow>,
    onOpenChannel: (String) -> Unit,
    firstCardFocusRequester: FocusRequester?,
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        TvSectionHeading("Live · ${rows.size}")
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            rows.forEachIndexed { index, row ->
                TvFollowLiveCard(
                    row = row,
                    onClick = { onOpenChannel(row.login) },
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

/**
 * Same card grammar as [com.puretv.twitch.tv.ui.components.TvStreamCard],
 * built on [FollowRow] instead of `StreamInfo`. The outer 6dp padding is the
 * neighbour's breathing room the focus-scale grows into, matched by the 24dp
 * [FlowRow] gap above so a focused card's ~8% grow never overlaps the next one.
 */
@Composable
private fun TvFollowLiveCard(row: FollowRow, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = PureTvTvTheme.colors
    val shapes = PureTvTvTheme.shapes
    val interaction = remember { MutableInteractionSource() }

    Column(
        modifier = modifier
            .padding(6.dp)
            .width(220.dp)
            .tvFocusClickable(
                interaction = interaction,
                onClick = onClick,
                restRadius = shapes.card,
                focusRadius = shapes.cardFocus,
                color = c.surfaceContainer,
                focusColor = c.surfaceHigh,
            )
            .padding(12.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(shapes.thumbShape),
        ) {
            val thumb = row.thumbnailUrl.streamThumb(440, 248)
            if (thumb != null) {
                AsyncImage(
                    model = thumb,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Box(Modifier.fillMaxSize().background(c.surfaceHigh))
            }
            Box(Modifier.fillMaxSize().background(c.cardScrim))
            TvLivePill(modifier = Modifier.align(Alignment.TopStart).padding(10.dp), height = 28.dp)
            Text(
                text = formatTvViewerCount(row.viewerCount.toLong()),
                style = PureTvTvType.dataSmall,
                color = Color.White,
                modifier = Modifier.align(Alignment.BottomEnd).padding(10.dp),
            )
        }
        Column(Modifier.padding(top = 10.dp)) {
            Text(
                text = row.displayName,
                style = MaterialTheme.typography.titleMedium,
                color = c.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = row.gameName.ifBlank { "Live" },
                style = MaterialTheme.typography.bodyMedium,
                color = c.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** The single surfaceLow container holding every offline follow as a flat row list. */
@Composable
private fun OfflineSection(
    rows: List<FollowRow>,
    onOpenChannel: (String) -> Unit,
    firstRowFocusRequester: FocusRequester?,
) {
    val c = PureTvTvTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        TvSectionHeading("Offline · ${rows.size}")
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(PureTvTvTheme.shapes.cardShape)
                .background(c.surfaceLow),
        ) {
            rows.forEachIndexed { index, row ->
                TvFollowOfflineRow(
                    row = row,
                    onClick = { onOpenChannel(row.login) },
                    modifier = if (index == 0 && firstRowFocusRequester != null) {
                        Modifier.focusRequester(firstRowFocusRequester)
                    } else {
                        Modifier
                    },
                )
            }
        }
    }
}

/**
 * FollowRow has no last-seen/last-played field for an offline channel (gameName
 * is only ever populated from a live stream), so this row carries just the name
 * and status, not an invented secondary line. One focusable target per row,
 * same grammar as [TvSearchResultRow] in [TvSearchScreen], rather than a
 * second nested focus stop for a trailing button.
 */
@Composable
private fun TvFollowOfflineRow(row: FollowRow, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = PureTvTvTheme.colors
    val shapes = PureTvTvTheme.shapes
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        modifier = modifier
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
            .padding(horizontal = 24.dp, vertical = 16.dp),
    ) {
        TvFollowAvatar(displayName = row.displayName, imageUrl = row.avatarUrl)
        Text(
            text = row.displayName,
            style = MaterialTheme.typography.titleMedium,
            color = if (focused) c.onPrimary else c.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "Offline",
            style = PureTvTvType.data,
            color = if (focused) c.onPrimary else c.onSurfaceVariant,
        )
    }
}

@Composable
private fun TvFollowAvatar(displayName: String, imageUrl: String?, modifier: Modifier = Modifier) {
    val c = PureTvTvTheme.colors
    Box(
        modifier = modifier.size(52.dp).clip(CircleShape).background(c.surfaceHigh),
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

/** Twitch hands back thumbnail URLs with `{width}`/`{height}` placeholders. Blank for an offline channel. */
private fun String.streamThumb(width: Int, height: Int): String? =
    takeIf { it.isNotBlank() }?.replace("{width}", "$width")?.replace("{height}", "$height")
