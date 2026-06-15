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
}
