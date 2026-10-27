package com.puretv.twitch.desktop.player

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class VariantFallbackTest {
    @Test
    fun aFailedRefreshGetsTheLastPlaylistAFewTimesThenTheError() {
        val fallback = VariantFallback(maxInARow = 2)
        fallback.remember("v1", "#EXTM3U\nlast")
        assertEquals("#EXTM3U\nlast", fallback.fallback("v1"))
        assertEquals("#EXTM3U\nlast", fallback.fallback("v1"))
        assertNull(fallback.fallback("v1"))
    }

    @Test
    fun aGoodRefreshRestoresTheAllowance() {
        val fallback = VariantFallback(maxInARow = 1)
        fallback.remember("v1", "a")
        assertEquals("a", fallback.fallback("v1"))
        assertNull(fallback.fallback("v1"))
        fallback.remember("v1", "b")
        assertEquals("b", fallback.fallback("v1"))
    }

    @Test
    fun nothingServedYetMeansNothingToFallBackOn() {
        assertNull(VariantFallback().fallback("never"))
    }

    @Test
    fun variantsAreSeparateAndTheLeastRecentlyUsedGoesFirst() {
        val fallback = VariantFallback(maxEntries = 2)
        fallback.remember("a", "A")
        fallback.remember("b", "B")
        fallback.remember("c", "C")
        assertNull(fallback.fallback("a"))
        assertEquals("B", fallback.fallback("b"))
        assertEquals("C", fallback.fallback("c"))
    }
}
