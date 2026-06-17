package com.puretv.twitch.core.chat

import com.puretv.twitch.core.model.ChatEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * IRCv3 message-tag values are escaped (space->\s, ;->\:, \->\\, CR->\r, LF->\n).
 * Without unescaping, reply quotes and USERNOTICE system messages render visible
 * `\s`/`\:` mojibake. See [TwitchIrcParser.unescapeTagValue].
 */
class TagUnescapeTest {

    @Test fun unescapesEachSequence() {
        assertEquals("hello world", TwitchIrcParser.unescapeTagValue("hello\\sworld"))
        assertEquals("a;b", TwitchIrcParser.unescapeTagValue("a\\:b"))
        assertEquals("line1\nline2", TwitchIrcParser.unescapeTagValue("line1\\nline2"))
    }

    @Test fun escapedBackslashIsNotASpace() {
        // "\\s" = literal backslash + 's', NOT a space. Must be left-to-right.
        assertEquals("a\\sb", TwitchIrcParser.unescapeTagValue("a\\\\sb"))
    }

    @Test fun plainValueUnchanged() {
        assertEquals("PureTV", TwitchIrcParser.unescapeTagValue("PureTV"))
    }

    @Test fun replyParentBodyIsUnescapedThroughFullParse() {
        val line = "@reply-parent-display-name=Bob;reply-parent-msg-body=hi\\sthere\\:ok;" +
            "id=abc;display-name=Alice :alice!alice@alice.tmi.twitch.tv PRIVMSG #chan :@Bob sup"
        val event = TwitchIrcParser.parse(line, "chan")
        assertIs<ChatEvent.Message>(event)
        assertEquals("hi there;ok", event.message.replyParentBody)
        assertEquals("Bob", event.message.replyParentDisplayName)
    }
}
