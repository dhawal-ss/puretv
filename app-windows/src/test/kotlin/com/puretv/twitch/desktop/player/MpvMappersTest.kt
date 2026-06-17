package com.puretv.twitch.desktop.player

import com.puretv.twitch.core.model.UpscalingMode
import kotlin.test.*

class MpvMappersTest {
    // Scaler mapping moved to MpvScalerPropsTest (mpvScalerProps replaced mpvUpscaleArgs).
    @Test fun statusMapping() {
        val base = PlayerStatus()
        assertEquals(3000L, mpvApply(base, "time-pos", "3.0").positionMs)
        assertEquals(120000L, mpvApply(base, "duration", "120.0").durationMs)
        assertFalse(mpvApply(base.copy(isPlaying=true), "pause", "yes").isPlaying)
        assertTrue(mpvApply(base, "pause", "no").isPlaying)
        assertTrue(mpvApply(base, "core-idle", "yes").isBuffering)
        assertTrue(mpvApply(base, "paused-for-cache", "yes").isBuffering)
        assertTrue(mpvApply(base, "seekable", "yes").isSeekable)
        // Unknown property passes through unchanged.
        assertEquals(base, mpvApply(base, "totally-unknown", "whatever"))
    }

    @Test fun nullPropertyValuesAreSafe() {
        val base = PlayerStatus()
        // mpv can fire a property-change with a null value (e.g. during seek /
        // backend re-init); == against a literal yields false cleanly, no NPE.
        assertFalse(mpvApply(base.copy(isPlaying = true), "pause", null).isPlaying)
        assertFalse(mpvApply(base, "seekable", null).isSeekable)
        assertEquals(0L, mpvApply(base.copy(positionMs = 5L), "time-pos", null).positionMs)
    }
}
