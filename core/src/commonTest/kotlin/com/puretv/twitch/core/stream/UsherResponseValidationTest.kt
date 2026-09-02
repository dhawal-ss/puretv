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
import kotlin.test.assertFails
import kotlin.test.assertTrue

/**
 * The shared Ktor client deliberately does NOT validate usher responses
 * (`HttpResponseValidator` in `CoreModule` is scoped to Helix, and Ktor's
 * `expectSuccess` defaults to false), so a usher rejection arrives here as an
 * ordinary response carrying a JSON error body.
 *
 * Captured from live traffic on 2026-09-01: a usher master fetch with a bad
 * signature answers `403` with a JSON array body, NOT a playlist. Without a
 * check, [StreamResolver.resolveMasterPlaylist] returns that as a SUCCESS —
 * `variants` parses to empty and `masterUrl` points at a URL that will never
 * produce media. The caller stores it as `playableUrl`, the player fails on it,
 * and the viewer gets a black screen with no audio and no error, while chat
 * (a separate connection) keeps working. Resolution must fail loudly instead.
 */
class UsherResponseValidationTest {

    private val tokenJson =
        """{"data":{"streamPlaybackAccessToken":{"value":"{}","signature":"sig"}}}"""

    /** Live-captured shape of a usher rejection body. */
    private val usher403Body =
        """[{"url":"/api/channel/hls/teamliquid.m3u8","error":"Signature is invalid","error_code":"invalid_signature"}]"""

    private val validMaster = """
        #EXTM3U
        #EXT-X-STREAM-INF:BANDWIDTH=8029169,RESOLUTION=1920x1080,CODECS="avc1.64002A,mp4a.40.2",VIDEO="chunked",FRAME-RATE=60.000
        https://video-edge.example/chunked.m3u8
    """.trimIndent()

    private fun resolver(usherStatus: HttpStatusCode, usherBody: String): StreamResolver {
        val client = HttpClient(MockEngine { request ->
            if (request.url.host.contains("gql")) {
                respond(tokenJson, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
            } else {
                respond(usherBody, usherStatus, headersOf(HttpHeaders.ContentType, "application/json"))
            }
        })
        return StreamResolver(client, TwitchGqlClient(client))
    }

    @Test
    fun usherRejectionFailsInsteadOfReturningADeadUrl() = runTest {
        val failure = assertFails {
            resolver(HttpStatusCode.Forbidden, usher403Body).resolveMasterPlaylist("teamliquid")
        }
        val message = failure.message.orEmpty()
        assertTrue(
            "403" in message,
            "the failure must name usher's status so the viewer/log can tell a rejection from an outage: $message",
        )
    }

    @Test
    fun usherBodyThatIsNotAPlaylistFails() = runTest {
        val failure = assertFails {
            resolver(HttpStatusCode.OK, usher403Body).resolveMasterPlaylist("teamliquid")
        }
        assertTrue(
            failure.message.orEmpty().isNotBlank(),
            "a 200 carrying a non-playlist body is still not playable and must fail",
        )
    }

    @Test
    fun healthyMasterStillResolves() = runTest {
        val result = resolver(HttpStatusCode.OK, validMaster).resolveMasterPlaylist("teamliquid")
        assertEquals(1, result.variants.size, "a real master must still parse into its variant ladder")
        assertTrue(result.masterUrl.startsWith("https://usher.ttvnw.net/"), result.masterUrl)
    }
}
