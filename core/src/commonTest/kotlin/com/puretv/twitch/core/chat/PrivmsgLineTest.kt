package com.puretv.twitch.core.chat

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PrivmsgLineTest {

    @Test
    fun `no-reply line lowercases channel and has no reply tag`() {
        val line = buildPrivmsgLine("Foo", "hello")
        assertEquals("PRIVMSG #foo :hello", line)
    }

    @Test
    fun `reply line is prefixed with reply-parent-msg-id tag`() {
        val line = buildPrivmsgLine("Foo", "hello", "abc123")
        assertEquals("@reply-parent-msg-id=abc123 PRIVMSG #foo :hello", line)
    }

    @Test
    fun `blank reply parent id produces a plain line`() {
        val line = buildPrivmsgLine("foo", "hello", "   ")
        assertEquals("PRIVMSG #foo :hello", line)
    }

    @Test
    fun `CRLF in body is neutralised to spaces`() {
        val line = buildPrivmsgLine("foo", "a\r\nb\nc\rd")
        assertEquals("PRIVMSG #foo :a  b c d", line)
        assertFalse(line.contains('\r'))
        assertFalse(line.contains('\n'))
    }

    @Test
    fun `over-length body is truncated to the max length`() {
        val body = "x".repeat(MAX_CHAT_MESSAGE_LENGTH + 50)
        val line = buildPrivmsgLine("foo", body)
        val sentBody = line.substringAfter(" :")
        assertEquals(MAX_CHAT_MESSAGE_LENGTH, sentBody.length)
        assertTrue(line.startsWith("PRIVMSG #foo :"))
    }
}
