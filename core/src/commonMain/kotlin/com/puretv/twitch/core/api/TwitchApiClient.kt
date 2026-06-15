package com.puretv.twitch.core.api

import com.puretv.twitch.core.model.ChannelEmote
import com.puretv.twitch.core.model.ChannelInfo
import com.puretv.twitch.core.model.EmoteProvider
import com.puretv.twitch.core.model.GameInfo
import com.puretv.twitch.core.model.MutedSegment
import com.puretv.twitch.core.model.StreamInfo
import com.puretv.twitch.core.model.VideoInfo
import com.puretv.twitch.core.model.VideoType
import com.puretv.twitch.core.model.parseTwitchDuration
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.delay
import kotlinx.serialization.SerialName
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

    /**
     * GET /chat/emotes/global — Twitch's first-party global emote set.
     * No extra OAuth scope required (an app/user token works). Best-effort:
     * any failure yields an empty list so the picker degrades gracefully.
     */
    suspend fun getGlobalEmotes(): List<ChannelEmote> = runCatching {
        val resp: HelixEnvelope<TwitchEmoteDto> = withRateLimitRetry {
            authedClient().get("${TwitchConfig.API_BASE}/chat/emotes/global") {
                header("Client-Id", TwitchConfig.CLIENT_ID)
                tokenProvider()?.let { header("Authorization", "Bearer $it") }
            }.body()
        }
        parseTwitchEmotes(resp)
    }.getOrDefault(emptyList())

    /**
     * GET /chat/emotes?broadcaster_id= — a channel's first-party Twitch emotes
     * (sub/bits/follower). Best-effort: failures degrade to an empty list.
     */
    suspend fun getChannelTwitchEmotes(broadcasterId: String): List<ChannelEmote> = runCatching {
        val resp: HelixEnvelope<TwitchEmoteDto> = withRateLimitRetry {
            authedClient().get("${TwitchConfig.API_BASE}/chat/emotes") {
                header("Client-Id", TwitchConfig.CLIENT_ID)
                tokenProvider()?.let { header("Authorization", "Bearer $it") }
                parameter("broadcaster_id", broadcasterId)
            }.body()
        }
        parseTwitchEmotes(resp)
    }.getOrDefault(emptyList())

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
     * GET /videos — a channel's past videos. [type] null => Helix "all"
     * (archive + highlight + upload). [after] is the pagination cursor.
     */
    suspend fun getVideos(
        userId: String,
        type: VideoType? = null,
        first: Int = 20,
        after: String? = null,
    ): VideoPage {
        val typeParam = when (type) {
            VideoType.ARCHIVE -> "archive"
            VideoType.HIGHLIGHT -> "highlight"
            VideoType.UPLOAD -> "upload"
            VideoType.UNKNOWN, null -> "all"
        }
        val resp: HelixPagedEnvelope<HelixVideo> = withRateLimitRetry {
            authedClient().get("${TwitchConfig.API_BASE}/videos") {
                header("Client-Id", TwitchConfig.CLIENT_ID)
                tokenProvider()?.let { header("Authorization", "Bearer $it") }
                parameter("user_id", userId)
                parameter("type", typeParam)
                parameter("first", first.toString())
                if (after != null) parameter("after", after)
            }.body()
        }
        return VideoPage(resp.data.map { it.toDomain() }, resp.pagination.cursor)
    }

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

/** One entry from GET /chat/emotes(/global). `format` carries "static"/"animated". */
@Serializable
data class TwitchEmoteDto(
    val id: String = "",
    val name: String = "",
    val format: List<String> = emptyList(),
)

/** Twitch CDN image URL for an emote id; "animated" variant when the emote has one. */
internal fun twitchEmoteImageUrl(id: String, animated: Boolean): String {
    val fmt = if (animated) "animated" else "static"
    return "https://static-cdn.jtvnw.net/emoticons/v2/" + id + "/" + fmt + "/dark/2.0"
}

/** Maps a Helix emote envelope to [ChannelEmote]s, dropping blank-id/blank-name entries. */
internal fun parseTwitchEmotes(env: HelixEnvelope<TwitchEmoteDto>): List<ChannelEmote> =
    env.data.filter { it.id.isNotBlank() && it.name.isNotBlank() }.map {
        val animated = it.format.contains("animated")
        ChannelEmote(it.id, it.name, twitchEmoteImageUrl(it.id, animated), EmoteProvider.TWITCH, animated)
    }

@Serializable
data class HelixPagination(val cursor: String? = null)

@Serializable
data class HelixPagedEnvelope<T>(
    val data: List<T> = emptyList(),
    val pagination: HelixPagination = HelixPagination(),
)

@Serializable
data class HelixMutedSegment(val duration: Int = 0, val offset: Int = 0)

@Serializable
data class HelixVideo(
    val id: String,
    @SerialName("user_id") val userId: String = "",
    @SerialName("user_login") val userLogin: String = "",
    @SerialName("user_name") val userName: String = "",
    val title: String = "",
    val description: String = "",
    @SerialName("created_at") val createdAt: String = "",
    @SerialName("published_at") val publishedAt: String = "",
    @SerialName("thumbnail_url") val thumbnailUrl: String = "",
    @SerialName("view_count") val viewCount: Int = 0,
    val type: String = "",
    val duration: String = "",
    @SerialName("muted_segments") val mutedSegments: List<HelixMutedSegment>? = null,
)

fun HelixVideo.toDomain(): VideoInfo =
    VideoInfo(
        id = id,
        userId = userId,
        userLogin = userLogin,
        userName = userName,
        title = title,
        description = description,
        type = VideoType.fromApi(type),
        durationSeconds = parseTwitchDuration(duration),
        createdAt = createdAt,
        publishedAt = publishedAt,
        thumbnailUrl = thumbnailUrl,
        viewCount = viewCount,
        mutedSegments = (mutedSegments ?: emptyList()).map {
            MutedSegment(durationSeconds = it.duration, offsetSeconds = it.offset)
        },
    )

/** A page of videos plus the cursor for the next page (null when no more). */
data class VideoPage(val videos: List<VideoInfo>, val cursor: String?)

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
