package com.puretv.twitch.android.ui.screens

import android.app.Activity
import android.content.res.Configuration
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.ui.AspectRatioFrameLayout
import coil3.compose.AsyncImage
import com.puretv.twitch.android.LocalIsInPip
import com.puretv.twitch.android.player.PlayerSurface
import com.puretv.twitch.android.ui.StreamUiState
import com.puretv.twitch.android.ui.StreamViewModel
import com.puretv.twitch.android.ui.components.AdBlockPill
import com.puretv.twitch.android.ui.components.ChatPanel
import com.puretv.twitch.android.ui.components.ExpressiveButton
import com.puretv.twitch.android.ui.components.ExpressiveButtonSize
import com.puretv.twitch.android.ui.components.ExpressiveButtonStyle
import com.puretv.twitch.android.ui.components.ExpressiveIcons
import com.puretv.twitch.android.ui.components.LivePill
import com.puretv.twitch.android.ui.components.expressiveClickable
import com.puretv.twitch.android.ui.components.formatViewerCount
import com.puretv.twitch.android.ui.theme.PureTvTheme
import com.puretv.twitch.android.ui.theme.PureTvType
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
    val c = PureTvTheme.colors

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
        // Hidden system bars report a zero inset, so this costs nothing in
        // fullscreen and keeps the video and chat clear of the buttons in
        // windowed landscape, where the navigation bar sits on one side.
        BoxWithConstraints(modifier = Modifier.fillMaxSize().background(c.surfaceLowest).navigationBarsPadding()) {
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
                            .background(c.outlineVariant)
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
        Column(modifier = Modifier.fillMaxSize().background(c.surfaceLowest).navigationBarsPadding()) {
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
                state.channel?.let { channel -> ChannelHeaderRow(channel = channel, streamInfo = state.streamInfo) }
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

/**
 * Avatar, name, category and viewer count, mirroring the desktop watch page's
 * top bar. No follow action here: unlike desktop's `StreamViewModel`, the
 * Android one exposes no `isFollowed`/`toggleFollow`, so a follow button would
 * be chrome with nothing behind it. Left out rather than faked.
 */
@Composable
private fun ChannelHeaderRow(channel: com.puretv.twitch.core.model.ChannelInfo, streamInfo: com.puretv.twitch.core.model.StreamInfo?) {
    val c = PureTvTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth().padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AsyncImage(
            model = channel.profileImageUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(44.dp).clip(CircleShape).background(c.surfaceHigh),
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                channel.displayName,
                style = MaterialTheme.typography.titleMedium,
                color = c.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            streamInfo?.let { info ->
                val game = info.gameName.takeIf { it.isNotBlank() } ?: "No category"
                Text(
                    "$game · ${formatViewerCount(info.viewerCount.toLong())} viewers",
                    style = PureTvType.data,
                    color = c.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
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
    val c = PureTvTheme.colors
    // Keep the top controls clear of the status bar / notch in portrait and
    // windowed landscape. In fullscreen the system bars are hidden, so no inset.
    val topInset = if (fullscreen) Modifier else Modifier.windowInsetsPadding(WindowInsets.statusBars)

    // Overlay chrome (back, pills, badges) tracks the player controller: it shows
    // on a tap and fades out with the controller after ~3s so it does not persist
    // over the video. Starts visible (the controller auto-shows on attach). Play/
    // pause itself is drawn by ExoPlayer's own PlayerView controller (useController
    // below), which owns that state; this overlay only adds the chrome around it.
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
                        color = c.onSurface,
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                    )
                    ExpressiveButton(
                        text = "Try again",
                        onClick = onRetry,
                        style = ExpressiveButtonStyle.Filled,
                        size = ExpressiveButtonSize.Medium,
                    )
                }
                PlayerBackButton(onClick = onBack, modifier = Modifier.align(Alignment.TopStart).then(topInset).padding(6.dp))
            } else {
                // Playing: the whole chrome group fades in/out with the controls.
                AnimatedVisibility(
                    visible = controlsVisible,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        PlayerBackButton(onClick = onBack, modifier = Modifier.align(Alignment.TopStart).then(topInset).padding(6.dp))

                        PlayerControlsGroup(
                            chatVisible = chatVisible,
                            fullscreen = fullscreen,
                            fillScreen = fillScreen,
                            onToggleChat = onToggleChat,
                            onToggleFill = onToggleFill,
                            onToggleFullscreen = onToggleFullscreen,
                            modifier = Modifier.align(Alignment.TopEnd).then(topInset).padding(6.dp),
                        )

                        AdBlockPill(
                            status = state.adBlockStatus,
                            onClick = {},
                            modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp),
                        )

                        state.streamInfo?.let { info ->
                            LivePill(
                                trailing = formatViewerCount(info.viewerCount.toLong()),
                                modifier = Modifier.align(Alignment.BottomStart).padding(6.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Round scrim button for chrome that floats directly over the video (back,
 * mirrors [AdBlockPill]'s own contrast handling): a translucent black circle
 * with a white glyph reads over any frame the stream throws at it, which a
 * theme role tuned for the app's own dark surfaces would not guarantee.
 */
@Composable
private fun PlayerBackButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(44.dp)
            .expressiveClickable(
                interaction = interaction,
                onClick = onClick,
                restRadius = 22.dp,
                pressRadius = PureTvTheme.shapes.pillMorph,
                color = Color.Black.copy(alpha = 0.45f),
            ),
    ) {
        Icon(ExpressiveIcons.Back, contentDescription = "Back", tint = Color.White, modifier = Modifier.size(22.dp))
    }
}

/**
 * The welded chat/fill/fullscreen cluster: one 28dp-radius `surfaceHigh` bar
 * with 1dp `outlineVariant` dividers between square segments, matching the
 * desktop controls bar's `ConnectedControlsGroup`. Fill/Fit only matters once
 * fullscreen owns the whole display, so that segment only appears there.
 */
@Composable
private fun PlayerControlsGroup(
    chatVisible: Boolean,
    fullscreen: Boolean,
    fillScreen: Boolean,
    onToggleChat: () -> Unit,
    onToggleFill: () -> Unit,
    onToggleFullscreen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = PureTvTheme.colors
    Row(
        modifier = modifier
            .height(48.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(c.surfaceHigh),
    ) {
        ControlsGroupButton(
            icon = ExpressiveIcons.Chat,
            contentDescription = if (chatVisible) "Hide chat" else "Show chat",
            onClick = onToggleChat,
            tint = if (chatVisible) c.primary else null,
        )
        if (fullscreen) {
            GroupDivider()
            ControlsGroupButton(
                icon = ExpressiveIcons.AspectRatio,
                contentDescription = if (fillScreen) "Fit to screen" else "Fill screen",
                onClick = onToggleFill,
                tint = if (fillScreen) c.primary else null,
            )
        }
        GroupDivider()
        ControlsGroupButton(
            icon = if (fullscreen) ExpressiveIcons.FullscreenExit else ExpressiveIcons.Fullscreen,
            contentDescription = if (fullscreen) "Exit fullscreen" else "Fullscreen",
            onClick = onToggleFullscreen,
        )
    }
}

@Composable
private fun GroupDivider() {
    Box(Modifier.width(1.dp).fillMaxHeight().background(PureTvTheme.colors.outlineVariant))
}

@Composable
private fun ControlsGroupButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    tint: Color? = null,
) {
    val c = PureTvTheme.colors
    val interaction = remember { MutableInteractionSource() }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(48.dp)
            .expressiveClickable(
                interaction = interaction,
                onClick = onClick,
                restRadius = 0.dp,
                pressRadius = 0.dp,
                color = Color.Transparent,
            ),
    ) {
        Icon(icon, contentDescription = contentDescription, tint = tint ?: c.onSurface, modifier = Modifier.size(22.dp))
    }
}
