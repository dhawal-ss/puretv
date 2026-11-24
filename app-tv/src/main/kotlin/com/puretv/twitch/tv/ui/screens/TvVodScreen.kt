package com.puretv.twitch.tv.ui.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.puretv.twitch.core.stream.Storyboard
import com.puretv.twitch.core.vod.VodFormat
import com.puretv.twitch.core.vod.VodScrub
import com.puretv.twitch.tv.player.TvPlayer
import com.puretv.twitch.tv.ui.TvVodViewModel
import com.puretv.twitch.tv.ui.VodLaunch
import com.puretv.twitch.tv.ui.components.ExpressiveIcons
import com.puretv.twitch.tv.ui.components.StoryboardPreview
import com.puretv.twitch.tv.ui.components.TvChatOverlay
import com.puretv.twitch.tv.ui.components.TvButtonStyle
import com.puretv.twitch.tv.ui.components.TvExpressiveButton
import com.puretv.twitch.tv.ui.theme.PureTvTvMotion
import com.puretv.twitch.tv.ui.theme.PureTvTvTheme
import com.puretv.twitch.tv.ui.theme.PureTvTvType
import kotlinx.coroutines.delay
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import org.koin.core.parameter.parametersOf

/**
 * A past broadcast, fullscreen. One root `focusable` + `onKeyEvent` owns every
 * remote key, the same structure as [TvStreamScreen]:
 *
 *   OK / Play-Pause   play or pause (with a scrub pending, jump there now)
 *   Left / Right      move a pending position 10 s, faster while held
 *   Rewind / FF       move it 60 s
 *   BACK              cancel a pending scrub first, then leave
 *
 * The seek itself fires once, [VodScrub.COMMIT_DELAY_MS] after the last scrub
 * key, so holding Right on a remote without FF/RW races ahead and seeks once.
 * The bar at the bottom shows the position, the length and the pending spot.
 */
@OptIn(UnstableApi::class)
@Composable
fun TvVodScreen(
    launch: VodLaunch,
    onBack: () -> Unit,
    viewModel: TvVodViewModel = koinViewModel(parameters = { parametersOf(launch) }),
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val tvPlayer = koinInject<TvPlayer>()
    val player = tvPlayer.exoPlayer
    val rootFocusRequester = remember { FocusRequester() }
    val resumeFocusRequester = remember { FocusRequester() }
    val offer = state.resumeOfferMs

    var controlsVisible by remember { mutableStateOf(true) }
    var lastInputAt by remember { mutableLongStateOf(0L) }
    var isPlaying by remember { mutableStateOf(player.isPlaying) }
    var buffering by remember { mutableStateOf(true) }
    var positionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    // Where Left/Right/FF/RW have moved to; the seek fires after the keys settle.
    var pendingMs by remember { mutableStateOf<Long?>(null) }
    var chatVisible by remember { mutableStateOf(false) }
    val chatMessages by viewModel.chatMessages.collectAsState()

    // The chat replay follows the playback position. Polled once a second, and
    // only while the overlay is on screen.
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(chatVisible) {
        // Not while the screen is stopped.
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (chatVisible) {
                viewModel.onChatPosition(player.currentPosition.coerceAtLeast(0L) / 1000)
                delay(1_000)
            }
        }
    }

    // A go-live stream can be stacked on this video and empty the shared player on
    // its way out; once this screen is back in front the video starts again where it
    // was. The short wait lets the leaving stream finish its own teardown first.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) viewModel.onScreenPaused()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            delay(RESUME_SETTLE_MS)
            viewModel.onScreenResumed()
        }
    }

    LaunchedEffect(Unit) {
        runCatching { rootFocusRequester.requestFocus() }
        lastInputAt = System.currentTimeMillis()
    }

    // The offer takes focus (Resume first) so OK answers it; afterwards the root
    // gets the remote back for scrubbing.
    val offerShown = offer != null && state.error == null
    LaunchedEffect(offerShown) {
        runCatching { if (offerShown) resumeFocusRequester.requestFocus() else rootFocusRequester.requestFocus() }
    }

    // Auto-hide 3 s after the last key, but never while paused or mid-scrub.
    LaunchedEffect(lastInputAt, isPlaying, pendingMs == null) {
        if (lastInputAt == 0L) return@LaunchedEffect
        controlsVisible = true
        if (!isPlaying || pendingMs != null) return@LaunchedEffect
        delay(CONTROLS_TIMEOUT_MS)
        controlsVisible = false
    }

    // The position is only read while the bar is on screen.
    LaunchedEffect(controlsVisible) {
        while (controlsVisible) {
            positionMs = player.currentPosition.coerceAtLeast(0L)
            durationMs = player.duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: 0L
            delay(POSITION_POLL_MS)
        }
    }

    // Debounced seek: each scrub key restarts this, so only the last one seeks.
    LaunchedEffect(pendingMs) {
        val target = pendingMs ?: return@LaunchedEffect
        delay(VodScrub.COMMIT_DELAY_MS)
        player.seekTo(target)
        positionMs = target
        pendingMs = null
    }

    DisposableEffect(player) {
        val window = context.findActivity()?.window
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(nowPlaying: Boolean) {
                isPlaying = nowPlaying
                // Screen-on only while actually playing, so a paused video lets
                // the TV's screensaver in, as on the live screen.
                if (nowPlaying) {
                    window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                } else {
                    window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                buffering = playbackState == Player.STATE_BUFFERING
            }
        }
        player.addListener(listener)
        listener.onIsPlayingChanged(player.isPlaying)
        // No stop here: the view model stops the player after reading where the
        // viewer got to.
        onDispose {
            player.removeListener(listener)
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    fun scrub(deltaMs: Long) {
        val duration = player.duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: 0L
        pendingMs = VodScrub.move(pendingMs, player.currentPosition.coerceAtLeast(0L), duration, deltaMs)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(rootFocusRequester)
            .focusable()
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false

                // BACK never counts as activity (see TvStreamScreen): a pending
                // scrub is cancelled first, then the screen is left.
                if (event.key == Key.Back) {
                    if (pendingMs != null) pendingMs = null else onBack()
                    return@onKeyEvent true
                }

                lastInputAt = System.currentTimeMillis()

                if (state.error != null) {
                    return@onKeyEvent when (event.key) {
                        Key.DirectionCenter, Key.Enter, Key.NumPadEnter, Key.MediaPlayPause -> {
                            if (state.errorIsPermanent) onBack() else viewModel.retry()
                            true
                        }
                        else -> false
                    }
                }

                // While the offer is up, the two buttons use the D-pad and OK themselves.
                if (offer != null) return@onKeyEvent false

                when (event.key) {
                    Key.DirectionRight -> { scrub(VodScrub.stepFor(event.nativeKeyEvent.repeatCount)); true }
                    Key.DirectionLeft -> { scrub(-VodScrub.stepFor(event.nativeKeyEvent.repeatCount)); true }
                    Key.DirectionUp -> { if (!chatVisible) { chatVisible = true; true } else false }
                    Key.DirectionDown -> { if (chatVisible) { chatVisible = false; true } else false }
                    Key.Menu -> { chatVisible = !chatVisible; true }
                    Key.MediaFastForward -> { scrub(VodScrub.JUMP_MS); true }
                    Key.MediaRewind -> { scrub(-VodScrub.JUMP_MS); true }
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter, Key.MediaPlayPause -> {
                        val target = pendingMs
                        if (target != null) {
                            player.seekTo(target)
                            positionMs = target
                            pendingMs = null
                        } else {
                            tvPlayer.togglePlayPause()
                            isPlaying = player.isPlaying
                        }
                        true
                    }
                    else -> {
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
                        this.player = tvPlayer.exoPlayer
                        // Never take D-pad focus: the root Box owns every key.
                        isFocusable = false
                        isFocusableInTouchMode = false
                        descendantFocusability = android.view.ViewGroup.FOCUS_BLOCK_DESCENDANTS
                    }
                },
                // Unhook from the shared player when the view goes, or it stays attached for good.
                onRelease = { view -> view.player = null },
                update = { view -> view.player = tvPlayer.exoPlayer },
            )
        }

        val error = state.error
        if (error != null) {
            Column(
                modifier = Modifier.fillMaxSize().padding(48.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
            ) {
                Text(
                    text = error,
                    style = MaterialTheme.typography.titleMedium,
                    color = PureTvTvTheme.colors.onSurface,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = if (state.errorIsPermanent) "Press OK or BACK to go back." else "Press OK to try again, or BACK to go back.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = PureTvTvTheme.colors.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        } else if (state.playableUrl == null || buffering) {
            Text(
                text = "Loading",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White.copy(alpha = 0.8f),
            )
        }

        TvChatOverlay(
            visible = chatVisible,
            messages = chatMessages,
            modifier = Modifier.fillMaxSize(),
        )

        if (offer != null && error == null) {
            Row(
                modifier = Modifier.align(Alignment.BottomStart).padding(start = 48.dp, bottom = 190.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                TvExpressiveButton(
                    text = "Resume from ${VodFormat.timecode(offer)}",
                    onClick = viewModel::resume,
                    style = TvButtonStyle.Filled,
                    modifier = Modifier.focusRequester(resumeFocusRequester),
                )
                TvExpressiveButton(text = "Start over", onClick = viewModel::startOver, style = TvButtonStyle.Tonal)
            }
        }

        AnimatedVisibility(
            visible = error == null && (controlsVisible || pendingMs != null),
            enter = PureTvTvMotion.ControlsEnter,
            exit = PureTvTvMotion.ControlsExit,
            modifier = Modifier.fillMaxSize(),
        ) {
            VodBar(
                title = launch.title.ifBlank { "Past broadcast" },
                channel = launch.channelLogin,
                isPlaying = isPlaying,
                positionMs = positionMs,
                durationMs = durationMs,
                pendingMs = pendingMs,
                storyboard = state.storyboard,
            )
        }
    }
}

/** Title, play state, the progress track with the pending scrub, and the times. */
@Composable
private fun VodBar(
    title: String,
    channel: String,
    isPlaying: Boolean,
    positionMs: Long,
    durationMs: Long,
    pendingMs: Long?,
    storyboard: Storyboard?,
) {
    val c = PureTvTvTheme.colors
    Box(modifier = Modifier.fillMaxSize()) {
        // The frame at the PENDING spot, so a held key shows where it will land.
        if (pendingMs != null && storyboard != null) {
            StoryboardPreview(
                storyboard = storyboard,
                positionMs = pendingMs,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 210.dp),
            )
        }
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Brush.verticalGradient(0f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.85f)))
                .padding(horizontal = 48.dp, vertical = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Icon(
                    imageVector = if (isPlaying) ExpressiveIcons.Pause else ExpressiveIcons.Play,
                    contentDescription = if (isPlaying) "Playing" else "Paused",
                    tint = Color.White,
                    modifier = Modifier.size(36.dp),
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(text = channel, style = PureTvTvType.data, color = Color.White.copy(alpha = 0.75f), maxLines = 1)
                }
                if (pendingMs != null) {
                    Text(
                        text = "Jump to ${VodFormat.timecode(pendingMs)}",
                        style = PureTvTvType.data,
                        color = c.primary,
                    )
                }
            }
            ProgressTrack(positionMs = positionMs, durationMs = durationMs, pendingMs = pendingMs)
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "${VodFormat.timecode(positionMs)} / ${VodFormat.timecode(durationMs)}",
                    style = PureTvTvType.data,
                    color = Color.White,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = "Left and right to skip, OK to pause, up for chat",
                    style = PureTvTvType.data,
                    color = Color.White.copy(alpha = 0.6f),
                )
            }
        }
    }
}

/** The played part in white, the pending scrub as a marker in the accent colour. */
@Composable
private fun ProgressTrack(positionMs: Long, durationMs: Long, pendingMs: Long?) {
    val c = PureTvTvTheme.colors
    fun fraction(ms: Long): Float = if (durationMs > 0) (ms.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    BoxWithConstraints(modifier = Modifier.fillMaxWidth().height(14.dp), contentAlignment = Alignment.CenterStart) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(Color.White.copy(alpha = 0.25f)),
        )
        Box(
            Modifier
                .fillMaxWidth(fraction(positionMs))
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(Color.White),
        )
        if (pendingMs != null) {
            Box(
                Modifier
                    .offset(x = maxWidth * fraction(pendingMs) - 7.dp)
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(c.primary),
            )
        }
    }
}

/** Unwraps Compose's themed context to the Activity that owns the TV window. */
private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private const val RESUME_SETTLE_MS = 300L
private const val POSITION_POLL_MS = 500L
private const val CONTROLS_TIMEOUT_MS = 3_000L
