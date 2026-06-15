package com.puretv.twitch.core.api

import com.puretv.twitch.core.model.EmoteProvider
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TwitchEmoteParseTest {
    private val json = Json { ignoreUnknownKeys = true }

    // Inline (Android-safe) sample modeled on GET /chat/emotes responses, with
    // extra unknown fields to prove ignoreUnknownKeys tolerance.
    private val body = """
    {
      "data": [
        {
          "id": "304456832",
          "name": "twitchdevPitchfork",
          "format": ["static"],
          "scale": ["1.0", "2.0", "3.0"],
          "theme_mode": ["light", "dark"],
          "emote_type": "subscriptions"
        },
        {
          "id": "emotesv2_anim01",
          "name": "twitchdevHype",
          "format": ["static", "animated"],
          "scale": ["1.0", "2.0", "3.0"],
          "theme_mode": ["light", "dark"],
          "emote_type": "subscriptions"
        },
        {
          "id": "",
          "name": "blankId",
          "format": ["static"]
        },
        {
          "id": "hasIdNoName",
          "name": "",
          "format": ["static"]
        }
      ],
      "template": "https://static-cdn.jtvnw.net/emoticons/v2/{{id}}/{{format}}/{{theme_mode}}/{{scale}}"
    }
    """.trimIndent()

    @Test fun parsesTwitchEmotesPreservingCodesAndFiltering() {
        val env = json.decodeFromString<HelixEnvelope<TwitchEmoteDto>>(body)
        val emotes = parseTwitchEmotes(env)

        // Blank id and blank name entries are filtered out.
        assertEquals(2, emotes.size)
        assertEquals(listOf("twitchdevPitchfork", "twitchdevHype"), emotes.map { it.name })

        // All come back tagged TWITCH.
        assertTrue(emotes.all { it.provider == EmoteProvider.TWITCH })

        val static = emotes.first { it.name == "twitchdevPitchfork" }
        assertEquals(false, static.animated)
        assertTrue(static.url.contains("/static/"), "static emote url should use /static/, was " + static.url)

        val animated = emotes.first { it.name == "twitchdevHype" }
        assertEquals(true, animated.animated)
        assertTrue(animated.url.contains("/animated/"), "animated emote url should use /animated/, was " + animated.url)
    }
}
