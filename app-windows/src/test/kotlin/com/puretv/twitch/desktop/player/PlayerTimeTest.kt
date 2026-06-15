package com.puretv.twitch.desktop.player

import kotlin.test.Test
import kotlin.test.assertEquals

class PlayerTimeTest {
    @Test fun underOneHour() = assertEquals("8:33", formatTimecode(513_000))
    @Test fun overOneHour() = assertEquals("3:08:33", formatTimecode(11_313_000))
    @Test fun zero() = assertEquals("0:00", formatTimecode(0))
    @Test fun negativeClampsToZero() = assertEquals("0:00", formatTimecode(-5))
}
