package com.puretv.twitch.core.stream

import com.puretv.twitch.core.emotes.ResolvedEmote
import com.puretv.twitch.core.model.ChatMessage
import com.puretv.twitch.core.model.EmoteProvider
import com.puretv.twitch.core.model.MessagePart
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class VodReplayEmoteTest {
    private fun comment(vararg parts: MessagePart) = ReplayComment(
        offsetSeconds = 0,
        message = ChatMessage(
            id = "1", channel = "", username = "u", displayName = "U", color = "",
            message = "", parsedParts = parts.toList(), badges = emptyList(), timestamp = 0L,
            isSubscriber = false, isModerator = false, isBroadcaster = false,
        ),
    )

    private val index = mapOf(
        "catJAM" to ResolvedEmote("catJAM", "u/catJAM", animated = false, provider = EmoteProvider.SEVENTV, zeroWidth = false),
    )

    @Test fun tokenizesThirdPartyEmotesInReplayComments() {
        val out = listOf(comment(MessagePart.Text("hi catJAM"))).withThirdPartyEmotes(index)
        assertEquals(
            listOf(
                MessagePart.Text("hi "),
                MessagePart.ThirdPartyEmote("u/catJAM", "catJAM", EmoteProvider.SEVENTV, animated = false),
            ),
            out[0].message.parsedParts,
        )
    }

    @Test fun emptyIndexReturnsSameListUnchanged() {
        val input = listOf(comment(MessagePart.Text("hi catJAM")))
        assertSame(input, input.withThirdPartyEmotes(emptyMap()))
    }
}
