package com.puretv.twitch.core.emotes

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class EmoteRepositoryGlobalLoadTest {
    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")
    private val sevenTvGlobal = """{"emotes":[{"id":"1","name":"catJAM","flags":0,"data":{"animated":true,"flags":0}}]}"""
    private val bttvGlobal = """[{"id":"b1","code":"FeelsGood","imageType":"png"}]"""

    @Test fun transientBttvFailureDoesNotPoisonTheSession() = runTest {
        var bttvCalls = 0
        val engine = MockEngine { request ->
            val url = request.url.toString()
            when {
                "betterttv" in url -> {
                    bttvCalls++
                    if (bttvCalls == 1) respond("err", HttpStatusCode.InternalServerError)
                    else respond(bttvGlobal, HttpStatusCode.OK, jsonHeaders)
                }
                "7tv.io" in url -> respond(sevenTvGlobal, HttpStatusCode.OK, jsonHeaders)
                else -> respond("", HttpStatusCode.NotFound)
            }
        }
        val client = HttpClient(engine) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }
        val repo = EmoteRepository(client, InMemoryEmoteCache())

        // First load: BTTV 500s, only 7TV emote comes back — must NOT be cached as authoritative.
        val first = repo.loadGlobalEmotes()
        assertEquals(listOf("catJAM"), first.map { it.name })

        // Second load: BTTV now succeeds — both emotes present (no poisoning).
        val second = repo.loadGlobalEmotes()
        assertEquals(setOf("catJAM", "FeelsGood"), second.map { it.name }.toSet())
    }
}
