package com.puretv.twitch.desktop.player

/**
 * Detects a live player that still reports PLAYING but whose clock has stopped.
 * A recovery request reloads the local /stream URL, minting a fresh Twitch
 * playlist without requiring the user to leave and re-enter the channel.
 */
internal class PlaybackStallWatchdog(
    private val stallThresholdMs: Long = 15_000,
) {
    private var lastPositionMs: Long? = null
    private var lastProgressAtMs: Long? = null
    private var hasObservedProgress = false

    fun sample(status: PlayerStatus, nowMs: Long): Boolean {
        if (!status.isPlaying) {
            reset()
            return false
        }

        val previousPosition = lastPositionMs
        if (previousPosition == null || status.positionMs != previousPosition) {
            if (previousPosition != null) hasObservedProgress = true
            lastPositionMs = status.positionMs
            lastProgressAtMs = nowMs
            return false
        }

        // Some live backends may never expose a useful clock. Do not restart a
        // healthy stream merely because its reported position stays at zero;
        // recovery is armed only after this playback actually advanced once.
        if (!hasObservedProgress) return false

        val progressAt = lastProgressAtMs ?: nowMs.also { lastProgressAtMs = it }
        if (nowMs - progressAt < stallThresholdMs) return false

        // Give the restarted player a full threshold window to make progress
        // before requesting another recovery.
        lastProgressAtMs = nowMs
        return true
    }

    fun reset() {
        lastPositionMs = null
        lastProgressAtMs = null
        hasObservedProgress = false
    }
}
