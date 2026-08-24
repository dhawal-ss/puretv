package com.puretv.twitch.core.repository

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

/**
 * Regression test for a bug where Android/TV's followed-channels rail silently
 * truncated to the first 100 follows: [UserRepository.loadFollows] called the
 * single-page Helix endpoint instead of following pagination, so any follow
 * past the first page never appeared. Mirrors FollowedChannelsPaginationTest,
 * but at the UserRepository level so a future regression here fails loudly.
 */
class UserRepositoryFollowsTest {
    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")

    private fun page(logins: List<String>, cursor: String?): String {
        val data = logins.joinToString(",") {
            """{"broadcaster_id":"id_$it","broadcaster_login":"$it","broadcaster_name":"$it"}"""
        }
        val pagination = if (cursor == null) """{}""" else """{"cursor":"$cursor"}"""
        return """{"data":[$data],"pagination":$pagination}"""
    }

    @Test fun loadFollowsCollectsEveryPage() = runTest {
        var calls = 0
        val engine = MockEngine { request ->
            calls++
            val after = request.url.parameters["after"]
            if (after == null) respond(page(listOf("a", "b"), cursor = "PAGE2"), HttpStatusCode.OK, jsonHeaders)
            else respond(page(listOf("c"), cursor = null), HttpStatusCode.OK, jsonHeaders)
        }
        val client = HttpClient(engine) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }
        val repo = UserRepository(TwitchApiClient(client) { "token" })

        repo.loadFollows("user123")

        assertEquals(listOf("a", "b", "c"), repo.followedLogins.value)
        assertEquals(2, calls, "must follow the pagination cursor, not stop at the first page")
    }
}
