package com.puretv.twitch.android.ui.screens

import android.app.Activity
import android.content.res.Configuration
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.ui.AspectRatioFrameLayout
import com.puretv.twitch.android.LocalIsInPip
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
    val isInPip = LocalIsInPip.current

    // Saveable so an immersive fullscreen survives process death + restore (plain
    // remember would silently drop the user back to the windowed layout).
    var fullscreen by rememberSaveable { mutableStateOf(false) }
    // Crop-to-fill toggle for fullscreen: on tall/notched phones (e.g. OnePlus 13,
    // ~20:9) FIT letterboxes a 16:9 stream with side bars; ZOOM fills the whole
    // display including under the camera cutout, cropping a little top/bottom.
    var fillScreen by rememberSaveable { mutableStateOf(false) }
    val resizeMode = if (fullscreen && fillScreen) {
        AspectRatioFrameLayout.RESIZE_MODE_ZOOM
    } else {
        AspectRatioFrameLayout.RESIZE_MODE_FIT
    }
    var chatVisible by remember(state.chatEnabled) { mutableStateOf(state.chatEnabled) }
    var chatFraction by remember(state.chatFraction) { mutableStateOf(state.chatFraction.coerceIn(0.2f, 0.7f)) }

    // Immersive fullscreen: hide the system bars so the video fills the screen
    // (including under the camera cutout, which the Activity already opts into via
    // layoutInDisplayCutoutMode set once in MainActivity). Bars are restored on
    // exit/dispose. The cutout mode is intentionally NOT toggled here: doing so at
    // runtime left a persistent safe-area gap on some OEM skins after exit.
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
    // In PiP the window is a postage stamp: collapse to video only, no chat.
    val showChat = chatVisible && !fullscreen && !isInPip

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
                    resizeMode = resizeMode,
                    fillScreen = fillScreen,
                    onBack = onBack,
                    onToggleFullscreen = { fullscreen = !fullscreen },
                    onToggleFill = { fillScreen = !fillScreen },
                    onToggleChat = toggleChat,
                    onRetry = viewModel::retry,
                    isInPip = isInPip,
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
                                    chatFraction = (chatFraction - dragAmount / widthPx).coerceIn(0.2f, 0.7f)
                                }
                            },
                    )
                    ChatPanel(
                        messages = state.chatMessages,
                        onSend = viewModel::sendChatMessage,
                        emotes = state.emotes,
                        canSend = state.isLoggedIn,
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
                resizeMode = resizeMode,
                fillScreen = fillScreen,
                onBack = onBack,
                onToggleFullscreen = { fullscreen = true },
                onToggleFill = { fillScreen = !fillScreen },
                onToggleChat = toggleChat,
                onRetry = viewModel::retry,
                isInPip = isInPip,
                modifier = if (isInPip) Modifier.fillMaxSize() else Modifier.fillMaxWidth().aspectRatio(16f / 9f),
            )
            if (!isInPip) {
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
            }
            if (showChat) {
                ChatPanel(
                    messages = state.chatMessages,
                    onSend = viewModel::sendChatMessage,
                    emotes = state.emotes,
                    canSend = state.isLoggedIn,
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
    resizeMode: Int,
    fillScreen: Boolean,
    onBack: () -> Unit,
    onToggleFullscreen: () -> Unit,
    onToggleFill: () -> Unit,
    onToggleChat: () -> Unit,
    onRetry: () -> Unit,
    isInPip: Boolean,
    modifier: Modifier = Modifier,
) {
    // Keep the top controls clear of the status bar / notch in portrait and
    // windowed landscape. In fullscreen the system bars are hidden, so no inset.
    val topInset = if (fullscreen) Modifier else Modifier.windowInsetsPadding(WindowInsets.statusBars)

    // Overlay chrome (back, pills, badges) tracks the player controller: it shows
    // on a tap and fades out with the controller after ~3s so it does not persist
    // over the video. Starts visible (the controller auto-shows on attach).
    var controlsVisible by remember { mutableStateOf(true) }

    Box(modifier = modifier.background(Color.Black)) {
        // In PiP the controller is hidden too: the small window is video only.
        PlayerSurface(
            playableUrl = state.playableUrl,
            useController = !isInPip,
            resizeMode = resizeMode,
            // Double-tap the video to toggle fill/fit, but only in fullscreen where
            // filling the whole display (and cutout) is meaningful.
            onDoubleTap = { if (fullscreen) onToggleFill() },
            onControlsVisibilityChanged = { controlsVisible = it },
            modifier = Modifier.fillMaxSize(),
        )

        // All chrome is suppressed in PiP so the floating window shows only video.
        if (!isInPip) {
            if (state.playbackError != null) {
                // Resolution failed: keep the error + Retry + Back visible always
                // (do NOT auto-hide) so the user can recover.
                Column(
                    modifier = Modifier.fillMaxSize().background(Color.Black).padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
                ) {
                    Text(
                        state.playbackError,
                        color = Color.White,
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                    )
                    Button(onClick = onRetry) { Text("Try again") }
                }
                IconButton(onClick = onBack, modifier = Modifier.align(Alignment.TopStart).then(topInset).padding(6.dp)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
            } else {
                // Playing: the whole chrome group fades in/out with the controls.
                AnimatedVisibility(
                    visible = controlsVisible,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        IconButton(onClick = onBack, modifier = Modifier.align(Alignment.TopStart).then(topInset).padding(6.dp)) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                        }
                        Row(
                            modifier = Modifier.align(Alignment.TopEnd).then(topInset).padding(6.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            OverlayPill(text = if (chatVisible) "Hide chat" else "Show chat", onClick = onToggleChat)
                            // Fill/Fit only matters once we own the whole screen (fullscreen).
                            if (fullscreen) {
                                OverlayPill(text = if (fillScreen) "Fit" else "Fill", onClick = onToggleFill)
                            }
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
