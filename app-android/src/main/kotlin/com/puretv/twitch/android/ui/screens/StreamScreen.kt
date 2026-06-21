package com.puretv.twitch.android.ui.screens

import android.app.Activity
import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.puretv.twitch.android.player.PlayerSurface
import com.puretv.twitch.android.ui.StreamUiState
import com.puretv.twitch.android.ui.StreamViewModel
import com.puretv.twitch.android.ui.components.AdBlockPill
import com.puretv.twitch.android.ui.components.ChatPanel
import com.puretv.twitch.android.ui.components.LiveBadge
import com.puretv.twitch.android.ui.theme.PureTvColors
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * SECTION 06.4 [CRITICAL]: adaptive playback. Portrait stacks a 16:9 player over
 * the channel info and chat. Landscape (and fullscreen) place the player beside
 * chat with a draggable divider that resizes the split (persisted). Fullscreen
 * hides the system bars and chat for an immersive, edge-to-edge video.
 */
@Composable
fun StreamScreen(channelLogin: String, onBack: () -> Unit) {
    val viewModel: StreamViewModel = koinViewModel(parameters = { parametersOf(channelLogin) })
    val state by viewModel.state.collectAsState()
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    var fullscreen by remember { mutableStateOf(false) }
    var chatVisible by remember(state.chatEnabled) { mutableStateOf(state.chatEnabled) }
    var chatFraction by remember(state.chatFraction) { mutableStateOf(state.chatFraction.coerceIn(0.2f, 0.5f)) }

    // Immersive fullscreen: hide the system bars while fullscreen, restore otherwise.
    val view = LocalView.current
    DisposableEffect(fullscreen) {
        val window = (view.context as? Activity)?.window
        if (window != null) {
            val controller = WindowCompat.getInsetsController(window, view)
            if (fullscreen) {
                controller.hide(WindowInsetsCompat.Type.systemBars())
                controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            } else {
                controller.show(WindowInsetsCompat.Type.systemBars())
            }
        }
        onDispose {
            (view.context as? Activity)?.window?.let {
                WindowCompat.getInsetsController(it, view).show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    val sideBySide = isLandscape || fullscreen
    val showChat = chatVisible && !fullscreen

    val toggleChat: () -> Unit = {
        val next = !chatVisible
        chatVisible = next
        viewModel.setChatEnabled(next)
    }

    if (sideBySide) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize().background(PureTvColors.Background)) {
            val widthPx = constraints.maxWidth.toFloat()
            Row(modifier = Modifier.fillMaxSize()) {
                PlayerArea(
                    state = state,
                    fullscreen = fullscreen,
                    chatVisible = chatVisible,
                    onBack = onBack,
                    onToggleFullscreen = { fullscreen = !fullscreen },
                    onToggleChat = toggleChat,
                    modifier = Modifier.weight(if (showChat) 1f - chatFraction else 1f).fillMaxHeight(),
                )
                if (showChat) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(6.dp)
                            .background(PureTvColors.SurfaceVariant)
                            .pointerInput(widthPx) {
                                detectHorizontalDragGestures(
                                    onDragEnd = { viewModel.setChatFraction(chatFraction) },
                                ) { _, dragAmount ->
                                    chatFraction = (chatFraction - dragAmount / widthPx).coerceIn(0.2f, 0.5f)
                                }
                            },
                    )
                    ChatPanel(
                        messages = state.chatMessages,
                        onSend = viewModel::sendChatMessage,
                        modifier = Modifier.weight(chatFraction).fillMaxHeight(),
                    )
                }
            }
        }
    } else {
        Column(modifier = Modifier.fillMaxSize().background(PureTvColors.Background)) {
            PlayerArea(
                state = state,
                fullscreen = false,
                chatVisible = chatVisible,
                onBack = onBack,
                onToggleFullscreen = { fullscreen = true },
                onToggleChat = toggleChat,
                modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
            )
            state.channel?.let { channel ->
                Column(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(channel.displayName, style = MaterialTheme.typography.titleLarge, color = PureTvColors.TextPrimary)
                    state.streamInfo?.let { info ->
                        Text(info.title, style = MaterialTheme.typography.bodyMedium, color = PureTvColors.TextSecondary, maxLines = 2)
                        Text(info.gameName, style = MaterialTheme.typography.bodySmall, color = PureTvColors.TwitchPurpleLight)
                    }
                }
            }
            if (showChat) {
                ChatPanel(
                    messages = state.chatMessages,
                    onSend = viewModel::sendChatMessage,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun PlayerArea(
    state: StreamUiState,
    fullscreen: Boolean,
    chatVisible: Boolean,
    onBack: () -> Unit,
    onToggleFullscreen: () -> Unit,
    onToggleChat: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.background(Color.Black)) {
        PlayerSurface(playableUrl = state.playableUrl, modifier = Modifier.fillMaxSize())

        IconButton(onClick = onBack, modifier = Modifier.align(Alignment.TopStart).padding(6.dp)) {
            Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
        }

        Row(
            modifier = Modifier.align(Alignment.TopEnd).padding(6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OverlayPill(text = if (chatVisible) "Hide chat" else "Show chat", onClick = onToggleChat)
            OverlayPill(text = if (fullscreen) "Exit fullscreen" else "Fullscreen", onClick = onToggleFullscreen)
        }

        AdBlockPill(
            status = state.adBlockStatus,
            onClick = {},
            modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp),
        )

        state.streamInfo?.let { info ->
            Box(modifier = Modifier.align(Alignment.BottomStart).padding(6.dp)) {
                LiveBadge(viewerCount = info.viewerCount.toLong())
            }
        }
    }
}

@Composable
private fun OverlayPill(text: String, onClick: () -> Unit) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = Color.White,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Color.Black.copy(alpha = 0.6f))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    )
}
