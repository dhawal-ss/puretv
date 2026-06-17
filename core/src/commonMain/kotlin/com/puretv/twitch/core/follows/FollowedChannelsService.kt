package com.puretv.twitch.core.follows

import com.puretv.twitch.core.api.TwitchApiClient
import com.puretv.twitch.core.model.StreamInfo

/** Loads the followed rail. Interface so desktop ViewModels can fake it in tests. */
interface FollowedChannelsSource {
    /**
     * @param userId signed-in user's Twitch id (for GET /channels/followed).
     * @param localPins extra channels to union in (desktop's local "Following" list).
     */
    suspend fun load(userId: String, localPins: List<FollowedRef>): FollowedList

    /**
     * Drop any cached profile data. Call on sign-out so that a different account
     * signing in within the same process never reuses the previous user's cached
     * profiles. (Follow membership is never cached — only avatar/display-name by
     * login — so this is defensive hygiene, not a correctness requirement.)
     */
    fun clear()
}

/**
 * Real implementation over Helix. Steps:
 *  1. fetch ALL follows (paginated) and normalize to [FollowedRef],
 *  2. union local pins, dedup by lowercased login,
 *  3. live status via GET /streams chunked by 100 (Helix caps at 100 user_login + first=100),
 *  4. profiles via GET /users chunked by 100, cached (profiles change rarely),
 *  5. [buildFollowedList].
 *
 * Profile cache is a plain map: `load` is called serially by the desktop poll loop,
 * so there is no concurrent mutation in practice.
 */
class FollowedChannelsService(private val api: TwitchApiClient) : FollowedChannelsSource {
    private val profileCache = mutableMapOf<String, Profile>()

    override suspend fun load(userId: String, localPins: List<FollowedRef>): FollowedList {
        val remote = api.getAllFollowedChannels(userId).map {
            FollowedRef(it.broadcaster_id, it.broadcaster_login, it.broadcaster_name)
        }
        val refs = (remote + localPins).distinctBy { it.login.lowercase() }

        val live: List<StreamInfo> = refs.map { it.login }
            .chunked(100)
            .flatMap { api.getStreams(userLogins = it, first = 100) }

        // Only resolve profiles we don't already have. Blank-id refs (e.g. legacy
        // local pins saved without an id) are skipped — there is nothing to look up
        // by id, so they render with the initial-avatar fallback.
        val missingIds = refs.filter { profileCache[it.login.lowercase()] == null && it.id.isNotBlank() }.map { it.id }
        // Best-effort enrichment: if a /users chunk throws, it propagates to the
        // caller (the rail ViewModel), which keeps the last-known list and retries
        // on the next poll. Profiles already cached this pass are retained.
        missingIds.chunked(100).forEach { ids ->
            api.getUsers(ids = ids).forEach { ch ->
                profileCache[ch.login.lowercase()] = Profile(ch.displayName, ch.profileImageUrl)
            }
        }

        return buildFollowedList(refs, live, profileCache.toMap())
    }

    override fun clear() {
        profileCache.clear()
    }
}
