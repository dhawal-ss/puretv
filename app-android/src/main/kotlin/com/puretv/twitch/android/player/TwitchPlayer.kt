package com.puretv.twitch.android.player

import android.content.Context
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultLivePlaybackSpeedControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.puretv.twitch.core.adblock.AdBlockEngine
import com.puretv.twitch.core.adblock.AdSimulator
import com.puretv.twitch.core.adblock.PlaylistAction
import com.puretv.twitch.core.adblock.PlaylistDetect
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody

/**
 * SECTION 06.3 [CRITICAL]. ExoPlayer wrapper tuned for low-latency live HLS,
 * with the [AdBlockInterceptor] wired directly into the OkHttp pipeline so ad
 * pods are stripped transparently on every playlist fetch.
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
                // Deeper buffer than the old (2s/15s/0.5s/1s) thresholds: mobile
                // links jitter hard, and the thin buffer caused chronic rebuffering.
                .setBufferDurationsMs(
                    /* minBuffer  */ 5_000,
                    /* maxBuffer  */ 30_000,
                    /* playback   */ 1_500,
                    /* rebuffer   */ 3_000,
                )
                .build(),
        )
        .setLivePlaybackSpeedControl(
            DefaultLivePlaybackSpeedControl.Builder()
                .setFallbackMaxPlaybackSpeed(1.04f)
                .build(),
        )
        .build()

    /**
     * Bounded retry counter for transient HLS errors (see [recoveryListener]).
     * Reset to 0 on the first STATE_READY so a healthy stream never inherits a
     * sibling stream's exhausted budget on the shared singleton.
     */
    private var errorRetries = 0

    /**
     * SECTION 06.3 — keeps live HLS blips from dead-ending on a black screen.
     * BEHIND_LIVE_WINDOW (we fell off the live edge) is recovered by re-seeking
     * to the default (live) position and re-preparing. Any other error gets a
     * BOUNDED prepare() retry (max 3) so a flapping segment server cannot spin
     * the loader forever; once playback reaches STATE_READY the budget resets.
     */
    private val recoveryListener = object : Player.Listener {
        override fun onPlayerError(error: PlaybackException) {
            if (error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) {
                exoPlayer.seekToDefaultPosition()
                exoPlayer.prepare()
                return
            }
            if (errorRetries < MAX_ERROR_RETRIES) {
                errorRetries++
                exoPlayer.prepare()
            }
            // Past the bound we stop: do not loop forever on a hard failure.
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_READY) {
                errorRetries = 0
            }
        }
    }

    init {
        exoPlayer.addListener(recoveryListener)
    }

    fun release() = exoPlayer.release()

    private companion object {
        const val MAX_ERROR_RETRIES = 3
    }
}

/**
 * Strips Twitch ad pods from HLS playlist responses. The decision is made from
 * the RESPONSE, not the URL: obvious media segments (.ts/.m4s/.mp4/.aac) pass
 * through untouched and unbuffered; everything else is peeked, and any HLS
 * playlist (Content-Type mpegurl, or a body starting with #EXTM3U) is run
 * through the synchronous, no-network [AdBlockEngine.filterPlaylist]. This
 * catches the master AND every media-playlist refresh regardless of host or
 * query string (the leak the old url.endsWith(".m3u8") guard missed), mints no
 * token (so the anonymous-mint invariant holds), and does no network on the
 * loader thread.
 */
@UnstableApi
class AdBlockInterceptor(private val adBlockEngine: AdBlockEngine) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()

        if (PlaylistDetect.classifyRequest(request.url.encodedPath) == PlaylistAction.SKIP_SEGMENT) {
            return chain.proceed(request)
        }

        val response = chain.proceed(request)
        val contentType = response.header("Content-Type")
        val firstLine = if ("mpegurl" in (contentType?.lowercase() ?: "")) {
            "#EXTM3U"
        } else {
            runCatching { response.peekBody(8L * 1024).string() }
                .getOrNull()
                ?.lineSequence()
                ?.firstOrNull { it.isNotBlank() }
        }

        if (PlaylistDetect.classifyResponse(contentType, firstLine) != PlaylistAction.FILTER_PLAYLIST) {
            return response
        }

        val body = response.body ?: return response
        val raw = AdSimulator.inject(body.string())
        val cleaned = try {
            val filtered = adBlockEngine.filterPlaylist(raw)
            adBlockEngine.reportFiltered(filtered)
            filtered.content
        } catch (t: Throwable) {
            // Never silently pass ads as success: report the failure so the pill
            // goes AD_BLOCK_OFF, then return the RAW playlist so playback keeps
            // running instead of dead-ending. There is no black-frame detector on
            // Android (the user may briefly see the ad); a detector that pauses or
            // re-tries on a black frame is a known future improvement, NOT built here.
            adBlockEngine.reportResolutionFailure()
            raw
        }

        return response.newBuilder()
            .body(cleaned.toResponseBody("application/vnd.apple.mpegurl".toMediaType()))
            .removeHeader("Content-Length")
            .build()
    }
}
