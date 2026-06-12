package com.puretv.twitch.core.adblock

import com.puretv.twitch.core.stream.StreamResolver
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * SECTION 4.1 [CRITICAL — the actual ad remover] — backup player-type swap.
 *
 * Server-side port of the primary technique in pixeltris/TwitchAdSolutions
 * (vaft). Twitch fingerprints the GQL `playerType` for ad targeting: the web
 * player (`site`) gets the most ads, while `embed`/`popout` frequently get an
 * ad-free manifest for the *same* live stream at the *same* instant.
 *
 * So when the primary stream's media playlist contains a stitched ad, instead
 * of stalling through it (the [ManifestRewriter] fallback), we:
 *   1. mint a fresh playback token under a different player type,
 *   2. rebuild the usher master URL and fetch it,
 *   3. pull the SAME quality's media playlist out of that master,
 *   4. and if it's clean (no `stitched` signifier), serve it verbatim.
 * First clean player type wins → seamless, ad-free, no freeze.
 *
 * Only if every backup player type is also showing the ad do we fall back to
 * segment stripping. Master playlists per player type are cached briefly
 * (the token is valid for minutes) so an active ad break doesn't mint a new
 * token on every ~2s playlist refresh.
 */
class BackupStreamResolver(
    private val httpClient: HttpClient,
    private val streamResolver: StreamResolver,
    /**
     * Player types to try, in order. `embed` and `popout` both deliver Source
     * quality and use the default `web` platform, so they need no GQL hash or
     * variable changes. Mirrors vaft's `BackupPlayerTypes` (minus `autoplay`,
     * which needs `platform=android` and only yields 360p).
     */
    private val backupPlayerTypes: List<String> = listOf("embed", "popout"),
    private val cacheTtlMs: Long = 90_000,
) {
    private data class CachedMaster(val master: String, val fetchedAtMs: Long)

    // key = "channel|playerType"
    private val masterCache = HashMap<String, CachedMaster>()
    private val cacheLock = Mutex()

    /**
     * Returns an ad-free media playlist for [channelLogin] at the requested
     * quality, or null if no backup player type produced a clean one (caller
     * should then fall back to [ManifestRewriter]).
     *
     * [nowMs] is supplied by the caller (e.g. `System.currentTimeMillis()` on
     * the JVM) to keep this class in `commonMain` without a platform clock.
     */
    suspend fun resolveAdFreeMediaPlaylist(
        channelLogin: String,
        targetResolution: String,
        targetFrameRate: Double,
        nowMs: Long,
        oauthToken: String? = null,
    ): BackupResult? {
        for (playerType in backupPlayerTypes) {
            val master = cachedOrFetchMaster(channelLogin, playerType, oauthToken, nowMs) ?: continue
            val variantUrl = streamResolver.selectVariantUrl(master, targetResolution, targetFrameRate) ?: continue

            val media = runCatching { httpClient.get(variantUrl).bodyAsText() }.getOrNull()
            if (media == null) {
                // The cached master/token is probably stale (stream restarted or
                // token expired) — drop it so the next attempt re-mints.
                invalidate(channelLogin, playerType)
                continue
            }
            if (!AdMarkers.containsAds(media)) {
                return BackupResult(playerType = playerType, mediaPlaylist = media)
            }
        }
        return null
    }

    private suspend fun cachedOrFetchMaster(
        channelLogin: String,
        playerType: String,
        oauthToken: String?,
        nowMs: Long,
    ): String? {
        val key = "$channelLogin|$playerType"
        cacheLock.withLock {
            masterCache[key]?.let { if (nowMs - it.fetchedAtMs < cacheTtlMs) return it.master }
        }
        val master = runCatching {
            streamResolver.resolveMasterPlaylist(channelLogin, oauthToken, playerType).rawContent
        }.getOrNull() ?: return null
        cacheLock.withLock { masterCache[key] = CachedMaster(master, nowMs) }
        return master
    }

    private suspend fun invalidate(channelLogin: String, playerType: String) {
        cacheLock.withLock { masterCache.remove("$channelLogin|$playerType") }
    }

    data class BackupResult(val playerType: String, val mediaPlaylist: String)
}
