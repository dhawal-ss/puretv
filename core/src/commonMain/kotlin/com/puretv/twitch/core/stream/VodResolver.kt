package com.puretv.twitch.core.stream

import com.puretv.twitch.core.api.TwitchConfig
import com.puretv.twitch.core.model.StreamQuality
import com.puretv.twitch.core.model.StreamToken
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.encodeURLParameter
import kotlin.random.Random

/**
 * VOD counterpart to [StreamResolver]. Reuses [TwitchGqlClient.fetchVodToken]
 * and the pure [HlsMasterParser]; the only VOD-specific piece is the usher path
 * (`/vod/{id}.m3u8`, vs live's `/api/channel/hls/{login}.m3u8`).
 *
 * Pure url helpers live in the companion so they unit-test without an HttpClient.
 */
class VodResolver(
    private val httpClient: HttpClient,
    private val gqlClient: TwitchGqlClient,
) {
    suspend fun fetchToken(vodId: String, oauthToken: String? = null): StreamToken =
        gqlClient.fetchVodToken(vodId, oauthToken)

    suspend fun resolveVodMasterPlaylist(vodId: String, oauthToken: String? = null): MasterPlaylistResult {
        val token = fetchToken(vodId, oauthToken)
        val url = buildVodMasterUrl(vodId, token)
        val raw = httpClient.get(url).bodyAsText()
        return MasterPlaylistResult(masterUrl = url, rawContent = raw, variants = HlsMasterParser.parseVariants(raw))
    }

    /** Convenience: resolve then pick the URL to actually hand the player. */
    suspend fun resolvePlayableUrl(vodId: String, quality: StreamQuality, oauthToken: String? = null): String =
        playableUrlFor(resolveVodMasterPlaylist(vodId, oauthToken), quality)

    /** Load + parse a VOD's storyboard, or null if it has none / fails. */
    suspend fun loadStoryboard(vodId: String): Storyboard? {
        val url = gqlClient.fetchSeekPreviewsUrl(vodId) ?: return null
        val jsonText = httpClient.get(url).bodyAsText()
        return runCatching { StoryboardParser.parse(url, jsonText) }.getOrNull()
    }

    companion object {
        fun buildVodMasterUrl(vodId: String, token: StreamToken): String {
            val p = Random.nextInt(100_000, 999_999)
            val encodedToken = token.value.encodeURLParameter()
            return "${TwitchConfig.USHER_VOD_BASE}/$vodId.m3u8" +
                "?sig=${token.signature}" +
                "&token=$encodedToken" +
                "&allow_source=true" +
                "&allow_audio_only=true" +
                "&p=$p" +
                "&player=twitchweb" +
                "&playlist_include_framerate=true" +
                "&supported_codecs=avc1"
        }

        /**
         * The URL to hand the player. AUTO → the usher master (VLC adapts);
         * a fixed quality → that variant's media-playlist URL, falling back to
         * the master if the quality isn't present in this VOD's ladder.
         */
        fun playableUrlFor(master: MasterPlaylistResult, quality: StreamQuality): String {
            if (quality == StreamQuality.AUTO) return master.masterUrl
            return master.variants.firstOrNull { it.quality == quality }?.url ?: master.masterUrl
        }
    }
}
