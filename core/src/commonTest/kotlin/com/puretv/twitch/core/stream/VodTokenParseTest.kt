package com.puretv.twitch.core.stream

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class VodTokenParseTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test fun decodesVideoPlaybackAccessToken() {
        val body = """
        {"data":{"videoPlaybackAccessToken":{"value":"{\"vod\":1}","signature":"abc123"}}}
        """.trimIndent()
        val env = json.decodeFromString<GqlEnvelope<PlaybackAccessTokenData>>(body)
        val token = env.data?.videoPlaybackAccessToken
        assertNotNull(token, "VOD responses carry the token under videoPlaybackAccessToken")
        assertEquals("abc123", token.signature)
    }
}
