package com.puretv.twitch.core.api

import com.puretv.twitch.core.model.ChannelEmote
import com.puretv.twitch.core.model.ChannelInfo
import com.puretv.twitch.core.model.EmoteProvider
import com.puretv.twitch.core.model.GameInfo
import com.puretv.twitch.core.model.MutedSegment
import com.puretv.twitch.core.model.StreamInfo
import com.puretv.twitch.core.model.VideoInfo
import com.puretv.twitch.core.model.VideoType
import com.puretv.twitch.core.stream.TwitchGqlDirectoryClient
import com.puretv.twitch.core.stream.toSizeTemplate
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
 * - [tokenProvider] supplies the latest cached user/app access token (from the
 *   platform's [TokenHolder]). This client does NOT itself refresh — token
 *   refresh is the platform store's responsibility (desktop:
 *   `DesktopSettingsStore.refreshIfNeeded`, invoked at app start). There is no
 *   in-client refresh interceptor.
 * - 429 responses are normalized to [RateLimitedException] by the client's
 *   response validator and retried with jittered exponential backoff via
 *   [withRateLimitRetry].
 */
class TwitchApiClient(
    private val httpClient: HttpClient,
    private val tokenProvider: () -> String?,
) {
    private suspend fun authedClient() = httpClient.also { /* headers attached per-request below */ }

    /**
     * True when a user token is on hand, read fresh on every call because sign
     * in and sign out happen while the app runs. A blank token counts as none:
     * Helix would refuse it exactly as it refuses a missing one.
     */
    fun hasUserToken(): Boolean = !tokenProvider().isNullOrBlank()

    /**
     * Where Twitch's own chat emotes and badges come from while nobody is signed in:
     * Helix refuses those reads without a user token, anonymous GQL does not. Set once by
     * the DI graph; null (tests, tools) keeps Helix as the only source. Signed in, Helix
     * stays the source, so this adds no calls for a signed-in viewer.
     */
    @Volatile var anonymousChat: TwitchGqlDirectoryClient? = null

    private fun signedOutChat(): TwitchGqlDirectoryClient? = anonymousChat?.takeIf { !hasUserToken() }

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

    /**
     * GET /streams/followed: every live stream [userId] follows, following the
     * pagination cursor. One call usually covers it, where checking each follow
     * costs one /streams call per 100 follows. Uses the user:read:follows scope
     * PureTV already asks for, so no one has to sign in again.
     */
    suspend fun getFollowedStreams(userId: String): List<StreamInfo> {
        val all = mutableListOf<StreamInfo>()
        var cursor: String? = null
        do {
            val resp: HelixPagedEnvelope<StreamInfo> = withRateLimitRetry {
                authedClient().get("${TwitchConfig.API_BASE}/streams/followed") {
                    header("Client-Id", TwitchConfig.CLIENT_ID)
                    tokenProvider()?.let { header("Authorization", "Bearer $it") }
                    parameter("user_id", userId)
                    parameter("first", "100")
                    if (cursor != null) parameter("after", cursor)
                }.body()
            }
            all += resp.data
            cursor = resp.pagination.cursor?.takeIf { it.isNotBlank() && resp.data.isNotEmpty() }
        } while (cursor != null && all.size < MAX_FOLLOWED_STREAMS)
        return all
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
    suspend fun getGlobalEmotes(): List<ChannelEmote> {
        // Twitch's global set rarely changes and every stream open asks for it,
        // so the first real answer is kept for the session.
        globalEmotesCache?.let { return it }
        val anonymous = signedOutChat()
        val fresh = if (anonymous != null) {
            runCatching { anonymous.globalEmotes() }.getOrDefault(emptyList())
        } else {
            runCatching {
                val resp: HelixEnvelope<TwitchEmoteDto> = withRateLimitRetry {
                    authedClient().get("${TwitchConfig.API_BASE}/chat/emotes/global") {
                        header("Client-Id", TwitchConfig.CLIENT_ID)
                        tokenProvider()?.let { header("Authorization", "Bearer $it") }
                    }.body()
                }
                parseTwitchEmotes(resp)
            }.getOrDefault(emptyList())
        }
        if (fresh.isNotEmpty()) globalEmotesCache = fresh
        return fresh
    }

    @Volatile private var globalEmotesCache: List<ChannelEmote>? = null

    /**
     * GET /chat/emotes?broadcaster_id= — a channel's first-party Twitch emotes
     * (sub/bits/follower). Best-effort: failures degrade to an empty list.
     */
    suspend fun getChannelTwitchEmotes(broadcasterId: String): List<ChannelEmote> {
        signedOutChat()?.let { anonymous ->
            return runCatching { anonymous.channelEmotes(broadcasterId) }.getOrDefault(emptyList())
        }
        return helixChannelTwitchEmotes(broadcasterId)
    }

    private suspend fun helixChannelTwitchEmotes(broadcasterId: String): List<ChannelEmote> = runCatching {
        val resp: HelixEnvelope<TwitchEmoteDto> = withRateLimitRetry {
            authedClient().get("${TwitchConfig.API_BASE}/chat/emotes") {
                header("Client-Id", TwitchConfig.CLIENT_ID)
                tokenProvider()?.let { header("Authorization", "Bearer $it") }
                parameter("broadcaster_id", broadcasterId)
            }.body()
        }
        parseTwitchEmotes(resp)
    }.getOrDefault(emptyList())

    /**
     * GET /chat/badges/global — Twitch's first-party global chat badges
     * (admin, staff, turbo, global mod, …). Best-effort: failures degrade to empty.
     */
    suspend fun getGlobalChatBadges(): List<ChatBadgeSetDto> {
        signedOutChat()?.let { anonymous ->
            return runCatching { anonymous.globalBadgeSets() }.getOrDefault(emptyList())
        }
        return helixGlobalChatBadges()
    }

    private suspend fun helixGlobalChatBadges(): List<ChatBadgeSetDto> = runCatching {
        val resp: HelixEnvelope<ChatBadgeSetDto> = get("/chat/badges/global")
        resp.data
    }.getOrDefault(emptyList())

    /**
     * GET /chat/badges?broadcaster_id= — a channel's own badge sets (subscriber
     * tiers, bits, founder, …). Channel sets override global on a set-id collision.
     * Best-effort: failures degrade to an empty list.
     */
    suspend fun getChannelChatBadges(broadcasterId: String): List<ChatBadgeSetDto> {
        signedOutChat()?.let { anonymous ->
            return runCatching { anonymous.channelBadgeSets(broadcasterId) }.getOrDefault(emptyList())
        }
        return helixChannelChatBadges(broadcasterId)
    }

    private suspend fun helixChannelChatBadges(broadcasterId: String): List<ChatBadgeSetDto> = runCatching {
        val resp: HelixEnvelope<ChatBadgeSetDto> = get("/chat/badges", mapOf("broadcaster_id" to broadcasterId))
        resp.data
    }.getOrDefault(emptyList())

    /**
     * GET /channels/followed — ALL channels followed by [userId], following the
     * Helix pagination cursor until exhausted. Requires user:read:follows scope.
     */
    suspend fun getAllFollowedChannels(userId: String): List<FollowedChannel> {
        val all = mutableListOf<FollowedChannel>()
        var cursor: String? = null
        do {
            val resp: HelixPagedEnvelope<FollowedChannel> = withRateLimitRetry {
                authedClient().get("${TwitchConfig.API_BASE}/channels/followed") {
                    header("Client-Id", TwitchConfig.CLIENT_ID)
                    tokenProvider()?.let { header("Authorization", "Bearer $it") }
                    parameter("user_id", userId)
                    parameter("first", "100")
                    if (cursor != null) parameter("after", cursor)
                }.body()
            }
            all += resp.data
            cursor = resp.pagination.cursor
        } while (!cursor.isNullOrBlank())
        return all
    }

    /** GET /games/top — top live categories. */
    suspend fun getTopGames(first: Int = 20): List<GameInfo> {
        val resp: HelixEnvelope<GameInfo> = get("/games/top", mapOf("first" to first.toString()))
        return resp.data
    }

    /**
     * GET /games/top, one page of [first] (Helix allows up to 100) starting at the
     * [after] cursor. The returned cursor is null on the last page.
     */
    suspend fun getTopGamesPage(first: Int = 100, after: String? = null): GamesPage {
        val resp: HelixPagedEnvelope<GameInfo> = withRateLimitRetry {
            authedClient().get("${TwitchConfig.API_BASE}/games/top") {
                header("Client-Id", TwitchConfig.CLIENT_ID)
                tokenProvider()?.let { header("Authorization", "Bearer $it") }
                parameter("first", first.coerceIn(1, 100).toString())
                if (after != null) parameter("after", after)
            }.body()
        }
        return GamesPage(resp.data, resp.pagination.cursor?.takeIf { it.isNotBlank() })
    }

    /**
     * GET /search/categories. Helix hands back fixed-size box art (`-52x72.jpg`),
     * so it is turned back into the `{width}x{height}` template the tiles fill.
     */
    suspend fun searchCategories(query: String, first: Int = 20): List<GameInfo> {
        val resp: HelixEnvelope<GameInfo> = get(
            "/search/categories",
            mapOf("query" to query, "first" to first.coerceIn(1, 100).toString()),
        )
        return resp.data.map { it.copy(boxArtUrl = toSizeTemplate(it.boxArtUrl)) }
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

    /** GET /videos?id=: one past video by its id, or null when Helix has no such video. */
    suspend fun getVideoById(videoId: String): VideoInfo? {
        val resp: HelixPagedEnvelope<HelixVideo> = withRateLimitRetry {
            authedClient().get("${TwitchConfig.API_BASE}/videos") {
                header("Client-Id", TwitchConfig.CLIENT_ID)
                tokenProvider()?.let { header("Authorization", "Bearer $it") }
                parameter("id", videoId)
            }.body()
        }
        return resp.data.firstOrNull()?.toDomain()
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

/**
 * Thrown by the shared HttpClient's response validator (see `buildKtorClient`)
 * when a Helix (api.twitch.tv/helix) call returns a non-2xx status OTHER than 429.
 *
 * Audit H1: without this, a 401/403/500 JSON error envelope deserialized cleanly
 * into an empty [HelixEnvelope] (`data` defaults to `[]`), so calls silently
 * returned no data and — critically — cursor-paginated loops truncated mid-stream
 * as if they were complete. This surfaces those failures loudly instead.
 *
 * Distinct from [RateLimitedException] (429): [withRateLimitRetry] retries that
 * one with backoff, but lets a [HelixApiException] propagate so callers that
 * `runCatching`-degrade still see "empty", while pagination can no longer be
 * silently cut short.
 */
class HelixApiException(
    val statusCode: Int,
    val statusDescription: String,
) : Exception("Twitch Helix request failed: HTTP $statusCode $statusDescription")

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

/** One version (tier) of a chat badge set, with CDN image URLs at 1x/2x/4x. */
@Serializable
data class ChatBadgeVersionDto(
    val id: String = "",
    @SerialName("image_url_1x") val imageUrl1x: String = "",
    @SerialName("image_url_2x") val imageUrl2x: String = "",
    @SerialName("image_url_4x") val imageUrl4x: String = "",
    val title: String = "",
)

/** A chat badge set (e.g. "subscriber", "moderator") and its versions. */
@Serializable
data class ChatBadgeSetDto(
    @SerialName("set_id") val setId: String = "",
    val versions: List<ChatBadgeVersionDto> = emptyList(),
)

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

/** One page of categories; [cursor] fetches the next one and is null on the last page. */
data class GamesPage(val games: List<GameInfo>, val cursor: String?)

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

/** Safety stop for [TwitchApiClient.getFollowedStreams]'s pagination: no one follows this many live channels. */
private const val MAX_FOLLOWED_STREAMS = 2_000
