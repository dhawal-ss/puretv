package com.puretv.twitch.core.repository

import com.puretv.twitch.core.api.TwitchApiClient
import com.puretv.twitch.core.stream.StreamResolver
import com.puretv.twitch.core.stream.TwitchGqlClient
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Regression test for a bug where the "Following - Live now" rail on Android
 * and TV silently lost channels: [StreamRepository.streamsForChannels] passed
 * every login straight through to one Helix /streams call, which caps
 * `user_login` at 100 and defaults `first` (a page-size limit on the matched
 * results, not just a pagination hint) to 20. A caller with more than 100
 * followed channels, or more than 20 simultaneously live, would lose results
 * with no error.
 */
class StreamsForChannelsChunkingTest {
    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")

    private fun streamsBody(logins: List<String>): String {
        val data = logins.joinToString(",") {
            """{"id":"s_$it","user_id":"id_$it","user_login":"$it","user_name":"$it","game_name":"","title":"","viewer_count":1,"started_at":"","thumbnail_url":""}"""
        }
        return """{"data":[$data]}"""
    }

    @Test fun chunksOver100LoginsAndUnionsResults() = runTest {
        val seenLoginCounts = mutableListOf<Int>()
        val seenFirstValues = mutableListOf<String?>()
        val lock = Mutex()
        val logins = (1..150).map { "chan$it" }
        val engine = MockEngine { request ->
            val loginParams = request.url.parameters.getAll("user_login").orEmpty()
            lock.withLock {
                seenLoginCounts += loginParams.size
                seenFirstValues += request.url.parameters["first"]
            }
            respond(streamsBody(loginParams), HttpStatusCode.OK, jsonHeaders)
        }
        val client = HttpClient(engine) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }
        val repo = StreamRepository(TwitchApiClient(client) { "token" }, StreamResolver(client, TwitchGqlClient(client)))

        val result = repo.streamsForChannels(logins)

        assertEquals(150, result.size, "every login's stream must come back, not just the first 100")
        assertEquals(setOf(100, 50), seenLoginCounts.toSet(), "must chunk at Helix's 100-login cap")
        assertTrue(seenFirstValues.all { it == "100" }, "must request first=100 per chunk so a busy chunk isn't truncated to the default 20")
    }
}
