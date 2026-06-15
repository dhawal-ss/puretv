package com.puretv.twitch.core.emotes

import com.puretv.twitch.core.model.ChannelEmote
import com.puretv.twitch.core.model.EmoteProvider
import kotlin.test.Test
import kotlin.test.assertEquals

class PickableEmoteTest {
    private fun emote(name: String, provider: EmoteProvider, url: String = "u/" + name, animated: Boolean = false) =
        ChannelEmote(id = "id_" + name, name = name, url = url, provider = provider, animated = animated)

    @Test fun ordersChannelTwitchThenChannelThirdPartyThenGlobalTwitchThenGlobalThirdParty() {
        val result = buildPickableEmotes(
            twitchChannel = listOf(emote("ChanT", EmoteProvider.TWITCH)),
            thirdPartyChannel = listOf(emote("ChanBTTV", EmoteProvider.BTTV)),
            twitchGlobal = listOf(emote("GlobT", EmoteProvider.TWITCH)),
            thirdPartyGlobal = listOf(emote("GlobFFZ", EmoteProvider.FFZ)),
        )
        assertEquals(listOf("ChanT", "ChanBTTV", "GlobT", "GlobFFZ"), result.map { it.code })
    }

    @Test fun mapsSourcesCorrectly() {
        val result = buildPickableEmotes(
            twitchChannel = listOf(emote("ChanT", EmoteProvider.TWITCH)),
            thirdPartyChannel = listOf(
                emote("B", EmoteProvider.BTTV),
                emote("F", EmoteProvider.FFZ),
                emote("S", EmoteProvider.SEVENTV),
            ),
            twitchGlobal = listOf(emote("GlobT", EmoteProvider.TWITCH)),
            thirdPartyGlobal = emptyList(),
        )
        val bySource = result.associate { it.code to it.source }
        assertEquals(EmoteSource.TWITCH_CHANNEL, bySource["ChanT"])
        assertEquals(EmoteSource.BTTV, bySource["B"])
        assertEquals(EmoteSource.FFZ, bySource["F"])
        assertEquals(EmoteSource.SEVENTV, bySource["S"])
        assertEquals(EmoteSource.TWITCH_GLOBAL, bySource["GlobT"])
    }

    @Test fun dedupesByCodeKeepingFirstOccurrenceAndItsSource() {
        // Same code appears as a channel Twitch emote AND a global third-party
        // emote; the channel one wins (first), keeping its source + url.
        val result = buildPickableEmotes(
            twitchChannel = listOf(emote("Dup", EmoteProvider.TWITCH, url = "channel-url")),
            thirdPartyChannel = emptyList(),
            twitchGlobal = emptyList(),
            thirdPartyGlobal = listOf(emote("dup", EmoteProvider.SEVENTV, url = "global-url")),
        )
        assertEquals(1, result.size)
        assertEquals("Dup", result[0].code)
        assertEquals("channel-url", result[0].imageUrl)
        assertEquals(EmoteSource.TWITCH_CHANNEL, result[0].source)
    }

    @Test fun skipsBlankNames() {
        val result = buildPickableEmotes(
            twitchChannel = listOf(emote("", EmoteProvider.TWITCH), emote("Keep", EmoteProvider.TWITCH)),
            thirdPartyChannel = emptyList(),
            twitchGlobal = emptyList(),
            thirdPartyGlobal = emptyList(),
        )
        assertEquals(listOf("Keep"), result.map { it.code })
    }
}
