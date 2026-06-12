package com.puretv.twitch.desktop.player

import com.puretv.twitch.core.adblock.AdBlockEngine
import com.puretv.twitch.core.adblock.AdMarkers
import com.puretv.twitch.core.adblock.BackupStreamResolver
import com.puretv.twitch.core.model.AdBlockStrategyResult
import com.puretv.twitch.core.model.CleanStreamResult
import com.puretv.twitch.core.model.StreamQuality
import com.puretv.twitch.core.repository.StreamRepository
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
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
 *         (`StreamRepository.resolvePlayableStream`)
 *       → runs it through `AdBlockEngine.resolveCleanStream` (proxy-router
 *         or manifest-rewrite strategy per Section 4)
 *       → returns the cleaned m3u8 text with the correct HLS content type
 *
 * This keeps 100% of the ad-block logic in `core`, shared verbatim with the
 * phone/TV apps' in-process equivalents — only the *transport* differs
 * because VLCJ needs a URL, not a Kotlin object.
 */
class LocalStreamProxy(
    private val streamRepository: StreamRepository,
    private val adBlockEngine: AdBlockEngine,
    private val httpClient: HttpClient,
    private val backupStreamResolver: BackupStreamResolver,
) {
    companion object {
        const val PORT = 7979

        /**
         * Player type used for the PRIMARY stream resolution. `popout` is
         * targeted with far fewer ads than the default `site` web player
         * (the reference userscript forces this for every request), so most
         * streams come back ad-free before the backup swap is even needed.
         */
        const val PRIMARY_PLAYER_TYPE = "popout"

        /** Builds the URL VLCJ should be pointed at for [channelLogin]/[quality]. */
        fun streamUrl(channelLogin: String, quality: StreamQuality = StreamQuality.AUTO): String =
            "http://localhost:$PORT/stream?channel=$channelLogin&quality=${quality.name}"
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
    private val variantFailures = AtomicInteger(0)
    private val lastSuccess = AtomicReference<FetchSnapshot?>(null)
    private val lastFailure = AtomicReference<FetchSnapshot?>(null)
    private val lastVariant = AtomicReference<FetchSnapshot?>(null)

    // Test hook (see GET /simulate): when true, EVERY variant is treated as if
    // it contained an ad, forcing the backup player-type swap so the whole path
    // (embed/popout token mint → master fetch → same-quality match → clean
    // check → serve) can be exercised live without waiting for a real midroll.
    // Mirrors vaft's window.simulateAds().
    private val simulateAds = AtomicBoolean(false)

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
        engine = embeddedServer(Netty, port = PORT, host = "127.0.0.1") {
            routing {
                get("/stream") {
                    val channel = call.request.queryParameters["channel"]
                    val qualityParam = call.request.queryParameters["quality"]
                    if (channel.isNullOrBlank()) {
                        call.respondText("Missing 'channel' query parameter", status = HttpStatusCode.BadRequest)
                        return@get
                    }
                    val quality = runCatching { StreamQuality.valueOf(qualityParam ?: "AUTO") }.getOrDefault(StreamQuality.AUTO)

                    totalRequests.incrementAndGet()
                    runCatching {
                        val playable = streamRepository.resolvePlayableStream(channel, playerType = PRIMARY_PLAYER_TYPE)
                        adBlockEngine.resolveCleanStream(playable.masterUrl, quality)
                    }.onSuccess { result ->
                        when (result) {
                            is CleanStreamResult.Success -> {
                                when (result.strategyUsed) {
                                    AdBlockStrategyResult.PROXY -> proxySuccesses.incrementAndGet()
                                    AdBlockStrategyResult.MANIFEST_REWRITE -> rewriterFallbacks.incrementAndGet()
                                    else -> Unit
                                }
                                // When the user selects a specific quality, filter the master
                                // playlist down to only that variant before handing it to VLC.
                                // Without this VLC always receives the full multi-variant master
                                // and ignores the quality param, auto-selecting by bandwidth.
                                val qualityFiltered = if (quality != StreamQuality.AUTO) {
                                    filterMasterToQuality(result.playlistContent, quality)
                                } else {
                                    result.playlistContent
                                }
                                // CRITICAL: rewrite variant URLs so VLC fetches each per-quality
                                // media playlist through us (ad-block choke point).
                                val rewrittenMaster = rewriteVariantUrls(qualityFiltered, channel)
                                lastSuccess.set(snapshot(channel, result, rewrittenMaster))
                                call.respondText(
                                    text = rewrittenMaster,
                                    contentType = ContentType.parse("application/vnd.apple.mpegurl"),
                                )
                            }
                            is CleanStreamResult.Failure -> {
                                failures.incrementAndGet()
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
                    val originalUrl = runCatching { URLDecoder.decode(encoded, StandardCharsets.UTF_8) }.getOrNull()
                    if (originalUrl.isNullOrBlank() || !originalUrl.startsWith("http")) {
                        call.respondText("Invalid 'url' query parameter", status = HttpStatusCode.BadRequest)
                        return@get
                    }
                    val channel = call.request.queryParameters["channel"].orEmpty()
                    val resolution = call.request.queryParameters["res"].orEmpty()
                    val frameRate = call.request.queryParameters["fps"]?.toDoubleOrNull() ?: 0.0
                    variantFetches.incrementAndGet()
                    runCatching {
                        httpClient.get(originalUrl).bodyAsText()
                    }.onSuccess { raw ->
                        val (servedText, strategy) = resolveCleanVariant(raw, channel, resolution, frameRate)
                        lastVariant.set(
                            FetchSnapshot(
                                channel = channel.ifBlank { "(variant)" },
                                strategy = strategy,
                                contentLength = servedText.length,
                                containsStitchedAdMarker = AdMarkers.containsAds(servedText),
                                sample = servedText.take(800),
                                timestampMs = System.currentTimeMillis(),
                                errorMessage = null,
                            ),
                        )
                        call.respondText(
                            text = servedText,
                            contentType = ContentType.parse("application/vnd.apple.mpegurl"),
                        )
                    }.onFailure { e ->
                        variantFailures.incrementAndGet()
                        call.respondText(
                            text = "# Variant fetch failed: ${e.message}",
                            contentType = ContentType.parse("application/vnd.apple.mpegurl"),
                            status = HttpStatusCode.BadGateway,
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
        }.start(wait = false)
    }

    suspend fun stop() = lifecycleLock.withLock {
        engine?.stop(gracePeriodMillis = 200, timeoutMillis = 1_000)
        engine = null
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
     * Decide what to serve VLC for one variant playlist [raw]:
     *   - clean already → pass through (cheap rewriter no-op);
     *   - has a stitched ad and we know the [channel]/[resolution] → try the
     *     backup player-type swap for a seamless ad-free playlist;
     *   - backups all ad-showing (or no channel info) → strip (stalls the ad).
     *
     * Returns the playlist text plus a short strategy label for diagnostics.
     */
    private suspend fun resolveCleanVariant(
        raw: String,
        channel: String,
        resolution: String,
        frameRate: Double,
    ): Pair<String, String> {
        val hasAd = AdMarkers.containsAds(raw) || simulateAds.get()
        if (!hasAd) return raw to "CLEAN"

        if (channel.isNotBlank()) {
            val backup = runCatching {
                backupStreamResolver.resolveAdFreeMediaPlaylist(
                    channelLogin = channel,
                    targetResolution = resolution,
                    targetFrameRate = frameRate,
                    nowMs = System.currentTimeMillis(),
                )
            }.getOrNull()
            if (backup != null) {
                variantBackupSwaps.incrementAndGet()
                return backup.mediaPlaylist to "BACKUP_${backup.playerType.uppercase()}"
            }
        }

        // No clean backup available — strip the ad pod (player stalls through it).
        val filtered = adBlockEngine.filterPlaylist(raw)
        if (filtered.adSegmentsRemoved > 0) variantAdSegmentsStripped.addAndGet(filtered.adSegmentsRemoved)
        return filtered.content to "STRIP"
    }

    /**
     * Return a copy of [masterPlaylist] that contains only the single
     * `#EXT-X-STREAM-INF` + URL pair whose VIDEO= attribute maps to [quality].
     * All header/tag lines (including `#EXTM3U`, `#EXT-X-TWITCH-INFO`,
     * `#EXT-X-MEDIA`) are preserved so VLC still parses a valid HLS master.
     *
     * Falls back to the unfiltered master if no exact match is found (e.g.
     * the requested quality isn't available for this broadcaster).
     */
    private fun filterMasterToQuality(masterPlaylist: String, quality: StreamQuality): String {
        val lines = masterPlaylist.lines()
        val out = StringBuilder(masterPlaylist.length)
        var foundMatch = false
        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            val trimmed = line.trim()
            if (trimmed.startsWith("#EXT-X-STREAM-INF")) {
                val url = lines.getOrNull(i + 1)?.trim().orEmpty()
                val nameHint = streamInfAttr(trimmed, "VIDEO")
                    .ifBlank { streamInfAttr(trimmed, "RESOLUTION") }
                val variantQuality = StreamQuality.fromVariantName(nameHint)
                if (variantQuality == quality && url.isNotEmpty() && !url.startsWith("#")) {
                    out.append(line).append('\n').append(url).append('\n')
                    foundMatch = true
                }
                // Skip past the URL line regardless (it's consumed here)
                if (url.isNotEmpty() && !url.startsWith("#")) i++
            } else {
                // Keep all non-variant lines: headers, EXT-X-MEDIA, blanks, etc.
                out.append(line).append('\n')
            }
            i++
        }
        return if (foundMatch) out.toString() else masterPlaylist
    }

    /**
     * Walk a master playlist line-by-line; any non-comment line that looks
     * like an absolute HTTP(S) URL is treated as a variant (per-quality media
     * playlist) reference and rewritten to point at our `/variant` route,
     * carrying the [channel] plus the variant's RESOLUTION/FRAME-RATE (read
     * from the preceding `#EXT-X-STREAM-INF`) so `/variant` can request the
     * same quality from a backup player type. Comment/tag lines are preserved
     * verbatim — only URI lines change.
     *
     * Twitch's master playlists use absolute URLs exclusively (verified
     * against `video-edge-*.abs.hls.ttvnw.net` and `usher.ttvnw.net`
     * responses), so we don't bother with relative-URL resolution here. If
     * that ever changes a stream will fail loudly (VLC can't fetch a
     * relative URL with no base) rather than silently leak ads.
     */
    private fun rewriteVariantUrls(masterPlaylist: String, channel: String): String {
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
    private fun streamInfAttr(line: String, name: String): String =
        Regex("(?:^|[,:])$name=([^,]+)").find(line)?.groupValues?.get(1)?.trim()?.trim('"').orEmpty()

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
                "variant_failures": ${variantFailures.get()}
            },
            "last_master_success": ${snap(success)},
            "last_master_failure": ${snap(failure)},
            "last_variant": ${snap(lastVariant.get())},
            "notes": [
                "variant_backup_swaps > 0 is the GOOD outcome: a stitched ad was detected and we served a clean same-quality playlist from a backup player type (embed/popout) instead — seamless, no stall. This is the primary ad remover.",
                "variant_ad_segments_stripped > 0 means every backup player type was ALSO showing the ad, so we fell back to stripping (the player freezes through the ad). Frequent stripping suggests the backup swap isn't finding clean streams.",
                "variant_fetches grows at roughly 1/sec per active stream once VLC settles, since live variants refresh every 2s.",
                "the primary stream is resolved with playerType='popout', which is targeted with far fewer ads than the default 'site' web player — many streams never trigger a swap at all."
            ]
        }
        """.trimIndent()
    }
}
