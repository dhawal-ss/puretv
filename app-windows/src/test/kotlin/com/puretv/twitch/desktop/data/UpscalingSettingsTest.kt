package com.puretv.twitch.desktop.data

import com.puretv.twitch.core.model.UpscalingMode
import kotlin.test.Test
import kotlin.test.assertEquals

class UpscalingSettingsTest {
    @Test fun parsesKnownValues() {
        assertEquals(UpscalingMode.AUTO, parseUpscalingMode("auto"))
        assertEquals(UpscalingMode.AUTO, parseUpscalingMode("AUTO"))
        assertEquals(UpscalingMode.OFF, parseUpscalingMode("off"))
    }
    @Test fun unknownOrNullDefaultsToOff() {
        assertEquals(UpscalingMode.OFF, parseUpscalingMode("garbage"))
        assertEquals(UpscalingMode.OFF, parseUpscalingMode(null))
    }
}
