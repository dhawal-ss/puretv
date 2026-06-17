package com.puretv.twitch.core.emotes

import com.puretv.twitch.core.model.ChannelEmote
import com.puretv.twitch.core.model.EmoteProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EmoteIndexTest {
    private fun e(name: String, provider: EmoteProvider = EmoteProvider.SEVENTV, url: String = "u/$name", zeroWidth: Boolean = false) =
        ChannelEmote(id = "id_$name", name = name, url = url, provider = provider, animated = false, zeroWidth = zeroWidth)

    @Test fun indexesByExactCaseSensitiveCode() {
        val idx = buildEmoteIndex(thirdPartyChannel = listOf(e("catJAM")), thirdPartyGlobal = emptyList())
        assertEquals("u/catJAM", idx["catJAM"]?.url)
        assertNull(idx["catjam"]) // case-sensitive
    }

    @Test fun channelWinsOverGlobalForSameCode() {
        val idx = buildEmoteIndex(
            thirdPartyChannel = listOf(e("Dup", url = "channel")),
            thirdPartyGlobal = listOf(e("Dup", url = "global")),
        )
        assertEquals(1, idx.size)
        assertEquals("channel", idx["Dup"]?.url)
    }

    @Test fun skipsBlankCodesAndCarriesZeroWidth() {
        val idx = buildEmoteIndex(
            thirdPartyChannel = listOf(e(""), e("ZW", zeroWidth = true)),
            thirdPartyGlobal = emptyList(),
        )
        assertEquals(setOf("ZW"), idx.keys)
        assertTrue(idx["ZW"]!!.zeroWidth)
    }

    @Test fun includesFirstPartyTwitchEmotesWhenProvided() {
        // Self-echo can't get a Twitch `emotes=` tag, so typed first-party emotes
        // must be resolvable by name from the index.
        val idx = buildEmoteIndex(
            thirdPartyChannel = listOf(e("catJAM")),
            thirdPartyGlobal = emptyList(),
            twitchChannel = emptyList(),
            twitchGlobal = listOf(e("Kappa", provider = EmoteProvider.TWITCH)),
        )
        assertEquals("u/catJAM", idx["catJAM"]?.url)
        assertEquals("u/Kappa", idx["Kappa"]?.url)
        assertEquals(EmoteProvider.TWITCH, idx["Kappa"]?.provider)
    }

    @Test fun thirdPartyWinsOverTwitchOnNameCollision() {
        val idx = buildEmoteIndex(
            thirdPartyChannel = listOf(e("DUP", url = "tp")),
            thirdPartyGlobal = emptyList(),
            twitchChannel = listOf(e("DUP", provider = EmoteProvider.TWITCH, url = "tw")),
            twitchGlobal = emptyList(),
        )
        assertEquals("tp", idx["DUP"]?.url)
    }

    @Test fun existingThirdPartyOnlyCallsUnchanged() {
        // Defaulted twitch params keep the incoming-message path third-party-only.
        val idx = buildEmoteIndex(thirdPartyChannel = listOf(e("catJAM")), thirdPartyGlobal = emptyList())
        assertEquals(setOf("catJAM"), idx.keys)
    }
}
