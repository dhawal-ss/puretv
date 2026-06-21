package com.puretv.twitch.core.stream

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Regression lock for the desktop AD-BLOCK REGRESSION: threading the user OAuth
 * token into the GQL PlaybackAccessToken mint makes Twitch reject it (the mint's
 * Client-ID is the web GQL client, not our app client) and blanks the screen. The
 * mint must stay anonymous on the playback path; only the Helix REST client may
 * carry the user token.
 */
class PlaybackTokenMintAnonymityTest {

    private fun clientCapturing(sink: (String?) -> Unit) = HttpClient(MockEngine { request ->
        sink(request.headers[HttpHeaders.Authorization])
        respond(
            content = """{"data":{"streamPlaybackAccessToken":{"value":"{}","signature":"s"}}}""",
            status = HttpStatusCode.OK,
            headers = headersOf(HttpHeaders.ContentType, "application/json"),
        )
    })

    @Test fun anonymousMintSendsNoAuthorizationHeader() = runTest {
        var auth: String? = "SENTINEL"
        val gql = TwitchGqlClient(clientCapturing { auth = it })
        runCatching { gql.fetchStreamToken(channelLogin = "twitch", oauthToken = null) }
        assertNull(auth, "anonymous playback-token mint must not send Authorization")
    }

    @Test fun explicitTokenIsSentWhenProvided() = runTest {
        var auth: String? = null
        val gql = TwitchGqlClient(clientCapturing { auth = it })
        runCatching { gql.fetchStreamToken(channelLogin = "twitch", oauthToken = "abc") }
        assertEquals("OAuth abc", auth, "when a token IS passed it must be an OAuth header")
    }
}
