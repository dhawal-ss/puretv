package com.puretv.twitch.tv.ui.screens

import com.puretv.twitch.tv.ui.theme.TvShelfScroll
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.puretv.twitch.core.model.StreamInfo
import com.puretv.twitch.core.playback.ContinueItem
import com.puretv.twitch.core.playback.FlipOrigin
import com.puretv.twitch.core.vod.WatchProgress
import com.puretv.twitch.tv.ui.VodLaunch
import com.puretv.twitch.tv.ui.components.TvRecentChannelCard
import com.puretv.twitch.tv.ui.components.TvVodResumeCard
import com.puretv.twitch.tv.ui.HomeViewModel
import com.puretv.twitch.tv.ui.components.ExpressiveIcons
import com.puretv.twitch.tv.ui.components.TvButtonSize
import com.puretv.twitch.tv.ui.components.TvButtonStyle
import com.puretv.twitch.tv.ui.components.TvExpressiveButton
import com.puretv.twitch.tv.ui.components.TvLivePill
import com.puretv.twitch.tv.ui.components.TvNavDestination
import com.puretv.twitch.tv.ui.components.TvNavDrawer
import com.puretv.twitch.tv.ui.components.TvSectionHeading
import com.puretv.twitch.tv.ui.components.TvShieldPill
import com.puretv.twitch.tv.ui.components.TvStreamCard
import com.puretv.twitch.tv.ui.components.formatTvViewerCount
import com.puretv.twitch.tv.ui.components.TvPanel
import com.puretv.twitch.tv.ui.theme.PureTvTvTheme
import com.puretv.twitch.tv.ui.theme.PureTvTvType
import com.puretv.twitch.tv.update.TvUpdateManager
import com.puretv.twitch.tv.update.TvUpdateState
import kotlinx.coroutines.delay
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

/**
 * SECTION 07.2 / 07.3 [CRITICAL] — landing screen: persistent left
 * [TvNavDrawer], a hero band spotlighting the single most relevant live
 * stream (first followed channel that's live, else the top live stream),
 * then horizontally-scrolling shelves ("Following" / "Live Channels") of
 * [TvStreamCard]s.
 *
 * Focus handling: the hero's "Watch now" button (or, when nothing is live
 * yet, the first card of the first shelf) holds a [FocusRequester] that's
 * claimed the moment its target actually exists in the tree — state arrives
 * asynchronously (cached-first paint, then network), so the claim retries as
 * new data lands rather than firing once against an empty screen, and stops
 * for good once it succeeds so it never steals focus back later.
 * `onPreviewKeyEvent` on the content column intercepts DPAD_LEFT when focus
 * is already on the leftmost column and redirects it into the nav rail —
 * satisfying Section 7.3's rule "D-pad LEFT on the leftmost item opens the
 * nav drawer" without fighting Compose's default focus-search (which would
 * otherwise have nowhere to go).
 */
@Composable
fun TvHomeScreen(
    onOpenStream: (String) -> Unit,
    onOpenChannel: (String) -> Unit,
    onOpenVod: (VodLaunch) -> Unit,
    onOpenFollowing: () -> Unit,
    onOpenBrowse: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenLogin: () -> Unit,
    viewModel: HomeViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val updateManager = koinInject<TvUpdateManager>()
    val updateState by updateManager.state.collectAsState()
    val c = PureTvTvTheme.colors
    val drawerFocusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val heroFocusRequester = remember { FocusRequester() }
    val firstCardFocusRequester = remember { FocusRequester() }
    val lifecycleOwner = LocalLifecycleOwner.current

    val hero = state.followedLive.firstOrNull() ?: state.topStreams.firstOrNull()

    // The row a stream is opened from is the set the remote flips through (when the
    // channel is not one of the viewer's live follows, which are tried first).
    val flipOrigin = koinInject<FlipOrigin>()
    val openFrom = { row: List<StreamInfo>, login: String ->
        flipOrigin.record(row.map { it.userLogin })
        onOpenStream(login)
    }

    // Data arrives asynchronously (cache, then network), so the very first
    // composition usually has nothing to focus yet. Keep retrying — cheaply,
    // since it's a no-op once claimed — until the actual initial-focus target
    // (the hero's "Watch now" button, or the first shelf card when nothing is
    // live) exists, then stop for good so it never yanks focus away later.
    var initialFocusClaimed by remember { mutableStateOf(false) }
    LaunchedEffect(hero, state.followedLive, state.topStreams, initialFocusClaimed) {
        if (initialFocusClaimed) return@LaunchedEffect
        if (hero == null && state.followedLive.isEmpty() && state.topStreams.isEmpty()) return@LaunchedEffect
        val requester = if (hero != null) heroFocusRequester else firstCardFocusRequester
        initialFocusClaimed = runCatching { requester.requestFocus() }.isSuccess
    }

    // Keep "Live Now" current. The TV app used to load top streams exactly once
    // at ViewModel creation, so a long-lived (Fire TV never kills the process)
    // session froze on the first snapshot. Refresh on every return to the
    // foreground and then poll while the screen stays resumed; the loop is
    // cancelled the moment the app is backgrounded so it never polls unseen.
    LaunchedEffect(Unit) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                viewModel.refreshIfStale()
                delay(HOME_REFRESH_INTERVAL_MS)
            }
        }
    }

    Row(modifier = Modifier.fillMaxSize().background(c.surface)) {
        TvNavDrawer(
            selected = TvNavDestination.HOME,
            isLoggedIn = state.isLoggedIn,
            onSelect = { dest ->
                when (dest) {
                    TvNavDestination.HOME -> Unit
                    TvNavDestination.FOLLOWING -> onOpenFollowing()
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
            // Quality-of-life: a newer TV APK is available — one click jumps to
            // Settings where the download & install lives. NeedsInstallConsent
            // counts too: that is the state where the update is furthest along
            // and most easily forgotten, since it is waiting on the viewer.
            when (val u = updateState) {
                is TvUpdateState.Available -> u.info.versionName
                is TvUpdateState.NeedsInstallConsent -> u.info.versionName
                else -> null
            }?.let { versionName ->
                UpdateBanner(versionName = versionName, onOpenSettings = onOpenSettings)
            }

            hero?.let { h ->
                // The ?: chain above always prefers a followed live stream, so
                // whenever that list isn't empty, `h` IS its first entry.
                HomeHero(
                    stream = h,
                    followedLive = state.followedLive.isNotEmpty(),
                    onWatch = { openFrom(if (state.followedLive.isNotEmpty()) state.followedLive else state.topStreams, h.userLogin) },
                    watchFocusRequester = heroFocusRequester,
                )
            }

            ContinueRow(
                row = state.continueWatching,
                onOpenChannel = { item ->
                    if (item.stream != null) {
                        // The remote flips through the live channels in this row.
                        flipOrigin.record(
                            state.continueWatching.filterIsInstance<ContinueItem.Channel>()
                                .filter { it.stream != null }.map { it.login },
                        )
                        onOpenStream(item.login)
                    } else {
                        onOpenChannel(item.login)
                    }
                },
                onOpenVod = { progress ->
                    onOpenVod(VodLaunch(progress.vodId, progress.channelLogin, progress.title, progress.thumbnailUrl))
                },
            )

            if (state.followedLive.isNotEmpty()) {
                ContentRow(
                    title = "Following · Live now",
                    streams = state.followedLive,
                    firstCardFocusRequester = if (hero == null) firstCardFocusRequester else null,
                    onOpenStream = { openFrom(state.followedLive, it) },
                )
            }
            ContentRow(
                title = "Live Channels",
                streams = state.topStreams,
                firstCardFocusRequester = if (hero == null && state.followedLive.isEmpty()) firstCardFocusRequester else null,
                onOpenStream = { openFrom(state.topStreams, it) },
            )

            // Signed out, the shelves above fill from the public directory; only
            // the viewer's own follows need an account. Offered below the content,
            // never in its place, and it never takes the initial focus.
            if (state.sessionKnown && !state.isLoggedIn) {
                SignInPanel(onSignIn = onOpenLogin)
            }

            // Only surfaces when there's genuinely nothing to show (no cache, no
            // network) — the cached-first paint keeps this hidden in the common case.
            if (state.topStreams.isEmpty() && !state.isLoading) {
                // A bare line of text reads as a rendering failure on a ten-foot
                // screen. Give the message the same panel every other block has,
                // so an empty Home still looks deliberate.
                TvPanel(modifier = Modifier.padding(horizontal = 48.dp)) {
                    Column {
                        Text(
                            text = if (state.error != null) "Could not load" else "Nothing live right now",
                            style = MaterialTheme.typography.headlineSmall,
                            color = c.onSurface,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = state.error ?: "This refreshes on its own, so it will fill in as channels go live.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = c.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

// Poll cadence for the foreground "Live Now" refresh. 90s keeps viewer counts /
// who's-live reasonably fresh without hammering Helix on a lean-back screen.
private const val HOME_REFRESH_INTERVAL_MS = 90_000L

/**
 * The one-stream spotlight: full-bleed art behind [PureTvTvTheme.colors.heroScrim],
 * status pills, the title in `displayMedium` (capped at 2 lines so a long title
 * can only grow the band via `heightIn(min = ...)`, never overflow it), a single
 * mono metadata line, then the XLarge "Watch now" button — the screen's
 * initially-focused element.
 */
@Composable
private fun HomeHero(
    stream: StreamInfo,
    followedLive: Boolean,
    onWatch: () -> Unit,
    watchFocusRequester: FocusRequester,
    modifier: Modifier = Modifier,
) {
    val c = PureTvTvTheme.colors
    val shapes = PureTvTvTheme.shapes

    Box(
        modifier
            .fillMaxWidth()
            .heightIn(min = 420.dp)
            .clip(shapes.heroShape),
    ) {
        Box(Modifier.fillMaxSize().background(c.surfaceHigh))
        val art = templatedUrl(stream.thumbnailUrl, 1280, 720)
        if (art.isNotBlank()) {
            AsyncImage(
                model = art,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }
        Box(Modifier.fillMaxSize().background(c.heroScrim))

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth(0.62f)
                .padding(48.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TvLivePill()
                TvShieldPill("ADS BLOCKED")
            }
            Spacer(Modifier.height(24.dp))
            Text(
                text = stream.title.ifBlank { stream.userName },
                style = MaterialTheme.typography.displayMedium,
                color = c.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = heroMetaLine(stream, followedLive),
                style = PureTvTvType.data,
                color = c.onSurface.copy(alpha = 0.82f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(32.dp))
            TvExpressiveButton(
                text = "Watch now",
                onClick = onWatch,
                style = TvButtonStyle.Filled,
                size = TvButtonSize.XLarge,
                icon = ExpressiveIcons.Play,
                modifier = Modifier.focusRequester(watchFocusRequester),
            )
        }
    }
}

/** One mono line: channel, game (when known), viewer count, followed status. */
private fun heroMetaLine(stream: StreamInfo, followedLive: Boolean): String = buildList {
    add(stream.userName)
    if (stream.gameName.isNotBlank()) add(stream.gameName)
    add("${formatTvViewerCount(stream.viewerCount.toLong())} watching")
    if (followedLive) add("FOLLOWING")
}.joinToString(" · ")

/** The signed-out offer: what signing in adds, and a button straight to the sign-in screen. */
@Composable
private fun SignInPanel(onSignIn: () -> Unit) {
    val c = PureTvTvTheme.colors
    TvPanel {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(32.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text("See the channels you follow", style = MaterialTheme.typography.titleLarge, color = c.onSurface)
                Spacer(Modifier.height(8.dp))
                Text(
                    "You're watching without an account. Sign in with Twitch to bring in your follows and chat.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = c.onSurfaceVariant,
                )
            }
            TvExpressiveButton(
                text = "Sign in",
                onClick = onSignIn,
                style = TvButtonStyle.Filled,
                size = TvButtonSize.Medium,
                icon = ExpressiveIcons.SignIn,
            )
        }
    }
}

/** Update-available banner: tertiary tonal strip, jumps to Settings to install. */
@Composable
private fun UpdateBanner(versionName: String, onOpenSettings: () -> Unit) {
    val c = PureTvTvTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(PureTvTvTheme.shapes.cardShape)
            .background(c.tertiaryContainer)
            .padding(horizontal = 28.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            "Update available: $versionName",
            style = MaterialTheme.typography.titleMedium,
            color = c.onTertiaryContainer,
        )
        TvExpressiveButton(
            text = "Open Settings",
            onClick = onOpenSettings,
            style = TvButtonStyle.FilledTertiary,
            size = TvButtonSize.Medium,
        )
    }
}

/**
 * "Continue watching": channels watched before (live ones first, with their live picture),
 * broadcasts left part way, then channels that are offline now. Hidden until there is
 * something to continue.
 */
@Composable
private fun ContinueRow(
    row: List<ContinueItem>,
    onOpenChannel: (ContinueItem.Channel) -> Unit,
    onOpenVod: (WatchProgress) -> Unit,
) {
    if (row.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        TvSectionHeading("Continue watching")
        TvShelfScroll {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                contentPadding = PaddingValues(vertical = 8.dp),
            ) {
                items(
                    row,
                    key = { item ->
                        when (item) {
                            is ContinueItem.Channel -> "channel:${item.login}"
                            is ContinueItem.Vod -> "vod:${item.progress.vodId}"
                        }
                    },
                ) { item ->
                    when (item) {
                        is ContinueItem.Channel -> TvRecentChannelCard(item, onClick = { onOpenChannel(item) })
                        is ContinueItem.Vod -> TvVodResumeCard(item, onClick = { onOpenVod(item.progress) })
                    }
                }
            }
        }
    }
}

@Composable
private fun ContentRow(
    title: String,
    streams: List<StreamInfo>,
    firstCardFocusRequester: FocusRequester?,
    onOpenStream: (String) -> Unit,
) {
    if (streams.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        TvSectionHeading(title)
        TvShelfScroll {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                contentPadding = PaddingValues(vertical = 8.dp),
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
}

/** Twitch thumbnail URLs are templates carrying `{width}`/`{height}`. */
private fun templatedUrl(url: String, width: Int, height: Int): String =
    url.replace("{width}", width.toString()).replace("{height}", height.toString())
