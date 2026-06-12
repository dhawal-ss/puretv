package com.puretv.twitch.android.player

import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import com.puretv.twitch.android.ui.theme.PureTvColors
import org.koin.compose.koinInject

/**
 * SECTION 06.3 — Compose host for the [TwitchPlayer]/ExoPlayer surface.
 * Wrapped in [AndroidView] since Media3's `PlayerView` has no Compose-native
 * equivalent yet; lifecycle is tied to this composable via [DisposableEffect]
 * so the underlying ExoPlayer instance (owned by Koin, shared across
 * navigation so PiP keeps playing) is released only when the app — not just
 * this screen — goes away.
 */
@OptIn(UnstableApi::class)
@Composable
fun PlayerSurface(playableUrl: String?, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val twitchPlayer = koinInject<TwitchPlayer>()

    LaunchedEffect(playableUrl) {
        val url = playableUrl ?: return@LaunchedEffect
        twitchPlayer.exoPlayer.setMediaItem(MediaItem.fromUri(url))
        twitchPlayer.exoPlayer.prepare()
        twitchPlayer.exoPlayer.play()
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
                useController = true
                player = twitchPlayer.exoPlayer
            }
        },
        update = { view -> view.player = twitchPlayer.exoPlayer },
    )

    DisposableEffect(Unit) {
        onDispose { /* Player lifetime is owned by Koin singleton — survives PiP/navigation. */ }
    }
}
