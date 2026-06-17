package com.puretv.twitch.core.api

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

class FollowedChannelsPaginationTest {
    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")

    private fun page(logins: List<String>, cursor: String?): String {
        val data = logins.joinToString(",") {
            """{"broadcaster_id":"id_$it","broadcaster_login":"$it","broadcaster_name":"$it"}"""
        }
        val pagination = if (cursor == null) """{}""" else """{"cursor":"$cursor"}"""
        return """{"data":[$data],"pagination":$pagination}"""
    }

    @Test fun followsAcrossPagesAreConcatenated() = runTest {
        var calls = 0
        var sawAfter: String? = null
        val engine = MockEngine { request ->
            calls++
            sawAfter = request.url.parameters["after"]
            if (sawAfter == null) respond(page(listOf("a", "b"), cursor = "PAGE2"), HttpStatusCode.OK, jsonHeaders)
            else respond(page(listOf("c"), cursor = null), HttpStatusCode.OK, jsonHeaders)
        }
        val client = HttpClient(engine) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }
        val api = TwitchApiClient(client) { "token" }

        val all = api.getAllFollowedChannels("user123")

        assertEquals(listOf("a", "b", "c"), all.map { it.broadcaster_login })
        assertEquals(2, calls, "must follow the cursor for a second page")
        assertEquals("PAGE2", sawAfter, "second request must pass after=cursor")
    }
}
