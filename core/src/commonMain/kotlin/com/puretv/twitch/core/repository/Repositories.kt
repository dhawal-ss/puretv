package com.puretv.twitch.core.repository

import com.puretv.twitch.core.api.ChannelSearchResult
import com.puretv.twitch.core.api.GamesPage
import com.puretv.twitch.core.api.TwitchApiClient
import com.puretv.twitch.core.model.ChannelInfo
import com.puretv.twitch.core.model.GameInfo
import com.puretv.twitch.core.model.StreamInfo
import com.puretv.twitch.core.stream.StreamResolver
import com.puretv.twitch.core.stream.TwitchGqlDirectoryClient
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Wraps [TwitchApiClient] with light in-memory caching the UI layer can
 * observe directly. Persistent caching (Room/SQLite) hangs off these via
 * the platform-specific repository constructors wired in Koin (Section 11).
 */
class StreamRepository(
    private val apiClient: TwitchApiClient,
    private val streamResolver: StreamResolver,
    // Signed-out browsing (Wave 4, D5.1): with no user token the public
    // directory reads below go through anonymous GQL. With a token they stay on
    // Helix, so a Helix 401 is still reported as the sign-in problem it is.
    // Null keeps every read on Helix.
    private val directory: TwitchGqlDirectoryClient? = null,
) {
    private val _topStreams = MutableStateFlow<List<StreamInfo>>(emptyList())
    val topStreams: StateFlow<List<StreamInfo>> = _topStreams.asStateFlow()

    /** The anonymous directory, or null while a user token is on hand. Checked per call. */
    private fun signedOutDirectory(): TwitchGqlDirectoryClient? =
        directory?.takeIf { !apiClient.hasUserToken() }

    suspend fun refreshTopStreams(gameId: String? = null): List<StreamInfo> {
        val anonymous = signedOutDirectory()
        val streams = when {
            anonymous == null -> apiClient.getStreams(gameIds = listOfNotNull(gameId))
            gameId != null -> anonymous.streamsForGame(gameId, first = 20)
            else -> anonymous.topStreams(first = 20)
        }
        _topStreams.value = streams
        return streams
    }

    /**
     * Live status for exactly these [logins]. Helix caps `user_login` at 100 per
     * call and defaults `first` to 20 (a page-size limit that also caps how many
     * of the matched channels come back), so a caller with more than 20 logins
     * live at once — or more than 100 logins total — would silently lose results
     * from a single unchunked call. Fans out chunks of 100 in parallel, each
     * requesting `first = 100`, and unions the results; a single-login call
     * (the common "is this one channel live" case) is just a one-chunk fan-out.
     */
    suspend fun streamsForChannels(logins: List<String>): List<StreamInfo> {
        if (logins.isEmpty()) return emptyList()
        signedOutDirectory()?.let { return it.liveStreamsForLogins(logins) }
        return coroutineScope {
            logins.chunked(100)
                .map { chunk -> async { apiClient.getStreams(userLogins = chunk, first = 100) } }
                .awaitAll()
                .flatten()
        }
    }

    /**
     * Live streams in a single category (game), ordered by viewer count — backs
     * the Browse → Category screen. Deliberately does NOT touch [topStreams]:
     * unlike [refreshTopStreams], this is a one-off read so Home's cached top
     * list is never overwritten when the user drills into a category.
     */
    /** Everything [userId] follows that is live right now, in one Helix call (see TwitchApiClient.getFollowedStreams). */
    suspend fun followedLiveStreams(userId: String): List<StreamInfo> = apiClient.getFollowedStreams(userId)

    suspend fun streamsForGame(gameId: String, first: Int = 40): List<StreamInfo> {
        if (gameId.isBlank()) return emptyList()
        signedOutDirectory()?.let { return it.streamsForGame(gameId, first) }
        return apiClient.getStreams(gameIds = listOf(gameId), first = first)
    }

    suspend fun resolvePlayableStream(
        channelLogin: String,
        oauthToken: String? = null,
        playerType: String = StreamResolver.PRIMARY_PLAYER_TYPE,
    ) = streamResolver.resolveMasterPlaylist(channelLogin, oauthToken, playerType)
}

class ChannelRepository(
    private val apiClient: TwitchApiClient,
    // Same rule as [StreamRepository]: anonymous GQL only while there is no
    // user token. Null keeps every read on Helix.
    private val directory: TwitchGqlDirectoryClient? = null,
) {
    private fun signedOutDirectory(): TwitchGqlDirectoryClient? =
        directory?.takeIf { !apiClient.hasUserToken() }

    // Audit M7: this cache is read and written from suspend funcs that may run on
    // different dispatchers/threads; a plain mutableMapOf gives no happens-before
    // guarantee and concurrent puts can corrupt or lose entries. A Mutex
    // serializes all access without changing the public API. The network fetch is
    // deliberately kept OUTSIDE the lock so a slow /users call never blocks cache
    // reads for other logins.
    private val cacheMutex = Mutex()
    private val cache = mutableMapOf<String, ChannelInfo>()

    suspend fun getChannel(login: String): ChannelInfo? {
        cacheMutex.withLock { cache[login] }?.let { return it }
        val anonymous = signedOutDirectory()
        val channel = if (anonymous != null) {
            anonymous.user(login)
        } else {
            apiClient.getUsers(logins = listOf(login)).firstOrNull()
        }
        channel?.let { cacheMutex.withLock { cache[login] = it } }
        return channel
    }

    suspend fun search(query: String, liveOnly: Boolean = false): List<ChannelSearchResult> {
        val anonymous = signedOutDirectory()
        return if (anonymous != null) anonymous.searchChannels(query, liveOnly) else apiClient.searchChannels(query, liveOnly)
    }

    suspend fun topGames(): List<GameInfo> {
        val anonymous = signedOutDirectory()
        return if (anonymous != null) anonymous.topGames() else apiClient.getTopGames()
    }

    /**
     * Categories for Browse, [first] at a time from [after]. Signed in, Helix pages
     * through every live category. Signed out there is exactly one page: anonymous
     * GQL refuses every `after` cursor ("failed integrity check"), so the 100 most
     * watched categories come back with no cursor.
     */
    suspend fun topGamesPage(after: String? = null, first: Int = 100): GamesPage {
        val anonymous = signedOutDirectory()
        if (anonymous != null) {
            return if (after == null) GamesPage(anonymous.topGames(first), null) else GamesPage(emptyList(), null)
        }
        return apiClient.getTopGamesPage(first, after)
    }

    /** Categories matching [query], for Search (issue #41). */
    suspend fun searchCategories(query: String, first: Int = 20): List<GameInfo> {
        val anonymous = signedOutDirectory()
        return if (anonymous != null) anonymous.searchCategories(query, first) else apiClient.searchCategories(query, first)
    }
}

class UserRepository(private val apiClient: TwitchApiClient) {
    private val _followedLogins = MutableStateFlow<List<String>>(emptyList())
    val followedLogins: StateFlow<List<String>> = _followedLogins.asStateFlow()

    suspend fun loadFollows(userId: String) {
        val follows = apiClient.getAllFollowedChannels(userId)
        _followedLogins.value = follows.map { it.broadcaster_login }
    }

    /**
     * The authenticated account (Helix /users with no login/id returns the
     * current token's owner), or null if there is no token or the call fails.
     * Used right after sign-in to capture the username/userId for chat identity
     * and the Settings screen.
     */
    suspend fun getCurrentUser(): ChannelInfo? = apiClient.getUsers().firstOrNull()

    /**
     * Loads follows for whoever owns the current token. Helix /users with no
     * login/id returns the authenticated user, so this works right after a
     * device-code sign-in even before the user id has been persisted to settings.
     */
    suspend fun loadFollowsForCurrentUser() {
        val me = getCurrentUser() ?: return
        loadFollows(me.id)
    }
}
