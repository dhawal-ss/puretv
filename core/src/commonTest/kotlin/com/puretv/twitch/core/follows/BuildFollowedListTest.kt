package com.puretv.twitch.core.follows

import com.puretv.twitch.core.model.StreamInfo
import kotlin.test.Test
import kotlin.test.assertEquals

class BuildFollowedListTest {
    private fun ref(login: String, id: String = "id_$login") = FollowedRef(id, login, login.replaceFirstChar { it.uppercase() })
    private fun stream(login: String, viewers: Int, game: String = "Game") = StreamInfo(
        id = "s_$login", userId = "id_$login", userLogin = login, userName = login, gameName = game, viewerCount = viewers,
    )

    @Test fun liveFirstSortedByViewersThenOfflineByName() {
        val follows = listOf(ref("alice"), ref("bob"), ref("carol"), ref("dave"))
        val live = listOf(stream("bob", 500), stream("alice", 1500))
        val result = buildFollowedList(follows, live, emptyMap())

        assertEquals(listOf("alice", "bob"), result.live.map { it.login })   // viewers desc
        assertEquals(1500, result.live.first().viewerCount)
        assertEquals(listOf("carol", "dave"), result.offline.map { it.login }) // name asc
        assertEquals(0, result.offline.first().viewerCount)
    }

    @Test fun profilesEnrichAvatarAndDisplayNameWithFallback() {
        val follows = listOf(ref("alice"))
        val profiles = mapOf("alice" to Profile(displayName = "Alice!", avatarUrl = "http://a/alice.png"))
        val result = buildFollowedList(follows, emptyList(), profiles)

        val row = result.offline.single()
        assertEquals("Alice!", row.displayName)
        assertEquals("http://a/alice.png", row.avatarUrl)
    }

    @Test fun missingProfileLeavesNullAvatarAndRefDisplayName() {
        val result = buildFollowedList(listOf(ref("bob")), emptyList(), emptyMap())
        val row = result.offline.single()
        assertEquals("Bob", row.displayName) // from FollowedRef
        assertEquals(null, row.avatarUrl)
    }

    @Test fun gameNameCarriedForLiveRows() {
        val result = buildFollowedList(listOf(ref("alice")), listOf(stream("alice", 10, game = "Chess")), emptyMap())
        assertEquals("Chess", result.live.single().gameName)
    }
}
