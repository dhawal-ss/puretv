package com.puretv.twitch.core.stream

import com.puretv.twitch.core.api.TwitchConfig
import com.puretv.twitch.core.model.PlaylistVariant
import com.puretv.twitch.core.model.StreamQuality
import com.puretv.twitch.core.model.StreamToken
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.encodeURLParameter
import kotlin.random.Random

/**
 * Resolves a Twitch channel login into a playable HLS master-playlist URL,
 * then parses that master playlist into the user-selectable [PlaylistVariant]s
 * described in SECTION 03.4 (Source, 1080p60, 720p60, 480p, 360p, Audio Only).
 */
class StreamResolver(
    private val httpClient: HttpClient,
    private val gqlClient: TwitchGqlClient,
) {

    suspend fun fetchToken(
        channelLogin: String,
        oauthToken: String? = null,
        playerType: String = "site",
    ): StreamToken = gqlClient.fetchStreamToken(channelLogin, oauthToken, playerType)

    /**
     * Builds the signed usher.ttvnw.net master-playlist URL.
     *
     * GOTCHA #5 (Section 14): the signature/token query params are part of what
     * Twitch validates — never re-encode or reorder them once minted. Anything
     * downstream (proxy, ad-block engine) must treat this URL as opaque and only
     * rewrite the *body* of the playlist it returns, not the request URL.
     */
    fun buildMasterUrl(channelLogin: String, token: StreamToken): String {
        val p = Random.nextInt(100_000, 999_999)
        val encodedToken = token.value.encodeURLParameter()
        return "${TwitchConfig.USHER_BASE}/$channelLogin.m3u8" +
            "?sig=${token.signature}" +
            "&token=$encodedToken" +
            "&allow_source=true" +
            "&allow_spectre=false" +
            "&fast_bread=true" +
            "&p=$p" +
            "&player_backend=mediaplayer" +
            "&playlist_include_framerate=true" +
            "&reassignments_supported=true" +
            "&supported_codecs=avc1" +
            "&transcode_mode=cbr_v1"
    }

    /** One-shot: token -> master URL -> raw master playlist text. */
    suspend fun resolveMasterPlaylist(
        channelLogin: String,
        oauthToken: String? = null,
        playerType: String = "site",
    ): MasterPlaylistResult {
        val token = fetchToken(channelLogin, oauthToken, playerType)
        val url = buildMasterUrl(channelLogin, token)
        val raw = httpClient.get(url).bodyAsText()
        return MasterPlaylistResult(masterUrl = url, rawContent = raw, variants = parseVariants(raw))
    }

    /** @see HlsMasterParser.selectVariantUrl */
    fun selectVariantUrl(
        masterPlaylist: String,
        targetResolution: String,
        targetFrameRate: Double,
    ): String? = HlsMasterParser.selectVariantUrl(masterPlaylist, targetResolution, targetFrameRate)

    /** @see HlsMasterParser.parseVariants */
    fun parseVariants(masterPlaylist: String): List<PlaylistVariant> =
        HlsMasterParser.parseVariants(masterPlaylist)
}

/**
 * Pure HLS master-playlist parsing — no network, no collaborators — so the
 * quality-ladder parsing and backup-swap variant matching can be unit-tested
 * directly. [StreamResolver] delegates here.
 */
object HlsMasterParser {

    /**
     * Parses an HLS master playlist (#EXT-X-STREAM-INF / URL pairs) into the
     * quality ladder the UI lets the user pick from.
     */
    fun parseVariants(masterPlaylist: String): List<PlaylistVariant> {
        val variants = mutableListOf<PlaylistVariant>()
        val lines = masterPlaylist.lines()
        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            if (line.startsWith("#EXT-X-STREAM-INF")) {
                val attrs = parseAttributeList(line.substringAfter(":"))
                val url = lines.getOrNull(i + 1)?.trim().orEmpty()
                if (url.isNotEmpty() && !url.startsWith("#")) {
                    val resolution = attrs["RESOLUTION"] ?: ""
                    val frameRate = attrs["FRAME-RATE"]?.toDoubleOrNull() ?: 0.0
                    val bandwidth = attrs["BANDWIDTH"]?.toLongOrNull() ?: 0L
                    val nameHint = attrs["VIDEO"] ?: resolution
                    variants += PlaylistVariant(
                        quality = StreamQuality.fromVariantName(nameHint),
                        resolution = resolution,
                        frameRate = frameRate,
                        bandwidth = bandwidth,
                        url = url,
                    )
                    i++
                }
            }
            i++
        }
        return variants.sortedBy { it.quality.sortOrder }
    }

    /**
     * Picks the media-playlist URL from [masterPlaylist] that best matches a
     * target quality, so the ad-block backup-swap can fetch the *same* quality
     * from an alternate player type's manifest. Server-side port of vaft's
     * `getStreamUrlForResolution`:
     *   1. exact resolution + frame rate
     *   2. exact resolution, any frame rate
     *   3. closest by pixel count
     *
     * [targetResolution] is the `WIDTHxHEIGHT` string from the primary stream's
     * `#EXT-X-STREAM-INF`. Returns null only if the master has no variants.
     */
    fun selectVariantUrl(
        masterPlaylist: String,
        targetResolution: String,
        targetFrameRate: Double,
    ): String? {
        val variants = parseVariants(masterPlaylist)
        if (variants.isEmpty()) return null

        variants.firstOrNull {
            it.resolution == targetResolution && frameRatesMatch(it.frameRate, targetFrameRate)
        }?.let { return it.url }

        variants.firstOrNull { it.resolution == targetResolution }?.let { return it.url }

        val targetPixels = pixelCount(targetResolution)
        return variants.minByOrNull { kotlin.math.abs(pixelCount(it.resolution) - targetPixels) }?.url
    }

    private fun frameRatesMatch(a: Double, b: Double): Boolean = kotlin.math.abs(a - b) < 1.0

    private fun pixelCount(resolution: String): Long {
        val parts = resolution.split('x')
        if (parts.size != 2) return 0
        val w = parts[0].trim().toLongOrNull() ?: return 0
        val h = parts[1].trim().toLongOrNull() ?: return 0
        return w * h
    }

    private fun parseAttributeList(raw: String): Map<String, String> {
        val map = mutableMapOf<String, String>()
        var i = 0
        var key = StringBuilder()
        var value = StringBuilder()
        var inQuotes = false
        var readingKey = true
        fun flush() {
            if (key.isNotEmpty()) map[key.toString().trim()] = value.toString().trim('"')
            key = StringBuilder(); value = StringBuilder(); readingKey = true
        }
        while (i < raw.length) {
            val c = raw[i]
            when {
                c == '"' -> inQuotes = !inQuotes
                c == '=' && readingKey && !inQuotes -> readingKey = false
                c == ',' && !inQuotes -> flush()
                readingKey -> key.append(c)
                else -> value.append(c)
            }
            i++
        }
        flush()
        return map
    }
}

data class MasterPlaylistResult(
    val masterUrl: String,
    val rawContent: String,
    val variants: List<PlaylistVariant>,
)
