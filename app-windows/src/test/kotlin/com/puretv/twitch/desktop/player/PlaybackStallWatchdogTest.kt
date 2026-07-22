package com.puretv.twitch.desktop.player

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlaybackStallWatchdogTest {
    @Test
    fun requestsRecoveryAfterPlayingClockStopsForThreshold() {
        val watchdog = PlaybackStallWatchdog(stallThresholdMs = 15_000)
        assertFalse(watchdog.sample(PlayerStatus(isPlaying = true, positionMs = 9_000), nowMs = 0))
        val playing = PlayerStatus(isPlaying = true, positionMs = 10_000)
        assertFalse(watchdog.sample(playing, nowMs = 1_000))
        assertFalse(watchdog.sample(playing, nowMs = 15_999))
        assertTrue(watchdog.sample(playing, nowMs = 16_000))
        assertFalse(watchdog.sample(playing, nowMs = 16_001))
    }

    @Test
    fun clockProgressResetsTheStallWindow() {
        val watchdog = PlaybackStallWatchdog(stallThresholdMs = 15_000)

        assertFalse(watchdog.sample(PlayerStatus(isPlaying = true, positionMs = 1_000), 0))
        assertFalse(watchdog.sample(PlayerStatus(isPlaying = true, positionMs = 2_000), 14_000))
        assertFalse(watchdog.sample(PlayerStatus(isPlaying = true, positionMs = 2_000), 28_999))
        assertTrue(watchdog.sample(PlayerStatus(isPlaying = true, positionMs = 2_000), 29_000))
    }

    @Test
    fun pausedPlaybackNeverAutoRestarts() {
        val watchdog = PlaybackStallWatchdog(stallThresholdMs = 15_000)

        assertFalse(watchdog.sample(PlayerStatus(isPlaying = true, positionMs = 1_000), 0))
        assertFalse(watchdog.sample(PlayerStatus(isPlaying = false, positionMs = 1_000), 30_000))
        assertFalse(watchdog.sample(PlayerStatus(isPlaying = true, positionMs = 1_000), 60_000))
    }

    @Test
    fun aBackendWithoutAClockDoesNotFalsePositive() {
        val watchdog = PlaybackStallWatchdog(stallThresholdMs = 15_000)
        val playingWithoutClock = PlayerStatus(isPlaying = true, positionMs = 0)

        assertFalse(watchdog.sample(playingWithoutClock, 0))
        assertFalse(watchdog.sample(playingWithoutClock, 60_000))
    }
}
