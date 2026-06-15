package com.puretv.twitch.desktop.ui.chat

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ChatScrollPolicyTest {

    @Test fun shouldStickMirrorsAtBottom() {
        assertTrue(shouldStick(atBottom = true))
        assertFalse(shouldStick(atBottom = false))
    }

    @Test fun nextUnreadClearsAtBottom() {
        // At bottom always clears, regardless of growth or prior state.
        assertFalse(nextUnread(atBottom = true, messagesGrew = true, current = true))
        assertFalse(nextUnread(atBottom = true, messagesGrew = false, current = true))
        assertFalse(nextUnread(atBottom = true, messagesGrew = true, current = false))
        assertFalse(nextUnread(atBottom = true, messagesGrew = false, current = false))
    }

    @Test fun nextUnreadSetsWhenGrewWhileScrolledUp() {
        assertTrue(nextUnread(atBottom = false, messagesGrew = true, current = false))
        assertTrue(nextUnread(atBottom = false, messagesGrew = true, current = true))
    }

    @Test fun nextUnreadUnchangedWhenNoGrowthWhileScrolledUp() {
        // Not at bottom, no growth: preserve the current flag.
        assertTrue(nextUnread(atBottom = false, messagesGrew = false, current = true))
        assertFalse(nextUnread(atBottom = false, messagesGrew = false, current = false))
    }

    @Test fun unchangedValueRoundTrips() {
        // Sanity: equality form used by the scroll effect.
        assertEquals(true, nextUnread(atBottom = false, messagesGrew = false, current = true))
        assertEquals(false, nextUnread(atBottom = true, messagesGrew = true, current = true))
    }
}
