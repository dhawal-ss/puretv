package com.puretv.twitch.desktop.player

import com.puretv.twitch.core.model.UpscalingMode
import kotlin.test.*

class MpvMappersTest {
    @Test fun offUsesBilinear() {
        assertEquals("bilinear", mpvUpscaleArgs(UpscalingMode.OFF, "ignored")["scale"])
        assertNull(mpvUpscaleArgs(UpscalingMode.OFF, "x")["glsl-shaders"])
    }
    @Test fun standardUsesEwa() {
        assertEquals("ewa_lanczossharp", mpvUpscaleArgs(UpscalingMode.STANDARD, "x")["scale"])
        assertNull(mpvUpscaleArgs(UpscalingMode.STANDARD, "x")["glsl-shaders"])
    }
    @Test fun animeAddsShader() {
        val a = mpvUpscaleArgs(UpscalingMode.ANIME, "C:/s/anime.glsl")
        assertEquals("ewa_lanczossharp", a["scale"])
        assertEquals("C:/s/anime.glsl", a["glsl-shaders"])
    }
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
