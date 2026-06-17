package com.puretv.twitch.desktop.player

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertTrue

class FormatStatsOsdTest {
    private val anime = VideoStats(
        sourceWidth = 1920, sourceHeight = 1080,
        outputWidth = 2560, outputHeight = 1440,
        scaler = "ewa_lanczossharp",
        shaderName = "Anime4K_Upscale_CNN_x2_M.glsl",
        hwdec = "d3d11va", vo = "gpu-next", fps = 60.0,
    )

    @Test fun showsBothResolutionsAndUpscaleFactor() {
        val s = formatStatsOsd(anime, modeLabel = "Anime")
        assertContains(s, "1920x1080")
        assertContains(s, "2560x1440")
        assertContains(s, "1.33")
        assertContains(s, "upscale")
    }

    @Test fun showsTheActiveModeLabel() {
        // The "can I even tell it's on?" fix: the overlay must name the active mode.
        assertContains(formatStatsOsd(anime, modeLabel = "Sharp"), "Sharp")
        assertContains(formatStatsOsd(anime, modeLabel = "Off"), "Off")
    }

    @Test fun showsScalerAndShader() {
        val s = formatStatsOsd(anime, modeLabel = "Anime")
        assertContains(s, "ewa_lanczossharp")
        assertContains(s, "Anime4K")
    }

    @Test fun saysNativeWhenOutputEqualsSource() {
        val s = formatStatsOsd(anime.copy(outputWidth = 1920, outputHeight = 1080), modeLabel = "Sharp")
        assertContains(s, "native")
    }

    @Test fun toleratesAllNullsWithoutCrashing() {
        val s = formatStatsOsd(
            VideoStats(0, 0, 0, 0, scaler = null, shaderName = null, hwdec = null, vo = null, fps = null),
            modeLabel = "Off",
        )
        assertTrue(s.isNotBlank())
    }
}
