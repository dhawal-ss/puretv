package com.puretv.twitch.core.stream

import com.puretv.twitch.core.api.TwitchConfig
import com.puretv.twitch.core.model.StreamToken
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * SECTION 03.4 [CRITICAL] — Helix does not expose a playable stream URL.
 * The only way to obtain one is the undocumented GraphQL `PlaybackAccessToken`
 * persisted query, which returns a signed token that authorizes a single
 * usher.ttvnw.net master-playlist fetch.
 *
 * GOTCHA #1 (Section 14): Twitch occasionally rotates [TwitchConfig.PLAYBACK_ACCESS_TOKEN_HASH].
 * If this starts returning 400/403, refresh the hash from Twitch's web client bundle —
 * see [GqlHashProvider] for the dynamic-lookup strategy this client falls back to.
 */
class TwitchGqlClient(
    private val httpClient: HttpClient,
    private val hashProvider: GqlHashProvider = GqlHashProvider(),
    // encodeDefaults = true is LOAD-BEARING: Twitch's GraphQL schema requires
    // isLive/vodID/isVod/playerType as non-null types. With the kotlinx-serialization
    // default of false, our PlaybackAccessTokenVariables data class's default
    // values are stripped from the request body — Twitch then sees them as
    // explicit nulls and rejects the query with "Expected type X!, found null".
    private val json: Json = Json { ignoreUnknownKeys = true; encodeDefaults = true },
) {

    /**
     * @param playerType the GQL `playerType` Twitch fingerprints for ad targeting.
     *   `"site"` (the web player) gets the most ads; `"popout"`/`"embed"` get far
     *   fewer or none. The ad-block backup-swap (see `BackupStreamResolver`) calls
     *   this repeatedly with different player types to find an ad-free manifest —
     *   this is the single most effective lever for actually removing Twitch ads.
     */
    suspend fun fetchStreamToken(
        channelLogin: String,
        oauthToken: String?,
        playerType: String = "site",
    ): StreamToken {
        val hash = hashProvider.currentHash()
        val response = postPersistedQuery(
            operationName = "PlaybackAccessToken",
            hash = hash,
            variables = PlaybackAccessTokenVariables(login = channelLogin, playerType = playerType),
            oauthToken = oauthToken,
        )

        val payload = json.decodeFromString<GqlEnvelope<PlaybackAccessTokenData>>(response)
        val token = payload.data?.streamPlaybackAccessToken
            ?: run {
                // Hash likely stale — record the failure so GqlHashProvider can
                // attempt a dynamic refresh on the next call. Surface the raw
                // GQL body so the real cause (error message, integrity check,
                // banned channel, etc.) is visible instead of a generic blame.
                hashProvider.reportFailure(hash)
                val truncated = if (response.length > 600) response.substring(0, 600) + "…" else response
                throw GqlPlaybackTokenException(
                    "PlaybackAccessToken returned no token. Hash=$hash. GQL response: $truncated",
                )
            }

        return StreamToken(value = token.value, signature = token.signature)
    }

    /** Fetch a playback token for a VOD instead of a live channel. */
    suspend fun fetchVodToken(vodId: String, oauthToken: String?): StreamToken {
        val hash = hashProvider.currentHash()
        val response = postPersistedQuery(
            operationName = "PlaybackAccessToken",
            hash = hash,
            variables = PlaybackAccessTokenVariables(login = "", isLive = false, isVod = true, vodID = vodId),
            oauthToken = oauthToken,
        )
        val payload = json.decodeFromString<GqlEnvelope<PlaybackAccessTokenData>>(response)
        val token = payload.data?.streamPlaybackAccessToken
            ?: throw GqlPlaybackTokenException("VOD PlaybackAccessToken returned no token")
        return StreamToken(value = token.value, signature = token.signature)
    }

    private suspend fun postPersistedQuery(
        operationName: String,
        hash: String,
        variables: PlaybackAccessTokenVariables,
        oauthToken: String?,
    ): String {
        val body = GqlPersistedQueryRequest(
            operationName = operationName,
            variables = variables,
            extensions = GqlExtensions(persistedQuery = PersistedQuery(version = 1, sha256Hash = hash)),
        )
        return httpClient.post(TwitchConfig.GQL_ENDPOINT) {
            header("Client-ID", TwitchConfig.GQL_CLIENT_ID)
            oauthToken?.let { header("Authorization", "OAuth $it") }
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(body))
        }.body()
    }
}

class GqlPlaybackTokenException(message: String) : Exception(message)

// ---- Wire format for the persisted-query POST body ----

@Serializable
data class GqlPersistedQueryRequest(
    val operationName: String,
    val variables: PlaybackAccessTokenVariables,
    val extensions: GqlExtensions,
)

@Serializable
data class PlaybackAccessTokenVariables(
    val isLive: Boolean = true,
    val login: String,
    val isVod: Boolean = false,
    val vodID: String = "",
    val playerType: String = "site",
)

@Serializable
data class GqlExtensions(val persistedQuery: PersistedQuery)

@Serializable
data class PersistedQuery(val version: Int, val sha256Hash: String)

// ---- Response envelope ----

@Serializable
data class GqlEnvelope<T>(val data: T? = null)

@Serializable
data class PlaybackAccessTokenData(
    @SerialName("streamPlaybackAccessToken") val streamPlaybackAccessToken: PlaybackAccessTokenValue? = null,
)

@Serializable
data class PlaybackAccessTokenValue(
    val value: String,
    val signature: String,
)

/**
 * Holds the persisted-query hash and provides a refresh hook for GOTCHA #1.
 *
 * In production this should periodically scrape Twitch's web client JS bundle
 * for the current `PlaybackAccessToken` sha256Hash (it's a stable string literal
 * in the minified bundle) and cache the result, falling back to the pinned
 * constant when scraping fails or is disabled by the user.
 */
class GqlHashProvider(
    private var cachedHash: String = TwitchConfig.PLAYBACK_ACCESS_TOKEN_HASH,
    private val onFailureRefresh: (suspend () -> String?)? = null,
) {
    private var consecutiveFailures = 0

    fun currentHash(): String = cachedHash

    fun reportFailure(failedHash: String) {
        if (failedHash == cachedHash) consecutiveFailures++
    }

    /** Call from a background worker if [reportFailure] indicates the pinned hash is stale. */
    suspend fun tryRefresh(): Boolean {
        val refreshed = onFailureRefresh?.invoke() ?: return false
        if (refreshed.isNotBlank() && refreshed != cachedHash) {
            cachedHash = refreshed
            consecutiveFailures = 0
            return true
        }
        return false
    }

    fun shouldAttemptRefresh(): Boolean = consecutiveFailures >= 1
}
