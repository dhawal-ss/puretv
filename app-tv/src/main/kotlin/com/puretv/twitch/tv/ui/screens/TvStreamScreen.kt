package com.puretv.twitch.tv.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusable
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import com.puretv.twitch.core.model.StreamQuality
import com.puretv.twitch.tv.player.TvPlayer
import com.puretv.twitch.tv.ui.StreamViewModel
import com.puretv.twitch.tv.ui.components.TvChatOverlay
import com.puretv.twitch.tv.ui.components.TvControlsOverlay
import com.puretv.twitch.tv.ui.theme.PureTvTvColors
import kotlinx.coroutines.delay
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import org.koin.core.parameter.parametersOf

/**
 * SECTION 07.4 [CRITICAL] — immersive fullscreen player.
 *
 * Behavior, mapped 1:1 to the spec:
 *   • Starts fullscreen immediately, no chrome.
 *   • ANY D-pad key shows [TvControlsOverlay]; a 3-second inactivity timer
 *     hides it again (`controlsVisible` + [LaunchedEffect] restart-on-input).
 *   • DPAD_RIGHT (while controls are hidden / player has focus) opens chat;
 *     DPAD_LEFT closes chat and returns focus to the player.
 *   • FAST_FORWARD / REWIND step [StreamQuality] up/down (live streams can't
 *     seek — repurposed exactly as Section 7.4 specifies).
 *   • DPAD_CENTER / ENTER / PLAY_PAUSE toggle playback via [TvPlayer.togglePlayPause].
 *   • BACK dismisses the chat overlay first, then controls, then exits the
 *     screen (`onBack`) — the dismiss-overlay-before-exit hierarchy from
 *     Section 7.3's BACK-button rules, scoped to this screen's own state
 *     (the home/app-exit levels of that hierarchy live in `TvMainActivity`/
 *     the Activity's default back behavior).
 *
 * The whole screen is wrapped in a single `focusable` + `onKeyEvent` host so
 * every remote key reaches this handler regardless of which (invisible, by
 * default) child currently holds focus — avoiding the need to scatter key
 * handlers across the overlay's buttons.
 */
@OptIn(UnstableApi::class)
@Composable
fun TvStreamScreen(
    channelLogin: String,
    onBack: () -> Unit,
    viewModel: StreamViewModel = koinViewModel(parameters = { parametersOf(channelLogin) }),
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val tvPlayer = koinInject<TvPlayer>()
    val rootFocusRequester = remember { FocusRequester() }

    var controlsVisible by remember { mutableStateOf(true) }
    var chatVisible by remember { mutableStateOf(false) }
    var isPlaying by remember { mutableStateOf(true) }
    var quality by remember { mutableStateOf(StreamQuality.SOURCE) }
    var lastInputAt by remember { mutableStateOf(0L) }

    LaunchedEffect(Unit) {
        runCatching { rootFocusRequester.requestFocus() }
    }

    LaunchedEffect(state.playableUrl) {
        val url = state.playableUrl ?: return@LaunchedEffect
        tvPlayer.exoPlayer.setMediaItem(MediaItem.fromUri(url))
        tvPlayer.exoPlayer.prepare()
        tvPlayer.exoPlayer.play()
        isPlaying = true
    }

    // Auto-hide: any input bumps `lastInputAt`; this effect restarts its 3s
    // countdown each time that happens and hides the chrome once it elapses
    // with no further input (Section 7.4: "auto-hide after 3 seconds").
    LaunchedEffect(lastInputAt) {
        if (lastInputAt == 0L) return@LaunchedEffect
        controlsVisible = true
        delay(3_000)
        controlsVisible = false
    }

    DisposableEffect(Unit) {
        onDispose { /* TvPlayer is a Koin singleton — survives navigation; nothing to release here. */ }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(PureTvTvColors.Background)
            .focusRequester(rootFocusRequester)
            .focusable()
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                lastInputAt = System.currentTimeMillis()

                when (event.key) {
                    Key.Back -> {
                        when {
                            chatVisible -> { chatVisible = false; true }
                            controlsVisible -> { controlsVisible = false; true }
                            else -> { onBack(); true }
                        }
                    }
                    Key.DirectionRight -> {
                        if (!chatVisible) { chatVisible = true; true } else false
                    }
                    Key.DirectionLeft -> {
                        if (chatVisible) { chatVisible = false; true } else false
                    }
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter, Key.MediaPlayPause -> {
                        tvPlayer.togglePlayPause()
                        isPlaying = tvPlayer.exoPlayer.isPlaying
                        true
                    }
                    Key.MediaFastForward -> {
                        quality = stepQuality(quality, up = true)
                        true
                    }
                    Key.MediaRewind -> {
                        quality = stepQuality(quality, up = false)
                        true
                    }
                    Key.Menu -> {
                        chatVisible = !chatVisible
                        true
                    }
                    else -> {
                        // Any other D-pad/remote input still resets the auto-hide
                        // timer (handled above) and reveals the chrome.
                        controlsVisible = true
                        false
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        if (state.playableUrl != null) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = {
                    PlayerView(context).apply {
                        useController = false
                        player = tvPlayer.exoPlayer
                    }
                },
                update = { view -> view.player = tvPlayer.exoPlayer },
            )
        }

        TvControlsOverlay(
            visible = controlsVisible,
            title = state.streamInfo?.title ?: state.channel?.displayName ?: channelLogin,
            viewerCount = state.streamInfo?.viewerCount?.toLong() ?: 0L,
            isPlaying = isPlaying,
            currentQuality = quality,
            adBlockStatus = state.adBlockStatus,
            onBack = onBack,
            onTogglePlayPause = {
                tvPlayer.togglePlayPause()
                isPlaying = tvPlayer.exoPlayer.isPlaying
            },
            onSelectQuality = { quality = it },
            modifier = Modifier.fillMaxSize(),
        )

        TvChatOverlay(
            visible = chatVisible,
            messages = state.chatMessages,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/** Section 7.4 — FAST_FORWARD/REWIND remap to quality up/down on live (unseekable) streams. */
private fun stepQuality(current: StreamQuality, up: Boolean): StreamQuality {
    val ordered = StreamQuality.entries.sortedBy { it.sortOrder }
    val index = ordered.indexOf(current).let { if (it < 0) 0 else it }
    val next = if (up) (index - 1).coerceAtLeast(0) else (index + 1).coerceAtMost(ordered.lastIndex)
    return ordered[next]
}
