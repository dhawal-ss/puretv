package com.puretv.twitch.desktop.ui

import com.puretv.twitch.core.api.TwitchApiClient
import com.puretv.twitch.core.repository.ChannelRepository
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertNotNull

/**
 * Browse/Search must distinguish "the network failed" from "there's genuinely
 * nothing here": a failed load has to surface an error the screen can show (with a
 * retry affordance), not collapse silently into an indistinguishable empty state.
 */
class BrowseSearchErrorStateTest {
    private fun failingClient(): HttpClient {
        val engine = MockEngine { respond("boom", HttpStatusCode.InternalServerError, headersOf(HttpHeaders.ContentType, "application/json")) }
        return HttpClient(engine) { install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) } }
    }

    private fun <T> StateFlow<T>.awaitMatching(predicate: (T) -> Boolean): T =
        runBlocking { withTimeout(5_000) { first(predicate) } }

    @Test fun browseSurfacesErrorWhenTopGamesFails() {
        val api = TwitchApiClient(failingClient()) { "token" }
        val vm = BrowseViewModel(ChannelRepository(api))
        val state = vm.state.awaitMatching { it.error != null }
        assertNotNull(state.error, "a failed topGames load must surface an error, not look empty")
        vm.dispose()
    }

    @Test fun searchSurfacesErrorWhenQueryFails() {
        val api = TwitchApiClient(failingClient()) { "token" }
        val vm = SearchViewModel(ChannelRepository(api))
        vm.onQueryChange("ninja")
        val state = vm.state.awaitMatching { it.error != null }
        assertNotNull(state.error, "a failed search must surface an error, not just empty results")
        vm.dispose()
    }
}
