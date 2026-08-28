package com.puretv.twitch.tv.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.puretv.twitch.core.model.ChannelInfo
import com.puretv.twitch.tv.ui.ChannelViewModel
import com.puretv.twitch.tv.ui.components.ExpressiveIcons
import com.puretv.twitch.tv.ui.components.TvButtonSize
import com.puretv.twitch.tv.ui.components.TvButtonStyle
import com.puretv.twitch.tv.ui.components.TvExpressiveButton
import com.puretv.twitch.tv.ui.components.TvExpressiveIconButton
import com.puretv.twitch.tv.ui.components.TvLivePill
import com.puretv.twitch.tv.ui.components.TvPanel
import com.puretv.twitch.tv.ui.components.formatTvViewerCount
import com.puretv.twitch.tv.ui.theme.PureTvTvTheme
import com.puretv.twitch.tv.ui.theme.PureTvTvType
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * SECTION 07.2: channel profile, a banner that fades into the surface colour,
 * a large avatar riding its lower edge, identity + a live pill, a mono
 * metadata line, and a prominent "Watch now" CTA that's focused by default so
 * the D-pad confirm key immediately starts playback (Section 7.3's "primary
 * action gets initial focus" convention for detail screens).
 *
 * [ChannelViewModel] exposes no follow state on TV, unlike its desktop
 * counterpart, so no follow control is wired here: adding one would mean a
 * control with nothing behind it, which the shared brief rules out. See the
 * redesign report for that gap.
 */
@Composable
fun TvChannelScreen(
    channelLogin: String,
    onWatch: () -> Unit,
    onBack: () -> Unit,
    viewModel: ChannelViewModel = koinViewModel(parameters = { parametersOf(channelLogin) }),
) {
    val state by viewModel.state.collectAsState()
    val c = PureTvTvTheme.colors
    val watchFocusRequester = remember { FocusRequester() }

    LaunchedEffect(state.channel) {
        if (state.channel != null) runCatching { watchFocusRequester.requestFocus() }
    }

    val channel = state.channel
    val displayName = channel?.displayName ?: channelLogin
    val bannerUrl = channel?.offlineImageUrl?.takeIf { it.isNotBlank() }
        ?: channel?.profileImageUrl?.takeIf { it.isNotBlank() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(c.surface)
            .verticalScroll(rememberScrollState()),
    ) {
        Box(modifier = Modifier.fillMaxWidth().heightIn(min = 280.dp)) {
            if (!bannerUrl.isNullOrBlank()) {
                AsyncImage(
                    model = bannerUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Box(Modifier.fillMaxSize().background(c.surfaceHigh))
            }
            // The banner protects no fixed content of its own, so it can fade
            // straight into the page colour rather than needing a hard cut.
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(0f to c.surface.copy(alpha = 0f), 0.65f to c.surface.copy(alpha = 0.55f), 1f to c.surface),
                ),
            )
            TvExpressiveIconButton(
                icon = ExpressiveIcons.Back,
                contentDescription = "Back",
                onClick = onBack,
                modifier = Modifier.align(Alignment.TopStart).padding(32.dp),
            )
        }

        Column(modifier = Modifier.padding(horizontal = 48.dp), verticalArrangement = Arrangement.spacedBy(28.dp)) {
            IdentityRow(displayName = displayName, channel = channel, isLive = state.isLive, viewerCount = state.viewerCount)

            TvExpressiveButton(
                text = "Watch now",
                onClick = onWatch,
                style = TvButtonStyle.Filled,
                size = TvButtonSize.XLarge,
                icon = ExpressiveIcons.Play,
                modifier = Modifier.focusRequester(watchFocusRequester),
            )

            if (!channel?.description.isNullOrBlank()) {
                TvPanel {
                    Column {
                        Text(text = "About", style = MaterialTheme.typography.titleLarge, color = c.onSurface)
                        Spacer(Modifier.size(12.dp))
                        Text(text = channel!!.description, style = MaterialTheme.typography.bodyLarge, color = c.onSurfaceVariant)
                    }
                }
            }

            Spacer(Modifier.size(40.dp))
        }
    }
}

/** The avatar, name, live pill and mono metadata line, straddling the banner's lower edge. */
@Composable
private fun IdentityRow(displayName: String, channel: ChannelInfo?, isLive: Boolean, viewerCount: Long) {
    val c = PureTvTvTheme.colors

    Row(
        modifier = Modifier.fillMaxWidth().offset(y = (-64).dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        ChannelAvatar(displayName = displayName, imageUrl = channel?.profileImageUrl)
        Spacer(Modifier.size(28.dp))
        Column(modifier = Modifier.padding(bottom = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                Text(
                    text = displayName,
                    style = MaterialTheme.typography.displayMedium,
                    color = c.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (isLive) TvLivePill(trailing = "${formatTvViewerCount(viewerCount)} viewers")
            }
            val meta = buildList {
                channel?.broadcasterType?.takeIf { it.isNotBlank() }?.let { add(it.replaceFirstChar(Char::uppercase)) }
                channel?.viewCount?.takeIf { it > 0 }?.let { add("${formatTvViewerCount(it.toLong())} views") }
            }
            if (meta.isNotEmpty()) {
                Spacer(Modifier.size(8.dp))
                Text(text = meta.joinToString("  ·  "), style = PureTvTvType.data, color = c.onSurfaceVariant)
            }
        }
    }
}

/** 160dp hero avatar: the channel's profile image, or an initial on a flat fill. */
@Composable
private fun ChannelAvatar(displayName: String, imageUrl: String?) {
    val c = PureTvTvTheme.colors
    Box(
        modifier = Modifier.size(160.dp).clip(CircleShape).background(c.surfaceHigh),
        contentAlignment = Alignment.Center,
    ) {
        if (!imageUrl.isNullOrBlank()) {
            AsyncImage(
                model = imageUrl,
                contentDescription = displayName,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Text(
                text = displayName.take(1).uppercase(),
                style = MaterialTheme.typography.displayMedium,
                color = c.onSurfaceVariant,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}
