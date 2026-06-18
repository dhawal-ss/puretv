package com.puretv.twitch.core.follows

import com.puretv.twitch.core.api.TwitchApiClient
import com.puretv.twitch.core.model.StreamInfo
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlin.coroutines.cancellation.CancellationException

/** Loads the followed rail. Interface so desktop ViewModels can fake it in tests. */
interface FollowedChannelsSource {
    /**
     * Load the followed rail.
     *
     * @param userId signed-in user's Twitch id (for GET /channels/followed).
     * @param localPins extra channels to union in (desktop's local "Following" list).
     * @param onLive invoked once, as soon as live status resolves, with a fast result
     *   that may still be missing avatars (those rows render as initials). This lets the
     *   UI show the list quickly instead of waiting on the slower profile lookups.
     * @return the fully enriched list (avatars resolved); render this one last.
     */
    suspend fun load(
        userId: String,
        localPins: List<FollowedRef>,
        onLive: (FollowedList) -> Unit,
    ): FollowedList

    /**
     * Drop any cached profile data. Call on sign-out so that a different account
     * signing in within the same process never reuses the previous user's cached
     * profiles. (Follow membership is never cached, only avatar/display-name by login,
     * so this is defensive hygiene, not a correctness requirement.)
     */
    fun clear()
}

/**
 * Real implementation over Helix. On [load]:
 *  1. fetch ALL follows (paginated) and normalize to [FollowedRef], union local pins,
 *  2. fetch live status (GET /streams) and missing profiles (GET /users) as parallel
 *     chunks of 100 (Helix caps both at 100 ids per call),
 *  3. emit a fast live-only result via [load]'s onLive the moment live status is in
 *     (cached profiles applied; the rest render as initials),
 *  4. once profiles resolve, return the fully enriched [FollowedList].
 *
 * Profile cache is a plain map. `load` is called serially by the desktop poll loop, and
 * the only writes happen sequentially after the parallel fetches complete, so there is
 * no concurrent mutation.
 */
class FollowedChannelsService(private val api: TwitchApiClient) : FollowedChannelsSource {
    private val profileCache = mutableMapOf<String, Profile>()

    override suspend fun load(
        userId: String,
        localPins: List<FollowedRef>,
        onLive: (FollowedList) -> Unit,
    ): FollowedList = coroutineScope {
        val remote = api.getAllFollowedChannels(userId).map {
            FollowedRef(it.broadcaster_id, it.broadcaster_login, it.broadcaster_name)
        }
        val refs = (remote + localPins).distinctBy { it.login.lowercase() }

        // Live status and profiles are independent once we have the follow list, so kick
        // off both as parallel chunks rather than one slow sequential pass. Each chunk is
        // resilient: one failed /streams or /users call degrades to that chunk's rows
        // being missing, instead of an unguarded awaitAll() discarding ALL siblings and
        // emptying the rail. (We still hard-fail above if the follow LIST itself can't be
        // fetched — that genuinely-errored case should surface, not look empty.)
        val streamChunks = refs.map { it.login }.chunked(100)
            .map { chunk -> async { resilientChunk { api.getStreams(userLogins = chunk, first = 100) } } }
        // Only resolve profiles we don't already have. Blank-id refs (e.g. legacy local
        // pins saved without an id) are skipped; they render with the initial fallback.
        val missingIds = refs.filter { profileCache[it.login.lowercase()] == null && it.id.isNotBlank() }.map { it.id }
        val userChunks = missingIds.chunked(100)
            .map { ids -> async { resilientChunk { api.getUsers(ids = ids) } } }

        // Show the list as soon as live status is in, using whatever profiles are already
        // cached. Remaining avatars fill in after enrichment below.
        val live: List<StreamInfo> = streamChunks.awaitAll().flatten()
        onLive(buildFollowedList(refs, live, profileCache.toMap()))

        // Best-effort enrichment. A failed /users chunk leaves that chunk's avatars as
        // initials; successful chunks still populate the cache.
        userChunks.awaitAll().flatten().forEach { ch ->
            profileCache[ch.login.lowercase()] = Profile(ch.displayName, ch.profileImageUrl)
        }

        buildFollowedList(refs, live, profileCache.toMap())
    }

    override fun clear() {
        profileCache.clear()
    }

    /**
     * Run one fan-out chunk, degrading a genuine failure to an empty result so a single
     * bad chunk never discards its successful siblings. CancellationException is rethrown
     * so structured-concurrency cancellation (the parent scope being cancelled, e.g. the
     * rail poll being superseded) still propagates and isn't silently turned into "empty".
     */
    private suspend fun <T> resilientChunk(block: suspend () -> List<T>): List<T> =
        try {
            block()
        } catch (c: CancellationException) {
            throw c
        } catch (e: Throwable) {
            emptyList()
        }
}
