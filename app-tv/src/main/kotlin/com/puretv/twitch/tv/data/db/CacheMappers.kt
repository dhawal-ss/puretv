package com.puretv.twitch.tv.data.db

import com.puretv.twitch.core.model.StreamInfo

/**
 * SECTION 09.1 — TV counterpart of the phone app's `CacheMappers`. Bridges the
 * network [StreamInfo] model and the Room [CachedStream] row so Home can paint
 * the last session's streams instantly (offline / before the first network
 * response) and write every fresh result back. Duplicated rather than shared
 * because the Room entities are generated per-module (Section 12.2).
 */

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
 * Maps a cached row back to a [StreamInfo] for display. id/userId/gameId are not
 * persisted (the card navigates by login and never needs them), so they are
 * empty; the card uses login, name, title, game, viewer count, and thumbnail.
 */
fun CachedStream.toStreamInfo(): StreamInfo = StreamInfo(
    // Use the (unique) login as the id so a cached list never has duplicate
    // empty-string ids, which a LazyRow keyed on id would reject.
    id = channelLogin,
    userId = "",
    userLogin = channelLogin,
    userName = channelDisplayName,
    gameId = "",
    gameName = gameName,
    title = title,
    viewerCount = viewerCount.toInt(),
    thumbnailUrl = thumbnailUrl,
)
