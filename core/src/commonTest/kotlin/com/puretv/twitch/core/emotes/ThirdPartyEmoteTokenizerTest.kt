package com.puretv.twitch.core.emotes

import com.puretv.twitch.core.model.EmoteProvider
import com.puretv.twitch.core.model.MessagePart
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class ThirdPartyEmoteTokenizerTest {
    private fun idx(vararg entries: Pair<String, Boolean>): Map<String, ResolvedEmote> =
        entries.associate { (code, zw) ->
            code to ResolvedEmote(code, "u/$code", animated = false, provider = EmoteProvider.SEVENTV, zeroWidth = zw)
        }

    @Test fun replacesMatchingWordAndPreservesSurroundingText() {
        val out = applyThirdPartyEmotes(listOf(MessagePart.Text("hello catJAM bye")), idx("catJAM" to false))
        assertEquals(
            listOf(
                MessagePart.Text("hello "),
                MessagePart.ThirdPartyEmote("u/catJAM", "catJAM", EmoteProvider.SEVENTV, animated = false),
                MessagePart.Text(" bye"),
            ),
            out,
        )
    }

    @Test fun preservesWhitespaceRunsWhenNoMatch() {
        val out = applyThirdPartyEmotes(listOf(MessagePart.Text("a  b   c")), idx("zzz" to false))
        assertEquals(listOf(MessagePart.Text("a  b   c")), out)
    }

    @Test fun matchesWholeWordOnlyNotSubstring() {
        val out = applyThirdPartyEmotes(listOf(MessagePart.Text("category")), idx("cat" to false))
        assertEquals(listOf(MessagePart.Text("category")), out)
    }

    @Test fun isCaseSensitive() {
        val out = applyThirdPartyEmotes(listOf(MessagePart.Text("catjam")), idx("catJAM" to false))
        assertEquals(listOf(MessagePart.Text("catjam")), out)
    }

    @Test fun passesThroughNonTextParts() {
        val twitch = MessagePart.TwitchEmote("25", "Kappa")
        // The leading space in " catJAM" flushes as its own Text(" ") part BEFORE
        // the emote, so the emote is the LAST part, not index 1.
        val out = applyThirdPartyEmotes(listOf(twitch, MessagePart.Text(" catJAM")), idx("catJAM" to false))
        assertEquals(twitch, out.first())
        assertEquals(
            MessagePart.ThirdPartyEmote("u/catJAM", "catJAM", EmoteProvider.SEVENTV, false),
            out.last(),
        )
    }

    @Test fun emptyIndexReturnsSameInstance() {
        val input = listOf(MessagePart.Text("catJAM"))
        assertSame(input, applyThirdPartyEmotes(input, emptyMap()))
    }

    @Test fun zeroWidthStacksOntoThirdPartyBase() {
        val out = applyThirdPartyEmotes(
            listOf(MessagePart.Text("catJAM widepeepoHappy")),
            idx("catJAM" to false, "widepeepoHappy" to true),
        )
        assertEquals(1, out.size)
        val base = out[0] as MessagePart.ThirdPartyEmote
        assertEquals("catJAM", base.name)
        assertEquals(listOf("widepeepoHappy"), base.overlays.map { it.name })
    }

    @Test fun zeroWidthStacksOntoTwitchBase() {
        val out = applyThirdPartyEmotes(
            listOf(MessagePart.TwitchEmote("25", "Kappa"), MessagePart.Text(" ZW")),
            idx("ZW" to true),
        )
        assertEquals(1, out.size)
        val base = out[0] as MessagePart.TwitchEmote
        assertEquals("Kappa", base.name)
        assertEquals(listOf("ZW"), base.overlays.map { it.name })
    }

    @Test fun leadingZeroWidthWithNoBaseRendersStandalone() {
        val out = applyThirdPartyEmotes(listOf(MessagePart.Text("ZW hi")), idx("ZW" to true))
        assertEquals(MessagePart.ThirdPartyEmote("u/ZW", "ZW", EmoteProvider.SEVENTV, false), out[0])
        assertEquals(MessagePart.Text(" hi"), out[1])
    }
}
