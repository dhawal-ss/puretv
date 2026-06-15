package com.puretv.twitch.desktop.player
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VolumeLogicTest {
    @Test fun muteFromVolumeRemembersLevel() {
        val m = applyMuteToggle(VolumeState(70, false, DEFAULT_VOLUME))
        assertEquals(0, m.volume); assertTrue(m.isMuted); assertEquals(70, m.preMute)
    }
    @Test fun unmuteRestoresLevel() {
        val u = applyMuteToggle(VolumeState(0, true, 70))
        assertEquals(70, u.volume); assertFalse(u.isMuted)
    }
    @Test fun muteWhenZeroRemembersDefault() {
        val m = applyMuteToggle(VolumeState(0, false, DEFAULT_VOLUME))
        assertEquals(DEFAULT_VOLUME, applyMuteToggle(m).volume)
    }
    @Test fun dragToNonZeroWhileMutedUnmutes() {
        val r = applyVolumeChange(VolumeState(0, true, 50), 40)
        assertEquals(40, r.volume); assertFalse(r.isMuted)
    }
    @Test fun clampsAndDefaultIsFifty() {
        assertEquals(50, DEFAULT_VOLUME)
        assertEquals(100, applyVolumeChange(VolumeState(50,false,50), 250).volume)
        assertEquals(0, applyVolumeChange(VolumeState(50,false,50), -5).volume)
    }
}
