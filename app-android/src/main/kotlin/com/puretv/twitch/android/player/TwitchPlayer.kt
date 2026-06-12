package com.puretv.twitch.android.player

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
 * SECTION 06.3 [CRITICAL] — ExoPlayer wrapper tuned for low-latency live HLS,
 * with the [AdBlockInterceptor] wired directly into the OkHttp pipeline so
 * Strategies 1 & 2 (Section 04) run transparently on every playlist fetch.
 */
@UnstableApi
class TwitchPlayer(
    private val context: Context,
    private val adBlockEngine: AdBlockEngine,
) {
    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(AdBlockInterceptor(adBlockEngine))
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

    fun release() = exoPlayer.release()
}

/**
 * Intercepts only Twitch HLS playlist requests (usher.ttvnw.net / *.m3u8) —
 * segment requests pass straight through to the CDN, exactly as Section 4.2
 * specifies, so bandwidth and latency stay untouched.
 *
 * `runBlocking` here is intentional and bounded: OkHttp interceptors are
 * synchronous by contract, and [AdBlockEngine.resolveCleanStream] already
 * carries its own timeouts/fallback chain, so the worst case is "fall through
 * to chain.proceed(request)" rather than an unbounded hang.
 */
@UnstableApi
class AdBlockInterceptor(private val adBlockEngine: AdBlockEngine) : Interceptor {
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

            is com.puretv.twitch.core.model.CleanStreamResult.Failure ->
                // Both ad-block strategies failed — let the raw request through so
                // playback continues; the player-layer black-frame detector
                // (BlackFrameOverlay) takes over visual suppression from here.
                chain.proceed(request)
        }
    }
}
