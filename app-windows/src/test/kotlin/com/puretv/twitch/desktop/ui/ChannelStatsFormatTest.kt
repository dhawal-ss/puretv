package com.puretv.twitch.desktop.ui

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ChannelStatsFormatTest {
    private val now = Instant.parse("2026-06-15T12:00:00Z")

    @Test fun formatCompactBuckets() {
        assertEquals("947", formatCompact(947))
        assertEquals("1K", formatCompact(1_000))
        assertEquals("12.4K", formatCompact(12_400))
        assertEquals("11.3M", formatCompact(11_301_990))
        assertEquals("2M", formatCompact(2_000_000))
        assertEquals("0", formatCompact(0))
    }

    @Test fun accountAgeLabelBuckets() {
        assertEquals("13 years", accountAgeLabel("2012-11-03T15:50:32Z", now))
        assertEquals("4 months", accountAgeLabel("2026-02-01T00:00:00Z", now))
        assertEquals("New", accountAgeLabel("2026-06-12T00:00:00Z", now))
        assertEquals("", accountAgeLabel("garbage", now))
    }

    @Test fun joinedYearLabelParses() {
        assertEquals("2012", joinedYearLabel("2012-11-03T15:50:32Z"))
        assertNull(joinedYearLabel("nope"))
    }

    @Test fun uptimeLabelFormats() {
        assertEquals("2h 30m", uptimeLabel("2026-06-15T09:30:00Z", now))
        assertEquals("15m", uptimeLabel("2026-06-15T11:45:00Z", now))
        assertNull(uptimeLabel(null, now))
    }
}
