package com.puretv.twitch.core.stream

import com.puretv.twitch.core.model.ChatMessage
import kotlin.test.Test
import kotlin.test.assertEquals

class ReplayBufferTest {
    private fun c(id: String, off: Int) = ReplayComment(
        offsetSeconds = off,
        message = ChatMessage(id, "", "u", "u", "", "m", emptyList(), emptyList(), 0, false, false, false),
    )

    @Test fun dueFiltersAndSorts() {
        val b = ReplayBuffer()
        b.add(listOf(c("a", 40), c("b", 10), c("c", 100)))
        assertEquals(listOf("b", "a"), b.due(50).map { it.message.id })
    }

    @Test fun addDedupesById() {
        val b = ReplayBuffer()
        b.add(listOf(c("a", 10)))
        b.add(listOf(c("a", 10), c("b", 20)))
        assertEquals(2, b.due(100).size)
        assertEquals(20, b.maxOffsetSeconds)
    }

    @Test fun resetClears() {
        val b = ReplayBuffer()
        b.add(listOf(c("a", 10)))
        b.reset()
        assertEquals(0, b.due(100).size)
    }
}
