package com.puretv.twitch.core.stream

import com.puretv.twitch.core.api.ChannelSearchResult
import com.puretv.twitch.core.model.ChannelInfo
import com.puretv.twitch.core.model.GameInfo
import com.puretv.twitch.core.model.StreamInfo
import com.puretv.twitch.core.model.VideoInfo
import com.puretv.twitch.core.model.VideoType
import kotlinx.serialization.Serializable

/**
 * Wire shapes for the anonymous GQL directory reads in [TwitchGqlDirectoryClient],
 * plus their mapping into the same core models the Helix path returns, so a
 * screen cannot tell which source filled it.
 *
 * Every field is nullable or defaulted: GQL nulls out whatever it cannot resolve
 * (a banned broadcaster, a stream with no category) instead of failing the call,
 * and one odd entry must not sink a whole page.
 */

/**
 * Response envelope. Unlike Helix, GQL answers a bad query with HTTP 200, an
 * `errors` array and no `data`, so [errors] is read rather than ignored.
 * Separate from [GqlEnvelope] on purpose: the playback-token mint keeps its own.
 */
@Serializable
internal data class GqlDirectoryResponse<T>(
    val data: T? = null,
    val errors: List<GqlDirectoryError> = emptyList(),
)

@Serializable
internal data class GqlDirectoryError(val message: String = "")

/** A directory query failed: non-2xx, any `errors` entry, or no `data` at all. */
class GqlDirectoryException(message: String) : Exception(message)

// ---- Shared node shapes ----

@Serializable
internal data class GqlStreamNode(
    val id: String? = null,
    val title: String? = null,
    val viewersCount: Int? = null,
    val createdAt: String? = null,
    val type: String? = null,
    val previewImageURL: String? = null,
    val broadcaster: GqlBroadcaster? = null,
    val game: GqlGame? = null,
    val freeformTags: List<GqlTag>? = null,
)

@Serializable
internal data class GqlBroadcaster(
    val id: String? = null,
    val login: String? = null,
    val displayName: String? = null,
    val profileImageURL: String? = null,
    val broadcastSettings: GqlBroadcastSettings? = null,
)

@Serializable
internal data class GqlBroadcastSettings(
    val language: String? = null,
    val title: String? = null,
    val game: GqlGame? = null,
)

@Serializable
internal data class GqlGame(
    val id: String? = null,
    val name: String? = null,
    val displayName: String? = null,
    val boxArtURL: String? = null,
)

@Serializable
internal data class GqlTag(val name: String? = null)

@Serializable
internal data class GqlStreamEdge(val node: GqlStreamNode? = null)

@Serializable
internal data class GqlStreamConnection(val edges: List<GqlStreamEdge?> = emptyList())

// ---- Per-query data shapes ----

@Serializable
internal data class GqlTopStreamsData(val streams: GqlStreamConnection? = null)

@Serializable
internal data class GqlGameEdge(val node: GqlGame? = null)

@Serializable
internal data class GqlGameConnection(val edges: List<GqlGameEdge?> = emptyList())

@Serializable
internal data class GqlTopGamesData(val games: GqlGameConnection? = null)

@Serializable
internal data class GqlGameStreams(val streams: GqlStreamConnection? = null)

@Serializable
internal data class GqlGameStreamsData(val game: GqlGameStreams? = null)

@Serializable
internal data class GqlRoles(val isPartner: Boolean? = null, val isAffiliate: Boolean? = null)

@Serializable
internal data class GqlUserStreamSummary(
    val id: String? = null,
    val title: String? = null,
    val game: GqlGame? = null,
)

@Serializable
internal data class GqlSearchUser(
    val id: String? = null,
    val login: String? = null,
    val displayName: String? = null,
    val profileImageURL: String? = null,
    val broadcastSettings: GqlBroadcastSettings? = null,
    val stream: GqlUserStreamSummary? = null,
)

@Serializable
internal data class GqlSearchEdge(val item: GqlSearchUser? = null)

@Serializable
internal data class GqlSearchChannels(val edges: List<GqlSearchEdge?> = emptyList())

@Serializable
internal data class GqlSearchFor(val channels: GqlSearchChannels? = null, val games: GqlSearchGames? = null)

@Serializable
internal data class GqlSearchGameEdge(val item: GqlGame? = null)

@Serializable
internal data class GqlSearchGames(val edges: List<GqlSearchGameEdge?> = emptyList())

@Serializable
internal data class GqlSearchData(val searchFor: GqlSearchFor? = null)

@Serializable
internal data class GqlUser(
    val id: String? = null,
    val login: String? = null,
    val displayName: String? = null,
    val description: String? = null,
    val profileImageURL: String? = null,
    val offlineImageURL: String? = null,
    val createdAt: String? = null,
    val roles: GqlRoles? = null,
)

@Serializable
internal data class GqlUserData(val user: GqlUser? = null)

@Serializable
internal data class GqlUserWithStream(val stream: GqlStreamNode? = null)

@Serializable
internal data class GqlVideoOwner(val id: String? = null, val login: String? = null, val displayName: String? = null)

@Serializable
internal data class GqlVideoNode(
    val id: String? = null,
    val title: String? = null,
    val lengthSeconds: Long? = null,
    val createdAt: String? = null,
    val publishedAt: String? = null,
    val viewCount: Int? = null,
    val broadcastType: String? = null,
    val previewThumbnailURL: String? = null,
    val owner: GqlVideoOwner? = null,
)

@Serializable
internal data class GqlVideoEdge(val node: GqlVideoNode? = null)

@Serializable
internal data class GqlVideoConnection(val edges: List<GqlVideoEdge?> = emptyList())

@Serializable
internal data class GqlVideosUser(val videos: GqlVideoConnection? = null)

@Serializable
internal data class GqlVideosData(val user: GqlVideosUser? = null)

@Serializable
internal data class GqlVideoData(val video: GqlVideoNode? = null)

@Serializable
internal data class GqlUsersData(val users: List<GqlUserWithStream?> = emptyList())

// ---- Mapping into the Helix-shaped core models ----

/**
 * GQL hands back concrete image sizes (`live_user_x-440x248.jpg`,
 * `27471_IGDB-144x192.jpg`) where Helix hands back a `{width}x{height}`
 * template, and every screen fills that template at its own size. Put the
 * template back so signed-out art is as sharp as signed-in art.
 */
internal fun toSizeTemplate(url: String?): String =
    url.orEmpty().replace(SIZE_SUFFIX, "-{width}x{height}.$1")

private val SIZE_SUFFIX = Regex("""-\d+x\d+\.(\w+)$""")

/**
 * Helix hands back VOD art as `...-%{width}x%{height}.jpg` (percent-brace, unlike
 * stream art), and the screens fill that form, so restore it.
 */
internal fun toVodSizeTemplate(url: String?): String =
    url.orEmpty().replace(SIZE_SUFFIX, "-%{width}x%{height}.$1")

internal fun GqlVideoNode.toVideoInfo(fallbackUserId: String): VideoInfo? {
    val videoId = id?.takeIf { it.isNotBlank() } ?: return null
    val login = owner?.login.orEmpty()
    return VideoInfo(
        id = videoId,
        userId = owner?.id?.takeIf { it.isNotBlank() } ?: fallbackUserId,
        userLogin = login,
        userName = owner?.displayName?.takeIf { it.isNotBlank() } ?: login,
        title = title.orEmpty(),
        description = "",
        type = VideoType.fromApi(broadcastType.orEmpty()),
        durationSeconds = lengthSeconds ?: 0L,
        createdAt = createdAt.orEmpty(),
        publishedAt = publishedAt.orEmpty(),
        thumbnailUrl = toVodSizeTemplate(previewThumbnailURL),
        viewCount = viewCount ?: 0,
        mutedSegments = emptyList(),
    )
}

/** Null when the node lacks what a stream card needs (an id and a broadcaster login). */
internal fun GqlStreamNode.toStreamInfo(): StreamInfo? {
    val streamId = id?.takeIf { it.isNotBlank() } ?: return null
    val login = broadcaster?.login?.takeIf { it.isNotBlank() } ?: return null
    return StreamInfo(
        id = streamId,
        userId = broadcaster.id.orEmpty(),
        userLogin = login,
        userName = broadcaster.displayName?.takeIf { it.isNotBlank() } ?: login,
        gameId = game?.id.orEmpty(),
        gameName = game?.displayLabel().orEmpty(),
        title = title.orEmpty(),
        viewerCount = viewersCount ?: 0,
        startedAt = createdAt.orEmpty(),
        // GQL says "EN"; Helix (and everything that reads this) says "en".
        language = broadcaster.broadcastSettings?.language?.lowercase().orEmpty(),
        thumbnailUrl = toSizeTemplate(previewImageURL),
        tags = freeformTags.orEmpty().mapNotNull { it.name?.takeIf { n -> n.isNotBlank() } },
    )
}

/** Helix `game_name` is the display name ("Just Chatting"), so prefer it. */
private fun GqlGame.displayLabel(): String? = displayName?.takeIf { it.isNotBlank() } ?: name

internal fun GqlGame.toGameInfo(): GameInfo? {
    val gameId = id?.takeIf { it.isNotBlank() } ?: return null
    val label = displayLabel()?.takeIf { it.isNotBlank() } ?: return null
    return GameInfo(id = gameId, name = label, boxArtUrl = toSizeTemplate(boxArtURL))
}

internal fun GqlUser.toChannelInfo(): ChannelInfo? {
    val userId = id?.takeIf { it.isNotBlank() } ?: return null
    val userLogin = login?.takeIf { it.isNotBlank() } ?: return null
    return ChannelInfo(
        id = userId,
        login = userLogin,
        displayName = displayName?.takeIf { it.isNotBlank() } ?: userLogin,
        description = description.orEmpty(),
        profileImageUrl = profileImageURL.orEmpty(),
        offlineImageUrl = offlineImageURL.orEmpty(),
        broadcasterType = when {
            roles?.isPartner == true -> "partner"
            roles?.isAffiliate == true -> "affiliate"
            else -> ""
        },
        createdAt = createdAt.orEmpty(),
    )
}

internal fun GqlSearchUser.toSearchResult(): ChannelSearchResult? {
    val userId = id?.takeIf { it.isNotBlank() } ?: return null
    val userLogin = login?.takeIf { it.isNotBlank() } ?: return null
    return ChannelSearchResult(
        id = userId,
        broadcaster_login = userLogin,
        display_name = displayName?.takeIf { it.isNotBlank() } ?: userLogin,
        is_live = stream != null,
        // Helix reports the channel's last category even while offline.
        game_name = (stream?.game ?: broadcastSettings?.game)?.displayLabel().orEmpty(),
        // Helix search returns the profile picture here, not a stream preview.
        thumbnail_url = profileImageURL.orEmpty(),
        title = stream?.title?.takeIf { it.isNotBlank() } ?: broadcastSettings?.title.orEmpty(),
    )
}
