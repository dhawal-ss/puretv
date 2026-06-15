package com.puretv.twitch.desktop.ui.emotes

import kotlin.test.Test
import kotlin.test.assertEquals

class FrameIndexTest {
    @Test fun emptyDurationsReturnsZero() {
        assertEquals(0, frameIndexAt(0, emptyList()))
        assertEquals(0, frameIndexAt(123, emptyList()))
    }

    @Test fun singleFrameAlwaysZero() {
        assertEquals(0, frameIndexAt(0, listOf(100)))
        assertEquals(0, frameIndexAt(99_999, listOf(100)))
    }

    @Test fun picksFrameByCumulativeDuration() {
        val d = listOf(100, 100, 100)
        assertEquals(0, frameIndexAt(0, d))
        assertEquals(0, frameIndexAt(99, d))
        assertEquals(1, frameIndexAt(100, d))
        assertEquals(2, frameIndexAt(250, d))
    }

    @Test fun loopsAfterTotal() {
        val d = listOf(100, 100)
        assertEquals(0, frameIndexAt(200, d))
        assertEquals(1, frameIndexAt(350, d))
    }

    @Test fun zeroDurationUsesFloorNotDivideByZero() {
        val d = listOf(0, 0)
        assertEquals(0, frameIndexAt(0, d))
        assertEquals(1, frameIndexAt(100, d))
    }
}
