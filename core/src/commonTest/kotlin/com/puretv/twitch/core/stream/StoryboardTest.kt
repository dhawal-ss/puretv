package com.puretv.twitch.core.stream

import kotlin.test.Test
import kotlin.test.assertEquals

class StoryboardTest {
    private fun fixture(): String =
        this::class.java.classLoader.getResourceAsStream("vod/sample-storyboard-info.json")!!
            .bufferedReader().readText()

    private val url = "https://host/path/storyboards/2728868434-info.json"

    @Test fun parsesFixture() {
        val sb = StoryboardParser.parse(url, fixture())
        assertEquals("https://host/path/storyboards", sb.baseUrl)
        assertEquals(2, sb.specs.size)
        val low = sb.spec("low")!!
        assertEquals(5, low.cols); assertEquals(40, low.rows)
        assertEquals(160, low.width); assertEquals(90, low.height)
        assertEquals(93, low.interval); assertEquals(listOf("2728868434-low-0.jpg"), low.images)
    }

    @Test fun tileAtStart() {
        val low = StoryboardParser.parse(url, fixture()).spec("low")!!
        val tile = StoryboardParser.tileAt(low, "https://b", positionSeconds = 0)
        assertEquals("https://b/2728868434-low-0.jpg", tile.imageUrl)
        assertEquals(0, tile.srcXPx); assertEquals(0, tile.srcYPx)
        assertEquals(160, tile.tileWidthPx); assertEquals(90, tile.tileHeightPx)
    }

    @Test fun tileAtSeventhInterval() {
        val low = StoryboardParser.parse(url, fixture()).spec("low")!!
        val tile = StoryboardParser.tileAt(low, "https://b", positionSeconds = 651)
        assertEquals(320, tile.srcXPx); assertEquals(90, tile.srcYPx)
    }

    @Test fun tileClampsBeyondEnd() {
        val low = StoryboardParser.parse(url, fixture()).spec("low")!!
        val tile = StoryboardParser.tileAt(low, "https://b", positionSeconds = 9_999_999)
        assertEquals(4 * 160, tile.srcXPx); assertEquals(39 * 90, tile.srcYPx)
    }

    @Test fun highQualitySpansMultipleImages() {
        val high = StoryboardParser.parse(url, fixture()).spec("high")!!
        val tile = StoryboardParser.tileAt(high, "https://b", positionSeconds = 75L * 93)
        assertEquals("https://b/2728868434-high-1.jpg", tile.imageUrl)
        assertEquals(0, tile.srcXPx); assertEquals(5 * 124, tile.srcYPx)
    }
}
