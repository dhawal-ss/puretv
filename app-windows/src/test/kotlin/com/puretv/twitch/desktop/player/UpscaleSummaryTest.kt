package com.puretv.twitch.desktop.player

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The honest core of the upscaling proof: does the output (VO surface) exceed
 * the source, and by how much. mpv upscales the source to the output size, so
 * `output > source` is exactly "upscaling is happening".
 */
class UpscaleSummaryTest {
    @Test fun reportsUpscalingWithFactorWhenOutputLarger() {
        val s = upscaleSummary(srcW = 1920, srcH = 1080, outW = 2560, outH = 1440)
        assertTrue(s is UpscaleStatus.Upscaling, "1080p→1440p should be upscaling")
        assertEquals(1.333, (s as UpscaleStatus.Upscaling).factor, 0.01)
    }

    @Test fun reportsNativeWhenEqual() {
        assertEquals(UpscaleStatus.Native, upscaleSummary(1920, 1080, 1920, 1080))
    }

    @Test fun reportsDownscalingWhenOutputSmaller() {
        assertTrue(upscaleSummary(1920, 1080, 1280, 720) is UpscaleStatus.Downscaling)
    }

    @Test fun nonPositiveDimensionsAreNativeNotCrash() {
        // mpv returns null/0 for these properties before the first frame decodes.
        assertEquals(UpscaleStatus.Native, upscaleSummary(0, 0, 2560, 1440))
        assertEquals(UpscaleStatus.Native, upscaleSummary(1920, 1080, 0, 0))
    }
}
