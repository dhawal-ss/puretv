package com.puretv.twitch.core.model

/**
 * How Browse orders the categories. Twitch itself only sorts them by viewers
 * (GQL's GameSort has VIEWER_COUNT and RELEVANCE, which is the same order; Helix
 * has no option at all), so the list always arrives most watched first and the
 * other orders are worked out here by [sortedCategories].
 */
enum class CategorySort(
    /** Stored in settings files; never rename. */
    val key: String,
    val label: String,
) {
    VIEWERS("viewers", "Most viewers"),
    FEWEST("fewest", "Fewest viewers"),
    NAME("name", "A to Z"),
    ;

    /**
     * True when the order needs the whole list, not just the pages loaded so far:
     * the categories with the fewest viewers, or the ones late in the alphabet, can
     * be on the last page. Most viewers is the order the pages come in.
     */
    val needsEveryPage: Boolean get() = this != VIEWERS

    companion object {
        val DEFAULT = VIEWERS

        /** The order stored as [key]; anything unknown (or an older file without one) is [DEFAULT]. */
        fun parse(key: String?): CategorySort = entries.firstOrNull { it.key.equals(key, ignoreCase = true) } ?: DEFAULT
    }
}

/**
 * [games] (as Twitch sent them, most watched first) in [sort] order. Viewer counts
 * win when every category has one; otherwise the list position stands in for them,
 * which is the same order since Twitch ranks by viewers. Names compare without
 * regard to case, and ties keep Twitch's order.
 */
fun sortedCategories(games: List<GameInfo>, sort: CategorySort): List<GameInfo> = when (sort) {
    CategorySort.VIEWERS -> games
    CategorySort.FEWEST ->
        if (games.isNotEmpty() && games.all { it.viewers != null }) {
            games.withIndex().sortedWith(compareBy({ it.value.viewers }, { -it.index })).map { it.value }
        } else {
            games.asReversed()
        }
    CategorySort.NAME -> games.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
}

/** "12.3K viewers", "1.2M viewers", "1 viewer": the count under a category tile. */
fun formatCategoryViewers(viewers: Long): String {
    val count = when {
        viewers >= 1_000_000 -> compact(viewers / 1_000_000.0) + "M"
        viewers >= 1_000 -> compact(viewers / 1_000.0) + "K"
        else -> viewers.toString()
    }
    return if (viewers == 1L) "1 viewer" else "$count viewers"
}

// One decimal below 100 ("12.3"), none above ("123"), and no ".0".
private fun compact(value: Double): String {
    if (value >= 100) return value.toLong().toString()
    val tenths = (value * 10).toLong()
    return if (tenths % 10 == 0L) (tenths / 10).toString() else "${tenths / 10}.${tenths % 10}"
}
