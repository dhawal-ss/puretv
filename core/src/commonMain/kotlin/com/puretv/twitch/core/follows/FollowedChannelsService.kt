package com.puretv.twitch.core.follows

import com.puretv.twitch.core.api.TwitchApiClient
import com.puretv.twitch.core.model.StreamInfo
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.TimeMark
import kotlin.time.TimeSource

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
 *  1. fetch ALL follows (paginated, kept for [followsTtl] because the list rarely
 *     changes while the rail polls every minute) and union local pins,
 *  2. fetch live status with GET /streams/followed (one call for everything the
 *     user follows), plus GET /streams chunks only for local pins the user does
 *     not follow on Twitch, and missing profiles (GET /users) in parallel chunks
 *     of 100. If /streams/followed fails, live status falls back to /streams
 *     chunks for every follow, as before,
 *  3. emit a fast live-only result via [load]'s onLive the moment live status is in
 *     (cached profiles applied; the rest render as initials),
 *  4. once profiles resolve, return the fully enriched [FollowedList].
 *
 * Profile cache is a plain map. `load` is called serially by the desktop poll loop, and
 * the only writes happen sequentially after the parallel fetches complete, so there is
 * no concurrent mutation.
 */
class FollowedChannelsService(
    private val api: TwitchApiClient,
    private val timeSource: TimeSource = TimeSource.Monotonic,
    private val followsTtl: Duration = 10.minutes,
    // Each app points this at its LiveFollowsMonitor, so the rail and the go-live
    // alerts share one /streams/followed request instead of making one each.
    private val followedLive: suspend (userId: String) -> List<StreamInfo> = { api.getFollowedStreams(it) },
) : FollowedChannelsSource {
    // Several loaders run at once (the rail in each window, Following, the multi-stream
    // picker), so the cache is an immutable map swapped whole: a reader never sees it
    // mid-change. Two loaders saving at once can drop an entry, which is only refetched.
    @Volatile private var profileCache: Map<String, Profile> = emptyMap()

    private class CachedFollows(val userId: String, val refs: List<FollowedRef>, val fetchedAt: TimeMark)
    @Volatile private var cachedFollows: CachedFollows? = null

    override suspend fun load(
        userId: String,
        localPins: List<FollowedRef>,
        onLive: (FollowedList) -> Unit,
    ): FollowedList = coroutineScope {
        val remote = followsFor(userId)
        val followedLive = async { followedLiveOrNull(userId) }

        // Live status and profiles are independent once we have the follow list, so kick
        // off both as parallel chunks rather than one slow sequential pass. Each chunk is
        // resilient: one failed /streams or /users call degrades to that chunk's rows
        // being missing, instead of an unguarded awaitAll() discarding ALL siblings and
        // emptying the rail. (We still hard-fail above if the follow LIST itself can't be
        // fetched — that genuinely-errored case should surface, not look empty.)
        // Something followed on twitch.tv after the cached list was fetched still shows
        // up the moment it goes live: /streams/followed already knows about it.
        val liveFollows = followedLive.await()
        val newlyFollowed = liveFollows.orEmpty()
            .map { FollowedRef(it.userId, it.userLogin, it.userName) }
        val refs = (remote + newlyFollowed + localPins).distinctBy { it.login.lowercase() }
        val remoteLogins = remote.mapTo(HashSet()) { it.login.lowercase() }
        // With /streams/followed in hand only local pins the user does not follow on
        // Twitch still need a per-channel check; without it, every ref does.
        val toCheck = if (liveFollows == null) refs else localPins.filter { it.login.lowercase() !in remoteLogins }
        val streamChunks = toCheck.map { it.login }.chunked(100)
            .map { chunk -> async { resilientChunk { api.getStreams(userLogins = chunk, first = 100) } } }
        // Only resolve profiles we don't already have. Blank-id refs (e.g. legacy local
        // pins saved without an id) are skipped; they render with the initial fallback.
        val missingIds = refs.filter { profileCache[it.login.lowercase()] == null && it.id.isNotBlank() }.map { it.id }
        val userChunks = missingIds.chunked(100)
            .map { ids -> async { resilientChunk { api.getUsers(ids = ids) } } }

        // Show the list as soon as live status is in, using whatever profiles are already
        // cached. Remaining avatars fill in after enrichment below.
        val live: List<StreamInfo> = (liveFollows.orEmpty() + streamChunks.awaitAll().flatten())
            .distinctBy { it.userLogin.lowercase() }
        onLive(buildFollowedList(refs, live, profileCache))

        // Best-effort enrichment. A failed /users chunk leaves that chunk's avatars as
        // initials; successful chunks still populate the cache.
        val fetched = userChunks.awaitAll().flatten().associate { ch ->
            ch.login.lowercase() to Profile(ch.displayName, ch.profileImageUrl)
        }
        if (fetched.isNotEmpty()) profileCache = profileCache + fetched

        buildFollowedList(refs, live, profileCache)
    }

    override fun clear() {
        profileCache = emptyMap()
        cachedFollows = null
    }

    /** The follow list, refetched only when older than [followsTtl] or for another account. */
    private suspend fun followsFor(userId: String): List<FollowedRef> {
        cachedFollows?.takeIf { it.userId == userId && it.fetchedAt.elapsedNow() < followsTtl }?.let { return it.refs }
        val fresh = api.getAllFollowedChannels(userId).map {
            FollowedRef(it.broadcaster_id, it.broadcaster_login, it.broadcaster_name)
        }
        cachedFollows = CachedFollows(userId, fresh, timeSource.markNow())
        return fresh
    }

    /** Live follows from /streams/followed, or null if that call failed (callers fall back). */
    private suspend fun followedLiveOrNull(userId: String): List<StreamInfo>? =
        try {
            followedLive(userId)
        } catch (c: CancellationException) {
            throw c
        } catch (e: Throwable) {
            null
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
