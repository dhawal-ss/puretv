package com.puretv.twitch.core.emotes

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals

class SevenTvEmoteParseTest {
    private val json = Json { ignoreUnknownKeys = true }
    private fun obj(s: String) = json.parseToJsonElement(s).jsonObject

    @Test fun detectsZeroWidthFromActiveFlag() {
        val e = obj("""{"id":"1","name":"ZW","flags":1,"data":{"animated":false}}""").toSevenTvEmote()
        assertEquals(true, e.zeroWidth)
    }

    @Test fun detectsZeroWidthFromDataFlag() {
        val e = obj("""{"id":"1","name":"ZW","flags":0,"data":{"animated":true,"flags":256}}""").toSevenTvEmote()
        assertEquals(true, e.zeroWidth)
        assertEquals(true, e.animated)
    }

    @Test fun normalAnimatedEmoteIsNotZeroWidth() {
        val e = obj("""{"id":"60a","name":"catJAM","flags":0,"data":{"animated":true,"flags":0}}""").toSevenTvEmote()
        assertEquals(false, e.zeroWidth)
        assertEquals(true, e.animated)
        assertEquals("https://cdn.7tv.app/emote/60a/4x.webp", e.url)
    }
}
