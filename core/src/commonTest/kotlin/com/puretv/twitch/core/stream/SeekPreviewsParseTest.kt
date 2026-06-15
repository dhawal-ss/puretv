package com.puretv.twitch.core.stream

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class SeekPreviewsParseTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test fun decodesSeekPreviewsUrl() {
        val body = """{"data":{"video":{"seekPreviewsURL":"https://h/storyboards/1-info.json"}}}"""
        val env = json.decodeFromString<GqlEnvelope<VideoSeekPreviewsData>>(body)
        assertEquals("https://h/storyboards/1-info.json", env.data?.video?.seekPreviewsURL)
    }
}
