package com.puretv.twitch.desktop.data

import kotlinx.serialization.Serializable

/** Saved playback position for one VOD. Metadata lets Home render a card
 *  without re-fetching. positionMs/durationMs mirror PlayerStatus. */
@Serializable
data class WatchProgress(
    val vodId: String,
    val positionMs: Long,
    val durationMs: Long,
    val updatedAt: Long,
    val title: String = "",
    val channelLogin: String = "",
    val thumbnailUrl: String = "",
)

/** Decides whether a saved progress entry is worth resuming. */
object ResumePolicy {
    private const val MIN_RESUME_MS = 5_000L
    private const val FINISHED_PERCENT = 95

    /** The position to resume from, or null if too early, finished, or unknown length. */
    fun resumePositionMs(p: WatchProgress): Long? {
        if (p.durationMs <= 0) return null
        if (p.positionMs < MIN_RESUME_MS) return null
        if (p.positionMs >= p.durationMs * FINISHED_PERCENT / 100) return null
        return p.positionMs
    }
}
