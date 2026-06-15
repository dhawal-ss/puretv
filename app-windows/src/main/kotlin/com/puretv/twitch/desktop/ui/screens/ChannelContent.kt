package com.puretv.twitch.desktop.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.puretv.twitch.core.model.VideoType
import com.puretv.twitch.desktop.player.formatTimecode
import com.puretv.twitch.desktop.ui.ChannelViewModel
import com.puretv.twitch.desktop.ui.VodLaunch
import com.puretv.twitch.desktop.ui.VodListViewModel
import com.puretv.twitch.desktop.ui.components.Avatar
import com.puretv.twitch.desktop.ui.components.BoxScrim
import com.puretv.twitch.desktop.ui.components.ButtonVariant
import com.puretv.twitch.desktop.ui.components.CoverImage
import com.puretv.twitch.desktop.ui.components.Kicker
import com.puretv.twitch.desktop.ui.components.LiveDot
import com.puretv.twitch.desktop.ui.components.PureButton
import com.puretv.twitch.desktop.ui.components.PureIconButton
import com.puretv.twitch.desktop.ui.components.formatViewerCount
import com.puretv.twitch.desktop.ui.rememberDesktopViewModel
import com.puretv.twitch.desktop.ui.theme.PureTvShape
import com.puretv.twitch.desktop.ui.theme.PureTvTheme
import com.puretv.twitch.desktop.ui.theme.PureTvType
import org.koin.core.Koin
import org.koin.core.parameter.parametersOf

/**
 * Channel profile rendered as a Cinémathèque "cover story": a banner header, a
 * circular avatar straddling its lower edge, an editorial identity block, the
 * primary actions, and an About column paired with a "This stream" data panel.
 *
 * Only real [ChannelViewModel] state drives this screen — `state.channel`
 * ([com.puretv.twitch.core.model.ChannelInfo]), `state.isLive`, and
 * `isFollowed` — so the stats shown are strictly the fields Twitch returns for
 * a channel (broadcaster type, total views). No live viewer/game data is
 * surfaced here because this screen's state never exposes it.
 */
@Composable
fun ChannelContent(koin: Koin, channelLogin: String, onWatch: () -> Unit, onPlayVod: (VodLaunch) -> Unit, onBack: () -> Unit) {
    val viewModel = rememberDesktopViewModel(channelLogin) {
        koin.get<ChannelViewModel> { parametersOf(channelLogin) }
    }
    val state by viewModel.state.collectAsState()
    val isFollowed by viewModel.isFollowed.collectAsState()
    val c = PureTvTheme.colors

    val channel = state.channel
    val displayName = channel?.displayName ?: channelLogin
    val seed = channel?.login ?: channelLogin
    // Prefer the channel's offline banner; fall back to the profile portrait so
    // the duotone seed is never the only thing carrying the header.
    val bannerUrl = channel?.offlineImageUrl?.takeIf { it.isNotBlank() }
        ?: channel?.profileImageUrl?.takeIf { it.isNotBlank() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(c.background)
            .verticalScroll(rememberScrollState()),
    ) {
        // 1 ── Banner header ────────────────────────────────────────────────
        Box(modifier = Modifier.fillMaxWidth().height(220.dp)) {
            CoverImage(
                imageUrl = bannerUrl,
                seed = seed,
                contentDescription = displayName,
                modifier = Modifier.fillMaxSize(),
            )
            BoxScrim(Modifier.fillMaxSize())
            PureIconButton(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                onClick = onBack,
                tint = c.textPrimary,
                modifier = Modifier.align(Alignment.TopStart).padding(20.dp),
            )
        }

        // 2 + 3 ── Avatar straddling the banner edge + identity block ────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .offset(y = (-54).dp)
                .padding(horizontal = 38.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            Box(
                modifier = Modifier
                    .size(108.dp)
                    .clip(PureTvShape.pill)
                    .background(c.background)
                    .border(2.dp, c.background, PureTvShape.pill),
                contentAlignment = Alignment.Center,
            ) {
                Avatar(displayName, channel?.profileImageUrl, size = 108)
            }
            Spacer(Modifier.width(20.dp))
            Column(modifier = Modifier.padding(bottom = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        displayName,
                        style = MaterialTheme.typography.displayLarge,
                        color = c.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (state.isLive) {
                        Spacer(Modifier.width(14.dp))
                        Row(
                            modifier = Modifier
                                .clip(PureTvShape.xs)
                                .background(c.live.copy(alpha = 0.14f))
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            LiveDot(size = 6.dp)
                            Spacer(Modifier.width(6.dp))
                            Text("LIVE", style = PureTvType.dataSmall, color = c.live)
                        }
                    }
                }
                // Stats line — mono, real ChannelInfo fields only, " · " separated.
                val stats = buildList {
                    channel?.broadcasterType?.takeIf { it.isNotBlank() }
                        ?.let { add(it.replaceFirstChar(Char::uppercase)) }
                    channel?.viewCount?.takeIf { it > 0 }
                        ?.let { add("${formatViewerCount(it)} views") }
                }
                if (stats.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stats.joinToString("  ·  "),
                        style = PureTvType.data,
                        color = c.textTertiary,
                    )
                }
            }
        }

        // 4 + 5 ── Actions, About, and the "This stream" panel ──────────────
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .offset(y = (-40).dp)
                .padding(horizontal = 38.dp, vertical = 0.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PureButton(
                    text = if (state.isLive) "Watch now" else "Channel is offline",
                    onClick = onWatch,
                    variant = ButtonVariant.Primary,
                    enabled = state.isLive,
                    leadingIcon = Icons.Filled.PlayArrow,
                )
                Spacer(Modifier.width(12.dp))
                PureButton(
                    text = if (isFollowed) "Following" else "Follow",
                    onClick = viewModel::toggleFollow,
                    variant = ButtonVariant.Secondary,
                    enabled = channel != null,
                    leadingIcon = if (isFollowed) Icons.Filled.Check else Icons.Filled.Add,
                )
            }

            Spacer(Modifier.height(40.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(40.dp),
            ) {
                // About (left, the editorial column)
                Column(modifier = Modifier.weight(1f)) {
                    Kicker("About", rule = true)
                    Spacer(Modifier.height(16.dp))
                    val description = channel?.description?.takeIf { it.isNotBlank() }
                    Text(
                        description ?: "This channel hasn't written a bio yet.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (description != null) c.textSecondary else c.textMuted,
                    )
                }

                // Audience stats (right) — live viewers, followers, tracked history.
                ChannelStatsPanel(koin = koin, channelLogin = channelLogin)
            }

            channel?.id?.let { userId ->
                Spacer(Modifier.height(48.dp))
                PastBroadcastsSection(koin = koin, userId = userId, channelLogin = channelLogin, onPlayVod = onPlayVod)
            }

            Spacer(Modifier.height(48.dp))
        }
    }
}

@Composable
private fun PastBroadcastsSection(koin: Koin, userId: String, channelLogin: String, onPlayVod: (VodLaunch) -> Unit) {
    val vm = rememberDesktopViewModel(userId) { koin.get<VodListViewModel> { parametersOf(userId) } }
    val state by vm.state.collectAsState()
    val c = PureTvTheme.colors

    Column(modifier = Modifier.fillMaxWidth()) {
        Kicker("Past broadcasts", rule = true)
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            VodFilterChip("All", state.filter == null) { vm.setFilter(null) }
            VodFilterChip("Broadcasts", state.filter == VideoType.ARCHIVE) { vm.setFilter(VideoType.ARCHIVE) }
            VodFilterChip("Highlights", state.filter == VideoType.HIGHLIGHT) { vm.setFilter(VideoType.HIGHLIGHT) }
            VodFilterChip("Uploads", state.filter == VideoType.UPLOAD) { vm.setFilter(VideoType.UPLOAD) }
        }
        Spacer(Modifier.height(12.dp))
        when {
            state.error != null -> Text("Couldn't load videos.", style = PureTvType.data, color = c.textMuted)
            state.videos.isEmpty() && !state.loading -> Text("No saved videos.", style = PureTvType.data, color = c.textMuted)
        }
        state.videos.forEach { v ->
            Column(
                modifier = Modifier.fillMaxWidth().clickable { onPlayVod(VodLaunch(v.id, channelLogin, v.title, v.thumbnailUrl)) }.padding(vertical = 10.dp),
            ) {
                Text(
                    v.title.ifBlank { "Untitled" },
                    style = MaterialTheme.typography.bodyLarge,
                    color = c.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "${formatTimecode(v.durationSeconds * 1000)}  ·  ${formatViewerCount(v.viewCount)} views  ·  ${v.createdAt.take(10)}",
                    style = PureTvType.dataSmall,
                    color = c.textTertiary,
                )
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(c.hairline))
        }
        if (state.cursor != null) {
            Spacer(Modifier.height(12.dp))
            PureButton(
                text = if (state.loading) "Loading…" else "Load more",
                onClick = vm::loadMore,
                variant = ButtonVariant.Secondary,
                enabled = !state.loading,
            )
        }
    }
}

@Composable
private fun VodFilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    PureButton(
        text = label,
        onClick = onClick,
        variant = if (selected) ButtonVariant.Primary else ButtonVariant.Secondary,
    )
}
