package com.puretv.twitch.desktop.data

import com.puretv.twitch.core.model.UpscalingMode
import com.puretv.twitch.core.model.PlaybackBackend
import kotlin.test.Test
import kotlin.test.assertEquals

class UpscalingSettingsTest {
    @Test fun parsesUpscaling() {
        assertEquals(UpscalingMode.STANDARD, parseUpscalingMode("standard"))
        assertEquals(UpscalingMode.ANIME, parseUpscalingMode("ANIME"))
        assertEquals(UpscalingMode.OFF, parseUpscalingMode("off"))
        assertEquals(UpscalingMode.OFF, parseUpscalingMode("garbage"))
        assertEquals(UpscalingMode.OFF, parseUpscalingMode(null))
    }
    @Test fun parsesBackend() {
        assertEquals(PlaybackBackend.MPV, parsePlaybackBackend("mpv"))
        assertEquals(PlaybackBackend.VLC, parsePlaybackBackend("vlc"))
        assertEquals(PlaybackBackend.VLC, parsePlaybackBackend(null))
        assertEquals(PlaybackBackend.VLC, parsePlaybackBackend("garbage"))
    }
}
