package com.puretv.twitch.core.chat

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ChatLoginTest {
    @Test
    fun aTokenAndItsLoginAreSent() {
        assertEquals("tok" to "viewer", ChatLogin.choose("tok", "viewer", rejectedToken = null))
    }

    @Test
    fun withoutBothItReadsAnonymously() {
        assertNull(ChatLogin.choose(null, "viewer", null))
        assertNull(ChatLogin.choose("tok", null, null))
        assertNull(ChatLogin.choose("", "viewer", null))
    }

    @Test
    fun theRefusedTokenIsNotOfferedAgainButANewerOneIs() {
        assertNull(ChatLogin.choose("old", "viewer", rejectedToken = "old"))
        assertEquals("new" to "viewer", ChatLogin.choose("new", "viewer", rejectedToken = "old"))
    }

    @Test
    fun twitchsWelcomeAndRefusalAreRecognised() {
        val welcome = ":tmi.twitch.tv 001 viewer :Welcome, GLHF!\r\n:tmi.twitch.tv 002 viewer :Your host is tmi.twitch.tv\r\n"
        assertTrue(ChatLogin.isWelcome(welcome))
        assertFalse(ChatLogin.isRejected(welcome))
        val refused = ":tmi.twitch.tv NOTICE * :Login authentication failed\r\n"
        assertTrue(ChatLogin.isRejected(refused))
        assertFalse(ChatLogin.isWelcome(refused))
        assertTrue(ChatLogin.isRejected(":tmi.twitch.tv NOTICE * :Improperly formatted auth\r\n"))
    }

    @Test
    fun aChannelNoticeIsNotARefusal() {
        val notice = "@msg-id=msg_banned :tmi.twitch.tv NOTICE #somechannel :Login authentication failed"
        assertFalse(ChatLogin.isRejected(notice))
    }
}
