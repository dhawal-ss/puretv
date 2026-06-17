package com.puretv.twitch.core.follows

import com.puretv.twitch.core.api.TwitchApiClient
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class FollowedChannelsServiceTest {
    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")

    // 120 follows -> forces follow pagination (100 + 20) AND >100 chunking for streams/users.
    private val followLogins = (0 until 120).map { "c$it" }
    private val liveLogins = setOf("c0", "c1", "c2") // 3 of them live

    private fun followsPage(logins: List<String>, cursor: String?): String {
        val data = logins.joinToString(",") {
            """{"broadcaster_id":"id_$it","broadcaster_login":"$it","broadcaster_name":"Name_$it"}"""
        }
        return """{"data":[$data],"pagination":${if (cursor == null) "{}" else """{"cursor":"$cursor"}"""}}"""
    }

    private fun streamsFor(logins: List<String>): String {
        val live = logins.filter { it in liveLogins }
        val data = live.joinToString(",") {
            """{"id":"s_$it","user_id":"id_$it","user_login":"$it","user_name":"Name_$it","game_name":"Chess","viewer_count":${100 + it.removePrefix("c").toInt()}}"""
        }
        return """{"data":[$data]}"""
    }

    private fun usersFor(ids: List<String>): String {
        val data = ids.joinToString(",") {
            val login = it.removePrefix("id_")
            """{"id":"$it","login":"$login","display_name":"Name_$login","profile_image_url":"http://a/$login.png"}"""
        }
        return """{"data":[$data]}"""
    }

    @Test fun paginatesFollowsChunksLiveAndProfilesThenMerges() = runTest {
        var streamCalls = 0
        var userCalls = 0
        val engine = MockEngine { request ->
            val url = request.url
            when {
                url.encodedPath.endsWith("/channels/followed") -> {
                    val after = url.parameters["after"]
                    if (after == null) respond(followsPage(followLogins.take(100), "P2"), HttpStatusCode.OK, jsonHeaders)
                    else respond(followsPage(followLogins.drop(100), null), HttpStatusCode.OK, jsonHeaders)
                }
                url.encodedPath.endsWith("/streams") -> {
                    streamCalls++
                    respond(streamsFor(url.parameters.getAll("user_login").orEmpty()), HttpStatusCode.OK, jsonHeaders)
                }
                url.encodedPath.endsWith("/users") -> {
                    userCalls++
                    respond(usersFor(url.parameters.getAll("id").orEmpty()), HttpStatusCode.OK, jsonHeaders)
                }
                else -> respond("", HttpStatusCode.NotFound)
            }
        }
        val client = HttpClient(engine) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }
        val service = FollowedChannelsService(TwitchApiClient(client) { "token" })

        val result = service.load("user123", localPins = emptyList())

        assertEquals(3, result.live.size, "3 followed channels are live")
        assertEquals(117, result.offline.size, "the rest are offline")
        assertEquals(2, streamCalls, "120 logins must be chunked into 2 /streams calls (100 + 20)")
        assertEquals(2, userCalls, "120 ids must be chunked into 2 /users calls (100 + 20)")
        // Live sorted by viewers desc: c2 (102) > c1 (101) > c0 (100)
        assertEquals(listOf("c2", "c1", "c0"), result.live.map { it.login })
        assertEquals("http://a/c2.png", result.live.first().avatarUrl)
    }

    @Test fun localPinsAreUnionedAndDedupedByLogin() = runTest {
        val engine = MockEngine { request ->
            val url = request.url
            when {
                url.encodedPath.endsWith("/channels/followed") -> respond(followsPage(listOf("c0"), null), HttpStatusCode.OK, jsonHeaders)
                url.encodedPath.endsWith("/streams") -> respond(streamsFor(url.parameters.getAll("user_login").orEmpty()), HttpStatusCode.OK, jsonHeaders)
                url.encodedPath.endsWith("/users") -> respond(usersFor(url.parameters.getAll("id").orEmpty()), HttpStatusCode.OK, jsonHeaders)
                else -> respond("", HttpStatusCode.NotFound)
            }
        }
        val client = HttpClient(engine) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }
        val service = FollowedChannelsService(TwitchApiClient(client) { "token" })

        // "C0" duplicates the remote follow (case-insensitive); "pinonly" is new.
        val pins = listOf(FollowedRef("id_C0", "C0", "C0"), FollowedRef("id_pinonly", "pinonly", "PinOnly"))
        val result = service.load("user123", localPins = pins)

        val allLogins = (result.live + result.offline).map { it.login.lowercase() }
        assertEquals(2, allLogins.size, "c0 deduped; pinonly added")
        assertEquals(setOf("c0", "pinonly"), allLogins.toSet())
    }

    @Test fun secondLoadReusesCachedProfilesAndSkipsUsersCall() = runTest {
        var userCalls = 0
        val engine = MockEngine { request ->
            val url = request.url
            when {
                url.encodedPath.endsWith("/channels/followed") -> respond(followsPage(listOf("c0", "c1"), null), HttpStatusCode.OK, jsonHeaders)
                url.encodedPath.endsWith("/streams") -> respond(streamsFor(url.parameters.getAll("user_login").orEmpty()), HttpStatusCode.OK, jsonHeaders)
                url.encodedPath.endsWith("/users") -> {
                    userCalls++
                    respond(usersFor(url.parameters.getAll("id").orEmpty()), HttpStatusCode.OK, jsonHeaders)
                }
                else -> respond("", HttpStatusCode.NotFound)
            }
        }
        val client = HttpClient(engine) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }
        val service = FollowedChannelsService(TwitchApiClient(client) { "token" })

        service.load("user123", localPins = emptyList())          // first load populates the profile cache
        val second = service.load("user123", localPins = emptyList())

        assertEquals(1, userCalls, "profiles are cached: the second load must not re-fetch /users")
        // The cached profile is still applied on the second pass.
        assertEquals("http://a/c0.png", (second.live + second.offline).first { it.login == "c0" }.avatarUrl)
    }

    @Test fun clearDropsCacheSoNextLoadReFetchesProfiles() = runTest {
        var userCalls = 0
        val engine = MockEngine { request ->
            val url = request.url
            when {
                url.encodedPath.endsWith("/channels/followed") -> respond(followsPage(listOf("c0"), null), HttpStatusCode.OK, jsonHeaders)
                url.encodedPath.endsWith("/streams") -> respond(streamsFor(url.parameters.getAll("user_login").orEmpty()), HttpStatusCode.OK, jsonHeaders)
                url.encodedPath.endsWith("/users") -> {
                    userCalls++
                    respond(usersFor(url.parameters.getAll("id").orEmpty()), HttpStatusCode.OK, jsonHeaders)
                }
                else -> respond("", HttpStatusCode.NotFound)
            }
        }
        val client = HttpClient(engine) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }
        val service = FollowedChannelsService(TwitchApiClient(client) { "token" })

        service.load("user123", localPins = emptyList())   // caches c0's profile
        service.clear()                                     // sign-out drops the cache
        service.load("user123", localPins = emptyList())    // must re-fetch

        assertEquals(2, userCalls, "clear() must drop the cache so the next load re-fetches /users")
    }
}
