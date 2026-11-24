package com.puretv.twitch.android.player

import androidx.media3.common.Player
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class KeepScreenOnTest {

    @Test
    fun screenStaysOnWhilePlayingOrBuffering() {
        assertTrue(shouldKeepScreenOn(playWhenReady = true, playbackState = Player.STATE_READY))
        // A stall mid-stream must not let the screen time out.
        assertTrue(shouldKeepScreenOn(playWhenReady = true, playbackState = Player.STATE_BUFFERING))
    }

    @Test
    fun pausedFailedOrFinishedLetsTheScreenTimeOut() {
        assertFalse(shouldKeepScreenOn(playWhenReady = false, playbackState = Player.STATE_READY))
        assertFalse(shouldKeepScreenOn(playWhenReady = false, playbackState = Player.STATE_BUFFERING))
        assertFalse(shouldKeepScreenOn(playWhenReady = true, playbackState = Player.STATE_IDLE))
        assertFalse(shouldKeepScreenOn(playWhenReady = true, playbackState = Player.STATE_ENDED))
    }
}
