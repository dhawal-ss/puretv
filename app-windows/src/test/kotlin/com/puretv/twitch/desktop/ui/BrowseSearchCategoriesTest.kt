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
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

/**
 * Issue #41: Browse stopped at the top 20 categories and Search never found a
 * category. Browse now pages through Helix's cursor, and Search asks for categories
 * beside channels without letting a category failure sink the channel results.
 */
class BrowseSearchCategoriesTest {
    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")

    private fun client(handler: (path: String, after: String?) -> Pair<HttpStatusCode, String>): HttpClient {
        val engine = MockEngine { request ->
            val (status, body) = handler(request.url.encodedPath, request.url.parameters["after"])
            respond(body, status, jsonHeaders)
        }
        return HttpClient(engine) { install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) } }
    }

    private fun <T> StateFlow<T>.awaitMatching(predicate: (T) -> Boolean): T =
        runBlocking { withTimeout(5_000) { first(predicate) } }

    private fun games(vararg ids: String) = ids.joinToString(",") { """{"id":"$it","name":"Game $it","box_art_url":""}""" }

    @Test fun browseAppendsTheNextPageUntilTheLast() {
        val api = TwitchApiClient(
            client { _, after ->
                when (after) {
                    null -> HttpStatusCode.OK to """{"data":[${games("1", "2")}],"pagination":{"cursor":"c1"}}"""
                    // The second page repeats "2" (Helix pages can overlap as viewer counts move).
                    "c1" -> HttpStatusCode.OK to """{"data":[${games("2", "3")}],"pagination":{}}"""
                    else -> error("no page after the last")
                }
            },
        ) { "token" }
        val vm = BrowseViewModel(ChannelRepository(api))
        val first = vm.state.awaitMatching { !it.isLoading }
        assertEquals(listOf("1", "2"), first.games.map { it.id })
        assertEquals(true, first.canLoadMore)

        vm.loadMore()
        val second = vm.state.awaitMatching { it.games.size == 3 && !it.isLoadingMore }
        assertEquals(listOf("1", "2", "3"), second.games.map { it.id })
        assertFalse(second.canLoadMore, "the last page has no cursor")
        vm.loadMore() // nothing left to ask for: must not throw or request
        vm.dispose()
    }

    @Test fun searchShowsCategoriesAboveChannels() {
        val api = TwitchApiClient(
            client { path, _ ->
                when {
                    path.endsWith("/search/categories") ->
                        HttpStatusCode.OK to """{"data":[{"id":"1848745913","name":"Battlefield REDSEC","box_art_url":"https://static-cdn.jtvnw.net/ttv-boxart/1848745913_IGDB-52x72.jpg"}]}"""
                    path.endsWith("/search/channels") ->
                        HttpStatusCode.OK to """{"data":[{"id":"9","broadcaster_login":"battlefield","display_name":"Battlefield","is_live":false}]}"""
                    else -> error("unexpected $path")
                }
            },
        ) { "token" }
        val vm = SearchViewModel(ChannelRepository(api))
        vm.onQueryChange("battlefield redsec")
        val state = vm.state.awaitMatching { !it.isSearching && it.results.isNotEmpty() }
        assertEquals(listOf("Battlefield REDSEC"), state.categories.map { it.name })
        assertEquals(
            "https://static-cdn.jtvnw.net/ttv-boxart/1848745913_IGDB-{width}x{height}.jpg",
            state.categories.single().boxArtUrl,
            "Helix's fixed-size art is turned back into a template the tiles can fill",
        )
        vm.dispose()
    }

    @Test fun aCategoryFailureStillShowsTheChannels() {
        val api = TwitchApiClient(
            client { path, _ ->
                if (path.endsWith("/search/categories")) {
                    HttpStatusCode.InternalServerError to "boom"
                } else {
                    HttpStatusCode.OK to """{"data":[{"id":"9","broadcaster_login":"ninja","display_name":"Ninja","is_live":true}]}"""
                }
            },
        ) { "token" }
        val vm = SearchViewModel(ChannelRepository(api))
        vm.onQueryChange("ninja")
        val state = vm.state.awaitMatching { !it.isSearching && (it.results.isNotEmpty() || it.error != null) }
        assertNull(state.error, "categories are a bonus: their failure is not a failed search")
        assertEquals(listOf("ninja"), state.results.map { it.broadcaster_login })
        assertEquals(emptyList(), state.categories)
        vm.dispose()
    }

    @Test fun aToZLoadsEveryPageBeforeSorting() {
        val api = TwitchApiClient(
            client { _, after ->
                when (after) {
                    null -> HttpStatusCode.OK to """{"data":[{"id":"1","name":"Rust","box_art_url":""},{"id":"2","name":"Minecraft","box_art_url":""}],"pagination":{"cursor":"c1"}}"""
                    // The category that sorts first is on the last page.
                    "c1" -> HttpStatusCode.OK to """{"data":[{"id":"3","name":"Apex Legends","box_art_url":""}],"pagination":{}}"""
                    else -> error("no page after the last")
                }
            },
        ) { "token" }
        val vm = BrowseViewModel(ChannelRepository(api))
        vm.state.awaitMatching { !it.isLoading }
        vm.setSort(com.puretv.twitch.core.model.CategorySort.NAME)
        val sorted = vm.state.awaitMatching { it.games.size == 3 && !it.isLoadingMore }
        assertEquals(listOf("Apex Legends", "Minecraft", "Rust"), sorted.games.map { it.name })
        vm.setSort(com.puretv.twitch.core.model.CategorySort.VIEWERS)
        assertEquals(listOf("1", "2", "3"), vm.state.value.games.map { it.id }, "back to Twitch's order")
        vm.dispose()
    }
}
