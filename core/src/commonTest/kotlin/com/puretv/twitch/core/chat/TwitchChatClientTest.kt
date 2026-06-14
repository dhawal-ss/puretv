package com.puretv.twitch.core.chat

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Security regression tests for outgoing chat line construction.
 *
 * The headline case is CRLF command injection: an outgoing PRIVMSG body that
 * contains `\r`/`\n` would otherwise be parsed by Twitch IRC as multiple
 * commands, letting a crafted/pasted message forge JOIN/PRIVMSG/etc. under the
 * authenticated user's own `chat:edit` session.
 */
class TwitchChatClientTest {

    @Test
    fun buildsWellFormedPrivmsgForNormalMessage() {
        assertEquals("PRIVMSG #shroud :Hello Kappa", buildPrivmsgLine("shroud", "Hello Kappa"))
    }

    @Test
    fun lowercasesChannel() {
        assertEquals("PRIVMSG #shroud :hi", buildPrivmsgLine("Shroud", "hi"))
    }

    @Test
    fun stripsCarriageReturnAndNewlineFromMessageBody() {
        val line = buildPrivmsgLine("chan", "hello\r\nJOIN #victim")
        assertFalse(line.contains('\r'), "carriage return must be neutralised: $line")
        assertFalse(line.contains('\n'), "newline must be neutralised: $line")
    }

    @Test
    fun crlfInjectionCannotForgeASecondIrcCommand() {
        val line = buildPrivmsgLine("chan", "spam\r\nJOIN #victim\r\nPRIVMSG #other :x")
        assertEquals(1, line.lines().size, "message must collapse to a single IRC line: $line")
        assertTrue(line.startsWith("PRIVMSG #chan :"), "must remain a single PRIVMSG: $line")
        assertFalse(line.contains("\nJOIN"), "forged JOIN must not survive as its own line: $line")
    }

    @Test
    fun truncatesToTwitchMessageLengthLimit() {
        val body = buildPrivmsgLine("chan", "a".repeat(600)).removePrefix("PRIVMSG #chan :")
        assertEquals(MAX_CHAT_MESSAGE_LENGTH, body.length, "body must be truncated to Twitch's limit")
    }
}
