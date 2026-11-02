package com.puretv.twitch.tv.ui.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.focusable
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.puretv.twitch.core.adblock.AdBlockStatus
import com.puretv.twitch.core.audio.AudioLevelling
import com.puretv.twitch.core.model.StreamQuality
import com.puretv.twitch.core.playback.RemoteAction
import com.puretv.twitch.core.playback.RemoteKey
import com.puretv.twitch.core.playback.RemoteKeys
import com.puretv.twitch.core.playback.RemoteState
import com.puretv.twitch.tv.notify.TvGoLiveMonitor
import com.puretv.twitch.tv.player.TvPlayer
import com.puretv.twitch.tv.ui.StreamUiState
import com.puretv.twitch.tv.ui.StreamViewModel
import com.puretv.twitch.tv.ui.components.TvChatOverlay
import com.puretv.twitch.tv.ui.components.TvControlsOverlay
import com.puretv.twitch.tv.ui.components.TvFlipBanner
import com.puretv.twitch.tv.ui.components.TvSleepBanner
import com.puretv.twitch.tv.ui.components.rememberTvAlertBell
import com.puretv.twitch.tv.ui.theme.PureTvTvTheme
import kotlinx.coroutines.delay
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import org.koin.core.parameter.parametersOf

/**
 * SECTION 07.4 [CRITICAL] — immersive fullscreen player.
 *
 * Behavior (the key table itself lives in core's `RemoteKeys`, tested there):
 *   • Starts fullscreen with the controls up for 3 seconds, then none.
 *   • Controls hidden: UP/DOWN flip channels (see [StreamViewModel.flip]; the
 *     channel changes in place, because leaving this screen stops the shared
 *     player), OK shows the controls with focus on Play/Pause, RIGHT opens chat
 *     and LEFT closes it.
 *   • Controls visible: the D-pad moves focus between the controls (quality,
 *     chat, sleep timer) and OK presses the focused one; a 3-second inactivity
 *     timer hides them again.
 *   • CHANNEL UP/DOWN and MEDIA NEXT/PREVIOUS always flip; PLAY/PAUSE and
 *     FAST_FORWARD / REWIND (quality up/down, live streams can't seek) work
 *     everywhere.
 *   • BACK closes the chat overlay first, then hides the controls, then exits
 *     the screen (`onBack`). It never counts as activity for the auto-hide
 *     timer, or the chrome would resurrect itself (the old "back does nothing"
 *     lockup).
 *
 * The whole screen is wrapped in a single `focusable` + `onKeyEvent` host. A key
 * the table passes through (D-pad inside the controls) is left unconsumed, so
 * Compose's focus search moves focus along the controls.
 */
@OptIn(UnstableApi::class)
@Composable
fun TvStreamScreen(
    channelLogin: String,
    onBack: () -> Unit,
    /** The sleep timer ran out: leave the stream for Home. */
    onSleepExpired: () -> Unit = onBack,
    viewModel: StreamViewModel = koinViewModel(parameters = { parametersOf(channelLogin) }),
) {
    val state by viewModel.state.collectAsState()
    val bellLogin = state.channelLogin.ifBlank { channelLogin }
    val bell = rememberTvAlertBell(bellLogin, state.channel?.displayName ?: bellLogin)
    val context = LocalContext.current
    val tvPlayer = koinInject<TvPlayer>()
    val goLive = koinInject<TvGoLiveMonitor>()
    val rootFocusRequester = remember { FocusRequester() }
    val playPauseFocusRequester = remember { FocusRequester() }
    // The playGeneration the shared player was last handed (read by its first-frame callback).
    val playingGeneration = remember { intArrayOf(-1) }

    var controlsVisible by remember { mutableStateOf(true) }
    var chatVisible by remember { mutableStateOf(false) }
    var isPlaying by remember { mutableStateOf(true) }
    val quality = state.quality ?: StreamQuality.AUTO
    // Restarts the controls' auto-hide countdown; bumped by input while they are up.
    var controlsTouchedAt by remember { mutableStateOf(0L) }
    val latestState by rememberUpdatedState(state)
    val latestQuality by rememberUpdatedState(quality)

    LaunchedEffect(Unit) {
        // Reveal the chrome on entry, then let the auto-hide timer take it away.
        controlsTouchedAt = System.currentTimeMillis()
    }

    // The controls own D-pad focus while they are up (Play/Pause first); when they go,
    // focus returns to the root so every key still reaches the handler below.
    LaunchedEffect(controlsVisible) {
        if (controlsVisible) {
            // The controls compose in this same pass; wait one frame for their focus targets.
            withFrameNanos { }
            if (runCatching { playPauseFocusRequester.requestFocus() }.isFailure) {
                runCatching { rootFocusRequester.requestFocus() }
            }
        } else {
            runCatching { rootFocusRequester.requestFocus() }
        }
    }

    // The go-live banner skips the channel being watched, which changes with a flip.
    LaunchedEffect(state.channelLogin) {
        goLive.watchingLogin = state.channelLogin.ifBlank { null }
    }

    // Once per resolved stream (a flip resolves a new one), not once per URL.
    LaunchedEffect(state.playGeneration) {
        val url = state.playableUrl ?: return@LaunchedEffect
        // Remember which resolution is playing, so a first frame from the previous
        // channel's picture is not counted for this one (see StreamViewModel.onFirstFrame).
        playingGeneration[0] = state.playGeneration
        tvPlayer.playUrl(url, lowLatency = state.lowLatency)
        isPlaying = true
    }

    // A stream that cannot play must not leave the previous channel's picture running
    // under the explanation.
    LaunchedEffect(state.playbackError) {
        if (state.playbackError != null) tvPlayer.stop()
    }

    // The sleep timer ran out: stop and go home.
    LaunchedEffect(state.sleepExpired) {
        if (state.sleepExpired) {
            tvPlayer.stop()
            viewModel.onSleepHandled()
            onSleepExpired()
        }
    }

    // Local time-to-first-frame mark. Cleared on leave so a released screen's
    // view model is never called back.
    DisposableEffect(viewModel) {
        val listener = { viewModel.onFirstFrame(playingGeneration[0]) }
        val gaveUp: (androidx.media3.common.PlaybackException) -> Unit = { viewModel.onPlaybackGaveUp() }
        tvPlayer.onFirstFrame = listener
        tvPlayer.onGaveUp = gaveUp
        onDispose {
            if (tvPlayer.onFirstFrame === listener) tvPlayer.onFirstFrame = null
            if (tvPlayer.onGaveUp === gaveUp) tvPlayer.onGaveUp = null
        }
    }

    // Apply the quality to the player whenever it changes (the quality chips and
    // FAST_FORWARD/REWIND both go through the view model). The first value is the
    // saved preference, which also clears any cap the shared singleton player
    // carried over from a previous stream.
    LaunchedEffect(state.quality) {
        val selected = state.quality ?: return@LaunchedEffect
        runCatching { tvPlayer.setQuality(selected) }
    }

    // Auto-hide: input while the controls are up bumps `controlsTouchedAt`, which
    // restarts this 3s countdown; the chrome goes once it elapses with no further input
    // (Section 7.4: "auto-hide after 3 seconds"). Flipping channels does not show them.
    LaunchedEffect(controlsVisible, controlsTouchedAt) {
        if (!controlsVisible || controlsTouchedAt == 0L) return@LaunchedEffect
        delay(3_000)
        controlsVisible = false
    }

    DisposableEffect(Unit) {
        val player = tvPlayer.exoPlayer
        val window = context.findActivity()?.window
        val playbackListener = object : Player.Listener {
            override fun onIsPlayingChanged(nowPlaying: Boolean) {
                isPlaying = nowPlaying
                if (nowPlaying) {
                    window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                } else {
                    window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
            }
        }

        player.addListener(playbackListener)
        playbackListener.onIsPlayingChanged(player.isPlaying)

        // TvPlayer is an app-wide Koin singleton with playWhenReady=true, so
        // leaving this screen must STOP it, otherwise audio keeps playing after
        // the viewer navigates back. We stop (not release): the singleton is reused
        // for the next stream and rebuilds itself if it was ever released.
        // The screen-on flag is tied to actual playback and always cleared here,
        // allowing Fire TV's screensaver to work normally in menus or when paused.
        onDispose {
            player.removeListener(playbackListener)
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            tvPlayer.stop()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(PureTvTvTheme.colors.surfaceLowest)
            .focusRequester(rootFocusRequester)
            .focusable()
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false

                val key = event.key.toRemoteKey()
                val current = latestState
                val action = RemoteKeys.decide(
                    key,
                    RemoteState(
                        controlsVisible = controlsVisible,
                        chatVisible = chatVisible,
                        hasError = current.playbackError != null,
                        upDownFlips = current.upDownFlips,
                    ),
                )
                if (RemoteKeys.isActivity(key)) {
                    // Any key buys a sleepy viewer more time, and shows the flip neighbours
                    // are worth having ready (core throttles the prewarm).
                    viewModel.onViewerActivity()
                    // BACK is leaving, not reaching for another channel: nothing to warm.
                    if (key != RemoteKey.Back) viewModel.onRemoteIntent()
                    // BACK is never "activity" for the auto-hide: bumping the timer for it
                    // would resurrect the chrome and the viewer could never leave the player.
                    if (controlsVisible && key != RemoteKey.Back) controlsTouchedAt = System.currentTimeMillis()
                }

                when (action) {
                    RemoteAction.PassThrough -> false
                    RemoteAction.Ignore -> true
                    is RemoteAction.Flip -> { viewModel.flip(action.direction); true }
                    RemoteAction.ShowControls -> {
                        controlsVisible = true
                        controlsTouchedAt = System.currentTimeMillis()
                        true
                    }
                    RemoteAction.HideControls -> { controlsVisible = false; true }
                    RemoteAction.OpenChat -> { chatVisible = true; true }
                    RemoteAction.CloseChat -> { chatVisible = false; true }
                    RemoteAction.ToggleChat -> { chatVisible = !chatVisible; true }
                    RemoteAction.TogglePlayPause -> {
                        tvPlayer.togglePlayPause()
                        isPlaying = tvPlayer.exoPlayer.isPlaying
                        true
                    }
                    // With no playable URL there is nothing to pause, so OK becomes the
                    // retry affordance the error panel advertises.
                    RemoteAction.Retry -> { viewModel.retry(); true }
                    is RemoteAction.StepQuality -> {
                        viewModel.setQuality(stepQuality(latestQuality, up = action.up))
                        true
                    }
                    RemoteAction.Leave -> { onBack(); true }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        // The player always sits in the same slot of this row, whichever chat
        // position is chosen: moving its AndroidView to another parent would rebuild
        // the PlayerView. Beside the video, chat takes the row's second slot and the
        // video shrinks to fit; over the video, the second slot stays empty.
        Row(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                StreamVideoArea(
                    playableUrl = state.playableUrl,
                    playbackError = state.playbackError,
                    title = state.streamInfo?.title ?: state.channel?.displayName ?: state.channelLogin.ifBlank { channelLogin },
                    viewerCount = state.streamInfo?.viewerCount?.toLong() ?: 0L,
                    adBlockStatus = state.adBlockStatus,
                    tvPlayer = tvPlayer,
                    controlsVisible = controlsVisible,
                    isPlaying = isPlaying,
                    quality = quality,
                    onBack = onBack,
                    onTogglePlayPause = {
                        // On the error panel the focused Play button is the retry affordance.
                        if (state.playbackError != null) {
                            viewModel.retry()
                        } else {
                            tvPlayer.togglePlayPause()
                            isPlaying = tvPlayer.exoPlayer.isPlaying
                        }
                    },
                    onSelectQuality = viewModel::setQuality,
                    chatVisible = chatVisible,
                    onToggleChat = { chatVisible = !chatVisible },
                    sleepLabel = state.sleepLabel,
                    onCycleSleep = viewModel::cycleSleepTimer,
                    alertsOn = bell.on,
                    alertsNote = bell.message,
                    onToggleAlerts = bell.toggle,
                    audioLevellingLabel = audioLevellingLabel(state.audioNormalizer, state.audioLevelling),
                    onCycleAudioLevelling = { viewModel.cycleAudioLevelling() },
                    playPauseFocusRequester = playPauseFocusRequester,
                )
                if (!state.chatBesideVideo) {
                    StreamChat(state = state, visible = chatVisible, beside = false, modifier = Modifier.fillMaxSize())
                }
            }
            if (state.chatBesideVideo) {
                StreamChat(state = state, visible = chatVisible, beside = true, modifier = Modifier.fillMaxHeight())
            }
        }

        // Plain text over the picture, never focusable: the remote keeps driving the player.
        TvFlipBanner(
            target = state.flipTarget,
            note = state.flipNote,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 150.dp),
        )
        TvSleepBanner(
            text = state.sleepWarning,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 220.dp),
        )
    }
}

/** The remote key the decision table in core sees for a Compose [Key]. */
private fun Key.toRemoteKey(): RemoteKey = when (this) {
    Key.DirectionUp -> RemoteKey.Up
    Key.DirectionDown -> RemoteKey.Down
    Key.DirectionLeft -> RemoteKey.Left
    Key.DirectionRight -> RemoteKey.Right
    Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> RemoteKey.Center
    Key.Back -> RemoteKey.Back
    Key.MediaPlayPause, Key.MediaPlay, Key.MediaPause -> RemoteKey.PlayPause
    Key.MediaFastForward -> RemoteKey.FastForward
    Key.MediaRewind -> RemoteKey.Rewind
    Key.ChannelUp, Key.PageUp -> RemoteKey.ChannelUp
    Key.ChannelDown, Key.PageDown -> RemoteKey.ChannelDown
    Key.MediaNext -> RemoteKey.MediaNext
    Key.MediaPrevious -> RemoteKey.MediaPrevious
    Key.Menu -> RemoteKey.Menu
    else -> RemoteKey.Other
}

/** The stream's chat, in either position, wired to the screen state. */
@Composable
private fun StreamChat(state: StreamUiState, visible: Boolean, beside: Boolean, modifier: Modifier) {
    TvChatOverlay(
        visible = visible,
        messages = state.chatMessages,
        modifier = modifier,
        textScale = state.chatTextScale,
        roomModes = state.roomModes,
        badges = state.badges,
        showBadges = state.showBadges,
        emotes = state.emotes,
        beside = beside,
        opacity = state.chatOpacity,
    )
}

/**
 * The player surface, the failure explanation and the controls, all within the
 * video's slot. Takes only the fields it shows (not the whole state), so a chat
 * batch, which changes the state about ten times a second in a busy channel,
 * skips this part of the screen entirely.
 */
@OptIn(UnstableApi::class)
@Composable
private fun StreamVideoArea(
    playableUrl: String?,
    playbackError: String?,
    title: String,
    viewerCount: Long,
    adBlockStatus: AdBlockStatus,
    tvPlayer: TvPlayer,
    controlsVisible: Boolean,
    isPlaying: Boolean,
    quality: StreamQuality,
    onBack: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onSelectQuality: (StreamQuality) -> Unit,
    chatVisible: Boolean,
    onToggleChat: () -> Unit,
    sleepLabel: String,
    onCycleSleep: () -> Unit,
    alertsOn: Boolean,
    alertsNote: String?,
    onToggleAlerts: () -> Unit,
    audioLevellingLabel: String,
    onCycleAudioLevelling: () -> Unit,
    playPauseFocusRequester: FocusRequester,
) {
    val context = LocalContext.current
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (playableUrl != null) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = {
                    PlayerView(context).apply {
                        useController = false
                        player = tvPlayer.exoPlayer
                        // The player surface must never take D-pad focus: the root
                        // Box owns every remote key via onKeyEvent (incl. BACK). If
                        // PlayerView grabbed focus, key events would bypass that
                        // handler and BACK/quality/chat would stop responding.
                        isFocusable = false
                        isFocusableInTouchMode = false
                        descendantFocusability = android.view.ViewGroup.FOCUS_BLOCK_DESCENDANTS
                    }
                },
                update = { view -> view.player = tvPlayer.exoPlayer },
            )
        }

        // Resolution failed: say so instead of leaving a black rectangle. Not
        // tied to `controlsVisible` — the auto-hide timer must never take the
        // only explanation of the failure away with it.
        if (playbackError != null) {
            Column(
                modifier = Modifier.fillMaxSize().padding(48.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
            ) {
                Text(
                    text = playbackError,
                    style = MaterialTheme.typography.titleMedium,
                    color = PureTvTvTheme.colors.onSurface,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = "Press OK to try again, or BACK to choose another channel.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = PureTvTvTheme.colors.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }

        TvControlsOverlay(
            visible = controlsVisible,
            title = title,
            viewerCount = viewerCount,
            isPlaying = isPlaying,
            currentQuality = quality,
            adBlockStatus = adBlockStatus,
            onBack = onBack,
            onTogglePlayPause = onTogglePlayPause,
            onSelectQuality = onSelectQuality,
            modifier = Modifier.fillMaxSize(),
            chatVisible = chatVisible,
            onToggleChat = onToggleChat,
            sleepLabel = sleepLabel,
            onCycleSleep = onCycleSleep,
            alertsOn = alertsOn,
            alertsNote = alertsNote,
            onToggleAlerts = onToggleAlerts,
            audioLevellingLabel = audioLevellingLabel,
            onCycleAudioLevelling = onCycleAudioLevelling,
            playPauseFocusRequester = playPauseFocusRequester,
        )
    }
}

/** The player's audio levelling button label: the strength in use, or Off. */
private fun audioLevellingLabel(on: Boolean, level: AudioLevelling): String =
    "Audio levelling: ${if (on) level.label else "Off"}"

/** Section 7.4 — FAST_FORWARD/REWIND remap to quality up/down on live (unseekable) streams. */
private fun stepQuality(current: StreamQuality, up: Boolean): StreamQuality {
    val ordered = StreamQuality.entries.sortedBy { it.sortOrder }
    val index = ordered.indexOf(current).let { if (it < 0) 0 else it }
    val next = if (up) (index - 1).coerceAtLeast(0) else (index + 1).coerceAtMost(ordered.lastIndex)
    return ordered[next]
}

/** Unwraps Compose's themed context to the Activity that owns the TV window. */
private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
