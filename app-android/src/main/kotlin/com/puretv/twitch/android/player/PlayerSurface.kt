package com.puretv.twitch.android.player

import android.annotation.SuppressLint
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.puretv.twitch.android.ui.theme.PureTvTheme
import org.koin.compose.koinInject

/**
 * SECTION 06.3: Compose host for the [TwitchPlayer]/ExoPlayer surface.
 * Wrapped in [AndroidView] since Media3's `PlayerView` has no Compose-native
 * equivalent yet.
 *
 * The ExoPlayer instance is a Koin singleton shared across navigation so PiP
 * and background play keep the same session. Teardown (stop + clearMediaItems)
 * lives in `StreamViewModel.onCleared`, NOT here, because this surface leaves
 * composition on rotation and PiP branch switches too, and stopping there would
 * re-buffer playback on every rotation. onCleared fires only on a real
 * back-stack pop, i.e. truly leaving the stream.
 *
 * This surface only reports the Activity's stop/resume to [BackgroundPlayback],
 * which decides whether leaving pauses (background play off, no PiP) or keeps
 * playing under the media notification (background play on).
 */
@OptIn(UnstableApi::class)
@SuppressLint("ClickableViewAccessibility")
@Composable
fun PlayerSurface(
    playableUrl: String?,
    useController: Boolean = true,
    // AspectRatioFrameLayout.RESIZE_MODE_*: FIT letterboxes to preserve aspect;
    // ZOOM crops-to-fill so a 16:9 stream covers a taller phone screen (and the
    // camera cutout) with no black bars.
    resizeMode: Int = AspectRatioFrameLayout.RESIZE_MODE_FIT,
    // Fired on a double-tap of the video (used to toggle fill/fit in fullscreen).
    onDoubleTap: (() -> Unit)? = null,
    // Reports the player controller's show/hide so overlay chrome (back button,
    // pills, badges) can auto-hide in sync instead of persisting over the video.
    onControlsVisibilityChanged: ((Boolean) -> Unit)? = null,
    // Title, channel and artwork for the media notification and lock screen.
    metadata: MediaMetadata = MediaMetadata.EMPTY,
    // Settings > Low latency, applied when a new stream starts.
    lowLatency: Boolean = false,
    // The stream view model's claim on the shared player (see PlayOwnership).
    ticket: TwitchPlayer.PlayTicket? = null,
    modifier: Modifier = Modifier,
    // False when the caller already started [playableUrl] itself: a VOD is
    // played by its view model through TwitchPlayer.playVod, never playUrl.
    startsPlayback: Boolean = true,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val twitchPlayer = koinInject<TwitchPlayer>()
    val backgroundPlayback = koinInject<BackgroundPlayback>()
    val currentMetadata by rememberUpdatedState(metadata)
    val currentLowLatency by rememberUpdatedState(lowLatency)

    // Keep live handles to the latest callbacks so the listeners installed once in
    // factory{} always call the current ones (fullscreen/state changes).
    val currentOnDoubleTap by rememberUpdatedState(onDoubleTap)
    val currentOnControlsVis by rememberUpdatedState(onControlsVisibilityChanged)

    LaunchedEffect(playableUrl) {
        val url = playableUrl ?: return@LaunchedEffect
        val player = twitchPlayer.exoPlayer
        // Idempotent: if the player is already on this URL (e.g. the surface just
        // re-mounted after a rotation or PiP branch switch), do not re-prepare, or
        // playback would needlessly re-buffer. Only a genuinely new URL prepares
        // (which also resets the transient-error retry budget for the new stream).
        val currentUri = player.currentMediaItem?.localConfiguration?.uri?.toString()
        if (startsPlayback && currentUri != url) {
            twitchPlayer.playUrl(url, currentMetadata, currentLowLatency, ticket)
        }
        // Still in the foreground here, which is the only place Android 12+ lets
        // the foreground service start. No-op when background play is off.
        backgroundPlayback.startService()
    }

    val sharedPlayer = twitchPlayer.exoPlayer

    // Issue #42: keep the display awake while the video is meant to be playing,
    // as every video app does, so a short system screen timeout does not blank a
    // stream nobody is touching. Held through buffering too (a stall would
    // otherwise let the screen go dark mid-stream); released on pause, error or
    // end so the normal timeout applies again. It is set on the PlayerView, so
    // Android drops it on its own once the video leaves the screen.
    var keepScreenOn by remember { mutableStateOf(shouldKeepScreenOn(sharedPlayer)) }

    // Playback can restart without a lifecycle event: Stop in the notification
    // shade tears the session down without pausing the Activity, then a tap on
    // play would run with no notification behind it and keep going unseen once
    // the viewer leaves. Re-raise the session whenever playback actually starts;
    // startService is idempotent and gated on the setting. The same listener
    // keeps [keepScreenOn] in step with the player.
    DisposableEffect(sharedPlayer) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) backgroundPlayback.startService()
            }

            override fun onEvents(player: Player, events: Player.Events) {
                if (events.containsAny(Player.EVENT_PLAY_WHEN_READY_CHANGED, Player.EVENT_PLAYBACK_STATE_CHANGED)) {
                    keepScreenOn = shouldKeepScreenOn(player)
                }
            }
        }
        sharedPlayer.addListener(listener)
        keepScreenOn = shouldKeepScreenOn(sharedPlayer)
        onDispose { sharedPlayer.removeListener(listener) }
    }

    // Leaving the app pauses only when background play is off and no PiP window
    // is showing the video; coming back resumes only a pause we made ourselves.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> {
                    val activity = context as? android.app.Activity
                    backgroundPlayback.onAppStopped(
                        inPip = activity?.isInPictureInPictureMode == true,
                        changingConfigurations = activity?.isChangingConfigurations == true,
                    )
                }
                Lifecycle.Event.ON_RESUME -> backgroundPlayback.onAppResumed()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (playableUrl == null) {
        androidx.compose.foundation.layout.Box(
            modifier = modifier.background(PureTvTheme.colors.surfaceLowest),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator(color = PureTvTheme.colors.primary)
        }
        return
    }

    AndroidView(
        modifier = modifier,
        factory = {
            PlayerView(context).apply {
                this.useController = useController
                this.resizeMode = resizeMode
                player = twitchPlayer.exoPlayer

                // Auto-hide the controls ~3s after they are shown (default is longer),
                // and mirror their show/hide to the Compose overlay chrome so the
                // pills fade out with the controller instead of persisting.
                controllerShowTimeoutMs = 3_000
                setControllerVisibilityListener(
                    PlayerView.ControllerVisibilityListener { visibility ->
                        currentOnControlsVis?.invoke(visibility == View.VISIBLE)
                    },
                )

                // Observe touches for a double-tap without stealing single taps:
                // the listener returns false so the PlayerView still handles the
                // single tap (show/hide its controller), while the detector fires
                // onDoubleTap out of band. currentOnDoubleTap is read live so the
                // fullscreen-gated callback is always the current one.
                val detector = GestureDetector(
                    context,
                    object : GestureDetector.SimpleOnGestureListener() {
                        override fun onDoubleTap(e: MotionEvent): Boolean {
                            currentOnDoubleTap?.invoke()
                            return true
                        }
                    },
                )
                setOnTouchListener { _, event ->
                    detector.onTouchEvent(event)
                    false
                }
            }
        },
        update = { view ->
            view.player = twitchPlayer.exoPlayer
            view.keepScreenOn = keepScreenOn
            // Hide the controller in PiP (the tiny window should be video only).
            view.useController = useController
            view.resizeMode = resizeMode
        },
    )
}

private fun shouldKeepScreenOn(player: Player): Boolean =
    shouldKeepScreenOn(player.playWhenReady, player.playbackState)

/**
 * Whether the screen should stay on: the viewer has asked to play and the player
 * is either playing or buffering toward it. Paused, failed (IDLE) and finished
 * (ENDED) all let the device's own screen timeout apply.
 */
internal fun shouldKeepScreenOn(playWhenReady: Boolean, playbackState: Int): Boolean =
    playWhenReady && (playbackState == Player.STATE_READY || playbackState == Player.STATE_BUFFERING)
