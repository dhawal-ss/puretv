package com.puretv.twitch.core.model

import kotlin.test.Test
import kotlin.test.assertEquals

class VideoDurationTest {
    @Test fun parsesFullDuration() = assertEquals(11313L, parseTwitchDuration("3h8m33s"))
    @Test fun parsesMinutesSeconds() = assertEquals(513L, parseTwitchDuration("8m33s"))
    @Test fun parsesSecondsOnly() = assertEquals(33L, parseTwitchDuration("33s"))
    @Test fun parsesHoursOnly() = assertEquals(3600L, parseTwitchDuration("1h"))
    @Test fun emptyIsZero() = assertEquals(0L, parseTwitchDuration(""))

    @Test fun fromApiMapsKnownTypes() {
        assertEquals(VideoType.ARCHIVE, VideoType.fromApi("archive"))
        assertEquals(VideoType.HIGHLIGHT, VideoType.fromApi("highlight"))
        assertEquals(VideoType.UPLOAD, VideoType.fromApi("upload"))
        assertEquals(VideoType.UNKNOWN, VideoType.fromApi("rerun"))
    }
}
