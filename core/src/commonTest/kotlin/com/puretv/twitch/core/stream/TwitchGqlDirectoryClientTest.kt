package com.puretv.twitch.core.stream

import com.puretv.twitch.core.api.TwitchConfig
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Signed-out browsing reads the Twitch directory through anonymous GQL. These
 * pin the mapping into the Helix-shaped models (fixtures trimmed from live
 * anonymous captures on 2026-10-19), the error handling GQL needs (HTTP 200
 * with an `errors` body), and that the requests stay anonymous.
 */
class TwitchGqlDirectoryClientTest {
    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")

    private fun client(
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ) = TwitchGqlDirectoryClient(HttpClient(MockEngine { request -> handler(request) }))

    private fun replying(body: String, status: HttpStatusCode = HttpStatusCode.OK) =
        client { respond(body, status, jsonHeaders) }

    private fun HttpRequestData.bodyJson(): JsonObject =
        Json.parseToJsonElement((body as TextContent).text).jsonObject

    private val caseohNode = """
        {"id":"320909928288","title":"SPOOKY TIME","viewersCount":61518,"createdAt":"2026-10-20T02:03:33Z","type":"live",
         "previewImageURL":"https://static-cdn.jtvnw.net/previews-ttv/live_user_caseoh_-440x248.jpg",
         "broadcaster":{"id":"267160288","login":"caseoh_","displayName":"caseoh_","broadcastSettings":{"language":"EN"}},
         "game":{"id":"27471","name":"Minecraft","displayName":"Minecraft"},"freeformTags":[{"name":"English"},{"name":"funny"}]}
    """.trimIndent()

    @Test fun topStreamsMapIntoHelixShapedStreams() = runTest {
        val api = replying(
            """
            {"data":{"streams":{"edges":[
              {"node":$caseohNode},
              null,
              {"node":{"id":"9","type":"rerun","broadcaster":{"login":"old"}}},
              {"node":{"id":"10","title":"no broadcaster"}}
            ]}}}
            """.trimIndent(),
        )

        val streams = api.topStreams(first = 20)

        assertEquals(1, streams.size, "null edges, non-live and broadcaster-less nodes are dropped")
        val s = streams.single()
        assertEquals("320909928288", s.id)
        assertEquals("267160288", s.userId)
        assertEquals("caseoh_", s.userLogin)
        assertEquals("caseoh_", s.userName)
        assertEquals("27471", s.gameId)
        assertEquals("Minecraft", s.gameName)
        assertEquals("SPOOKY TIME", s.title)
        assertEquals(61518, s.viewerCount)
        assertEquals("2026-10-20T02:03:33Z", s.startedAt)
        assertEquals("en", s.language, "GQL's upper-case language must match Helix's lower case")
        assertEquals(
            "https://static-cdn.jtvnw.net/previews-ttv/live_user_caseoh_-{width}x{height}.jpg",
            s.thumbnailUrl,
            "screens fill a Helix-style size template, so the concrete size must become one",
        )
        assertEquals(listOf("English", "funny"), s.tags)
    }

    @Test fun pageSizesAreClampedPerQuery() = runTest {
        val firsts = mutableMapOf<String, String?>()
        val api = client { request ->
            val body = request.bodyJson()
            val vars = body["variables"]!!.jsonObject
            val key = when (body["query"]!!.jsonPrimitive.content) {
                TOP_STREAMS_QUERY -> "streams"
                TOP_GAMES_QUERY -> "games"
                SEARCH_QUERY -> "search"
                else -> "other"
            }
            firsts[key] = (vars["first"] ?: vars["limit"])?.jsonPrimitive?.content
            respond("""{"data":{"streams":{"edges":[]},"games":{"edges":[]},"searchFor":null}}""", HttpStatusCode.OK, jsonHeaders)
        }
        api.topStreams(first = 500)
        api.topGames(first = 500)
        api.searchChannels("x", first = 500)
        // Captured: top-level streams answers first=31 with "value must be between 1 and 30".
        assertEquals("30", firsts["streams"])
        assertEquals("100", firsts["games"])
        assertEquals("100", firsts["search"])
    }

    @Test fun topStreamsSendNoCursor() = runTest {
        // Anonymous cursors fail the integrity check, so none is ever sent.
        var variables: JsonObject? = null
        val api = client { request ->
            variables = request.bodyJson()["variables"]?.jsonObject
            respond("""{"data":{"streams":{"edges":[]}}}""", HttpStatusCode.OK, jsonHeaders)
        }
        api.topStreams(first = 25)
        assertEquals("25", variables?.get("first")?.jsonPrimitive?.content)
        assertNull(variables?.get("after"))
    }

    @Test fun topGamesUseDisplayNamesAndSizeTemplates() = runTest {
        val api = replying(
            """
            {"data":{"games":{"edges":[
              {"node":{"id":"509658","name":"Just Chatting","displayName":"Just Chatting","boxArtURL":"https://static-cdn.jtvnw.net/ttv-boxart/509658-144x192.jpg"}},
              {"node":{"id":"27471","name":"Minecraft","displayName":"Minecraft","boxArtURL":"https://static-cdn.jtvnw.net/ttv-boxart/27471_IGDB-144x192.jpg"}},
              {"node":{"id":"","name":"broken"}}
            ]}}}
            """.trimIndent(),
        )

        val games = api.topGames()

        assertEquals(listOf("509658", "27471"), games.map { it.id })
        assertEquals("Just Chatting", games[0].name)
        assertEquals("https://static-cdn.jtvnw.net/ttv-boxart/509658-{width}x{height}.jpg", games[0].boxArtUrl)
        assertEquals("https://static-cdn.jtvnw.net/ttv-boxart/27471_IGDB-{width}x{height}.jpg", games[1].boxArtUrl)
    }

    @Test fun streamsForGameMapsTheGamesStreams() = runTest {
        val api = replying("""{"data":{"game":{"streams":{"edges":[{"node":$caseohNode}]}}}}""")
        assertEquals(listOf("caseoh_"), api.streamsForGame("27471").map { it.userLogin })
    }

    @Test fun unknownGameIsEmptyNotAnError() = runTest {
        val api = replying("""{"data":{"game":null}}""")
        assertEquals(emptyList(), api.streamsForGame("0"))
    }

    @Test fun errorsBesideDataAreAFailure() = runTest {
        // Captured: Twitch's real failures are HTTP 200 with errors AND a data
        // object whose field is null, so "data is present" is not success.
        val api = replying(
            """{"errors":[{"message":"argument 'first' value must be between 1 and 30.","path":["streams"]}],"data":{"streams":null}}""",
        )
        val e = assertFailsWith<GqlDirectoryException> { api.topStreams() }
        assertTrue(e.message!!.contains("between 1 and 30"), "the GQL message is kept for diagnostics")
    }

    @Test fun aFailedIntegrityCheckIsAFailure() = runTest {
        val api = replying(
            """{"errors":[{"message":"failed integrity check","path":["streams"],"extensions":{"code":"IntegrityCheckFailed"}}],""" +
                """"data":{"streams":null}}""",
        )
        assertFailsWith<GqlDirectoryException> { api.topStreams() }
    }

    @Test fun anErrorsBodyWithNoDataIsAFailure() = runTest {
        // Captured: an invalid field is HTTP 200 with errors and no data.
        val api = replying("""{"errors":[{"message":"Cannot query field \"bogusField\" on type \"Stream\"."}]}""")
        val e = assertFailsWith<GqlDirectoryException> { api.topStreams() }
        assertTrue(e.message!!.contains("bogusField"), "the GQL message is kept for diagnostics")
    }

    @Test fun aNon2xxIsAFailure() = runTest {
        val api = replying("""{"error":"Service Unavailable"}""", HttpStatusCode.ServiceUnavailable)
        assertFailsWith<GqlDirectoryException> { api.topGames() }
    }

    @Test fun requestsAreAnonymousAndUseTheWebClientId() = runTest {
        var auth: String? = "SENTINEL"
        var clientId: String? = null
        var url: String? = null
        val api = client { request ->
            auth = request.headers[HttpHeaders.Authorization]
            clientId = request.headers["Client-ID"]
            url = request.url.toString()
            respond("""{"data":{"games":{"edges":[]}}}""", HttpStatusCode.OK, jsonHeaders)
        }
        api.topGames()
        assertNull(auth, "the signed-out directory must never send Authorization")
        assertEquals(TwitchConfig.GQL_CLIENT_ID, clientId)
        assertEquals(TwitchConfig.GQL_ENDPOINT, url)
    }

    @Test fun searchTextTravelsAsAVariableNeverInTheQuery() = runTest {
        val hostile = "a\"}{ user(login: \"x\") { id } }"
        var body: JsonObject? = null
        val api = client { request ->
            body = request.bodyJson()
            respond("""{"data":{"searchFor":{"channels":{"edges":[]}}}}""", HttpStatusCode.OK, jsonHeaders)
        }
        api.searchChannels(hostile)
        assertEquals(SEARCH_QUERY, body?.get("query")?.jsonPrimitive?.content, "the query text is a constant")
        assertEquals(hostile, body?.get("variables")?.jsonObject?.get("query")?.jsonPrimitive?.content)
    }

    @Test fun searchMapsLiveAndOfflineChannelsLikeHelix() = runTest {
        val api = replying(
            """
            {"data":{"searchFor":{"channels":{"edges":[
              {"item":{"id":"37402112","login":"shroud","displayName":"shroud",
                "profileImageURL":"https://static-cdn.jtvnw.net/jtv_user_pictures/shroud-profile_image-300x300.png",
                "broadcastSettings":{"title":"spamming runs","game":{"name":"Escape from Tarkov","displayName":"Escape from Tarkov"}},"stream":null}},
              {"item":{"id":"71092938","login":"xqc","displayName":"xQc","profileImageURL":"https://example/xqc-300x300.jpeg",
                "broadcastSettings":{"title":"old title","game":{"name":"Old","displayName":"Old"}},
                "stream":{"id":"322039747803","title":"LIVE NOW","game":{"name":"Grand Theft Auto V","displayName":"Grand Theft Auto V"}}}},
              {"item":{}}
            ]}}}}
            """.trimIndent(),
        )

        val all = api.searchChannels("sh")
        assertEquals(listOf("shroud", "xqc"), all.map { it.broadcaster_login })
        val offline = all[0]
        assertFalse(offline.is_live)
        assertEquals("spamming runs", offline.title, "offline channels keep their last title, as on Helix")
        assertEquals("Escape from Tarkov", offline.game_name)
        assertEquals("https://static-cdn.jtvnw.net/jtv_user_pictures/shroud-profile_image-300x300.png", offline.thumbnail_url)
        val live = all[1]
        assertTrue(live.is_live)
        assertEquals("xQc", live.display_name)
        assertEquals("LIVE NOW", live.title)
        assertEquals("Grand Theft Auto V", live.game_name)

        assertEquals(listOf("xqc"), api.searchChannels("sh", liveOnly = true).map { it.broadcaster_login })
    }

    @Test fun userMapsToChannelInfo() = runTest {
        val api = replying(
            """
            {"data":{"user":{"id":"37402112","login":"shroud","displayName":"shroud","description":"I'm back baby",
              "profileImageURL":"https://static-cdn.jtvnw.net/jtv_user_pictures/c754-profile_image-300x300.png",
              "offlineImageURL":"https://static-cdn.jtvnw.net/jtv_user_pictures/0e68-channel_offline_image-1920x1080.jpeg",
              "createdAt":"2012-11-03T15:50:32.87847Z","roles":{"isPartner":true,"isAffiliate":false}}}}
            """.trimIndent(),
        )

        val ch = api.user("shroud")!!
        assertEquals("37402112", ch.id)
        assertEquals("shroud", ch.login)
        assertEquals("shroud", ch.displayName)
        assertEquals("I'm back baby", ch.description)
        assertEquals("https://static-cdn.jtvnw.net/jtv_user_pictures/c754-profile_image-300x300.png", ch.profileImageUrl)
        assertEquals("https://static-cdn.jtvnw.net/jtv_user_pictures/0e68-channel_offline_image-1920x1080.jpeg", ch.offlineImageUrl)
        assertEquals("partner", ch.broadcasterType)
        assertEquals("2012-11-03T15:50:32.87847Z", ch.createdAt)
    }

    @Test fun affiliateAndPlainChannelsGetHelixBroadcasterTypes() = runTest {
        val affiliate = replying("""{"data":{"user":{"id":"1","login":"a","roles":{"isPartner":false,"isAffiliate":true}}}}""")
        assertEquals("affiliate", affiliate.user("a")?.broadcasterType)
        val plain = replying("""{"data":{"user":{"id":"2","login":"b","roles":null}}}""")
        assertEquals("", plain.user("b")?.broadcasterType)
    }

    @Test fun unknownLoginIsNull() = runTest {
        assertNull(replying("""{"data":{"user":null}}""").user("nobody"))
    }

    @Test fun liveStreamsForLoginsChunksAt100AndDropsUnknownAndOffline() = runTest {
        val lock = Mutex()
        val chunkSizes = mutableListOf<Int>()
        val api = client { request ->
            val logins = request.bodyJson()["variables"]!!.jsonObject["logins"]!!.jsonArray.map { it.jsonPrimitive.content }
            lock.withLock { chunkSizes += logins.size }
            // First login live, an unknown login (null), everyone else offline.
            val first = logins.first()
            val live = """{"stream":{"id":"s_$first","type":"live","broadcaster":{"id":"id_$first","login":"$first","displayName":"$first"}}}"""
            val rest = logins.drop(1).joinToString(",") { """{"stream":null}""" }
            respond("""{"data":{"users":[$live,null${if (rest.isEmpty()) "" else ",$rest"}]}}""", HttpStatusCode.OK, jsonHeaders)
        }

        val logins = (1..150).map { "chan$it" }
        val live = api.liveStreamsForLogins(logins)

        assertEquals(setOf(100, 50), chunkSizes.toSet(), "chunks of at most 100 logins per call")
        assertEquals(setOf("chan1", "chan101"), live.map { it.userLogin }.toSet())
    }

    @Test fun videosMapIntoHelixShapedVideos() = runTest {
        var variables: JsonObject? = null
        val api = client { request ->
            variables = request.bodyJson()["variables"]?.jsonObject
            respond(
                """
                {"data":{"user":{"videos":{"edges":[
                  {"node":{"id":"2904296965","title":"spamming runs","lengthSeconds":30959,"createdAt":"2026-10-18T16:16:06Z",
                    "publishedAt":"2026-10-18T16:16:06Z","viewCount":227578,"broadcastType":"ARCHIVE",
                    "previewThumbnailURL":"https://static-cdn.jtvnw.net/cf_vods/d2nvs/abc_shroud//thumb/thumb0-320x180.jpg",
                    "owner":{"id":"37402112","login":"shroud","displayName":"shroud"}}},
                  null,
                  {"node":{"title":"no id"}}
                ]}}}}
                """.trimIndent(),
                HttpStatusCode.OK,
                jsonHeaders,
            )
        }

        val videos = api.videosForUser("37402112", com.puretv.twitch.core.model.VideoType.ARCHIVE, first = 500)

        assertEquals("ARCHIVE", variables?.get("type")?.jsonPrimitive?.content)
        assertEquals("100", variables?.get("first")?.jsonPrimitive?.content)
        assertEquals("37402112", variables?.get("id")?.jsonPrimitive?.content)
        val v = videos.single()
        assertEquals("2904296965", v.id)
        assertEquals("37402112", v.userId)
        assertEquals("shroud", v.userLogin)
        assertEquals(com.puretv.twitch.core.model.VideoType.ARCHIVE, v.type)
        assertEquals(30959L, v.durationSeconds)
        assertEquals(227578, v.viewCount)
        assertEquals(
            "https://static-cdn.jtvnw.net/cf_vods/d2nvs/abc_shroud//thumb/thumb0-%{width}x%{height}.jpg",
            v.thumbnailUrl,
            "Helix VOD art is a percent-brace template",
        )
    }

    @Test fun allVideoTypesSendNoTypeVariable() = runTest {
        var variables: JsonObject? = null
        val api = client { request ->
            variables = request.bodyJson()["variables"]?.jsonObject
            respond("""{"data":{"user":null}}""", HttpStatusCode.OK, jsonHeaders)
        }
        assertEquals(emptyList(), api.videosForUser("1", null))
        assertNull(variables?.get("type"))
    }

    @Test fun oneVideoByIdMapsItsTitleChannelAndKind() = runTest {
        var variables: JsonObject? = null
        val api = client { request ->
            variables = request.bodyJson()["variables"]?.jsonObject
            respond(
                """
                {"data":{"video":{"id":"2904296965","title":"best clips","lengthSeconds":600,"createdAt":"2026-10-18T16:16:06Z",
                  "publishedAt":"2026-10-18T16:16:06Z","viewCount":12,"broadcastType":"HIGHLIGHT",
                  "previewThumbnailURL":"https://static-cdn.jtvnw.net/cf_vods/d2nvs/abc_shroud//thumb/thumb0-320x180.jpg",
                  "owner":{"id":"37402112","login":"shroud","displayName":"shroud"}}}}
                """.trimIndent(),
                HttpStatusCode.OK,
                jsonHeaders,
            )
        }
        val v = api.videoById("2904296965")!!
        assertEquals("2904296965", variables?.get("id")?.jsonPrimitive?.content)
        assertEquals("best clips", v.title)
        assertEquals("shroud", v.userLogin)
        assertEquals(com.puretv.twitch.core.model.VideoType.HIGHLIGHT, v.type)
        assertEquals(600L, v.durationSeconds)
    }

    @Test fun anUnknownVideoIdIsNullAndABlankOneAsksNothing() = runTest {
        var calls = 0
        val api = client { calls++; respond("""{"data":{"video":null}}""", HttpStatusCode.OK, jsonHeaders) }
        assertNull(api.videoById("1"))
        assertNull(api.videoById(" "))
        assertEquals(1, calls)
    }

    @Test fun noLoginsMeansNoRequest() = runTest {
        var calls = 0
        val api = client { calls++; respond("{}", HttpStatusCode.OK, jsonHeaders) }
        assertEquals(emptyList(), api.liveStreamsForLogins(emptyList()))
        assertEquals(0, calls)
    }

    // Issue #41: Search found channels only. Fixture trimmed from a live anonymous capture (2026-11-02).
    @Test fun categorySearchMapsGamesWithSizeTemplates() = runTest {
        var body: JsonObject? = null
        val api = client { request ->
            body = request.bodyJson()
            respond(
                """
                {"data":{"searchFor":{"games":{"edges":[
                  {"item":{"id":"1848745913","name":"Battlefield REDSEC","displayName":"Battlefield REDSEC",
                    "boxArtURL":"https://static-cdn.jtvnw.net/ttv-boxart/1848745913_IGDB-144x192.jpg"}},
                  null,
                  {"item":{}}
                ]}}}}
                """.trimIndent(),
                HttpStatusCode.OK,
                jsonHeaders,
            )
        }
        val games = api.searchCategories("Battlefield REDSEC")
        assertEquals(listOf("1848745913"), games.map { it.id })
        assertEquals("Battlefield REDSEC", games[0].name)
        assertEquals("https://static-cdn.jtvnw.net/ttv-boxart/1848745913_IGDB-{width}x{height}.jpg", games[0].boxArtUrl)
        assertEquals(SEARCH_CATEGORIES_QUERY, body?.get("query")?.jsonPrimitive?.content, "the query text is a constant")
        assertEquals("Battlefield REDSEC", body?.get("variables")?.jsonObject?.get("query")?.jsonPrimitive?.content)
    }

    @Test fun blankCategorySearchMakesNoRequest() = runTest {
        val api = client { error("no request expected") }
        assertEquals(emptyList(), api.searchCategories("  "))
    }
}
