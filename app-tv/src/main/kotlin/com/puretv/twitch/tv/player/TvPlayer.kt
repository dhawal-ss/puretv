package com.puretv.twitch.tv.player

import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultLivePlaybackSpeedControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.puretv.twitch.core.adblock.AdBlockEngine
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody

/**
 * SECTION 06.3 / 07.4 — TV counterpart of the phone app's `TwitchPlayer`.
 * Identical ExoPlayer/OkHttp/[AdBlockEngine] wiring (low-latency live HLS +
 * the [TvAdBlockInterceptor] pipeline) — duplicated rather than shared
 * because `ExoPlayer` is an Android-only `androidx.media3` type that can't
 * live in the multiplatform `core` module (Section 12.2: app-tv shares no
 * platform/UI code with app-android, only `core`).
 */
@UnstableApi
class TvPlayer(
    private val context: Context,
    private val adBlockEngine: AdBlockEngine,
) {
    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(TvAdBlockInterceptor(adBlockEngine))
        .build()

    val exoPlayer: ExoPlayer = ExoPlayer.Builder(context)
        .setMediaSourceFactory(
            DefaultMediaSourceFactory(context)
                .setDataSourceFactory(OkHttpDataSource.Factory(okHttpClient)),
        )
        .setLoadControl(
            DefaultLoadControl.Builder()
                .setBufferDurationsMs(
                    /* minBuffer  */ 2_000,
                    /* maxBuffer  */ 15_000,
                    /* playback   */ 500,
                    /* rebuffer   */ 1_000,
                )
                .build(),
        )
        .build()
        .apply {
            playWhenReady = true
            setLivePlaybackSpeedControl(
                DefaultLivePlaybackSpeedControl.Builder()
                    .setFallbackMaxPlaybackSpeed(1.04f)
                    .build(),
            )
        }

    /** Section 7.4 — REWIND/FAST_FORWARD remote buttons step quality up/down. */
    fun seekRelative(deltaMs: Long) {
        val target = (exoPlayer.currentPosition + deltaMs).coerceAtLeast(0)
        exoPlayer.seekTo(target)
    }

    fun togglePlayPause() {
        if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
    }

    fun release() = exoPlayer.release()
}

/** Identical playlist-only interception strategy as the phone app's `AdBlockInterceptor` (Section 4.2). */
@UnstableApi
class TvAdBlockInterceptor(private val adBlockEngine: AdBlockEngine) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val url = request.url.toString()

        if (!url.contains("usher.ttvnw.net") && !url.endsWith(".m3u8")) {
            return chain.proceed(request)
        }

        val result = runBlocking { adBlockEngine.resolveCleanStream(url) }
        return when (result) {
            is com.puretv.twitch.core.model.CleanStreamResult.Success -> Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body(result.playlistContent.toResponseBody("application/x-mpegurl".toMediaType()))
                .build()

            is com.puretv.twitch.core.model.CleanStreamResult.Failure -> chain.proceed(request)
        }
    }
}
