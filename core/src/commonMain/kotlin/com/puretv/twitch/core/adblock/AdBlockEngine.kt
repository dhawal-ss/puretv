package com.puretv.twitch.core.adblock

import com.puretv.twitch.core.model.AdBlockStrategyResult
import com.puretv.twitch.core.model.CleanStreamResult
import com.puretv.twitch.core.model.FilteredPlaylist
import com.puretv.twitch.core.model.PlaylistVariant
import com.puretv.twitch.core.model.StreamQuality
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.util.encodeBase64
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * SECTION 04 [CRITICAL — MOST IMPORTANT FEATURE]
 *
 * Three-tier fallback chain that guarantees the user is never shown a Twitch ad:
 *
 *   1. PROXY ROUTER     — route the ~2KB playlist through an ad-light-region proxy
 *   2. MANIFEST REWRITE — strip ad markers locally if the proxy is unavailable
 *   3. BLACK FRAME      — last resort, implemented at the player layer (mute + overlay)
 *      when an ad segment slips through both of the above (see [AdBlockStatus.PASSTHROUGH_BLOCKED]
 *      and the player-side detectors in app-android/app-tv/app-windows).
 *
 * Rule of thumb (Final Agent Instructions #5): "A stream playing with ads is a failure state."
 * Every code path here must end in either a clean playlist or an explicit, observable
 * failure — never a silent ad.
 */
class AdBlockEngine(
    private val config: AdBlockConfig,
    private val httpClient: HttpClient,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default),
) {
    private val _status = MutableStateFlow(AdBlockStatus.UNKNOWN)
    val status: StateFlow<AdBlockStatus> = _status.asStateFlow()

    private val rewriter = ManifestRewriter()
    private val healthTracker = ProxyHealthTracker()

    /**
     * Returns a clean m3u8 master/media playlist URL or content that the player
     * can consume without showing ads. Tries strategies in order based on
     * [AdBlockConfig.strategy] and current proxy health.
     */
    suspend fun resolveCleanStream(masterPlaylistUrl: String, quality: StreamQuality = StreamQuality.AUTO): CleanStreamResult {
        if (config.strategy == AdBlockStrategy.DISABLED) {
            _status.value = AdBlockStatus.DISABLED
            return passthrough(masterPlaylistUrl, AdBlockStrategyResult.DISABLED)
        }

        // Strategy 1 only runs if the user has actually configured an upstream
        // proxy endpoint. The 2026 default is empty (see AdBlockConfig docs) —
        // without this short-circuit we'd burn a network round-trip per stream
        // open hitting a known-dead URL before falling through.
        val hasUpstream = !config.customProxyEndpoint.isNullOrBlank() || config.proxyEndpoint.isNotBlank()
        if (config.strategy == AdBlockStrategy.PROXY_PRIMARY && hasUpstream && healthTracker.isHealthy()) {
            fetchViaProxy(masterPlaylistUrl)?.let { proxied ->
                _status.value = AdBlockStatus.AD_BLOCKED
                healthTracker.recordSuccess()
                return CleanStreamResult.Success(
                    playlistContent = proxied,
                    variants = emptyList(),
                    strategyUsed = AdBlockStrategyResult.PROXY,
                )
            }
            healthTracker.recordFailure()
        }

        if (config.fallbackToManifestRewrite) {
            val raw = fetchRaw(masterPlaylistUrl)
            if (raw != null) {
                val filtered = rewriter.filter(raw)
                _status.value = if (filtered.containedAds) AdBlockStatus.AD_FILTERED else AdBlockStatus.AD_BLOCKED
                return CleanStreamResult.Success(
                    playlistContent = filtered.content,
                    variants = emptyList(),
                    strategyUsed = AdBlockStrategyResult.MANIFEST_REWRITE,
                )
            }
        }

        // Both network-level strategies failed. The player layer's black-frame
        // fallback (Section 4.4) takes over from here — we report the failure
        // state so the UI can show "AD BLOCK OFF" and the player can engage
        // its segment-anomaly detector.
        _status.value = AdBlockStatus.AD_BLOCK_OFF
        return CleanStreamResult.Failure("Both proxy and manifest-rewrite strategies failed")
    }

    /**
     * Called on every playlist refresh (live playlists refresh roughly every
     * ~2 seconds). Cheap, synchronous, no network — pure text filtering so it
     * can run on the player's polling thread without adding latency.
     */
    fun filterPlaylist(playlistContent: String): FilteredPlaylist {
        val filtered = rewriter.filter(playlistContent)
        // Fail-safe (audit AD-1, Rule #5 "a stream playing with ads is a failure
        // state"): detection sees an ad in this window but the normal pass
        // stripped nothing — the pod's opening marker has scrolled out of the
        // live window, so the leading segments are mid-pod ad content that would
        // otherwise be served as content. Re-strip assuming we start mid-pod.
        if (filtered.adSegmentsRemoved == 0 && AdMarkers.containsAds(playlistContent)) {
            return rewriter.filter(playlistContent, assumeStartInAdBreak = true)
        }
        return filtered
    }

    /**
     * Called by the transport layer (e.g. `LocalStreamProxy`) when stream
     * resolution fails BEFORE [resolveCleanStream] can run — typically the GQL
     * playback-token mint threw (a banned/renamed channel, or Twitch changing
     * the API as in GOTCHA #1). Without this the status would sit at
     * [AdBlockStatus.UNKNOWN] ("Checking…") forever, silently masking the
     * outage; moving it to [AdBlockStatus.AD_BLOCK_OFF] makes the failure
     * visible in the on-screen pill instead.
     */
    fun reportResolutionFailure() {
        _status.value = AdBlockStatus.AD_BLOCK_OFF
    }

    // ---- Strategy 1: Proxy Router ----

    /**
     * SECTION 4.2 — TTV LOL PRO compatible proxy contract:
     *   GET {proxyEndpoint}/{base64(playlistUrl)}
     *   Header: X-Donate-To: https://ttv.lol/donate
     *
     * Only the playlist (≈2KB) goes through the proxy; video segments are
     * still fetched directly from the Twitch CDN — minimal latency & bandwidth
     * impact, and resilient to the proxy going down (falls through to Strategy 2).
     */
    private suspend fun fetchViaProxy(playlistUrl: String): String? = withContext(Dispatchers.IO) {
        val endpoint = config.customProxyEndpoint?.takeIf { it.isNotBlank() } ?: config.proxyEndpoint
        val encoded = playlistUrl.encodeToByteArray().encodeBase64()
        val body = try {
            httpClient.get("$endpoint/$encoded") {
                header("X-Donate-To", "https://ttv.lol/donate")
            }.bodyAsText()
        } catch (e: Exception) {
            return@withContext null
        }
        // Public proxies (ttv.lol etc.) sometimes return Cloudflare challenge
        // pages, 503 HTML, or maintenance pages with a 200 status — VLC then
        // tries to parse HTML as HLS and shows a silent black pane forever.
        // Treat anything that doesn't start with the HLS magic line as a proxy
        // failure so we fall through to the direct-from-Twitch path.
        if (!body.trimStart().startsWith("#EXTM3U")) return@withContext null
        body
    }

    private suspend fun fetchRaw(url: String): String? = withContext(Dispatchers.IO) {
        try {
            httpClient.get(url).bodyAsText()
        } catch (e: Exception) {
            null
        }
    }

    private fun passthrough(url: String, strategy: AdBlockStrategyResult): CleanStreamResult.Success =
        CleanStreamResult.Success(playlistContent = url, variants = emptyList(), strategyUsed = strategy)
}

data class AdBlockConfig(
    val strategy: AdBlockStrategy = AdBlockStrategy.PROXY_PRIMARY,
    /**
     * Optional TTV-LOL-PRO-compatible upstream proxy endpoint. Empty by default
     * as of 2026 because every well-known public endpoint
     * (`api.ttv.lol`, `*.luminous.dev`, `lb-*.cdn-perfprod.com`) is either dead,
     * domain-repurposed, Cloudflare-gated, or migrated to a non-HTTP-playlist
     * contract (TTV-LOL-PRO v2 switched to a SOCKS/HTTP-CONNECT proxy).
     *
     * Either run the bundled `proxy-server/` Go binary and point this at it
     * (typically `http://127.0.0.1:8080/playlist`), or rely on the in-process
     * variant-playlist interception in `LocalStreamProxy` (Windows desktop) —
     * which is the path that does the actual ad-segment stripping now.
     */
    val proxyEndpoint: String = "",
    val customProxyEndpoint: String? = null,
    val fallbackToManifestRewrite: Boolean = true,
    val fallbackToBlackFrame: Boolean = true,
)

enum class AdBlockStrategy {
    PROXY_PRIMARY,
    MANIFEST_REWRITE_ONLY,
    DISABLED,
}

/**
 * SECTION 10.3 — drives the on-screen pill: green "AD BLOCKED", yellow
 * "AD FILTERED", red "AD BLOCK OFF". Exposed as a StateFlow so all three
 * UIs (phone, TV, desktop) can render it identically.
 */
enum class AdBlockStatus {
    UNKNOWN,
    AD_BLOCKED,
    AD_FILTERED,
    AD_BLOCK_OFF,
    DISABLED,
}

/**
 * SECTION 09.3 / GOTCHA #2 — tracks proxy reliability so `ProxyHealthWorker`
 * can demote the strategy to manifest-rewrite after 3 consecutive failures
 * (and notify the user), then promote it back once the proxy recovers.
 */
class ProxyHealthTracker(private val unhealthyThreshold: Int = 3) {
    private var consecutiveFailures = 0
    private var consecutiveSuccesses = 0

    fun recordSuccess() {
        consecutiveSuccesses++
        consecutiveFailures = 0
    }

    fun recordFailure() {
        consecutiveFailures++
        consecutiveSuccesses = 0
    }

    fun isHealthy(): Boolean = consecutiveFailures < unhealthyThreshold

    fun shouldNotifyDegradation(): Boolean = consecutiveFailures == unhealthyThreshold
}
