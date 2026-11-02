package com.puretv.twitch.core.stream

import com.puretv.twitch.core.api.ChannelSearchResult
import com.puretv.twitch.core.api.ChatBadgeSetDto
import com.puretv.twitch.core.model.ChannelEmote
import com.puretv.twitch.core.api.TwitchConfig
import com.puretv.twitch.core.model.ChannelInfo
import com.puretv.twitch.core.model.GameInfo
import com.puretv.twitch.core.model.StreamInfo
import com.puretv.twitch.core.model.VideoInfo
import com.puretv.twitch.core.model.VideoType
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

/**
 * The public Twitch directory (top streams, categories, search, channel
 * profiles, who is live) read through anonymous GQL, for viewers who have not
 * signed in. Helix refuses every call without a user token, so this is what
 * lets Home, Browse and Search work signed out (Wave 4, Feature 5).
 *
 * Anonymous by design: the web client's [TwitchConfig.GQL_CLIENT_ID] and no
 * Authorization header, the same identity a logged-out twitch.tv tab uses.
 * The repositories only route here when there is no user token; signed in,
 * Helix stays the source, so this adds no calls for a signed-in viewer.
 *
 * Every input travels as a GQL variable, never spliced into the query text,
 * so a search for `"}{` is just a search.
 *
 * One page per list, on purpose: anonymously, every `after` cursor (top
 * streams, a channel's videos) is refused with "failed integrity check"
 * (live capture, 2026-10-20). No cursor is handed out, so nothing can build
 * paging on one that cannot work.
 */
class TwitchGqlDirectoryClient(
    private val httpClient: HttpClient,
    private val json: Json = Json { ignoreUnknownKeys = true },
) {

    /**
     * Live streams across Twitch, most viewers first. One page of at most
     * [TOP_STREAMS_MAX] (GQL refuses more for this list).
     */
    suspend fun topStreams(first: Int = 20): List<StreamInfo> {
        val data: GqlTopStreamsData = execute(
            TOP_STREAMS_QUERY,
            buildJsonObject { put("first", first.coerceIn(1, TOP_STREAMS_MAX)) },
        )
        return data.streams?.edges.orEmpty().mapNotNull { it?.node?.liveStreamInfo() }
    }

    /** Top categories by current viewers, like Helix `/games/top`. */
    suspend fun topGames(first: Int = 20): List<GameInfo> {
        val data: GqlTopGamesData = execute(
            TOP_GAMES_QUERY,
            buildJsonObject { put("first", first.coerceIn(1, MAX_PAGE)) },
        )
        return data.games?.edges.orEmpty().mapNotNull { it?.node?.toGameInfo() }
    }

    /** Live streams in one category, most viewers first. Empty for an unknown id. */
    suspend fun streamsForGame(gameId: String, first: Int = 40): List<StreamInfo> {
        if (gameId.isBlank()) return emptyList()
        val data: GqlGameStreamsData = execute(
            GAME_STREAMS_QUERY,
            buildJsonObject {
                put("id", gameId)
                put("first", first.coerceIn(1, MAX_PAGE))
            },
        )
        return data.game?.streams?.edges.orEmpty().mapNotNull { it?.node?.liveStreamInfo() }
    }

    /**
     * Channel search ranked the way twitch.tv ranks it (`searchFor`), mapped to
     * the Helix `/search/channels` result shape. [liveOnly] filters here,
     * because `searchFor` has no live-only switch.
     */
    suspend fun searchChannels(query: String, liveOnly: Boolean = false, first: Int = 20): List<ChannelSearchResult> {
        if (query.isBlank()) return emptyList()
        val data: GqlSearchData = execute(
            SEARCH_QUERY,
            buildJsonObject {
                put("query", query)
                put("limit", first.coerceIn(1, MAX_PAGE))
            },
        )
        val results = data.searchFor?.channels?.edges.orEmpty().mapNotNull { it?.item?.toSearchResult() }
        return if (liveOnly) results.filter { it.is_live } else results
    }

    /**
     * Category search ranked the way twitch.tv ranks it (`searchFor` on the game
     * index), mapped to the Helix `/search/categories` shape. Works anonymously
     * (live check, 2026-11-02).
     */
    suspend fun searchCategories(query: String, first: Int = 20): List<GameInfo> {
        if (query.isBlank()) return emptyList()
        val data: GqlSearchData = execute(
            SEARCH_CATEGORIES_QUERY,
            buildJsonObject {
                put("query", query)
                put("limit", first.coerceIn(1, MAX_PAGE))
            },
        )
        return data.searchFor?.games?.edges.orEmpty().mapNotNull { it?.item?.toGameInfo() }
    }

    /** A channel's profile, or null when no such login exists. */
    suspend fun user(login: String): ChannelInfo? {
        if (login.isBlank()) return null
        val data: GqlUserData = execute(USER_QUERY, buildJsonObject { put("login", login) })
        return data.user?.toChannelInfo()
    }

    /**
     * A channel's most recent past broadcasts (or highlights, or uploads), newest
     * first, one page of at most [MAX_PAGE]. [type] null means every kind, like
     * Helix "all". Looked up by the numeric user id, as Helix is.
     */
    suspend fun videosForUser(userId: String, type: VideoType? = null, first: Int = 20): List<VideoInfo> {
        if (userId.isBlank()) return emptyList()
        val gqlType = when (type) {
            VideoType.ARCHIVE -> "ARCHIVE"
            VideoType.HIGHLIGHT -> "HIGHLIGHT"
            VideoType.UPLOAD -> "UPLOAD"
            VideoType.UNKNOWN, null -> null
        }
        val data: GqlVideosData = execute(
            VIDEOS_QUERY,
            buildJsonObject {
                put("id", userId)
                put("first", first.coerceIn(1, MAX_PAGE))
                gqlType?.let { put("type", it) }
            },
        )
        return data.user?.videos?.edges.orEmpty().mapNotNull { it?.node?.toVideoInfo(userId) }
    }

    /** One past video by its id (title, channel, kind, art), or null when there is no such video. */
    suspend fun videoById(videoId: String): VideoInfo? {
        if (videoId.isBlank()) return null
        val data: GqlVideoData = execute(VIDEO_QUERY, buildJsonObject { put("id", videoId) })
        return data.video?.toVideoInfo(fallbackUserId = "")
    }

    /**
     * Which of [logins] are live right now, as Helix-shaped streams. Asks in
     * chunks of [MAX_PAGE] side by side; unknown logins come back as null
     * entries and are dropped, offline ones have no stream.
     */
    suspend fun liveStreamsForLogins(logins: List<String>): List<StreamInfo> {
        val wanted = logins.filter { it.isNotBlank() }
        if (wanted.isEmpty()) return emptyList()
        return coroutineScope {
            wanted.chunked(MAX_PAGE)
                .map { chunk ->
                    async {
                        val data: GqlUsersData = execute(
                            USERS_QUERY,
                            buildJsonObject { putJsonArray("logins") { chunk.forEach { add(it) } } },
                        )
                        data.users.mapNotNull { it?.stream?.liveStreamInfo() }
                    }
                }
                .awaitAll()
                .flatten()
        }
    }

    /** Twitch's global chat badges (staff, moderator, turbo, ...) in the Helix set shape. */
    suspend fun globalBadgeSets(): List<ChatBadgeSetDto> {
        val data: GqlGlobalBadgesData = execute(GLOBAL_BADGES_QUERY, buildJsonObject { })
        return data.badges.orEmpty().toBadgeSets()
    }

    /** A channel's own badge sets (subscriber tiers, bits, ...). Empty for an unknown id. */
    suspend fun channelBadgeSets(broadcasterId: String): List<ChatBadgeSetDto> {
        if (broadcasterId.isBlank()) return emptyList()
        val data: GqlChatUserData = execute(CHANNEL_BADGES_QUERY, buildJsonObject { put("id", broadcasterId) })
        return data.user?.broadcastBadges.orEmpty().toBadgeSets()
    }

    /** Twitch's global emotes (emote set 0), for the picker. */
    suspend fun globalEmotes(): List<ChannelEmote> {
        val data: GqlGlobalEmotesData = execute(GLOBAL_EMOTES_QUERY, buildJsonObject { })
        return data.emoteSet?.emotes.orEmpty().toTwitchEmotes()
    }

    /**
     * A channel's subscriber emotes. Bits and follower emotes are not offered
     * anonymously, so this is the sub tiers only, a little less than Helix returns.
     */
    suspend fun channelEmotes(broadcasterId: String): List<ChannelEmote> {
        if (broadcasterId.isBlank()) return emptyList()
        val data: GqlChatUserData = execute(CHANNEL_EMOTES_QUERY, buildJsonObject { put("id", broadcasterId) })
        return data.user?.subscriptionProducts.orEmpty().filterNotNull().flatMap { it.emotes.orEmpty() }.toTwitchEmotes()
    }

    private suspend inline fun <reified T> execute(query: String, variables: JsonObject): T {
        val response = httpClient.post(TwitchConfig.GQL_ENDPOINT) {
            header("Client-ID", TwitchConfig.GQL_CLIENT_ID)
            contentType(ContentType.Application.Json)
            setBody(
                buildJsonObject {
                    put("query", query)
                    put("variables", variables)
                }.toString(),
            )
        }
        // The shared client's response validator only covers Helix, so check here.
        if (!response.status.isSuccess()) {
            throw GqlDirectoryException("Twitch GQL request failed: HTTP ${response.status.value}")
        }
        val parsed = json.decodeFromString<GqlDirectoryResponse<T>>(response.bodyAsText())
        // GQL reports failures as HTTP 200 with `errors`, usually beside a `data`
        // whose field is null ({"streams":null} for an out-of-range page size or a
        // failed integrity check). Any error is a failure, never an empty directory.
        if (parsed.errors.isNotEmpty()) {
            throw GqlDirectoryException(
                "Twitch GQL request failed: " + parsed.errors.joinToString("; ") { it.message }.ifBlank { "unknown error" },
            )
        }
        return parsed.data ?: throw GqlDirectoryException("Twitch GQL returned no data")
    }

    companion object {
        /** Top-level `streams` refuses a page above 30 ("value must be between 1 and 30"). */
        const val TOP_STREAMS_MAX = 30

        /** `games`, `game.streams`, `searchFor`, `users` and `videos` all accept 100 (measured). */
        const val MAX_PAGE = 100
    }
}

/** Helix `/streams` only lists live broadcasts, so drop anything else GQL returns. */
private fun GqlStreamNode.liveStreamInfo(): StreamInfo? =
    if (type.isNullOrBlank() || type.equals("live", ignoreCase = true)) toStreamInfo() else null

// The stream fields every directory query asks for, mapped by GqlStreamNode.toStreamInfo.
private const val STREAM_FIELDS =
    "id title viewersCount createdAt type previewImageURL(width: 440, height: 248) " +
        "broadcaster { id login displayName broadcastSettings { language } } " +
        "game { id name displayName } freeformTags { name }"

internal const val TOP_STREAMS_QUERY =
    "query(\$first: Int) { streams(first: \$first, options: {sort: VIEWER_COUNT}) " +
        "{ edges { node { $STREAM_FIELDS } } } }"

internal const val TOP_GAMES_QUERY =
    "query(\$first: Int) { games(first: \$first, options: {sort: VIEWER_COUNT}) " +
        "{ edges { node { id name displayName boxArtURL(width: 144, height: 192) } } } }"

internal const val GAME_STREAMS_QUERY =
    "query(\$id: ID!, \$first: Int) { game(id: \$id) { streams(first: \$first, options: {sort: VIEWER_COUNT}) " +
        "{ edges { node { $STREAM_FIELDS } } } } }"

internal const val SEARCH_QUERY =
    "query(\$query: String!, \$limit: Int) { searchFor(userQuery: \$query, platform: \"web\", target: {index: CHANNEL, limit: \$limit}) " +
        "{ channels { edges { item { ... on User { id login displayName profileImageURL(width: 300) " +
        "broadcastSettings { title game { name displayName } } stream { id title game { name displayName } } } } } } } }"

internal const val SEARCH_CATEGORIES_QUERY =
    "query(\$query: String!, \$limit: Int) { searchFor(userQuery: \$query, platform: \"web\", target: {index: GAME, limit: \$limit}) " +
        "{ games { edges { item { ... on Game { id name displayName boxArtURL(width: 144, height: 192) } } } } } }"

internal const val USER_QUERY =
    "query(\$login: String!) { user(login: \$login) { id login displayName description " +
        "profileImageURL(width: 300) offlineImageURL createdAt roles { isPartner isAffiliate } } }"

internal const val VIDEOS_QUERY =
    "query(\$id: ID!, \$first: Int, \$type: BroadcastType) { user(id: \$id) { videos(first: \$first, type: \$type, sort: TIME) " +
        "{ edges { node { id title lengthSeconds createdAt publishedAt viewCount broadcastType " +
        "previewThumbnailURL(width: 320, height: 180) owner { id login displayName } } } } } }"

internal const val VIDEO_QUERY =
    "query(\$id: ID!) { video(id: \$id) { id title lengthSeconds createdAt publishedAt viewCount broadcastType " +
        "previewThumbnailURL(width: 320, height: 180) owner { id login displayName } } }"

internal const val USERS_QUERY =
    "query(\$logins: [String!]) { users(logins: \$logins) { stream { $STREAM_FIELDS } } }"

internal const val GLOBAL_BADGES_QUERY =
    "query { badges { setID version title imageURL(size: DOUBLE) } }"

internal const val GLOBAL_EMOTES_QUERY =
    "query { emoteSet(id: \"0\") { emotes { id token assetType } } }"

// Two queries on purpose: badges and sub emotes fail independently, and one must not take the other down.
internal const val CHANNEL_BADGES_QUERY =
    "query(\$id: ID!) { user(id: \$id) { broadcastBadges { setID version title imageURL(size: DOUBLE) } } }"

internal const val CHANNEL_EMOTES_QUERY =
    "query(\$id: ID!) { user(id: \$id) { subscriptionProducts { emotes { id token assetType } } } }"
