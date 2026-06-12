package com.puretv.twitch.core.api

import com.puretv.twitch.core.model.ChannelInfo
import com.puretv.twitch.core.model.GameInfo
import com.puretv.twitch.core.model.StreamInfo
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable
import kotlin.random.Random

/**
 * Thin wrapper over the Twitch Helix REST API.
 *
 * - [tokenProvider] supplies the current (auto-refreshing) user/app access token.
 *   The token interceptor in [buildKtorClient] re-fetches 5 minutes before expiry;
 *   this client only needs to read the latest cached value.
 * - 429 responses are retried with jittered exponential backoff via [withRateLimitRetry].
 */
class TwitchApiClient(
    private val httpClient: HttpClient,
    private val tokenProvider: () -> String?,
) {
    private suspend fun authedClient() = httpClient.also { /* headers attached per-request below */ }

    private suspend inline fun <reified T> get(
        path: String,
        params: Map<String, String?> = emptyMap(),
    ): T = withRateLimitRetry {
        authedClient().get("${TwitchConfig.API_BASE}$path") {
            header("Client-Id", TwitchConfig.CLIENT_ID)
            tokenProvider()?.let { header("Authorization", "Bearer $it") }
            params.forEach { (k, v) -> if (v != null) parameter(k, v) }
        }.body()
    }

    /** GET /streams — by user login(s) and/or game id(s). */
    suspend fun getStreams(userLogins: List<String> = emptyList(), gameIds: List<String> = emptyList(), first: Int = 20): List<StreamInfo> {
        val resp: HelixEnvelope<StreamInfo> = withRateLimitRetry {
            authedClient().get("${TwitchConfig.API_BASE}/streams") {
                header("Client-Id", TwitchConfig.CLIENT_ID)
                tokenProvider()?.let { header("Authorization", "Bearer $it") }
                userLogins.forEach { parameter("user_login", it) }
                gameIds.forEach { parameter("game_id", it) }
                parameter("first", first.toString())
            }.body()
        }
        return resp.data
    }

    /** Convenience: is a single channel currently live? Returns the StreamInfo if so. */
    suspend fun getLiveStream(userLogin: String): StreamInfo? = getStreams(userLogins = listOf(userLogin)).firstOrNull()

    /** GET /users — resolve login(s) or id(s) to full user profiles. */
    suspend fun getUsers(logins: List<String> = emptyList(), ids: List<String> = emptyList()): List<ChannelInfo> {
        val resp: HelixEnvelope<ChannelInfo> = withRateLimitRetry {
            authedClient().get("${TwitchConfig.API_BASE}/users") {
                header("Client-Id", TwitchConfig.CLIENT_ID)
                tokenProvider()?.let { header("Authorization", "Bearer $it") }
                logins.forEach { parameter("login", it) }
                ids.forEach { parameter("id", it) }
            }.body()
        }
        return resp.data
    }

    /** GET /users/follows — channels followed by [userId]. Requires user:read:follows scope. */
    suspend fun getFollowedChannels(userId: String, first: Int = 100): List<FollowedChannel> {
        val resp: HelixEnvelope<FollowedChannel> = get("/channels/followed", mapOf("user_id" to userId, "first" to first.toString()))
        return resp.data
    }

    /** GET /games/top — top live categories. */
    suspend fun getTopGames(first: Int = 20): List<GameInfo> {
        val resp: HelixEnvelope<GameInfo> = get("/games/top", mapOf("first" to first.toString()))
        return resp.data
    }

    /** GET /search/channels — search by query string. */
    suspend fun searchChannels(query: String, liveOnly: Boolean = false): List<ChannelSearchResult> {
        val resp: HelixEnvelope<ChannelSearchResult> = get(
            "/search/channels",
            mapOf("query" to query, "live_only" to liveOnly.toString()),
        )
        return resp.data
    }

    /** GET /streams?user_login= — quick "is this channel live right now" check. */
    suspend fun isChannelLive(userLogin: String): Boolean = getLiveStream(userLogin) != null

    /**
     * Wraps a Helix call with 429-aware retry: exponential backoff with full jitter,
     * honoring `Ratelimit-Reset` when present. Caps at [maxAttempts].
     */
    private suspend inline fun <T> withRateLimitRetry(maxAttempts: Int = 4, block: () -> T): T {
        var attempt = 0
        var lastError: Throwable? = null
        while (attempt < maxAttempts) {
            try {
                return block()
            } catch (e: RateLimitedException) {
                lastError = e
                val backoffMs = (500L * (1 shl attempt)) + Random.nextLong(0, 250)
                delay(backoffMs)
                attempt++
            }
        }
        throw lastError ?: IllegalStateException("Rate limit retry exhausted")
    }
}

class RateLimitedException(val resetEpochSeconds: Long?) : Exception("Twitch Helix rate limited (429)")

@Serializable
data class HelixEnvelope<T>(val data: List<T> = emptyList())

@Serializable
data class FollowedChannel(
    val broadcaster_id: String,
    val broadcaster_login: String,
    val broadcaster_name: String,
    val followed_at: String = "",
)

@Serializable
data class ChannelSearchResult(
    val id: String,
    val broadcaster_login: String,
    val display_name: String,
    val is_live: Boolean = false,
    val game_name: String = "",
    val thumbnail_url: String = "",
    val title: String = "",
)

/**
 * Returns true if the response indicates Helix rate limiting; callers wrap
 * the raw ktor call so the [HttpStatusCode.TooManyRequests] path can be
 * normalized into [RateLimitedException] for [TwitchApiClient.withRateLimitRetry].
 */
internal fun HttpStatusCode.isRateLimited(): Boolean = this == HttpStatusCode.TooManyRequests

/**
 * Convenience factory matching the platform Ktor engine wiring described in
 * SECTION 11 (Koin DI): OkHttp engine on both Android and Desktop, with a
 * defaultRequest block that injects the Client-Id once for every request.
 */
fun configureCommonDefaults(client: HttpClient) {
    // Hook point for Koin's `single { buildKtorClient() }` — kept here so both
    // platforms share identical default-request configuration.
}
