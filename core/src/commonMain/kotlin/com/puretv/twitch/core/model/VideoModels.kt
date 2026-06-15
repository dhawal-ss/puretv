package com.puretv.twitch.core.model

/** Past video kinds returned by Helix GET /videos `type`. */
enum class VideoType {
    ARCHIVE, HIGHLIGHT, UPLOAD, UNKNOWN;

    companion object {
        fun fromApi(raw: String): VideoType = when (raw.lowercase()) {
            "archive" -> ARCHIVE
            "highlight" -> HIGHLIGHT
            "upload" -> UPLOAD
            else -> UNKNOWN
        }
    }
}

/** A DMCA-muted audio range inside a VOD (Helix `muted_segments`). */
data class MutedSegment(val durationSeconds: Int, val offsetSeconds: Int)

/** A past video (archive/highlight/upload) on a channel. */
data class VideoInfo(
    val id: String,
    val userId: String,
    val userLogin: String,
    val userName: String,
    val title: String,
    val description: String,
    val type: VideoType,
    val durationSeconds: Long,
    val createdAt: String,
    val publishedAt: String,
    val thumbnailUrl: String,
    val viewCount: Int,
    val mutedSegments: List<MutedSegment>,
)

/**
 * Twitch sends VOD durations as compact strings like "3h8m33s", "8m33s", "33s".
 * Returns total seconds (0 for an unparseable/empty string).
 */
fun parseTwitchDuration(raw: String): Long {
    fun unit(suffix: Char): Long =
        Regex("(\\d+)$suffix").find(raw)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
    return unit('h') * 3600 + unit('m') * 60 + unit('s')
}
