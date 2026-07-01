package com.puretv.twitch.android.player

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultLivePlaybackSpeedControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.puretv.twitch.core.adblock.AdBlockEngine
import com.puretv.twitch.core.adblock.AdMarkers
import com.puretv.twitch.core.adblock.AdSimulator
import com.puretv.twitch.core.adblock.BackupStreamResolver
import com.puretv.twitch.core.adblock.PlaylistAction
import com.puretv.twitch.core.adblock.PlaylistDetect
import com.puretv.twitch.core.stream.HlsMasterParser
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import java.util.concurrent.ConcurrentHashMap

/**
 * SECTION 06.3 [CRITICAL]. ExoPlayer wrapper tuned for low-latency live HLS,
 * with the [AdBlockInterceptor] wired directly into the OkHttp pipeline so ad
 * pods are stripped transparently on every playlist fetch.
 */
@UnstableApi
class TwitchPlayer(
    private val context: Context,
    private val adBlockEngine: AdBlockEngine,
    private val backupStreamResolver: BackupStreamResolver,
) {
    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(AdBlockInterceptor(adBlockEngine, backupStreamResolver))
        .build()

    /**
     * Backing player. Held lazily and nulled on [release] so this app-wide
     * singleton can free its codec/audio resources when the Activity finishes
     * yet still hand back a FRESH player if the process survives and the user
     * reopens the app: accessing [exoPlayer] after a release rebuilds it rather
     * than handing out a released (and unusable) instance.
     */
    private var _exoPlayer: ExoPlayer? = null

    val exoPlayer: ExoPlayer
        get() = _exoPlayer ?: buildPlayer().also { _exoPlayer = it }

    private fun buildPlayer(): ExoPlayer = ExoPlayer.Builder(context)
        .setMediaSourceFactory(
            DefaultMediaSourceFactory(context)
                .setDataSourceFactory(OkHttpDataSource.Factory(okHttpClient)),
        )
        // Request audio focus so a phone call / other media app ducks or pauses
        // us instead of being talked over, and pause when headphones are unplugged
        // or Bluetooth disconnects (otherwise full-volume audio jumps to the
        // loudspeaker). Both are baseline expectations for a media app, especially
        // in PiP where there is no visible transport to stop playback.
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                .build(),
            /* handleAudioFocus = */ true,
        )
        .setHandleAudioBecomingNoisy(true)
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
        .apply { addListener(recoveryListener) }

    /**
     * Bounded retry counter for transient HLS errors (see [recoveryListener]).
     * Reset to 0 on the first STATE_READY AND whenever a new stream is selected
     * via [playUrl], so a healthy stream never inherits a dead sibling stream's
     * exhausted budget on the shared singleton.
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

    /**
     * Selects a stream for playback, resetting the transient-error retry budget
     * so a fresh stream always gets its full allowance even if the previous one
     * exhausted it without ever reaching STATE_READY (a dead/offline channel).
     */
    fun playUrl(url: String) {
        errorRetries = 0
        with(exoPlayer) {
            setMediaItem(MediaItem.fromUri(url))
            prepare()
            play()
        }
    }

    /**
     * Stops playback and clears the queue without tearing down the player. No-op
     * if the player was already released, so a stream-leave teardown after the
     * Activity finished does not resurrect a fresh idle player via [exoPlayer].
     */
    fun stop() {
        _exoPlayer?.run {
            stop()
            clearMediaItems()
        }
    }

    /**
     * Frees the underlying player. Safe to call from Activity.onDestroy: the next
     * [exoPlayer] access rebuilds a fresh instance, so a surviving-process relaunch
     * never touches a released player.
     */
    fun release() {
        _exoPlayer?.release()
        _exoPlayer = null
        errorRetries = 0
    }

    private companion object {
        const val MAX_ERROR_RETRIES = 3
    }
}

/**
 * Removes Twitch ad pods from HLS playlist responses. The decision is made from
 * the RESPONSE, not the URL: obvious media segments (.ts/.m4s/.mp4/.aac) pass
 * through untouched and unbuffered; everything else is peeked, and any HLS
 * playlist (Content-Type mpegurl, or a body starting with #EXTM3U) is handled.
 *
 * Two-tier removal, ported from the desktop `LocalStreamProxy` so Android blocks
 * ads the same way instead of stalling on a blank screen:
 *   1. BACKUP SWAP (primary, seamless) — when a media playlist contains a
 *      stitched ad, mint a fresh manifest under an alternate player type
 *      (embed/popout), which Twitch usually serves ad-free for the same live
 *      stream, and serve that quality's CLEAN media playlist verbatim. The
 *      viewer keeps seeing real content, no gap. (Green pill: AD BLOCKED.)
 *   2. STRIP (fallback) — only if every backup player type is ALSO ad-showing,
 *      cut the pod out. The player briefly stalls at the splice. (Yellow pill:
 *      AD FILTERED.)
 *
 * To run the backup swap we need the channel + the ad-bearing variant's quality;
 * both are learned from the master playlist as it passes through (the master
 * request URL carries the channel; its #EXT-X-STREAM-INF lines carry each
 * variant's resolution/frame-rate), keyed by the variant URL path so the media
 * refreshes that follow can look themselves up.
 */
@UnstableApi
class AdBlockInterceptor(
    private val adBlockEngine: AdBlockEngine,
    private val backupStreamResolver: BackupStreamResolver,
) : Interceptor {

    private data class VariantMeta(val resolution: String, val frameRate: Double)

    // Learned from the master. channelLogin is @Volatile (written on the master
    // fetch, read on media fetches, possibly different OkHttp threads); the map is
    // concurrent for the same reason. Keyed by the variant media-playlist URL path.
    @Volatile private var channelLogin: String? = null
    private val variantByPath = ConcurrentHashMap<String, VariantMeta>()

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()

        // Honour the user's "Enable ad block" switch: when off, do no filtering at
        // all (and the engine has already flipped the pill to DISABLED).
        if (!adBlockEngine.isEnabled) {
            return chain.proceed(request)
        }

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
        val rawBody = body.string()

        // A master playlist (#EXT-X-STREAM-INF) carries no ad segments; we only
        // learn its channel + variant ladder, then pass it through untouched.
        if (rawBody.contains("#EXT-X-STREAM-INF")) {
            learnFromMaster(request.url, rawBody)
            return response.newBuilder()
                .body(rawBody.toResponseBody("application/vnd.apple.mpegurl".toMediaType()))
                .removeHeader("Content-Length")
                .build()
        }

        // Media playlist: AdSimulator only makes sense here (a media playlist with
        // #EXTINF/#EXT-X-MEDIA-SEQUENCE), not on the master.
        val raw = AdSimulator.inject(rawBody)
        val cleaned = resolveCleanMedia(request.url, raw)

        return response.newBuilder()
            .body(cleaned.toResponseBody("application/vnd.apple.mpegurl".toMediaType()))
            .removeHeader("Content-Length")
            .build()
    }

    /** Record the channel + per-variant quality so media refreshes can swap. */
    private fun learnFromMaster(masterUrl: HttpUrl, master: String) {
        // Usher master URL: .../api/channel/hls/{channel}.m3u8
        val channel = masterUrl.pathSegments.lastOrNull()
            ?.removeSuffix(".m3u8")
            ?.takeIf { it.isNotBlank() }
        if (channel != null && channel != channelLogin) {
            // New stream: drop the previous channel's variant map so it can't
            // grow unbounded across a channel-surfing session.
            variantByPath.clear()
            channelLogin = channel
        }
        runCatching { HlsMasterParser.parseVariants(master) }.getOrNull()?.forEach { variant ->
            variant.url.toHttpUrlOrNull()?.let { url ->
                variantByPath[url.encodedPath] = VariantMeta(variant.resolution, variant.frameRate)
            }
        }
    }

    private fun resolveCleanMedia(requestUrl: HttpUrl, raw: String): String {
        if (!AdMarkers.containsAds(raw)) {
            adBlockEngine.reportAdBlocked() // clean refresh, no ad
            return raw
        }

        // Tier 1: seamless backup player-type swap (real content, no stall).
        val channel = channelLogin
        val meta = variantByPath[requestUrl.encodedPath]
        if (channel != null && meta != null) {
            val backup = runCatching {
                runBlocking {
                    backupStreamResolver.resolveAdFreeMediaPlaylist(
                        channelLogin = channel,
                        targetResolution = meta.resolution,
                        targetFrameRate = meta.frameRate,
                        nowMs = System.currentTimeMillis(),
                    )
                }
            }.getOrNull()
            if (backup != null) {
                adBlockEngine.reportAdBlocked() // swapped to a clean manifest, no gap
                return backup.mediaPlaylist
            }
        }

        // Tier 2: no clean backup available; strip the pod (brief stall).
        return try {
            val filtered = adBlockEngine.filterPlaylist(raw)
            adBlockEngine.reportFiltered(filtered)
            filtered.content
        } catch (t: Throwable) {
            // Never silently pass ads as success: report the failure so the pill
            // goes AD_BLOCK_OFF, then return the RAW playlist so playback keeps
            // running instead of dead-ending.
            adBlockEngine.reportResolutionFailure()
            raw
        }
    }
}
