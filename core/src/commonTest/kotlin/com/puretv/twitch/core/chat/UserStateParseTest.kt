package com.puretv.twitch.core.chat

import com.puretv.twitch.core.model.ChatEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class UserStateParseTest {
    @Test fun userStateBecomesSelfStateWithChannelBadges() {
        val line = "@badges=subscriber/12;color=#FF0000;display-name=Dhawal :tmi.twitch.tv USERSTATE #shroud"
        val event = TwitchIrcParser.parse(line, "shroud")
        assertIs<ChatEvent.SelfState>(event)
        assertEquals("Dhawal", event.displayName)
        assertEquals("#FF0000", event.color)
        assertTrue(event.badges.any { it.setId == "subscriber" })
    }

    @Test fun userStateWithoutColorFallsBackToDefault() {
        val line = "@badges=;color=;display-name=Dhawal :tmi.twitch.tv USERSTATE #shroud"
        val event = TwitchIrcParser.parse(line, "shroud")
        assertIs<ChatEvent.SelfState>(event)
        assertEquals("#9B5DE5", event.color)
    }
}
