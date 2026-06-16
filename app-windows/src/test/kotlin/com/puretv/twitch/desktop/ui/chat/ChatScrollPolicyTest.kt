package com.puretv.twitch.desktop.ui.chat

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ChatScrollPolicyTest {

    @Test fun userScrollingAwayFromBottomPausesFollowing() {
        assertFalse(nextFollowing(following = true, atBottom = false, userScrolling = true))
    }

    @Test fun reachingBottomResumesFollowing() {
        assertTrue(nextFollowing(following = false, atBottom = true, userScrolling = false))
        // Dragging down INTO the bottom also resumes.
        assertTrue(nextFollowing(following = false, atBottom = true, userScrolling = true))
    }

    @Test fun batchAppendWhileFollowingDoesNotPause() {
        // The VOD bug: a batch of new rows is briefly unmeasured so atBottom reads false,
        // but the user is NOT scrolling. Following must stay true so auto-scroll keeps firing.
        assertTrue(nextFollowing(following = true, atBottom = false, userScrolling = false))
    }

    @Test fun scrolledUpAndIdleStaysPaused() {
        assertFalse(nextFollowing(following = false, atBottom = false, userScrolling = false))
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
