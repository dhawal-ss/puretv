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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.puretv.twitch.android.ui.theme.PureTvColors
import org.koin.compose.koinInject

/**
 * SECTION 06.3 — Compose host for the [TwitchPlayer]/ExoPlayer surface.
 * Wrapped in [AndroidView] since Media3's `PlayerView` has no Compose-native
 * equivalent yet.
 *
 * The ExoPlayer instance is a Koin singleton shared across navigation so PiP
 * keeps playing; the playback model is "PiP + pause-on-leave", no background
 * service. Teardown (stop + clearMediaItems) lives in `StreamViewModel.onCleared`,
 * NOT here, because this surface leaves composition on rotation and PiP branch
 * switches too, and stopping there would re-buffer playback on every rotation.
 * onCleared fires only on a real back-stack pop, i.e. truly leaving the stream.
 *
 * This surface only owns the lifecycle pause/resume: it pauses on ON_STOP when
 * the host Activity is NOT in PiP, and resumes on ON_RESUME, so backgrounding
 * without PiP does not keep audio running while PiP is left playing.
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
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val twitchPlayer = koinInject<TwitchPlayer>()

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
        if (currentUri != url) {
            twitchPlayer.playUrl(url)
        }
    }

    // Pause when backgrounded WITHOUT PiP; resume when foregrounded. PiP is left
    // playing so the floating window keeps audio/video.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> {
                    val inPip =
                        (context as? android.app.Activity)?.isInPictureInPictureMode == true
                    if (!inPip) {
                        twitchPlayer.exoPlayer.pause()
                    }
                }
                Lifecycle.Event.ON_RESUME -> {
                    if (twitchPlayer.exoPlayer.mediaItemCount > 0) {
                        twitchPlayer.exoPlayer.play()
                    }
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (playableUrl == null) {
        androidx.compose.foundation.layout.Box(
            modifier = modifier.background(PureTvColors.Surface),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator(color = PureTvColors.TwitchPurple)
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
            // Hide the controller in PiP (the tiny window should be video only).
            view.useController = useController
            view.resizeMode = resizeMode
        },
    )
}
