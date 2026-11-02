package com.puretv.twitch.android.ui.screens

import android.app.Activity
import android.content.res.Configuration
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import com.puretv.twitch.android.LocalIsInPip
import com.puretv.twitch.android.player.PlayerSurface
import com.puretv.twitch.android.player.TwitchPlayer
import com.puretv.twitch.android.ui.VodLaunch
import com.puretv.twitch.android.ui.VodPlayerUiState
import com.puretv.twitch.android.ui.VodPlayerViewModel
import com.puretv.twitch.android.ui.components.ChatPanel
import com.puretv.twitch.android.ui.components.ExpressiveButton
import com.puretv.twitch.android.ui.components.ExpressiveButtonSize
import com.puretv.twitch.android.ui.components.ExpressiveButtonStyle
import com.puretv.twitch.android.ui.components.ExpressiveIcons
import com.puretv.twitch.android.ui.components.StoryboardPreview
import com.puretv.twitch.android.ui.components.expressiveClickable
import com.puretv.twitch.android.ui.theme.PureTvMotion
import com.puretv.twitch.android.ui.theme.PureTvTheme
import com.puretv.twitch.android.ui.theme.PureTvType
import com.puretv.twitch.core.model.StreamQuality
import com.puretv.twitch.core.vod.VodFormat
import kotlinx.coroutines.delay
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import org.koin.core.parameter.parametersOf

/**
 * A past broadcast. Portrait stacks the player over the title; landscape and
 * fullscreen give the video the whole screen. The controls are drawn here (not
 * by PlayerView) so the seek bar can be ours: play/pause, back and forward 10
 * seconds, a scrubber over the whole video, and a double tap on either half of
 * the picture to skip 10 seconds that way.
 */
@OptIn(UnstableApi::class)
@Composable
fun VodPlayerScreen(launch: VodLaunch, onBack: () -> Unit) {
    val viewModel: VodPlayerViewModel = koinViewModel(parameters = { parametersOf(launch) })
    val state by viewModel.state.collectAsState()
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val isInPip = LocalIsInPip.current
    val c = PureTvTheme.colors

    var fullscreen by rememberSaveable { mutableStateOf(false) }
    var chatVisible by rememberSaveable { mutableStateOf(true) }
    val showChat = chatVisible && !fullscreen && !isInPip

    // The replay follows the playback position. Polled once a second, and only
    // while the pane is on screen, so a hidden chat costs no wakeups.
    val twitchPlayer = koinInject<TwitchPlayer>()
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(showChat) {
        // Not while the screen is stopped (app in the background, screen off).
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (showChat) {
                viewModel.onChatPosition(twitchPlayer.exoPlayer.currentPosition.coerceAtLeast(0L) / 1000)
                delay(1_000)
            }
        }
    }

    // A go-live stream can be stacked on this video and empty the shared player on
    // its way out; coming back in front, the video starts again where it was.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> viewModel.onScreenPaused()
                Lifecycle.Event.ON_RESUME -> viewModel.onScreenResumed()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val chatMessages by viewModel.chatMessages.collectAsState()
    val chatError by viewModel.chatError.collectAsState()
    val chatPane: @Composable (Modifier) -> Unit = { modifier ->
        Column(modifier = modifier) {
            if (chatError != null) {
                Text(
                    chatError.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = c.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                )
            }
            ChatPanel(
                messages = chatMessages,
                onSend = {},
                canSend = false,
                noComposerText = "Chat replay",
                modifier = Modifier.weight(1f).fillMaxWidth(),
            )
        }
    }

    // Same immersive handling as the live player: bars hidden in fullscreen,
    // always restored on the way out.
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

    val playerArea: @Composable (Modifier) -> Unit = { modifier ->
        VodPlayerArea(
            state = state,
            fullscreen = fullscreen,
            isInPip = isInPip,
            onBack = onBack,
            onToggleFullscreen = { fullscreen = !fullscreen },
            chatVisible = chatVisible,
            onToggleChat = { chatVisible = !chatVisible },
            onToggleAudioNormalizer = { viewModel.toggleAudioNormalizer() },
            onRetry = viewModel::retry,
            onSelectQuality = viewModel::setQuality,
            onResume = viewModel::resume,
            onStartOver = viewModel::startOver,
            modifier = modifier,
        )
    }

    if (isLandscape || fullscreen || isInPip) {
        Row(modifier = Modifier.fillMaxSize().background(Color.Black).navigationBarsPadding()) {
            playerArea(Modifier.weight(if (showChat) 0.62f else 1f).fillMaxHeight())
            if (showChat) chatPane(Modifier.weight(0.38f).fillMaxHeight().background(c.surfaceLowest))
        }
    } else {
        Column(modifier = Modifier.fillMaxSize().background(c.surfaceLowest).navigationBarsPadding()) {
            playerArea(Modifier.fillMaxWidth().aspectRatio(16f / 9f))
            VodTitleBlock(launch = launch)
            if (showChat) chatPane(Modifier.weight(1f).fillMaxWidth())
        }
    }
}

@Composable
private fun VodTitleBlock(launch: VodLaunch) {
    val c = PureTvTheme.colors
    Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            launch.title.ifBlank { "Past broadcast" },
            style = MaterialTheme.typography.titleMedium,
            color = c.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(launch.channelLogin, style = PureTvType.data, color = c.onSurfaceVariant, maxLines = 1)
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun VodPlayerArea(
    state: VodPlayerUiState,
    fullscreen: Boolean,
    isInPip: Boolean,
    onBack: () -> Unit,
    onToggleFullscreen: () -> Unit,
    chatVisible: Boolean,
    onToggleChat: () -> Unit,
    onToggleAudioNormalizer: () -> Unit,
    onRetry: () -> Unit,
    onSelectQuality: (StreamQuality) -> Unit,
    onResume: () -> Unit,
    onStartOver: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val twitchPlayer = koinInject<TwitchPlayer>()
    val player = twitchPlayer.exoPlayer
    val topInset = if (fullscreen) Modifier else Modifier.windowInsetsPadding(WindowInsets.statusBars)

    var controlsVisible by remember { mutableStateOf(true) }
    var isPlaying by remember { mutableStateOf(player.isPlaying) }
    var buffering by remember { mutableStateOf(player.playbackState == Player.STATE_BUFFERING) }
    var positionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    // Where the scrubber thumb is while a finger drags it; null when not dragging.
    var scrubMs by remember { mutableStateOf<Long?>(null) }
    var lastTouchAt by remember { mutableLongStateOf(0L) }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(nowPlaying: Boolean) { isPlaying = nowPlaying }
            override fun onPlaybackStateChanged(playbackState: Int) {
                buffering = playbackState == Player.STATE_BUFFERING
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    // The position is only read while someone can see it: never with the
    // controls hidden or in the PiP window, so a long VOD costs no wakeups.
    LaunchedEffect(controlsVisible, isInPip) {
        while (controlsVisible && !isInPip) {
            positionMs = player.currentPosition.coerceAtLeast(0L)
            durationMs = player.duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: 0L
            delay(POSITION_POLL_MS)
        }
    }

    // Controls fade 3 s after the last touch while playing; never mid-drag.
    LaunchedEffect(controlsVisible, isPlaying, scrubMs == null, lastTouchAt) {
        if (controlsVisible && isPlaying && scrubMs == null) {
            delay(CONTROLS_TIMEOUT_MS)
            controlsVisible = false
        }
    }

    fun seekBy(deltaMs: Long) {
        val duration = player.duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: return
        val target = (player.currentPosition + deltaMs).coerceIn(0L, duration)
        player.seekTo(target)
        positionMs = target
        lastTouchAt = System.currentTimeMillis()
    }

    Box(modifier = modifier.background(Color.Black)) {
        PlayerSurface(
            playableUrl = state.playableUrl,
            useController = false,
            startsPlayback = false,
            modifier = Modifier.fillMaxSize(),
        )

        if (!isInPip) {
            // One tap shows or hides the controls; a double tap skips 10 s
            // back on the left half, forward on the right.
            Box(
                modifier = Modifier.fillMaxSize().pointerInput(Unit) {
                    detectTapGestures(
                        onTap = {
                            controlsVisible = !controlsVisible
                            lastTouchAt = System.currentTimeMillis()
                        },
                        onDoubleTap = { offset ->
                            seekBy(if (offset.x < size.width / 2) -SKIP_MS else SKIP_MS)
                            controlsVisible = true
                        },
                    )
                },
            )
        }

        AnimatedVisibility(
            visible = state.error == null && state.playableUrl != null && buffering,
            enter = fadeIn(tween(PureTvMotion.Medium, delayMillis = PureTvMotion.Fast)),
            exit = PureTvMotion.SwapExit,
            modifier = Modifier.align(Alignment.Center),
        ) {
            // The short delay keeps a one-frame stall from flashing a spinner.
            CircularProgressIndicator(color = PureTvTheme.colors.primary)
        }

        // The offer stays until answered: it is the only way back to the saved spot.
        val offer = state.resumeOfferMs
        // Held so the label does not change to 0:00 while the offer animates away.
        val lastOffer = remember { LongArray(1) }
        if (offer != null) lastOffer[0] = offer
        // The buttons stay on screen while they animate away; a second tap must do nothing.
        var answered by remember(offer) { mutableStateOf(false) }
        AnimatedVisibility(
            visible = !isInPip && offer != null && state.error == null,
            enter = PureTvMotion.BannerEnter,
            exit = PureTvMotion.BannerExit,
            modifier = Modifier.align(Alignment.BottomStart).padding(start = 12.dp, bottom = 56.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ExpressiveButton(
                    text = "Resume from ${VodFormat.timecode(lastOffer[0])}",
                    onClick = { if (!answered) { answered = true; onResume() } },
                    style = ExpressiveButtonStyle.Filled,
                    size = ExpressiveButtonSize.Small,
                )
                ExpressiveButton(
                    text = "Start over",
                    onClick = { if (!answered) { answered = true; onStartOver() } },
                    style = ExpressiveButtonStyle.Tonal,
                    size = ExpressiveButtonSize.Small,
                )
            }
        }

        if (!isInPip && state.error != null) {
            VodErrorPanel(message = state.error, permanent = state.errorIsPermanent, onRetry = onRetry, onBack = onBack)
            VodScrimButton(
                icon = ExpressiveIcons.Back,
                contentDescription = "Back",
                onClick = onBack,
                modifier = Modifier.align(Alignment.TopStart).then(topInset).padding(6.dp),
            )
        } else if (!isInPip) {
            AnimatedVisibility(
                visible = controlsVisible,
                enter = PureTvMotion.ControlsEnter,
                exit = PureTvMotion.ControlsExit,
                modifier = Modifier.fillMaxSize(),
            ) {
                Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f))) {
                    VodScrimButton(
                        icon = ExpressiveIcons.Back,
                        contentDescription = "Back",
                        onClick = onBack,
                        modifier = Modifier.align(Alignment.TopStart).then(topInset).padding(6.dp),
                    )
                    Row(
                        modifier = Modifier.align(Alignment.TopEnd).then(topInset).padding(6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        VodScrimButton(
                            icon = ExpressiveIcons.Chat,
                            contentDescription = if (chatVisible) "Hide chat" else "Show chat",
                            onClick = onToggleChat,
                            tint = if (chatVisible) PureTvTheme.colors.primary else Color.White,
                        )
                        VodScrimButton(
                            icon = ExpressiveIcons.Tune,
                            contentDescription = if (state.audioNormalizer) "Even out audio: on" else "Even out audio: off",
                            onClick = onToggleAudioNormalizer,
                            tint = if (state.audioNormalizer) PureTvTheme.colors.primary else Color.White,
                        )
                        VodQualityButton(quality = state.quality, onSelect = onSelectQuality)
                        VodScrimButton(
                            icon = if (fullscreen) ExpressiveIcons.FullscreenExit else ExpressiveIcons.Fullscreen,
                            contentDescription = if (fullscreen) "Exit fullscreen" else "Fullscreen",
                            onClick = onToggleFullscreen,
                        )
                    }
                    Row(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalArrangement = Arrangement.spacedBy(28.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        VodScrimButton(ExpressiveIcons.Back10, "Back 10 seconds", { seekBy(-SKIP_MS) })
                        VodScrimButton(
                            icon = if (isPlaying) ExpressiveIcons.Pause else ExpressiveIcons.Play,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            morphIcon = true,
                            onClick = {
                                if (player.isPlaying) {
                                    player.pause()
                                } else {
                                    // After the notification's Stop the player is idle: it has to be
                                    // prepared again, or Play does nothing.
                                    if (player.playbackState == Player.STATE_IDLE) player.prepare()
                                    player.play()
                                }
                                lastTouchAt = System.currentTimeMillis()
                            },
                            boxSize = 64,
                        )
                        VodScrimButton(ExpressiveIcons.Forward10, "Forward 10 seconds", { seekBy(SKIP_MS) })
                    }
                    // The frame at the dragged spot, floating above the bar.
                    val dragging = scrubMs
                    val sb = state.storyboard
                    if (dragging != null && sb != null && durationMs > 0) {
                        StoryboardPreview(
                            storyboard = sb,
                            positionMs = dragging,
                            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 64.dp),
                        )
                    }
                    VodSeekBar(
                        positionMs = scrubMs ?: positionMs,
                                        durationMs = durationMs,
                        onScrub = { scrubMs = it },
                        onScrubDone = {
                            scrubMs?.let { target ->
                                player.seekTo(target)
                                positionMs = target
                            }
                            scrubMs = null
                            lastTouchAt = System.currentTimeMillis()
                        },
                        modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                    )
                }
            }
        }
    }
}

/** The scrubber over the whole video, with the position and length either side. */
@Composable
private fun VodSeekBar(
    positionMs: Long,
    durationMs: Long,
    onScrub: (Long) -> Unit,
    onScrubDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = PureTvTheme.colors
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(VodFormat.timecode(positionMs), style = PureTvType.dataSmall, color = Color.White)
        Slider(
            value = if (durationMs > 0) positionMs.coerceIn(0L, durationMs).toFloat() else 0f,
            onValueChange = { onScrub(it.toLong()) },
            onValueChangeFinished = onScrubDone,
            valueRange = 0f..(durationMs.coerceAtLeast(1L).toFloat()),
            enabled = durationMs > 0,
            colors = SliderDefaults.colors(
                thumbColor = c.primary,
                activeTrackColor = c.primary,
                inactiveTrackColor = Color.White.copy(alpha = 0.3f),
            ),
            modifier = Modifier.weight(1f),
        )
        Text(VodFormat.timecode(durationMs), style = PureTvType.dataSmall, color = Color.White)
    }
}

/** Why the video will not play, with Try again unless trying again cannot help. */
@Composable
private fun VodErrorPanel(message: String, permanent: Boolean, onRetry: () -> Unit, onBack: () -> Unit) {
    val c = PureTvTheme.colors
    Column(
        modifier = Modifier.fillMaxSize().background(Color.Black).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        Text(message, color = c.onSurface, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
        ExpressiveButton(
            text = if (permanent) "Go back" else "Try again",
            onClick = if (permanent) onBack else onRetry,
            style = ExpressiveButtonStyle.Filled,
            size = ExpressiveButtonSize.Medium,
        )
    }
}

/**
 * Picks the video quality. Audio only is left out: it is a live-stream choice,
 * and a VOD keeps its picture. The pick is kept for next time, like on live.
 */
@Composable
private fun VodQualityButton(quality: StreamQuality, onSelect: (StreamQuality) -> Unit) {
    val c = PureTvTheme.colors
    var open by remember { mutableStateOf(false) }
    Box {
        VodScrimButton(
            icon = ExpressiveIcons.Quality,
            contentDescription = "Quality: ${quality.label}",
            onClick = { open = true },
            tint = if (quality != StreamQuality.AUTO) c.primary else Color.White,
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, containerColor = c.surfaceHigh) {
            StreamQuality.entries.filter { it != StreamQuality.AUDIO_ONLY }.sortedBy { it.sortOrder }.forEach { option ->
                DropdownMenuItem(
                    text = {
                        Text(
                            option.label,
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (option == quality) c.primary else c.onSurface,
                        )
                    },
                    onClick = {
                        open = false
                        if (option != quality) onSelect(option)
                    },
                )
            }
        }
    }
}

/**
 * Round scrim button for chrome over the video: a translucent black circle with
 * a white glyph reads over any frame (the same treatment as the live player's
 * back button).
 */
@Composable
private fun VodScrimButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    boxSize: Int = 44,
    tint: Color = Color.White,
    morphIcon: Boolean = false,
) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(boxSize.dp)
            .expressiveClickable(
                interaction = interaction,
                onClick = onClick,
                restRadius = (boxSize / 2).dp,
                pressRadius = PureTvTheme.shapes.pillMorph,
                color = Color.Black.copy(alpha = 0.45f),
            ),
    ) {
        if (morphIcon) {
            // Play and pause swap with a quick scale pop rather than a hard cut.
            AnimatedContent(
                targetState = icon,
                transitionSpec = {
                    (fadeIn(tween(PureTvMotion.Medium, easing = PureTvMotion.Standard)) +
                        scaleIn(tween(PureTvMotion.Medium, easing = PureTvMotion.Standard), initialScale = 0.7f)) togetherWith
                        (fadeOut(tween(PureTvMotion.Fast)) + scaleOut(tween(PureTvMotion.Fast), targetScale = 0.7f))
                },
                label = "vodScrimIcon",
            ) { glyph ->
                Icon(glyph, contentDescription = contentDescription, tint = tint, modifier = Modifier.size((boxSize / 2).dp))
            }
        } else {
            Icon(icon, contentDescription = contentDescription, tint = tint, modifier = Modifier.size((boxSize / 2).dp))
        }
    }
}

private const val SKIP_MS = 10_000L
private const val POSITION_POLL_MS = 500L
private const val CONTROLS_TIMEOUT_MS = 3_000L
