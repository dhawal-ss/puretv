package com.puretv.twitch.android.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.puretv.twitch.android.ui.ChannelUiState
import com.puretv.twitch.android.ui.ChannelViewModel
import com.puretv.twitch.android.ui.components.ErrorState
import com.puretv.twitch.android.ui.components.ExpressiveButton
import com.puretv.twitch.android.ui.components.ExpressiveButtonSize
import com.puretv.twitch.android.ui.components.ExpressiveButtonStyle
import com.puretv.twitch.android.ui.components.ExpressiveIconButton
import com.puretv.twitch.android.ui.components.ExpressiveIcons
import com.puretv.twitch.android.ui.components.ExpressivePanel
import com.puretv.twitch.android.ui.components.FullScreenLoading
import com.puretv.twitch.android.ui.components.LivePill
import com.puretv.twitch.android.ui.components.formatViewerCount
import com.puretv.twitch.android.ui.theme.PureTvTheme
import com.puretv.twitch.android.ui.theme.PureTvType
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * SECTION 06.4 — channel profile: a banner that fades into the surface, an
 * avatar straddling its lower edge, identity + live status, the watch CTA, and
 * an About panel.
 *
 * Android's [ChannelViewModel] carries no follow state and no past-broadcasts
 * list (unlike the desktop [ChannelViewModel], which owns a local FollowStore
 * and a VodListViewModel pairing), so neither a follow toggle nor a VOD section
 * appears here: adding either would be a control the view layer cannot back.
 */
@Composable
fun ChannelScreen(channelLogin: String, onWatch: () -> Unit, onBack: () -> Unit) {
    val viewModel: ChannelViewModel = koinViewModel(parameters = { parametersOf(channelLogin) })
    val state by viewModel.state.collectAsState()
    val c = PureTvTheme.colors

    Column(modifier = Modifier.fillMaxSize().background(c.surface)) {
        when {
            state.isLoading -> {
                BackHeader(onBack)
                FullScreenLoading()
            }
            state.channel == null -> {
                BackHeader(onBack)
                ErrorState(message = state.error ?: "Channel not found.", onRetry = viewModel::retry)
            }
            else -> ChannelBody(state = state, channelLogin = channelLogin, onWatch = onWatch, onBack = onBack)
        }
    }
}

/** Back affordance for the loading/error states, which have no banner to sit the button on. */
@Composable
private fun BackHeader(onBack: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        ExpressiveIconButton(
            icon = ExpressiveIcons.Back,
            contentDescription = "Back",
            onClick = onBack,
            style = ExpressiveButtonStyle.Tonal,
        )
    }
}

@Composable
private fun ChannelBody(state: ChannelUiState, channelLogin: String, onWatch: () -> Unit, onBack: () -> Unit) {
    val c = PureTvTheme.colors
    val channel = state.channel
    val displayName = channel?.displayName ?: channelLogin
    // Prefer the channel's offline banner; fall back to the profile portrait so
    // the header is never a flat placeholder square.
    val bannerUrl = channel?.offlineImageUrl?.takeIf { it.isNotBlank() }
        ?: channel?.profileImageUrl?.takeIf { it.isNotBlank() }

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        // heightIn(min = ...) rather than a fixed height: the aspect ratio drives
        // the real height, the floor only guards a pathologically narrow window.
        Box(modifier = Modifier.fillMaxWidth().heightIn(min = 180.dp)) {
            if (!bannerUrl.isNullOrBlank()) {
                AsyncImage(
                    model = bannerUrl,
                    contentDescription = displayName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
                )
            } else {
                Box(modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(c.surfaceHigh))
            }
            Box(
                modifier = Modifier.matchParentSize().background(
                    Brush.verticalGradient(0f to c.surface.copy(alpha = 0f), 0.6f to c.surface.copy(alpha = 0.5f), 1f to c.surface),
                ),
            )
            ExpressiveIconButton(
                icon = ExpressiveIcons.Back,
                contentDescription = "Back",
                onClick = onBack,
                style = ExpressiveButtonStyle.Tonal,
                modifier = Modifier.align(Alignment.TopStart).padding(16.dp),
            )
        }

        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
            IdentityBlock(displayName = displayName, state = state, onWatch = onWatch)
            Spacer(Modifier.height(24.dp))
            AboutPanel(channel?.description)
            Spacer(Modifier.height(32.dp))
        }
    }
}

/**
 * The 96dp hero avatar and the name / live status / watch CTA that follow it,
 * shifted up as one block so the avatar straddles the banner's bottom edge.
 */
@Composable
private fun IdentityBlock(displayName: String, state: ChannelUiState, onWatch: () -> Unit) {
    val c = PureTvTheme.colors
    val channel = state.channel
    val liveStream = state.liveStream

    Column(modifier = Modifier.offset(y = (-40).dp)) {
        HeroAvatar(displayName = displayName, imageUrl = channel?.profileImageUrl)
        Spacer(Modifier.height(12.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                displayName,
                style = MaterialTheme.typography.displaySmall,
                color = c.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (state.isLive) {
                Spacer(Modifier.width(10.dp))
                LivePill(trailing = liveStream?.viewerCount?.let { "${formatViewerCount(it.toLong())} viewers" })
            }
        }

        if (state.isLive && !liveStream?.title.isNullOrBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(
                liveStream?.title.orEmpty(),
                style = MaterialTheme.typography.titleMedium,
                color = c.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }

        // Metadata line: mono, real ChannelInfo/StreamInfo fields only, joined by " · ".
        val meta = buildList {
            if (state.isLive) liveStream?.gameName?.takeIf { it.isNotBlank() }?.let { add(it) }
            channel?.broadcasterType?.takeIf { it.isNotBlank() }?.let { add(it.replaceFirstChar(Char::uppercase)) }
            channel?.viewCount?.takeIf { it > 0 }?.let { add("${formatViewerCount(it.toLong())} views") }
        }
        if (meta.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Text(meta.joinToString("  ·  "), style = PureTvType.data, color = c.onSurfaceVariant)
        } else if (!state.isLive) {
            Spacer(Modifier.height(6.dp))
            Text("Offline", style = MaterialTheme.typography.bodyMedium, color = c.onSurfaceVariant)
        }

        Spacer(Modifier.height(16.dp))
        ExpressiveButton(
            text = if (state.isLive) "Watch now" else "Channel is offline",
            onClick = onWatch,
            style = ExpressiveButtonStyle.Filled,
            size = ExpressiveButtonSize.Large,
            icon = ExpressiveIcons.Play,
            enabled = state.isLive,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** Static (non-interactive) avatar: there is nothing to press on a phone's channel header. */
@Composable
private fun HeroAvatar(displayName: String, imageUrl: String?) {
    val c = PureTvTheme.colors

    Box(
        modifier = Modifier
            .size(96.dp)
            .clip(CircleShape)
            .background(c.surface)
            .padding(4.dp)
            .clip(CircleShape)
            .background(c.surfaceHigh),
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
                style = MaterialTheme.typography.displaySmall,
                color = c.onSurfaceVariant,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun AboutPanel(description: String?) {
    val c = PureTvTheme.colors
    ExpressivePanel(modifier = Modifier.fillMaxWidth()) {
        Column {
            Text("About", style = MaterialTheme.typography.titleLarge, color = c.onSurface)
            Spacer(Modifier.height(10.dp))
            val text = description?.takeIf { it.isNotBlank() }
            Text(
                text ?: "This channel hasn't written a bio yet.",
                style = MaterialTheme.typography.bodyLarge,
                color = if (text != null) c.onSurfaceVariant else c.outline,
            )
        }
    }
}
