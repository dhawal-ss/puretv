package com.puretv.twitch.core.chat

import com.puretv.twitch.core.api.ChatBadgeSetDto
import com.puretv.twitch.core.api.ChatBadgeVersionDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BadgeIndexTest {

    private fun set(id: String, vararg versions: ChatBadgeVersionDto) = ChatBadgeSetDto(id, versions.toList())
    private fun ver(id: String, url2x: String = "", url4x: String = "", url1x: String = "", title: String = id) =
        ChatBadgeVersionDto(id = id, imageUrl1x = url1x, imageUrl2x = url2x, imageUrl4x = url4x, title = title)

    @Test fun resolvesGlobalBadge() {
        val idx = BadgeIndex.from(
            global = listOf(set("moderator", ver("1", url2x = "mod2x"))),
            channel = emptyList(),
        )
        assertEquals(ChatBadgeImage("mod2x", "1"), idx.resolve("moderator", "1"))
    }

    @Test fun channelOverridesGlobalOnSameSetAndVersion() {
        val idx = BadgeIndex.from(
            global = listOf(set("subscriber", ver("0", url2x = "globalSub"))),
            channel = listOf(set("subscriber", ver("0", url2x = "channelSub"))),
        )
        assertEquals("channelSub", idx.resolve("subscriber", "0")?.url)
    }

    @Test fun prefers2xThenFallsBackTo4xThen1x() {
        val idx = BadgeIndex.from(
            global = listOf(
                set("a", ver("0", url2x = "two", url4x = "four", url1x = "one")),
                set("b", ver("0", url4x = "four", url1x = "one")), // no 2x
                set("d", ver("0", url1x = "one")),                 // only 1x
            ),
            channel = emptyList(),
        )
        assertEquals("two", idx.resolve("a", "0")?.url)
        assertEquals("four", idx.resolve("b", "0")?.url)
        assertEquals("one", idx.resolve("d", "0")?.url)
    }

    @Test fun unknownSetOrVersionResolvesNull() {
        val idx = BadgeIndex.from(listOf(set("moderator", ver("1", url2x = "x"))), emptyList())
        assertNull(idx.resolve("moderator", "2"))
        assertNull(idx.resolve("vip", "1"))
    }

    @Test fun skipsBlankIdsAndUrllessVersions() {
        val idx = BadgeIndex.from(
            global = listOf(
                set("", ver("0", url2x = "x")),          // blank set id -> dropped
                set("ok", ver("", url2x = "x"), ver("1")), // blank version id + url-less -> both dropped
            ),
            channel = emptyList(),
        )
        assertNull(idx.resolve("", "0"))
        assertNull(idx.resolve("ok", "1")) // had no url
    }

    @Test fun emptyIndexResolvesNull() {
        assertNull(BadgeIndex.EMPTY.resolve("moderator", "1"))
    }
}
