package com.puretv.twitch.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Browse's sort options (1.17.0). Twitch only ranks by viewers; the rest happens here. */
class CategorySortTest {
    private fun game(id: String, name: String, viewers: Long? = null) = GameInfo(id = id, name = name, viewers = viewers)

    // As Twitch sends them: most watched first.
    private val ranked = listOf(game("1", "Just Chatting", 170_000), game("2", "apex Legends", 9_000), game("3", "Rust", 3_300))

    @Test fun mostViewersKeepsTwitchsOrder() {
        assertEquals(listOf("1", "2", "3"), sortedCategories(ranked, CategorySort.VIEWERS).map { it.id })
    }

    @Test fun fewestViewersUsesTheCounts() {
        val shuffled = listOf(ranked[1], ranked[2], ranked[0])
        assertEquals(listOf("3", "2", "1"), sortedCategories(shuffled, CategorySort.FEWEST).map { it.id })
    }

    @Test fun fewestViewersWithoutCountsReversesTheRanking() {
        // Signed in, Helix sends no counts, only the order.
        val noCounts = ranked.map { it.copy(viewers = null) }
        assertEquals(listOf("3", "2", "1"), sortedCategories(noCounts, CategorySort.FEWEST).map { it.id })
    }

    @Test fun nameIgnoresCase() {
        assertEquals(listOf("apex Legends", "Just Chatting", "Rust"), sortedCategories(ranked, CategorySort.NAME).map { it.name })
    }

    @Test fun onlyMostViewersWorksPageByPage() {
        assertFalse(CategorySort.VIEWERS.needsEveryPage)
        assertTrue(CategorySort.FEWEST.needsEveryPage)
        assertTrue(CategorySort.NAME.needsEveryPage)
    }

    @Test fun sortsAreStoredByKeyAndUnknownKeysLoadTheDefault() {
        assertEquals(CategorySort.VIEWERS, AppSettings().browseSort)
        CategorySort.entries.forEach { assertEquals(it, CategorySort.parse(it.key)) }
        assertEquals(CategorySort.VIEWERS, CategorySort.parse(null))
        assertEquals(CategorySort.VIEWERS, CategorySort.parse("random"))
    }

    @Test fun viewerCountsReadLikeTwitch() {
        assertEquals("1 viewer", formatCategoryViewers(1))
        assertEquals("999 viewers", formatCategoryViewers(999))
        assertEquals("3.3K viewers", formatCategoryViewers(3_300))
        assertEquals("27K viewers", formatCategoryViewers(27_049))
        assertEquals("170K viewers", formatCategoryViewers(170_400))
        assertEquals("1.2M viewers", formatCategoryViewers(1_250_000))
    }
}
