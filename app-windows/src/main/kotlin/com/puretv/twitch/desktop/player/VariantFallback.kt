package com.puretv.twitch.desktop.player

/**
 * The last playlist the local proxy served for each variant (one quality of one stream),
 * so a refresh that Twitch fails (a 5xx, a request that hangs, an answer that is not a
 * playlist) can be answered with it instead. VLC reads an unchanged playlist as "no new
 * segments yet" and asks again a moment later, so a one-off failure costs nothing. With
 * low latency on there are only a few seconds of video buffered, so an error passed
 * straight through was a visible stall.
 *
 * Served at most [maxInARow] times in a row per variant: a session that is really gone
 * (the stream ended, the token expired) still reaches VLC as an error a few seconds
 * later, and the stream screen's recovery takes over. Bounded to [maxEntries] variants,
 * least recently used first out.
 */
internal class VariantFallback(private val maxInARow: Int = 2, private val maxEntries: Int = 32) {
    private class Entry(val playlist: String) {
        var servedInARow = 0
    }

    private val entries = object : LinkedHashMap<String, Entry>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Entry>): Boolean = size > maxEntries
    }

    /** [playlist] was just served for [variantUrl]: the one to fall back on, with a fresh allowance. */
    @Synchronized
    fun remember(variantUrl: String, playlist: String) {
        entries[variantUrl] = Entry(playlist)
    }

    /** The playlist to serve again after a failed refresh of [variantUrl], or null to report the failure. */
    @Synchronized
    fun fallback(variantUrl: String): String? {
        val entry = entries[variantUrl] ?: return null
        if (entry.servedInARow >= maxInARow) return null
        entry.servedInARow++
        return entry.playlist
    }
}
