package com.puretv.twitch.desktop.ui.chat

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ChatScrollPolicyTest {

    @Test fun shouldStickMirrorsAtBottom() {
        assertTrue(shouldStick(atBottom = true))
        assertFalse(shouldStick(atBottom = false))
    }

    @Test fun scrollAnchorChangesPerNewMessageEvenAtConstantSize() {
        fun m(id: String) = buildSelfEcho(
            id = id, login = "u", displayName = null, color = null,
            badges = emptyList(), channel = "c", text = "x", timestamp = 0L,
        )
        // Two buffers of identical size differing only by a newer last message —
        // exactly what the capped takeLast(200) buffer produces on each new message.
        val before = listOf(m("1"), m("2"))
        val after = listOf(m("2"), m("3"))
        // The bug: keying the auto-scroll effect on size (2 == 2) makes it stop
        // firing once the buffer caps. The anchor must differ so it keeps firing.
        assertNotEquals(scrollAnchor(before), scrollAnchor(after))
        assertEquals("3", scrollAnchor(after))
        assertNull(scrollAnchor(emptyList()))
    }
}
