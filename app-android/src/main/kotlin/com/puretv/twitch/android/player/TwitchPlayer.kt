package com.puretv.twitch.android.player

import android.content.Context
import android.os.Looper
import android.os.Handler
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultLivePlaybackSpeedControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.puretv.twitch.core.adblock.AdBlockEngine
import com.puretv.twitch.core.audio.AudioLevelling
import com.puretv.twitch.core.adblock.AdMarkers
import okhttp3.Protocol
import io.ktor.client.HttpClient
import com.puretv.twitch.core.stream.PlayableStreamCache
import com.puretv.twitch.core.adblock.MediaPlaylistCleaner
import com.puretv.twitch.core.adblock.AdSimulator
import com.puretv.twitch.core.adblock.BackupStreamResolver
import com.puretv.twitch.core.adblock.PlaylistAction
import com.puretv.twitch.core.adblock.PlaylistDetect
import com.puretv.twitch.core.adblock.TwitchPrefetch
import com.puretv.twitch.core.perf.TimingLog
import com.puretv.twitch.core.model.StreamQuality
import com.puretv.twitch.core.stream.HlsMasterParser
import com.puretv.twitch.core.stream.LiveLatency
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
    adBlockEngine: AdBlockEngine,
    backupStreamResolver: BackupStreamResolver,
    httpClient: HttpClient,
    playableStreamCache: PlayableStreamCache,
) {
    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(
            AdBlockInterceptor(
                adBlockEngine = adBlockEngine,
                cleaner = MediaPlaylistCleaner(
                    adBlockEngine = adBlockEngine,
                    backupStreamResolver = backupStreamResolver,
                    httpClient = httpClient,
                    nowMs = System::currentTimeMillis,
                    // ExoPlayer cannot play an empty window (issue #22); wait for content instead.
                    holdEmptyWindows = true,
                    lowLatency = { lowLatency },
                ),
                playableStreamCache = playableStreamCache,
                audioOnly = { audioOnly },
                lowLatency = { lowLatency },
                // Source is forced to the top rendition and audio only has one:
                // no automatic quality to mislead with prefetch downloads.
                singleRendition = { pickedSource || audioOnly },
            ),
        )
        .build()

    /**
     * The same client without [AdBlockInterceptor], for VODs (shares the
     * connection pool). VOD manifests are ad-free by construction, and the
     * interceptor's learnFromMaster would record the VOD id as a channel login
     * and run backup swaps against it.
     */
    private val plainHttpClient = okHttpClient.newBuilder().apply { interceptors().clear() }.build()
    private val vodSourceFactory by lazy { HlsMediaSource.Factory(OkHttpDataSource.Factory(plainHttpClient)) }

    /**
     * True while a VOD is loaded. A VOD has a fixed timeline, so the live-only
     * steps below (latency sampling, reloading the master for audio only,
     * parking at the live edge) skip it: each of them would restart it at 0.
     */
    @Volatile var isVod = false
        private set

    /**
     * What one stream screen's view model hands the player when it starts playing
     * for it: the callbacks to install, and the quality to come back to if another
     * stream used the shared player meanwhile (see [PlayOwnership]).
     */
    class PlayTicket(
        val onFirstFrame: () -> Unit,
        val onGaveUp: (PlaybackException) -> Unit,
        val quality: () -> StreamQuality?,
    )

    // Stream tickets and VOD owners share one claim, so a stream stacked on a VOD
    // (a go-live alert) and the VOD under it never stop each other by mistake.
    private val ownership = PlayOwnership<Any>()

    /**
     * Stops playback and clears the callbacks, but only if [ticket] is still the one
     * the player plays for. A stream screen that was popped after the screen below it
     * already took the player back gets false and changes nothing.
     */
    fun stopIfOwner(ticket: PlayTicket): Boolean {
        if (!ownership.release(ticket)) return false
        stop()
        onFirstFrame = null
        onGaveUp = null
        return true
    }

    /**
     * Called when the first video frame of a stream is on screen. Feeds the local
     * time-to-first-frame mark; the stream view model sets and clears it.
     */
    @Volatile var onFirstFrame: (() -> Unit)? = null

    /** The playing stream's "Low latency" setting, read by the playlist pipeline on OkHttp threads. */
    @Volatile private var lowLatency = false

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

    // "Even out loud and quiet audio". The flag outlives the player; each built
    // player gets its own processor, primed from it.
    @Volatile private var audioNormalizerOn = false
    @Volatile private var audioLevelling = AudioLevelling.DEFAULT
    @Volatile private var audioNormalizer: AudioNormalizer? = null

    /** Switches the audio normalizer live. Never builds the player. */
    fun setAudioNormalizer(enabled: Boolean) {
        audioNormalizerOn = enabled
        audioNormalizer?.enabled = enabled
    }

    /** Sets how strongly the normalizer works, live. Never builds the player. */
    fun setAudioLevelling(level: AudioLevelling) {
        audioLevelling = level
        audioNormalizer?.level = level
    }

    private fun buildPlayer(): ExoPlayer = ExoPlayer.Builder(
        context,
        NormalizingRenderersFactory(
            context,
            // Published before it reads the flag, so a switch flipped meanwhile is never lost.
            AudioNormalizer().also {
                audioNormalizer = it
                it.level = audioLevelling
                it.enabled = audioNormalizerOn
            },
        ),
    )
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
        // Background play (issue #27) keeps streaming with the screen off. A
        // foreground service alone holds no wake or Wi-Fi lock, so without this
        // the CPU or radio dozes and the stream stalls a few minutes in. ExoPlayer
        // only holds the locks while actually playing.
        .setWakeMode(C.WAKE_MODE_NETWORK)
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
     * Called on the main thread once the backed-off retries are spent. The stream
     * view model answers with a fresh stream session (the old signed URL may have
     * expired, for example after a long pause) or, failing that, a visible error,
     * so playback never just stays black.
     */
    @Volatile var onGaveUp: ((PlaybackException) -> Unit)? = null

    private val mainHandler = Handler(Looper.getMainLooper())
    private val retryRunnable = Runnable { _exoPlayer?.prepare() }

    // Local-only timing: how far behind live the stream settled, logged twice
    // after the first frame (TimingLog, logcat tag PureTvTiming), so low latency
    // on and off can be compared by hand.
    private var latencySamplesLeft = 0
    private val latencySample = object : Runnable {
        override fun run() {
            val player = _exoPlayer ?: return
            val offset = player.currentLiveOffset
            if (offset != C.TIME_UNSET) {
                TimingLog.log("stream.live_offset", offset, if (lowLatency) "low latency" else "default")
            }
            if (--latencySamplesLeft > 0) mainHandler.postDelayed(this, LATENCY_SAMPLE_MS)
        }
    }

    /**
     * SECTION 06.3 keeps live HLS blips from dead-ending on a black screen.
     * BEHIND_LIVE_WINDOW (we fell off the live edge) is recovered by re-seeking
     * to the default (live) position and re-preparing. Any other error is retried
     * with growing pauses ([RETRY_DELAYS_MS]), so a brief network drop gets time
     * to clear instead of three instant retries that all hit the same outage;
     * once those are spent, [onGaveUp] takes over. Reaching STATE_READY resets
     * the budget.
     */
    private val recoveryListener = object : Player.Listener {
        override fun onPlayerError(error: PlaybackException) {
            if (error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) {
                exoPlayer.seekToDefaultPosition()
                exoPlayer.prepare()
                return
            }
            if (errorRetries < RETRY_DELAYS_MS.size) {
                mainHandler.removeCallbacks(retryRunnable)
                mainHandler.postDelayed(retryRunnable, RETRY_DELAYS_MS[errorRetries])
                errorRetries++
            } else {
                onGaveUp?.invoke(error)
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_READY) errorRetries = 0
        }

        override fun onRenderedFirstFrame() {
            onFirstFrame?.invoke()
            mainHandler.removeCallbacks(latencySample)
            if (isVod) return
            latencySamplesLeft = LATENCY_SAMPLES
            mainHandler.postDelayed(latencySample, LATENCY_SAMPLE_MS)
        }
    }

    /**
     * Selects a stream for playback, resetting the transient-error retry budget
     * so a fresh stream always gets its full allowance even if the previous one
     * exhausted it without ever reaching STATE_READY (a dead/offline channel).
     * [metadata] is what the media notification and lock screen show.
     * [lowLatency] plays closer to live (see [liveConfiguration]).
     */
    fun playUrl(
        url: String,
        metadata: MediaMetadata = MediaMetadata.EMPTY,
        lowLatency: Boolean = false,
        ticket: PlayTicket? = null,
    ) {
        if (ticket != null) {
            onFirstFrame = ticket.onFirstFrame
            onGaveUp = ticket.onGaveUp
            // The shared player carries the last stream's quality and audio-only flags;
            // a stream taking it back from another one restores its own.
            if (ownership.claim(ticket)) ticket.quality()?.let(::setQuality)
        }
        errorRetries = 0
        mainHandler.removeCallbacks(retryRunnable)
        isVod = false
        this.lowLatency = lowLatency
        with(exoPlayer) {
            setMediaItem(
                MediaItem.Builder()
                    .setUri(url)
                    .setMediaMetadata(metadata)
                    .setLiveConfiguration(liveConfiguration(lowLatency))
                    .build(),
            )
            prepare()
            play()
        }
    }

    /**
     * Plays a VOD from [startPositionMs]. VOD manifests are ad-free by
     * construction, so they skip the live pipeline entirely: no ad interceptor
     * (see [plainHttpClient]), no live configuration, no latency sampling.
     * Seeking and resuming go through the player's own seekTo, never a reload.
     */
    fun playVod(
        url: String,
        metadata: MediaMetadata = MediaMetadata.EMPTY,
        startPositionMs: Long = 0L,
        owner: Any? = null,
    ) {
        owner?.let(ownership::claim)
        errorRetries = 0
        mainHandler.removeCallbacks(retryRunnable)
        mainHandler.removeCallbacks(latencySample)
        isVod = true
        lowLatency = false
        val item = MediaItem.Builder().setUri(url).setMediaMetadata(metadata).build()
        with(exoPlayer) {
            setMediaSource(vodSourceFactory.createMediaSource(item), startPositionMs.coerceAtLeast(0L))
            prepare()
            play()
        }
    }

    /** True while [owner] (a VOD view model) is the one the player plays for. */
    fun ownsVod(owner: Any): Boolean = ownership.isOwner(owner)

    /**
     * Stops playback, but only if [owner] is still the one the player plays for: a
     * VOD that was cleared after another video or stream took the player changes
     * nothing.
     */
    fun stopIfVodOwner(owner: Any): Boolean {
        if (!ownership.release(owner)) return false
        stop()
        onGaveUp = null
        return true
    }

    // Audio only, by the viewer's pick or because the app is in the background.
    // Read by the playlist interceptor on OkHttp threads: while either is set it
    // hands ExoPlayer the audio-only master, so no video is downloaded at all.
    @Volatile private var pickedAudioOnly = false
    @Volatile private var backgroundAudioOnly = false

    // Source forces the top rendition, so the player never picks by bandwidth.
    @Volatile private var pickedSource = false
    private val audioOnly: Boolean get() = pickedAudioOnly || backgroundAudioOnly

    /**
     * Picks a quality on the live HLS ladder. AUTO lets ABR pick what the link
     * sustains; SOURCE always plays the top rendition; a specific resolution caps
     * the video size; AUDIO_ONLY reloads the stream as audio only. Every branch
     * resets what the others set, because this singleton carries its parameters
     * between streams.
     */
    fun setQuality(quality: StreamQuality) {
        val builder = exoPlayer.trackSelectionParameters.buildUpon()
            .setForceHighestSupportedBitrate(quality == StreamQuality.SOURCE)
        pickedSource = quality == StreamQuality.SOURCE
        when (quality) {
            StreamQuality.SOURCE, StreamQuality.AUTO, StreamQuality.AUDIO_ONLY -> builder.clearVideoSizeConstraints()
            StreamQuality.P1080P60 -> builder.setMaxVideoSize(1920, 1080)
            StreamQuality.P720P60 -> builder.setMaxVideoSize(1280, 720)
            StreamQuality.P480P -> builder.setMaxVideoSize(854, 480)
            StreamQuality.P360P -> builder.setMaxVideoSize(640, 360)
        }
        exoPlayer.trackSelectionParameters = builder.build()
        updateAudioOnly { pickedAudioOnly = quality == StreamQuality.AUDIO_ONLY }
    }

    /**
     * With nobody looking (screen off, another app in front, no PiP window) the
     * picture is wasted data and battery, so background play switches to the
     * audio-only rendition and back to video on return. See [BackgroundPlayback].
     */
    fun setBackgroundAudioOnly(enabled: Boolean) {
        updateAudioOnly { backgroundAudioOnly = enabled }
    }

    private inline fun updateAudioOnly(change: () -> Unit) {
        val before = audioOnly
        change()
        // A VOD keeps its picture: reloading it would restart it at 0 through
        // the live pipeline. The flags still change, and stop() clears them.
        if (audioOnly != before && !isVod) reloadCurrentStream()
    }

    /**
     * Loads the current stream again from its master, which the interceptor now
     * serves as audio only or as video, and carries on at the live edge. Keeps
     * the play/pause state: a paused stream stays paused.
     */
    private fun reloadCurrentStream() {
        val player = _exoPlayer ?: return
        val item = player.currentMediaItem ?: return
        mainHandler.removeCallbacks(retryRunnable)
        player.setMediaItem(item)
        player.prepare()
    }

    /**
     * Stops playback and clears the queue without tearing down the player. No-op
     * if the player was already released, so a stream-leave teardown after the
     * Activity finished does not resurrect a fresh idle player via [exoPlayer].
     */
    fun stop() {
        mainHandler.removeCallbacks(retryRunnable)
        mainHandler.removeCallbacks(latencySample)
        // The next stream applies its own quality; it must not open as audio
        // only because this one was.
        pickedAudioOnly = false
        backgroundAudioOnly = false
        isVod = false
        _exoPlayer?.run {
            stop()
            clearMediaItems()
        }
    }

    /**
     * The notification's Stop: pause and drop the network and decoder, but keep
     * the stream queued and parked at the live edge. The stream screen is still
     * on the back stack, so coming back shows the player's play button, and one
     * tap re-prepares at live instead of leaving an empty, unplayable surface.
     */
    fun stopKeepingStream() {
        mainHandler.removeCallbacks(retryRunnable)
        mainHandler.removeCallbacks(latencySample)
        _exoPlayer?.run {
            pause()
            stop()
            // A VOD's default position is its start: keep it where the viewer was.
            if (!isVod) seekToDefaultPosition()
        }
    }

    /**
     * Frees the underlying player. Safe to call from Activity.onDestroy: the next
     * [exoPlayer] access rebuilds a fresh instance, so a surviving-process relaunch
     * never touches a released player.
     */
    fun release() {
        mainHandler.removeCallbacks(retryRunnable)
        mainHandler.removeCallbacks(latencySample)
        _exoPlayer?.release()
        _exoPlayer = null
        errorRetries = 0
    }

    private companion object {
        /** Pauses before each automatic retry: 1s, then 3s, then 6s. */
        val RETRY_DELAYS_MS = longArrayOf(1_000, 3_000, 6_000)

        const val LATENCY_SAMPLES = 2
        const val LATENCY_SAMPLE_MS = 30_000L
    }
}

/**
 * Removes Twitch ad pods from HLS playlist responses. The decision is made from
 * the RESPONSE, not the URL: obvious media segments (.ts/.m4s/.mp4/.aac) pass
 * through untouched and unbuffered; everything else is peeked, and any HLS
 * playlist (Content-Type mpegurl, or a body starting with #EXTM3U) is handled.
 *
 * The ad-blocking rules live in core's [MediaPlaylistCleaner], shared by all
 * three apps: the backup player-type swap first (seamless, green pill), then
 * stripping (yellow pill), holding an all-ad window instead of handing ExoPlayer
 * an empty playlist (issue #22), and keeping the timeline continuous across
 * swaps. This class only adapts OkHttp to it.
 *
 * The backup swap needs the channel + the ad-bearing variant's quality; both
 * are learned from the master playlist as it passes through (the master request
 * URL carries the channel; its #EXT-X-STREAM-INF lines carry each variant's
 * resolution/frame-rate), keyed by the variant URL path so the media refreshes
 * that follow can look themselves up.
 */
@UnstableApi
class AdBlockInterceptor(
    private val adBlockEngine: AdBlockEngine,
    private val cleaner: MediaPlaylistCleaner,
    private val playableStreamCache: PlayableStreamCache,
    /** True while the player should get the audio-only rendition instead of video. */
    private val audioOnly: () -> Boolean,
    /** The playing stream's low-latency choice, for playlists that skip the cleaner. */
    private val lowLatency: () -> Boolean,
    /** True while the player plays one rendition only (see [MediaPlaylistCleaner.clean]). */
    private val singleRendition: () -> Boolean,
) : Interceptor {

    // Learned from the master. channelLogin is @Volatile (written on the master
    // fetch, read on media fetches, possibly different OkHttp threads); the map is
    // concurrent for the same reason. Keyed by the variant media-playlist URL path.
    @Volatile private var channelLogin: String? = null
    private val variantByPath = ConcurrentHashMap<String, MediaPlaylistCleaner.VariantQuality>()

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()

        // The view model resolved (and validated) this exact master moments ago:
        // serve that copy instead of asking usher a second time. A master carries
        // no ad segments, so this is safe whether ad blocking is on or off.
        playableStreamCache.masterBody(request.url.toString())?.let { master ->
            learnFromMaster(request.url, master)
            return Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body(HlsMasterParser.forPlayback(master, audioOnly()).toResponseBody(HLS_MEDIA_TYPE))
                .build()
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
        // learn its channel + variant ladder, then hand the player the renditions
        // it should see (never the audio-only one by accident, see forPlayback).
        // This runs with ad blocking off too.
        if (rawBody.contains("#EXT-X-STREAM-INF")) {
            learnFromMaster(request.url, rawBody)
            return response.newBuilder()
                .body(HlsMasterParser.forPlayback(rawBody, audioOnly()).toResponseBody(HLS_MEDIA_TYPE))
                .removeHeader("Content-Length")
                .build()
        }

        // Honour the user's "Enable ad block" switch: when off, media playlists
        // pass through unfiltered (and the engine has already flipped the pill to
        // DISABLED). Low latency still applies while no ad is showing.
        if (!adBlockEngine.isEnabled) {
            val passed = if (lowLatency() && !AdMarkers.containsAds(rawBody)) {
                TwitchPrefetch.liveEdge(rawBody, promotePrefetch = singleRendition())
            } else {
                rawBody
            }
            return response.newBuilder()
                .body(passed.toResponseBody(HLS_MEDIA_TYPE))
                .removeHeader("Content-Length")
                .build()
        }

        // Media playlist: AdSimulator only makes sense here (a media playlist with
        // #EXTINF/#EXT-X-MEDIA-SEQUENCE), not on the master.
        val raw = AdSimulator.inject(rawBody)
        val cleaned = runBlocking {
            cleaner.clean(
                variantUrl = request.url.toString(),
                raw = raw,
                channelLogin = channelLogin,
                quality = variantByPath[request.url.encodedPath],
                isCancelled = { chain.call().isCanceled() },
                singleRendition = singleRendition(),
            )
        }

        return response.newBuilder()
            .body(cleaned.content.toResponseBody(HLS_MEDIA_TYPE))
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
                variantByPath[url.encodedPath] = MediaPlaylistCleaner.VariantQuality(variant.resolution, variant.frameRate)
            }
        }
    }

    private companion object {
        val HLS_MEDIA_TYPE = "application/vnd.apple.mpegurl".toMediaType()
    }
}

/**
 * The live target for a stream: with low latency on, aim [LiveLatency.EXO_TARGET_OFFSET_MS]
 * behind live and let ExoPlayer adjust its speed slightly to stay there; with it
 * off, ExoPlayer's own default.
 */
internal fun liveConfiguration(lowLatency: Boolean): MediaItem.LiveConfiguration =
    if (!lowLatency) {
        MediaItem.LiveConfiguration.UNSET
    } else {
        MediaItem.LiveConfiguration.Builder()
            .setTargetOffsetMs(LiveLatency.EXO_TARGET_OFFSET_MS)
            .setMinOffsetMs(LiveLatency.EXO_MIN_OFFSET_MS)
            .setMaxOffsetMs(LiveLatency.EXO_MAX_OFFSET_MS)
            .setMinPlaybackSpeed(LiveLatency.EXO_MIN_SPEED)
            .setMaxPlaybackSpeed(LiveLatency.EXO_MAX_SPEED)
            .build()
    }
