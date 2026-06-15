package com.puretv.twitch.core.api

import com.puretv.twitch.core.model.VideoType
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class GetVideosParseTest {
    private val json = Json { ignoreUnknownKeys = true }

    private val body = """
    {
      "data": [{
        "id": "335921245",
        "user_id": "141981764",
        "user_login": "twitchdev",
        "user_name": "TwitchDev",
        "title": "Twitch Developers 101",
        "description": "demo",
        "created_at": "2021-03-31T20:57:26Z",
        "published_at": "2021-03-31T20:57:26Z",
        "thumbnail_url": "https://x/%{width}x%{height}.jpg",
        "view_count": 1863062,
        "type": "archive",
        "duration": "3h8m33s",
        "muted_segments": [{"duration": 30, "offset": 120}]
      }],
      "pagination": {"cursor": "eyJiIjpudWxsf"}
    }
    """.trimIndent()

    @Test fun decodesAndMapsVideoPage() {
        val env = json.decodeFromString<HelixPagedEnvelope<HelixVideo>>(body)
        assertEquals("eyJiIjpudWxsf", env.pagination.cursor)
        val v = env.data.single().toDomain()
        assertEquals("335921245", v.id)
        assertEquals(VideoType.ARCHIVE, v.type)
        assertEquals(11313L, v.durationSeconds)
        assertEquals(1, v.mutedSegments.size)
        assertEquals(120, v.mutedSegments[0].offsetSeconds)
    }
}
