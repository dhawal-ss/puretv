package com.puretv.twitch.core.repository

import com.puretv.twitch.core.CallCounter
import com.puretv.twitch.core.api.HelixApiException
import com.puretv.twitch.core.api.TwitchApiClient
import com.puretv.twitch.core.di.buildKtorClient
import com.puretv.twitch.core.stream.StreamResolver
import com.puretv.twitch.core.stream.VodResolver
import com.puretv.twitch.core.stream.TwitchGqlClient
import com.puretv.twitch.core.stream.TwitchGqlDirectoryClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Owner decision D5.1: the anonymous GQL directory is used only while there is
 * no user token. Signed in, every public read stays on Helix (no extra calls),
 * and a Helix 401 with a token is still an error, never quietly swapped for GQL.
 *
 * Built on the real [buildKtorClient] so the Helix response validator runs.
 */
class SignedOutDirectoryRoutingTest {
    // VodRepository is in this package; VodResolver is imported above.
    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")

    // One body that satisfies every directory query's data shape.
    private val gqlBody =
        """{"data":{"streams":{"edges":[]},"games":{"edges":[]},"game":null,"searchFor":null,"user":null,"users":[]}}"""

    private class Harness(
        val streams: StreamRepository,
        val channels: ChannelRepository,
        val helixCalls: CallCounter,
        val gqlCalls: CallCounter,
    )

    private fun harness(helixStatus: HttpStatusCode = HttpStatusCode.OK, token: () -> String?): Harness {
        val helix = CallCounter()
        val gql = CallCounter()
        val engine = MockEngine { request ->
            when (request.url.host) {
                "api.twitch.tv" -> {
                    helix.incrementAndGet()
                    respond("""{"data":[]}""", helixStatus, jsonHeaders)
                }
                "gql.twitch.tv" -> {
                    gql.incrementAndGet()
                    respond(gqlBody, HttpStatusCode.OK, jsonHeaders)
                }
                else -> error("unexpected host ${request.url.host}")
            }
        }
        val client = buildKtorClient(engine)
        val api = TwitchApiClient(client, token)
        val directory = TwitchGqlDirectoryClient(client)
        return Harness(
            streams = StreamRepository(api, StreamResolver(client, TwitchGqlClient(client)), directory),
            channels = ChannelRepository(api, directory),
            helixCalls = helix,
            gqlCalls = gql,
        )
    }

    private suspend fun Harness.readEverything() {
        streams.refreshTopStreams()
        streams.refreshTopStreams(gameId = "509658")
        streams.streamsForChannels(listOf("a", "b"))
        streams.streamsForGame("509658")
        channels.getChannel("shroud")
        channels.search("shroud")
        channels.topGames()
    }

    @Test fun signedOutReadsGoThroughGqlOnly() = runTest {
        val h = harness { null }
        h.readEverything()
        assertEquals(0, h.helixCalls.get(), "with no token nothing may reach Helix")
        assertEquals(7, h.gqlCalls.get())
    }

    @Test fun aBlankTokenCountsAsSignedOut() = runTest {
        val h = harness { "" }
        h.readEverything()
        assertEquals(0, h.helixCalls.get(), "a blank token would only earn a Helix 401")
    }

    @Test fun signedInReadsStayOnHelixWithNoExtraCalls() = runTest {
        val h = harness { "user-token" }
        h.readEverything()
        assertEquals(0, h.gqlCalls.get(), "signed in, the directory must not add GQL calls")
        assertEquals(7, h.helixCalls.get())
    }

    @Test fun aHelix401WithATokenIsStillAnError() = runTest {
        val h = harness(HttpStatusCode.Unauthorized) { "expired-token" }
        assertFailsWith<HelixApiException> { h.streams.refreshTopStreams() }
        assertFailsWith<HelixApiException> { h.channels.topGames() }
        assertEquals(0, h.gqlCalls.get(), "a sign-in problem must not be masked by the anonymous directory")
    }

    @Test fun theRouteFollowsTheTokenAsItChanges() = runTest {
        var token: String? = null
        val h = harness { token }
        h.channels.topGames()
        assertEquals(1, h.gqlCalls.get())

        token = "fresh-sign-in"
        h.channels.topGames()
        assertEquals(1, h.helixCalls.get(), "signing in moves the next read to Helix")
        assertEquals(1, h.gqlCalls.get())
    }

    @Test fun videosRouteByTokenAndSignedOutHasNoNextPage() = runTest {
        suspend fun run(token: String?): Triple<Int, Int, String?> {
            val helix = CallCounter()
            val gql = CallCounter()
            val engine = MockEngine { request ->
                if (request.url.host == "gql.twitch.tv") {
                    gql.incrementAndGet()
                    respond("""{"data":{"user":{"videos":{"edges":[{"node":{"id":"7"}}]}}}}""", HttpStatusCode.OK, jsonHeaders)
                } else {
                    helix.incrementAndGet()
                    respond("""{"data":[],"pagination":{"cursor":"h"}}""", HttpStatusCode.OK, jsonHeaders)
                }
            }
            val client = buildKtorClient(engine)
            val repo = VodRepository(
                TwitchApiClient(client) { token },
                VodResolver(client, TwitchGqlClient(client)),
                TwitchGqlDirectoryClient(client),
            )
            val page = repo.videosFor("42")
            return Triple(helix.get(), gql.get(), page.cursor).also {
                if (token == null) assertEquals(listOf("7"), page.videos.map { v -> v.id })
            }
        }
        assertEquals(Triple(0, 1, null), run(null), "signed out: GQL only, no cursor")
        assertEquals(Triple(1, 0, "h"), run("tok"), "signed in: Helix only, cursor kept")
    }

    @Test fun withoutADirectoryEverythingStaysOnHelix() = runTest {
        val helix = CallCounter()
        val engine = MockEngine { request ->
            assertEquals("api.twitch.tv", request.url.host)
            helix.incrementAndGet()
            respond("""{"data":[]}""", HttpStatusCode.OK, jsonHeaders)
        }
        val api = TwitchApiClient(buildKtorClient(engine)) { null }
        // The constructors without a directory keep the old Helix-only behaviour.
        assertEquals(emptyList(), ChannelRepository(api).topGames())
        assertEquals(1, helix.get())
    }

    // Issue #41: Browse showed only 20 categories and Search found no categories.
    @Test fun browsePagesAndCategorySearchFollowTheToken() = runTest {
        val out = harness { null }
        val page = out.channels.topGamesPage()
        assertEquals(null, page.cursor, "anonymous GQL refuses cursors, so signed out is one page")
        assertEquals(emptyList(), out.channels.topGamesPage(after = "anything").games)
        out.channels.searchCategories("battlefield")
        assertEquals(0, out.helixCalls.get())
        assertEquals(2, out.gqlCalls.get(), "a cursor page signed out makes no request")

        val signedIn = harness { "user-token" }
        signedIn.channels.topGamesPage()
        signedIn.channels.topGamesPage(after = "c1")
        signedIn.channels.searchCategories("battlefield")
        assertEquals(0, signedIn.gqlCalls.get())
        assertEquals(3, signedIn.helixCalls.get())
    }
}
