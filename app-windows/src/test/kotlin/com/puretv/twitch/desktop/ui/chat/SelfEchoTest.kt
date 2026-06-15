package com.puretv.twitch.desktop.ui.chat

import com.puretv.twitch.core.model.Badge
import com.puretv.twitch.core.model.MessagePart
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SelfEchoTest {
    @Test fun usesDisplayNameColorAndText() {
        val m = buildSelfEcho(
            id = "self-1", login = "dhawal", displayName = "Dhawal", color = "#FF0000",
            badges = emptyList(), channel = "shroud", text = "hi there", timestamp = 1000L,
        )
        assertEquals("dhawal", m.username)
        assertEquals("Dhawal", m.displayName)
        assertEquals("#FF0000", m.color)
        assertEquals("hi there", m.message)
        assertEquals(listOf(MessagePart.Text("hi there")), m.parsedParts)
        assertEquals("shroud", m.channel)
        assertEquals(1000L, m.timestamp)
        assertNull(m.replyParentDisplayName)
    }

    @Test fun fallsBackToLoginAndDefaultColorWhenBlank() {
        val m = buildSelfEcho(
            id = "self-2", login = "dhawal", displayName = null, color = "",
            badges = emptyList(), channel = "shroud", text = "yo", timestamp = 1L,
        )
        assertEquals("dhawal", m.displayName)
        assertEquals("#9B5DE5", m.color)
    }

    @Test fun derivesBadgeFlags() {
        val subMod = buildSelfEcho(
            id = "self-3", login = "x", displayName = "X", color = "#fff",
            badges = listOf(Badge("subscriber", "12"), Badge("moderator", "1")),
            channel = "c", text = "t", timestamp = 0L,
        )
        assertTrue(subMod.isSubscriber)
        assertTrue(subMod.isModerator)
        assertFalse(subMod.isBroadcaster)
    }

    @Test fun carriesReplyParentContext() {
        val parent = buildSelfEcho(
            id = "p", login = "bob", displayName = "Bob", color = "#000",
            badges = emptyList(), channel = "c", text = "hello", timestamp = 0L,
        )
        val reply = buildSelfEcho(
            id = "self-4", login = "x", displayName = "X", color = "#fff",
            badges = emptyList(), channel = "c", text = "re", timestamp = 0L, replyParent = parent,
        )
        assertEquals("Bob", reply.replyParentDisplayName)
        assertEquals("hello", reply.replyParentBody)
    }
}
