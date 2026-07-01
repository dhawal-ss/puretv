package com.puretv.twitch.core.emotes

import com.puretv.twitch.core.model.ChannelEmote
import com.puretv.twitch.core.model.EmoteProvider
import kotlin.test.Test
import kotlin.test.assertEquals

class PrioritizeThirdPartyTest {

    private fun emote(name: String, provider: EmoteProvider) =
        ChannelEmote(id = name, name = name, url = "u/$name", provider = provider, animated = false)

    private val ALL = setOf(EmoteProvider.SEVENTV, EmoteProvider.BTTV, EmoteProvider.FFZ)

    @Test fun ordersSevenTvFirstThenBttvThenFfz() {
        val input = listOf(
            emote("a", EmoteProvider.FFZ),
            emote("b", EmoteProvider.BTTV),
            emote("c", EmoteProvider.SEVENTV),
        )
        val out = prioritizeThirdParty(input, ALL).map { it.provider }
        assertEquals(listOf(EmoteProvider.SEVENTV, EmoteProvider.BTTV, EmoteProvider.FFZ), out)
    }

    @Test fun stableWithinProvider() {
        val input = listOf(
            emote("z", EmoteProvider.SEVENTV),
            emote("y", EmoteProvider.SEVENTV),
            emote("x", EmoteProvider.SEVENTV),
        )
        // Same provider => original order preserved (stable sort).
        assertEquals(listOf("z", "y", "x"), prioritizeThirdParty(input, ALL).map { it.name })
    }

    @Test fun filtersDisabledProviders() {
        val input = listOf(
            emote("a", EmoteProvider.SEVENTV),
            emote("b", EmoteProvider.BTTV),
            emote("c", EmoteProvider.FFZ),
        )
        val out = prioritizeThirdParty(input, setOf(EmoteProvider.BTTV))
        assertEquals(listOf("b"), out.map { it.name })
    }

    @Test fun collisionWinnerIsSevenTvAfterIndexBuild() {
        // The whole point: when 7TV and BTTV both define catJAM, the index must pick 7TV.
        val input = listOf(
            emote("catJAM", EmoteProvider.BTTV),
            emote("catJAM", EmoteProvider.SEVENTV),
        )
        val prioritized = prioritizeThirdParty(input, ALL)
        val index = buildEmoteIndex(prioritized, emptyList())
        assertEquals(EmoteProvider.SEVENTV, index["catJAM"]?.provider)
    }

    @Test fun emptyEnabledSetYieldsNothing() {
        val input = listOf(emote("a", EmoteProvider.SEVENTV))
        assertEquals(emptyList(), prioritizeThirdParty(input, emptySet()))
    }
}
