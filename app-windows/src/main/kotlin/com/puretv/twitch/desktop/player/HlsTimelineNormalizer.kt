package com.puretv.twitch.desktop.player

import kotlin.math.max
import kotlin.math.roundToLong

/** Result of making one Twitch media playlist safe to serve at a stable local URL. */
internal data class HlsTimelineResult(
    val content: String,
    val sequenceNormalized: Boolean,
    val segmentsTrimmed: Int,
    val mediaSequence: Long?,
)

/**
 * Keeps HLS media-sequence identity stable while LocalStreamProxy swaps between
 * Twitch player-type sessions. Twitch can number the same broadcast in two
 * incompatible ways: a session-local MEDIA-SEQUENCE (often 0 or a small number)
 * and a broadcast-wide TWITCH-LIVE-SEQUENCE. Serving those sessions alternately
 * at one URL makes VLC see the live window jump thousands of segments backward.
 *
 * We canonicalize onto Twitch's broadcast-wide sequence. Some ad-only playlists
 * omit TWITCH-LIVE-SEQUENCE, so their stable value is derived from Twitch's
 * stream-relative elapsed seconds (Twitch live segments are clocked at 2s).
 * Finally, a per-upstream-variant floor prevents a backup playlist's wider window
 * from moving the first listed segment backward; already-consumed leading blocks
 * are trimmed while keeping discontinuity numbering valid.
 */
internal class HlsTimelineNormalizer(private val maxEntries: Int = 32) {
    private val sequenceFloors = LinkedHashMap<String, Long>(16, 0.75f, true)

    @Synchronized
    fun normalize(key: String, playlist: String): HlsTimelineResult {
        val mediaSequence = parseLongHeader(playlist, MEDIA_SEQUENCE)
            ?: return HlsTimelineResult(playlist, false, 0, null)
        val canonicalSequence = parseLongHeader(playlist, TWITCH_LIVE_SEQUENCE)
            ?: parseDoubleHeader(playlist, TWITCH_ELAPSED_SECONDS)
                ?.div(TWITCH_SEGMENT_SECONDS)
                ?.roundToLong()

        var content = playlist
        var normalized = false
        if (canonicalSequence != null && canonicalSequence != mediaSequence) {
            content = replaceHeader(content, MEDIA_SEQUENCE, canonicalSequence.toString())
            normalized = true
        }

        var currentSequence = parseLongHeader(content, MEDIA_SEQUENCE) ?: mediaSequence
        val floor = sequenceFloors[key]
        var trimmed = 0
        if (floor != null && currentSequence < floor) {
            val aligned = trimLeadingSegments(content, floor)
            content = aligned.first
            trimmed = aligned.second
            currentSequence = parseLongHeader(content, MEDIA_SEQUENCE) ?: floor
        }

        sequenceFloors[key] = max(floor ?: currentSequence, currentSequence)
        while (sequenceFloors.size > maxEntries) {
            sequenceFloors.remove(sequenceFloors.entries.first().key)
        }
        return HlsTimelineResult(content, normalized, trimmed, currentSequence)
    }

    private fun trimLeadingSegments(playlist: String, sequenceFloor: Long): Pair<String, Int> {
        val lines = playlist.lines()
        val current = parseLongHeader(playlist, MEDIA_SEQUENCE) ?: return playlist to 0
        val uriLines = lines.indices.filter { index ->
            val line = lines[index].trim()
            line.isNotEmpty() && !line.startsWith("#")
        }
        val requestedDrop = (sequenceFloor - current).coerceAtLeast(0).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        val dropCount = minOf(requestedDrop, uriLines.size)
        if (dropCount == 0) return playlist to 0

        val cutAfter = uriLines[dropCount - 1] + 1
        val removedLines = lines.take(cutAfter)
        val removedDuration = removedLines
            .filter { it.startsWith("#EXTINF:") }
            .sumOf { it.substringAfter(':').substringBefore(',').trim().toDoubleOrNull() ?: 0.0 }
        val removedDiscontinuities = removedLines.count { it.trim() == "#EXT-X-DISCONTINUITY" }

        val firstUriLine = uriLines.firstOrNull() ?: lines.size
        val headers = lines.take(firstUriLine).filter(::isPersistentHeader).toMutableList()
        replaceHeader(headers, MEDIA_SEQUENCE, sequenceFloor.toString())
        if (headers.any { it.startsWith(TWITCH_LIVE_SEQUENCE) }) {
            replaceHeader(headers, TWITCH_LIVE_SEQUENCE, sequenceFloor.toString())
        }
        if (removedDuration > 0 && headers.any { it.startsWith(TWITCH_ELAPSED_SECONDS) }) {
            val elapsed = parseDoubleHeader(playlist, TWITCH_ELAPSED_SECONDS) ?: 0.0
            replaceHeader(headers, TWITCH_ELAPSED_SECONDS, (elapsed + removedDuration).toString())
        }
        if (removedDiscontinuities > 0 || headers.any { it.startsWith(DISCONTINUITY_SEQUENCE) }) {
            val prior = parseLongHeader(playlist, DISCONTINUITY_SEQUENCE) ?: 0L
            replaceHeader(headers, DISCONTINUITY_SEQUENCE, (prior + removedDiscontinuities).toString())
        }

        val suffix = lines.drop(cutAfter).filterNot(::isPersistentHeader)
        return (headers + suffix).joinToString("\n") to dropCount
    }

    private companion object {
        const val MEDIA_SEQUENCE = "#EXT-X-MEDIA-SEQUENCE:"
        const val DISCONTINUITY_SEQUENCE = "#EXT-X-DISCONTINUITY-SEQUENCE:"
        const val TWITCH_LIVE_SEQUENCE = "#EXT-X-TWITCH-LIVE-SEQUENCE:"
        const val TWITCH_ELAPSED_SECONDS = "#EXT-X-TWITCH-ELAPSED-SECS:"
        const val TWITCH_SEGMENT_SECONDS = 2.0

        fun parseLongHeader(playlist: String, prefix: String): Long? =
            playlist.lineSequence().firstOrNull { it.startsWith(prefix) }
                ?.substringAfter(':')?.trim()?.toLongOrNull()

        fun parseDoubleHeader(playlist: String, prefix: String): Double? =
            playlist.lineSequence().firstOrNull { it.startsWith(prefix) }
                ?.substringAfter(':')?.trim()?.toDoubleOrNull()

        fun replaceHeader(playlist: String, prefix: String, value: String): String =
            playlist.lines().toMutableList().also { replaceHeader(it, prefix, value) }.joinToString("\n")

        fun replaceHeader(lines: MutableList<String>, prefix: String, value: String) {
            val index = lines.indexOfFirst { it.startsWith(prefix) }
            if (index >= 0) {
                lines[index] = "$prefix$value"
            } else {
                val mediaIndex = lines.indexOfFirst { it.startsWith(MEDIA_SEQUENCE) }
                lines.add(if (mediaIndex >= 0) mediaIndex + 1 else lines.size, "$prefix$value")
            }
        }

        fun isPersistentHeader(line: String): Boolean =
            line.startsWith("#EXTM3U") ||
                line.startsWith("#EXT-X-VERSION") ||
                line.startsWith("#EXT-X-TARGETDURATION") ||
                line.startsWith(MEDIA_SEQUENCE) ||
                line.startsWith(DISCONTINUITY_SEQUENCE) ||
                line.startsWith("#EXT-X-PLAYLIST-TYPE") ||
                line.startsWith("#EXT-X-INDEPENDENT-SEGMENTS") ||
                line.startsWith("#EXT-X-ALLOW-CACHE") ||
                line.startsWith("#EXT-X-START") ||
                line.startsWith("#EXT-X-TWITCH-LIVE-SEQUENCE") ||
                line.startsWith("#EXT-X-TWITCH-ELAPSED-SECS") ||
                line.startsWith("#EXT-X-TWITCH-TOTAL-SECS") ||
                line.startsWith("#EXT-X-DATERANGE")
    }
}
