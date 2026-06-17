package com.puretv.twitch.core.follows

import com.puretv.twitch.core.model.StreamInfo

/** A followed channel normalized from either the Helix follow graph or a local pin. */
data class FollowedRef(val id: String, val login: String, val displayName: String)

/** Profile bits resolved via GET /users, keyed by lowercased login. */
data class Profile(val displayName: String, val avatarUrl: String)

/** One row in the followed rail. [avatarUrl] null => render an initial fallback. */
data class FollowRow(
    val login: String,
    val displayName: String,
    val avatarUrl: String?,
    val isLive: Boolean,
    val viewerCount: Int,
    val gameName: String,
)

/** The followed rail split into live (viewers desc) and offline (name asc). */
data class FollowedList(val live: List<FollowRow>, val offline: List<FollowRow>)

/**
 * Pure merge: combine normalized follows with the currently-live streams and any
 * resolved profiles into a [FollowedList]. Deterministic, no I/O.
 *
 * @param follows real follows ∪ local pins, already deduped by login.
 * @param liveStreams streams currently live (from chunked GET /streams).
 * @param profiles login(lowercased) -> [Profile]; may be partial/empty.
 */
fun buildFollowedList(
    follows: List<FollowedRef>,
    liveStreams: List<StreamInfo>,
    profiles: Map<String, Profile>,
): FollowedList {
    val liveByLogin = liveStreams.associateBy { it.userLogin.lowercase() }

    val rows = follows.map { ref ->
        val key = ref.login.lowercase()
        val profile = profiles[key]
        val stream = liveByLogin[key]
        FollowRow(
            login = ref.login,
            displayName = profile?.displayName?.ifBlank { ref.displayName } ?: ref.displayName,
            avatarUrl = profile?.avatarUrl?.ifBlank { null },
            isLive = stream != null,
            viewerCount = stream?.viewerCount ?: 0,
            gameName = stream?.gameName ?: "",
        )
    }

    val (live, offline) = rows.partition { it.isLive }
    return FollowedList(
        live = live.sortedWith(compareByDescending<FollowRow> { it.viewerCount }.thenBy { it.displayName.lowercase() }),
        offline = offline.sortedBy { it.displayName.lowercase() },
    )
}
