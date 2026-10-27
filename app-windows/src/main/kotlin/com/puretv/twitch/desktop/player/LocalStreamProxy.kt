package com.puretv.twitch.desktop.player

import com.puretv.twitch.core.adblock.AdBlockEngine
import com.puretv.twitch.core.adblock.AdMarkers
import com.puretv.twitch.core.adblock.BackupStreamResolver
import com.puretv.twitch.core.adblock.MediaPlaylistCleaner
import com.puretv.twitch.core.model.AdBlockStrategyResult
import com.puretv.twitch.core.model.CleanStreamResult
import com.puretv.twitch.core.model.StreamQuality
import com.puretv.twitch.core.stream.HlsMasterParser
import com.puretv.twitch.core.stream.PlayableStreamCache
import com.puretv.twitch.core.stream.StreamResolver
import com.puretv.twitch.core.stream.Failure
import com.puretv.twitch.core.stream.FailureKind
import com.puretv.twitch.core.stream.StreamFailures
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.call
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import io.ktor.client.plugins.timeout
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/**
 * SECTION 08.3 [CRITICAL] — local ad-blocked HLS delivery.
 *
 * VLCJ/libVLC fetches playlists over plain HTTP; rather than teaching it to
 * speak our in-process Kotlin APIs, we run a tiny embedded Ktor/Netty server
 * on `localhost:7979` that VLC requests against like any other HLS origin:
 *
 *   GET http://localhost:7979/stream?channel=<login>&quality=<SOURCE|...>
 *       → resolves the channel's GQL playback token + master playlist
 *         (`PlayableStreamCache`, so a hover prewarm or a quality change
 *         reuses the session instead of minting a new one)
 *       → runs it through `AdBlockEngine.resolveCleanStream` (proxy-router
 *         or manifest-rewrite strategy per Section 4)
 *       → returns the cleaned m3u8 text with the correct HLS content type
 *
 * This keeps 100% of the ad-block logic in `core`, shared verbatim with the
 * phone/TV apps' in-process equivalents — only the *transport* differs
 * because VLCJ needs a URL, not a Kotlin object.
 */
/** Thrown by [LocalStreamProxy.start] when the local HLS proxy cannot bind its port. */
class ProxyUnavailableException(message: String) : Exception(message)

class LocalStreamProxy(
    private val playableStreamCache: PlayableStreamCache,
    private val adBlockEngine: AdBlockEngine,
    private val httpClient: HttpClient,
    backupStreamResolver: BackupStreamResolver,
    /** Settings > Low latency, read on every variant refresh. */
    lowLatency: () -> Boolean = { false },
) {
    companion object {
        // A playlist refresh normally takes 100 to 300 ms; see fetchVariant.
        private const val VARIANT_TIMEOUT_MS = 3_000L
        private const val VARIANT_ATTEMPTS = 2

        /** One attribute of an `#EXT-X-STREAM-INF` line (quotes removed), or "". */
        internal fun hlsAttr(line: String, name: String): String =
            Regex("(?:^|[,:])$name=([^,]+)").find(line)?.groupValues?.get(1)?.trim()?.trim('"').orEmpty()

        /**
         * Whether a master playlist's `#EXT-X-STREAM-INF` line is the rendition for [quality].
         * By name first (VIDEO="720p60"), then by height: Twitch names its top rendition
         * "chunked" (Source) even when it is the 1080p60 one, so on most channels a 1080p60
         * pick matched nothing by name, VLC got every rendition, and it started at 160p and
         * climbed, rebuilding its video output at each step.
         */
        internal fun variantMatches(streamInf: String, quality: StreamQuality): Boolean {
            val name = hlsAttr(streamInf, "VIDEO")
            if (name.isNotBlank() && StreamQuality.fromVariantName(name) == quality) return true
            val height = hlsAttr(streamInf, "RESOLUTION").substringAfter('x', "").toIntOrNull() ?: return false
            return when (quality) {
                StreamQuality.P1080P60 -> height == 1080
                StreamQuality.P720P60 -> height == 720
                StreamQuality.P480P -> height == 480
                StreamQuality.P360P -> height == 360
                else -> false
            }
        }

        /**
         * Which of a master playlist's `#EXT-X-STREAM-INF` lines plays [quality], by index,
         * or null when there is nothing to choose from.
         *  - Source: the "chunked" rendition when Twitch names one, otherwise the highest
         *    bandwidth (many channels name their top rendition "1080p60", and Twitch may
         *    list 360p first).
         *  - A height (1080p60 ... 360p): that rendition (see [variantMatches]); when the
         *    channel has none, the nearest one below it, else the lowest above it.
         *  - Anything else (Auto, audio only): null, the caller keeps its own handling.
         */
        internal fun chooseVariant(streamInfs: List<String>, quality: StreamQuality): Int? {
            if (streamInfs.isEmpty()) return null
            fun height(line: String) = hlsAttr(line, "RESOLUTION").substringAfter('x', "").toIntOrNull() ?: 0
            fun bandwidth(line: String) = hlsAttr(line, "BANDWIDTH").toLongOrNull() ?: 0L
            val wanted = when (quality) {
                StreamQuality.SOURCE -> return streamInfs.indexOfFirst { hlsAttr(it, "VIDEO").equals("chunked", ignoreCase = true) }
                    .takeIf { it >= 0 } ?: streamInfs.indices.maxByOrNull { bandwidth(streamInfs[it]) }
                StreamQuality.P1080P60 -> 1080
                StreamQuality.P720P60 -> 720
                StreamQuality.P480P -> 480
                StreamQuality.P360P -> 360
                else -> return null
            }
            streamInfs.indexOfFirst { variantMatches(it, quality) }.takeIf { it >= 0 }?.let { return it }
            val video = streamInfs.indices.filter { height(streamInfs[it]) > 0 }
            return video.filter { height(streamInfs[it]) <= wanted }.maxByOrNull { height(streamInfs[it]) * 10_000_000L + bandwidth(streamInfs[it]) }
                ?: video.minByOrNull { height(streamInfs[it]) }
        }

        // Fixed, so a second launch finds it busy (see SingleInstance). The property is only
        // for running a test copy beside an installed PureTV.
        val PORT: Int = System.getProperty("puretv.proxy.port")?.toIntOrNull() ?: 7979

        /** @see StreamResolver.PRIMARY_PLAYER_TYPE */
        const val PRIMARY_PLAYER_TYPE = StreamResolver.PRIMARY_PLAYER_TYPE

        /**
         * The address the proxy listens on. URLs name it as 127.0.0.1, not `localhost`:
         * `localhost` can resolve to IPv6 ::1 first, where another program may hold the same
         * port (seen with WSL, whose localhost forwarding listens on [::1]:7979 while a Linux
         * PureTV runs), and the player would then talk to that program instead.
         */
        const val HOST = "127.0.0.1"

        /** Builds the URL VLCJ should be pointed at for [channelLogin]/[quality]. */
        fun streamUrl(channelLogin: String, quality: StreamQuality = StreamQuality.AUTO): String =
            "http://$HOST:$PORT/stream?channel=$channelLogin&quality=${quality.name}"

        /**
         * The URL for a multi-stream tile: one rendition, the best one at or below
         * [maxHeight] (see [TileQuality.capMaster]). Each tile asks for its own
         * channel, and the proxy serves all of them at once.
         */
        fun tileStreamUrl(channelLogin: String, maxHeight: Int): String =
            "http://$HOST:$PORT/stream?channel=$channelLogin&quality=AUTO&cap=$maxHeight"

        /**
         * The /variant route fetches an upstream URL on the player's behalf, so
         * restrict it to the Twitch HLS CDN — otherwise it is an open localhost
         * proxy (SSRF): any local process or a crafted page could hit
         * `http://127.0.0.1:7979/variant?url=http://169.254.169.254/...` and use
         * the app as a confused-deputy fetcher. Twitch variant media playlists
         * are always served from *.ttvnw.net (video-edge-*.abs.hls.ttvnw.net /
         * usher.ttvnw.net), so this allowlist never blocks real playback.
         */
        fun isAllowedVariantHost(url: String): Boolean {
            val host = runCatching { java.net.URI(url).host }.getOrNull()?.lowercase() ?: return false
            return host == "ttvnw.net" || host.endsWith(".ttvnw.net")
        }

        /**
         * True when a request's Host header names this machine's loopback address. A web page
         * can reach 127.0.0.1 through its own domain (DNS rebinding) and would then be
         * "same origin" with the proxy, able to read /diag and the playlists and to drive
         * /simulate; its requests carry that domain as Host, so anything else is refused.
         * The players always send 127.0.0.1:7979 (see [HOST]).
         */
        fun isAllowedHostHeader(host: String?): Boolean {
            val name = host?.trim()?.lowercase()?.substringBeforeLast(':') ?: return false
            return name == HOST || name == "localhost"
        }

        /**
         * True if [t] (or any exception in its cause chain) is a port-in-use bind
         * failure. Netty wraps the underlying [java.net.BindException], so we walk the
         * causes. The visited-set guards against a cyclic chain.
         */
        fun isAddressInUse(t: Throwable?): Boolean {
            var cur = t
            val seen = HashSet<Throwable>()
            while (cur != null && seen.add(cur)) {
                if (cur is java.net.BindException) return true
                cur = cur.cause
            }
            return false
        }
    }

    // Ktor 3: embeddedServer { }.start() returns EmbeddedServer (engine-typed),
    // not the older ApplicationEngine interface.
    private var engine: EmbeddedServer<*, *>? = null
    private val lifecycleLock = Mutex()

    // Diagnostic counters — observable via GET /diag. Netty dispatches requests
    // across worker threads, so plain Int would race; AtomicInteger is the
    // cheap correct primitive here.
    private val totalRequests = AtomicInteger(0)
    private val proxySuccesses = AtomicInteger(0)
    private val rewriterFallbacks = AtomicInteger(0)
    private val failures = AtomicInteger(0)
    private val variantFetches = AtomicInteger(0)
    private val variantBackupSwaps = AtomicInteger(0)
    private val variantAdSegmentsStripped = AtomicInteger(0)
    private val variantSequenceNormalizations = AtomicInteger(0)
    private val variantTimelineSegmentsTrimmed = AtomicInteger(0)
    private val variantFailures = AtomicInteger(0)
    private val variantFallback = VariantFallback()

    private sealed interface VariantFetch {
        class Playlist(val body: String) : VariantFetch
        class Failed(val status: HttpStatusCode, val reason: String) : VariantFetch
    }

    /**
     * One media playlist refresh from Twitch. A status other than 2xx, or a body that is
     * not a playlist, is a failure: it has no ad markers, so it used to pass for a clean
     * playlist and reach VLC with a 200. Each try gets [VARIANT_TIMEOUT_MS] instead of the
     * client's 15 s, since the buffer ahead of the picture is only a few seconds with low
     * latency on; a timeout or a 5xx is tried once more (usually on a fresh connection).
     */
    private suspend fun fetchVariant(url: String): VariantFetch {
        var outcome: VariantFetch = VariantFetch.Failed(HttpStatusCode.BadGateway, "not fetched")
        repeat(VARIANT_ATTEMPTS) {
            outcome = try {
                val response = httpClient.get(url) { timeout { requestTimeoutMillis = VARIANT_TIMEOUT_MS } }
                val body = response.bodyAsText()
                when {
                    !response.status.isSuccess() -> VariantFetch.Failed(response.status, "HTTP ${response.status.value}")
                    !body.trimStart().startsWith("#EXTM3U") -> VariantFetch.Failed(HttpStatusCode.BadGateway, "not a playlist")
                    else -> return VariantFetch.Playlist(body)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                VariantFetch.Failed(HttpStatusCode.BadGateway, e.message ?: e::class.simpleName.orEmpty())
            }
            // A 4xx (an expired or ended session) answers the same on a retry.
            if ((outcome as VariantFetch.Failed).status.value in 400..499) return outcome
        }
        return outcome
    }
    private val lastSuccess = AtomicReference<FetchSnapshot?>(null)
    private val lastFailure = AtomicReference<FetchSnapshot?>(null)
    private val lastVariant = AtomicReference<FetchSnapshot?>(null)

    // Why the last /stream for each channel failed, classified for the viewer.
    // VLC only ever sees an HTTP error and reports a generic fault, so the stream
    // screen asks here what really happened (offline, restricted, region...).
    private val streamFailures = ConcurrentHashMap<String, Failure>()

    /** The classified reason the last /stream for [channelLogin] failed, or null if it worked. */
    fun failureFor(channelLogin: String): Failure? = streamFailures[channelLogin.lowercase()]

    // Test hook (see GET /simulate): when true, EVERY variant is treated as if
    // it contained an ad, forcing the backup player-type swap so the whole path
    // (embed/popout token mint → master fetch → same-quality match → clean
    // check → serve) can be exercised live without waiting for a real midroll.
    // Mirrors vaft's window.simulateAds().
    private val simulateAds = AtomicBoolean(false)

    // The ad-blocking rules shared with the phone and TV apps. VLC copes with an
    // empty window on its own (and the stream screen has a stall watchdog), so
    // the issue #22 hold is an ExoPlayer-only behaviour.
    private val cleaner = MediaPlaylistCleaner(
        adBlockEngine = adBlockEngine,
        backupStreamResolver = backupStreamResolver,
        httpClient = httpClient,
        nowMs = System::currentTimeMillis,
        holdEmptyWindows = false,
        lowLatency = lowLatency,
    )

    /**
     * Snapshot of one /stream resolution, captured so the user can inspect
     * what actually came back from Twitch / the proxy without having to
     * attach a debugger or read process logs.
     */
    private data class FetchSnapshot(
        val channel: String,
        val strategy: String,
        val contentLength: Int,
        val containsStitchedAdMarker: Boolean,
        val sample: String,
        val timestampMs: Long,
        val errorMessage: String? = null,
    )

    suspend fun start() = lifecycleLock.withLock {
        if (engine != null) return@withLock
        val server = embeddedServer(Netty, port = PORT, host = HOST) {
            intercept(ApplicationCallPipeline.Plugins) {
                if (!isAllowedHostHeader(call.request.headers[HttpHeaders.Host])) {
                    call.respondText("Forbidden", status = HttpStatusCode.Forbidden)
                    finish()
                }
            }
            routing {
                get("/stream") {
                    val channel = call.request.queryParameters["channel"]
                    val qualityParam = call.request.queryParameters["quality"]
                    if (channel.isNullOrBlank()) {
                        call.respondText("Missing 'channel' query parameter", status = HttpStatusCode.BadRequest)
                        return@get
                    }
                    val quality = runCatching { StreamQuality.valueOf(qualityParam ?: "AUTO") }.getOrDefault(StreamQuality.AUTO)
                    // Multi-stream tiles cap the picture by tile count (see TileQuality).
                    val capHeight = call.request.queryParameters["cap"]?.toIntOrNull()

                    totalRequests.incrementAndGet()
                    streamFailures.remove(channel.lowercase())
                    // A new play of this channel: its reports count again. Multi-stream tiles
                    // (they send a cap) claim their own channel when they open, so a request
                    // from a tile that was just closed cannot bring the channel back to life.
                    if (capHeight == null) adBlockEngine.claimChannel(channel)
                    runCatching {
                        val playable = playableStreamCache.get(channel)
                        // Resolution already fetched and validated the master; reuse it.
                        adBlockEngine.resolveCleanStream(
                            playable.masterUrl,
                            quality,
                            prefetchedMaster = playable.rawContent,
                            channel = channel,
                        )
                    }.onSuccess { result ->
                        when (result) {
                            is CleanStreamResult.Success -> {
                                when (result.strategyUsed) {
                                    AdBlockStrategyResult.PROXY -> proxySuccesses.incrementAndGet()
                                    AdBlockStrategyResult.MANIFEST_REWRITE -> rewriterFallbacks.incrementAndGet()
                                    else -> Unit
                                }
                                // VLC picks renditions by bandwidth, so it never sees the
                                // audio-only one unless that is what was asked for (then it
                                // sees nothing else). When the user selects a specific video
                                // quality, filter the master down to only that variant too.
                                // Without this VLC always receives the full multi-variant
                                // master and ignores the quality param.
                                val playable = HlsMasterParser.forPlayback(
                                    result.playlistContent,
                                    audioOnly = quality == StreamQuality.AUDIO_ONLY,
                                )
                                val qualityFiltered = when {
                                    capHeight != null -> TileQuality.capMaster(playable, capHeight)
                                    quality != StreamQuality.AUTO && quality != StreamQuality.AUDIO_ONLY ->
                                        filterMasterToQuality(playable, quality)
                                    else -> playable
                                }
                                // CRITICAL: rewrite variant URLs so VLC fetches each per-quality
                                // media playlist through us (ad-block choke point).
                                val singleRendition = HlsMasterParser.parseVariants(qualityFiltered).size == 1
                                val rewrittenMaster = rewriteVariantUrls(qualityFiltered, channel, singleRendition)
                                lastSuccess.set(snapshot(channel, result, rewrittenMaster))
                                call.respondText(
                                    text = rewrittenMaster,
                                    contentType = ContentType.parse("application/vnd.apple.mpegurl"),
                                )
                            }
                            is CleanStreamResult.Failure -> {
                                failures.incrementAndGet()
                                streamFailures[channel.lowercase()] = Failure(FailureKind.UNKNOWN, result.reason)
                                lastFailure.set(
                                    FetchSnapshot(
                                        channel = channel,
                                        strategy = "NONE",
                                        contentLength = 0,
                                        containsStitchedAdMarker = false,
                                        sample = "",
                                        timestampMs = System.currentTimeMillis(),
                                        errorMessage = result.reason,
                                    ),
                                )
                                call.respondText(
                                    text = "# Ad-block resolution failed: ${result.reason}",
                                    contentType = ContentType.parse("application/vnd.apple.mpegurl"),
                                    status = HttpStatusCode.BadGateway,
                                )
                            }
                        }
                    }.onFailure { e ->
                        failures.incrementAndGet()
                        streamFailures[channel.lowercase()] = StreamFailures.classify(e)
                        // Resolution threw before resolveCleanStream could run (e.g. the
                        // GQL token mint failed). resolveCleanStream never set a status, so
                        // flip the engine off UNKNOWN here — otherwise the pill hangs on
                        // "Checking…" forever and hides the outage (GOTCHA #1 lesson).
                        adBlockEngine.reportResolutionFailure(channel)
                        lastFailure.set(
                            FetchSnapshot(
                                channel = channel,
                                strategy = "EXCEPTION",
                                contentLength = 0,
                                containsStitchedAdMarker = false,
                                sample = "",
                                timestampMs = System.currentTimeMillis(),
                                errorMessage = e.message ?: e::class.simpleName,
                            ),
                        )
                        call.respondText(
                            text = "Failed to resolve stream for '$channel': ${e.message}",
                            status = HttpStatusCode.InternalServerError,
                        )
                    }
                }

                // SECTION 4 IN PRACTICE — every variant (per-quality media) playlist
                // VLC requests comes through here. When it contains a stitched ad we
                // first try the BACKUP PLAYER-TYPE SWAP (Section 4.1): fetch the same
                // quality from an `embed`/`popout` manifest and, if that one is clean,
                // serve it verbatim — seamless, no stall. Only if every backup is also
                // ad-showing do we fall back to ManifestRewriter stripping (Section 4.3,
                // which freezes through the ad). Clean playlists pass straight through.
                // Segment .ts requests are NOT intercepted (would burn bandwidth); they
                // stream straight from the CDN.
                get("/variant") {
                    val encoded = call.request.queryParameters["url"]
                    if (encoded.isNullOrBlank()) {
                        call.respondText("Missing 'url' query parameter", status = HttpStatusCode.BadRequest)
                        return@get
                    }
                    // Ktor already decoded the query parameter. Decoding it again turned any
                    // '+' or '%xx' inside Twitch's URL into something else.
                    val originalUrl = encoded
                    if (!originalUrl.startsWith("http")) {
                        call.respondText("Invalid 'url' query parameter", status = HttpStatusCode.BadRequest)
                        return@get
                    }
                    // SSRF guard: only proxy the Twitch HLS CDN, never an
                    // arbitrary host supplied via ?url= (see isAllowedVariantHost).
                    if (!isAllowedVariantHost(originalUrl)) {
                        call.respondText("Disallowed upstream host", status = HttpStatusCode.Forbidden)
                        return@get
                    }
                    val channel = call.request.queryParameters["channel"].orEmpty()
                    val resolution = call.request.queryParameters["res"].orEmpty()
                    val frameRate = call.request.queryParameters["fps"]?.toDoubleOrNull() ?: 0.0
                    variantFetches.incrementAndGet()
                    val fetched = fetchVariant(originalUrl)
                    if (fetched is VariantFetch.Failed) {
                        variantFailures.incrementAndGet()
                        val stale = variantFallback.fallback(originalUrl)
                        lastVariant.set(
                            FetchSnapshot(
                                channel = channel.ifBlank { "(variant)" },
                                strategy = if (stale != null) "LAST_GOOD" else "FAILED",
                                contentLength = stale?.length ?: 0,
                                containsStitchedAdMarker = false,
                                sample = "",
                                timestampMs = System.currentTimeMillis(),
                                errorMessage = fetched.reason,
                            ),
                        )
                        if (stale != null) {
                            call.respondText(text = stale, contentType = ContentType.parse("application/vnd.apple.mpegurl"))
                        } else {
                            call.respondText(
                                text = "# Variant fetch failed: ${fetched.reason}",
                                contentType = ContentType.parse("application/vnd.apple.mpegurl"),
                                status = fetched.status,
                            )
                        }
                        return@get
                    }
                    (fetched as VariantFetch.Playlist).body.let { raw ->
                        // Backup swap, strip, and the timeline normalization that keeps
                        // VLC's view continuous across player-type sessions (a backup's
                        // MEDIA-SEQUENCE can differ by thousands from the primary's).
                        // A variant without a resolution is the audio-only rendition;
                        // its backup is the audio-only rendition of another session.
                        val cleaned = cleaner.clean(
                            variantUrl = originalUrl,
                            raw = raw,
                            channelLogin = channel.ifBlank { null },
                            quality = MediaPlaylistCleaner.VariantQuality(resolution, frameRate),
                            forceAd = simulateAds.get(),
                            singleRendition = call.request.queryParameters["single"] == "1",
                        )
                        when (cleaned.strategy) {
                            MediaPlaylistCleaner.Strategy.BACKUP -> variantBackupSwaps.incrementAndGet()
                            MediaPlaylistCleaner.Strategy.STRIP ->
                                if (cleaned.adSegmentsRemoved > 0) variantAdSegmentsStripped.addAndGet(cleaned.adSegmentsRemoved)
                            else -> Unit
                        }
                        if (cleaned.sequenceNormalized) variantSequenceNormalizations.incrementAndGet()
                        if (cleaned.segmentsTrimmed > 0) {
                            variantTimelineSegmentsTrimmed.addAndGet(cleaned.segmentsTrimmed)
                        }
                        val deliveredText = cleaned.content
                        val strategy = cleaned.backupPlayerType
                            ?.let { "BACKUP_${it.uppercase()}" }
                            ?: cleaned.strategy.name
                        lastVariant.set(
                            FetchSnapshot(
                                channel = channel.ifBlank { "(variant)" },
                                strategy = strategy,
                                contentLength = deliveredText.length,
                                containsStitchedAdMarker = AdMarkers.containsAds(deliveredText),
                                sample = deliveredText.take(800),
                                timestampMs = System.currentTimeMillis(),
                                errorMessage = null,
                            ),
                        )
                        variantFallback.remember(originalUrl, deliveredText)
                        call.respondText(
                            text = deliveredText,
                            contentType = ContentType.parse("application/vnd.apple.mpegurl"),
                        )
                    }
                }

                get("/health") {
                    call.respondText("ok")
                }

                // Test hook — toggle forced ad simulation to prove the backup
                // swap works without waiting for a real midroll:
                //   http://localhost:7979/simulate?ads=on   (then watch /diag)
                //   http://localhost:7979/simulate?ads=off
                // With it on, variant_backup_swaps should climb and playback
                // should keep going (we serve the clean embed/popout playlist).
                get("/simulate") {
                    when (call.request.queryParameters["ads"]?.lowercase()) {
                        "on", "true", "1" -> simulateAds.set(true)
                        "off", "false", "0" -> simulateAds.set(false)
                        else -> {
                            call.respondText("Usage: /simulate?ads=on|off (currently ${if (simulateAds.get()) "ON" else "OFF"})")
                            return@get
                        }
                    }
                    call.respondText("simulate ads = ${if (simulateAds.get()) "ON" else "OFF"}")
                }

                // Human-readable JSON-ish diagnostic. Hit
                // http://localhost:7979/diag in a browser while a stream is
                // playing to see exactly which strategy is winning and what
                // playlist content came back from the upstream proxy.
                get("/diag") {
                    val success = lastSuccess.get()
                    val failure = lastFailure.get()
                    call.respondText(
                        text = renderDiag(success, failure),
                        contentType = ContentType.Application.Json,
                    )
                }
            }
        }
        // Binding happens synchronously inside start(); a second PureTV process (or a
        // leftover Netty from a hard-killed one) holding port 7979 throws here. Translate
        // it into a clear, typed failure instead of letting a raw BindException escape
        // uncaught into the stream ViewModel's init coroutine (which silently leaves the
        // player on an endless "Loading…" with no explanation).
        try {
            server.start(wait = false)
            engine = server
        } catch (e: Throwable) {
            runCatching { server.stop(0, 0) } // release anything half-bound
            engine = null
            if (isAddressInUse(e)) {
                throw ProxyUnavailableException(
                    "PureTV's local video proxy could not start because port $PORT is already in use. " +
                        "This usually means another PureTV window is open, or a previous one did not fully close. " +
                        "Close other PureTV windows and reopen this stream.",
                )
            }
            throw e
        }
    }

    suspend fun stop() = lifecycleLock.withLock {
        engine?.stop(gracePeriodMillis = 200, timeoutMillis = 1_000)
        engine = null
    }

    /**
     * Counters and the last results for "Copy diagnostics". Unlike /diag it leaves
     * out playlist samples: those quote signed media URLs, and this text is meant
     * to be pasted into a bug report.
     */
    fun diagnosticsSummary(): String = buildString {
        appendLine("master requests ${totalRequests.get()}, failures ${failures.get()}, rewriter ${rewriterFallbacks.get()}")
        appendLine(
            "variant fetches ${variantFetches.get()}, backup swaps ${variantBackupSwaps.get()}, " +
                "ad segments stripped ${variantAdSegmentsStripped.get()}, variant failures ${variantFailures.get()}",
        )
        lastFailure.get()?.let { appendLine("last failure: ${it.channel} ${it.strategy} ${it.errorMessage.orEmpty()}") }
        lastVariant.get()?.let { appendLine("last variant: ${it.channel} ${it.strategy}, ad marker ${it.containsStitchedAdMarker}") }
        streamFailures.forEach { (channel, failure) -> appendLine("classified: $channel ${failure.kind}") }
    }

    private fun snapshot(channel: String, result: CleanStreamResult.Success, deliveredContent: String): FetchSnapshot {
        // The "delivered" content is what VLC actually got — i.e. after we
        // rewrote variant URLs through localhost:7979. Marker detection runs
        // on the upstream content so a "true" here means upstream sent us a
        // dirty playlist, not that our rewrite somehow re-introduced ads.
        val upstream = result.playlistContent
        return FetchSnapshot(
            channel = channel,
            strategy = result.strategyUsed.name,
            contentLength = deliveredContent.length,
            containsStitchedAdMarker = upstream.contains("twitch-stitched-ad", ignoreCase = true) ||
                upstream.contains("#EXT-X-CUE-OUT") ||
                upstream.contains("stitched-ad"),
            sample = deliveredContent.take(800),
            timestampMs = System.currentTimeMillis(),
        )
    }

    /**
     * Return a copy of [masterPlaylist] with only the one `#EXT-X-STREAM-INF` + URL pair
     * [chooseVariant] picks for [quality]. All header/tag lines (including `#EXTM3U`,
     * `#EXT-X-TWITCH-INFO`, `#EXT-X-MEDIA`) are preserved so VLC still parses a valid
     * HLS master. Only a master with no variants at all comes back unfiltered: handing
     * VLC every rendition makes it start at the lowest (160p) and climb.
     */
    private fun filterMasterToQuality(masterPlaylist: String, quality: StreamQuality): String {
        val lines = masterPlaylist.lines()
        val variants = buildList {
            lines.forEachIndexed { i, line ->
                val trimmed = line.trim()
                val url = lines.getOrNull(i + 1)?.trim().orEmpty()
                if (trimmed.startsWith("#EXT-X-STREAM-INF") && url.isNotEmpty() && !url.startsWith("#")) add(i to trimmed)
            }
        }
        val chosen = chooseVariant(variants.map { it.second }, quality)?.let { variants[it].first } ?: return masterPlaylist
        val out = StringBuilder(masterPlaylist.length)
        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            if (variants.any { it.first == i }) {
                // A variant and its URL line: keep only the chosen one.
                if (i == chosen) out.append(line).append('\n').append(lines[i + 1]).append('\n')
                i += 2
                continue
            }
            // Keep all non-variant lines: headers, EXT-X-MEDIA, blanks, etc.
            out.append(line).append('\n')
            i++
        }
        return out.toString()
    }

    /**
     * Walk a master playlist line-by-line; any non-comment line that looks
     * like an absolute HTTP(S) URL is treated as a variant (per-quality media
     * playlist) reference and rewritten to point at our `/variant` route,
     * carrying the [channel] plus the variant's RESOLUTION/FRAME-RATE (read
     * from the preceding `#EXT-X-STREAM-INF`) so `/variant` can request the
     * same quality from a backup player type, and whether it is the only
     * rendition VLC was given ([singleRendition]). Comment/tag lines are preserved
     * verbatim — only URI lines change.
     *
     * Twitch's master playlists use absolute URLs exclusively (verified
     * against `video-edge-*.abs.hls.ttvnw.net` and `usher.ttvnw.net`
     * responses), so we don't bother with relative-URL resolution here. If
     * that ever changes a stream will fail loudly (VLC can't fetch a
     * relative URL with no base) rather than silently leak ads.
     */
    private fun rewriteVariantUrls(masterPlaylist: String, channel: String, singleRendition: Boolean): String {
        val out = StringBuilder(masterPlaylist.length + 512)
        var pendingResolution = ""
        var pendingFrameRate = ""
        for (line in masterPlaylist.lineSequence()) {
            val trimmed = line.trim()
            when {
                trimmed.startsWith("#EXT-X-STREAM-INF") -> {
                    pendingResolution = streamInfAttr(trimmed, "RESOLUTION")
                    pendingFrameRate = streamInfAttr(trimmed, "FRAME-RATE")
                    out.append(line).append('\n')
                }
                trimmed.isNotEmpty() && !trimmed.startsWith("#") &&
                    (trimmed.startsWith("http://") || trimmed.startsWith("https://")) -> {
                    out.append("http://127.0.0.1:").append(PORT)
                        .append("/variant?url=").append(URLEncoder.encode(trimmed, StandardCharsets.UTF_8))
                    if (channel.isNotBlank()) {
                        out.append("&channel=").append(URLEncoder.encode(channel, StandardCharsets.UTF_8))
                    }
                    if (pendingResolution.isNotBlank()) {
                        out.append("&res=").append(URLEncoder.encode(pendingResolution, StandardCharsets.UTF_8))
                    }
                    if (pendingFrameRate.isNotBlank()) {
                        out.append("&fps=").append(URLEncoder.encode(pendingFrameRate, StandardCharsets.UTF_8))
                    }
                    // VLC has no other rendition to switch to, so low latency may
                    // promote prefetch hints (see TwitchPrefetch).
                    if (singleRendition) out.append("&single=1")
                    out.append('\n')
                    pendingResolution = ""
                    pendingFrameRate = ""
                }
                else -> out.append(line).append('\n')
            }
        }
        return out.toString()
    }

    /**
     * Pull an unquoted `#EXT-X-STREAM-INF` attribute value (RESOLUTION,
     * FRAME-RATE) — both are comma-free so matching up to the next comma is
     * safe. Anchored on a `:`/`,` boundary so e.g. FRAME-RATE isn't matched
     * inside another token.
     */
    private fun streamInfAttr(line: String, name: String): String = hlsAttr(line, name)

    private fun renderDiag(success: FetchSnapshot?, failure: FetchSnapshot?): String {
        // Hand-rolled JSON to avoid pulling kotlinx-serialization into this
        // file just for one debug endpoint. Values are escaped for the only
        // characters that would break JSON parsing (\, ", control chars).
        fun esc(s: String?): String = s?.let {
            buildString(it.length + 8) {
                append('"')
                for (c in it) when (c) {
                    '\\' -> append("\\\\")
                    '"' -> append("\\\"")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    else -> if (c.code < 0x20) append("\\u%04x".format(c.code)) else append(c)
                }
                append('"')
            }
        } ?: "null"

        fun snap(s: FetchSnapshot?): String = s?.let {
            """
            {
                "channel": ${esc(it.channel)},
                "strategy": ${esc(it.strategy)},
                "content_length": ${it.contentLength},
                "contains_stitched_ad_marker": ${it.containsStitchedAdMarker},
                "error": ${esc(it.errorMessage)},
                "timestamp_ms": ${it.timestampMs},
                "age_seconds": ${(System.currentTimeMillis() - it.timestampMs) / 1000},
                "sample_first_800_chars": ${esc(it.sample)}
            }
            """.trimIndent()
        } ?: "null"

        return """
        {
            "counters": {
                "simulate_ads": ${simulateAds.get()},
                "total_master_requests": ${totalRequests.get()},
                "proxy_successes": ${proxySuccesses.get()},
                "rewriter_fallbacks": ${rewriterFallbacks.get()},
                "failures": ${failures.get()},
                "variant_fetches": ${variantFetches.get()},
                "variant_backup_swaps": ${variantBackupSwaps.get()},
                "variant_ad_segments_stripped": ${variantAdSegmentsStripped.get()},
                "variant_sequence_normalizations": ${variantSequenceNormalizations.get()},
                "variant_timeline_segments_trimmed": ${variantTimelineSegmentsTrimmed.get()},
                "variant_failures": ${variantFailures.get()}
            },
            "last_master_success": ${snap(success)},
            "last_master_failure": ${snap(failure)},
            "last_variant": ${snap(lastVariant.get())},
            "notes": [
                "variant_backup_swaps > 0 means a stitched ad was detected and a clean same-quality backup playlist was served.",
                "variant_sequence_normalizations > 0 means backup/primary HLS session timelines were mapped onto one continuous VLC timeline; this prevents ad-transition freezes.",
                "variant_timeline_segments_trimmed > 0 means overlapping backup segments were removed to keep the served timeline monotonic.",
                "variant_ad_segments_stripped > 0 means every backup player type was also showing the ad, so ad segments were removed and HLS sequence metadata was repaired.",
                "variant_fetches grows at roughly 1/sec per active stream once VLC settles, since live variants refresh every 2s.",
                "the primary stream is resolved with playerType='popout', which is targeted with far fewer ads than the default 'site' web player — many streams never trigger a swap at all."
            ]
        }
        """.trimIndent()
    }
}
