package com.puretv.twitch.core.repository

import com.puretv.twitch.core.api.TwitchApiClient
import com.puretv.twitch.core.model.ChannelInfo
import com.puretv.twitch.core.model.GameInfo
import com.puretv.twitch.core.model.StreamInfo
import com.puretv.twitch.core.stream.StreamResolver
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Wraps [TwitchApiClient] with light in-memory caching the UI layer can
 * observe directly. Persistent caching (Room/SQLite) hangs off these via
 * the platform-specific repository constructors wired in Koin (Section 11).
 */
class StreamRepository(
    private val apiClient: TwitchApiClient,
    private val streamResolver: StreamResolver,
) {
    private val _topStreams = MutableStateFlow<List<StreamInfo>>(emptyList())
    val topStreams: StateFlow<List<StreamInfo>> = _topStreams.asStateFlow()

    suspend fun refreshTopStreams(gameId: String? = null): List<StreamInfo> {
        val streams = apiClient.getStreams(gameIds = listOfNotNull(gameId))
        _topStreams.value = streams
        return streams
    }

    suspend fun streamsForChannels(logins: List<String>): List<StreamInfo> =
        if (logins.isEmpty()) emptyList() else apiClient.getStreams(userLogins = logins)

    /**
     * Live streams in a single category (game), ordered by viewer count — backs
     * the Browse → Category screen. Deliberately does NOT touch [topStreams]:
     * unlike [refreshTopStreams], this is a one-off read so Home's cached top
     * list is never overwritten when the user drills into a category.
     */
    suspend fun streamsForGame(gameId: String, first: Int = 40): List<StreamInfo> =
        if (gameId.isBlank()) emptyList() else apiClient.getStreams(gameIds = listOf(gameId), first = first)

    suspend fun resolvePlayableStream(
        channelLogin: String,
        oauthToken: String? = null,
        playerType: String = "site",
    ) = streamResolver.resolveMasterPlaylist(channelLogin, oauthToken, playerType)
}

class ChannelRepository(private val apiClient: TwitchApiClient) {
    private val cache = mutableMapOf<String, ChannelInfo>()

    suspend fun getChannel(login: String): ChannelInfo? {
        cache[login]?.let { return it }
        val channel = apiClient.getUsers(logins = listOf(login)).firstOrNull()
        channel?.let { cache[login] = it }
        return channel
    }

    suspend fun search(query: String, liveOnly: Boolean = false) = apiClient.searchChannels(query, liveOnly)

    suspend fun topGames(): List<GameInfo> = apiClient.getTopGames()
}

class UserRepository(private val apiClient: TwitchApiClient) {
    private val _followedLogins = MutableStateFlow<List<String>>(emptyList())
    val followedLogins: StateFlow<List<String>> = _followedLogins.asStateFlow()

    suspend fun loadFollows(userId: String) {
        val follows = apiClient.getFollowedChannels(userId)
        _followedLogins.value = follows.map { it.broadcaster_login }
    }

    /**
     * Loads follows for whoever owns the current token. Helix /users with no
     * login/id returns the authenticated user, so this works right after a
     * device-code sign-in even before the user id has been persisted to settings.
     */
    suspend fun loadFollowsForCurrentUser() {
        val me = apiClient.getUsers().firstOrNull() ?: return
        loadFollows(me.id)
    }
}
