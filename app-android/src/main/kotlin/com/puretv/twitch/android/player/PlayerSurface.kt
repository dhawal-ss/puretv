package com.puretv.twitch.android.player

import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
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
@Composable
fun PlayerSurface(playableUrl: String?, useController: Boolean = true, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val twitchPlayer = koinInject<TwitchPlayer>()

    LaunchedEffect(playableUrl) {
        val url = playableUrl ?: return@LaunchedEffect
        val player = twitchPlayer.exoPlayer
        // Idempotent: if the player is already on this URL (e.g. the surface just
        // re-mounted after a rotation or PiP branch switch), do not re-prepare, or
        // playback would needlessly re-buffer. Only a genuinely new URL prepares.
        val currentUri = player.currentMediaItem?.localConfiguration?.uri?.toString()
        if (currentUri != url) {
            player.setMediaItem(MediaItem.fromUri(url))
            player.prepare()
            player.play()
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
                player = twitchPlayer.exoPlayer
            }
        },
        update = { view ->
            view.player = twitchPlayer.exoPlayer
            // Hide the controller in PiP (the tiny window should be video only).
            view.useController = useController
        },
    )
}
