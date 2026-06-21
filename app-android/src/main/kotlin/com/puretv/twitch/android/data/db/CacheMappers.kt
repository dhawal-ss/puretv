package com.puretv.twitch.android.data.db

import com.puretv.twitch.core.model.StreamInfo

/**
 * SECTION 09.3: mappers between the live core [StreamInfo] model and the Room
 * [CachedStream] row. Lets Home paint last session's streams instantly from the
 * cache, and write fresh network results back through.
 */

/** Maps a live [StreamInfo] into its persistent cache row. */
fun StreamInfo.toCachedStream(now: Long): CachedStream = CachedStream(
    channelLogin = userLogin,
    channelDisplayName = userName,
    title = title,
    gameName = gameName,
    viewerCount = viewerCount.toLong(),
    thumbnailUrl = thumbnailUrl,
    fetchedAtEpochMs = now,
)

/**
 * Maps a cached row back to a [StreamInfo] for display. id/userId/gameId are
 * not persisted (the card navigates by login and never needs them), so they are
 * empty; the card uses login, name, title, game, viewer count, and thumbnail.
 */
fun CachedStream.toStreamInfo(): StreamInfo = StreamInfo(
    id = "",
    userId = "",
    userLogin = channelLogin,
    userName = channelDisplayName,
    gameId = "",
    gameName = gameName,
    title = title,
    viewerCount = viewerCount.toInt(),
    thumbnailUrl = thumbnailUrl,
)
