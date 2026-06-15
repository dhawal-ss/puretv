package com.puretv.twitch.desktop.data

import kotlin.test.Test
import kotlin.test.assertEquals

class ViewerHistoryAggregatorTest {
    private fun sample(epochSec: Long, viewers: Int) = ViewerSample(epochSec, viewers)

    @Test fun recordOnNullHistory() {
        val h = ViewerHistoryAggregator.record(null, "shroud", sample(1000, 42))
        assertEquals(1, h.samples.size)
        assertEquals(42, h.peakViewers)
        assertEquals(1, h.sessionsTracked)
        assertEquals(1000, h.lastSampleEpochSec)
    }

    @Test fun secondSampleWithinGapStaysSameSession() {
        var h = ViewerHistoryAggregator.record(null, "shroud", sample(1000, 42))
        h = ViewerHistoryAggregator.record(h, "shroud", sample(1060, 50))
        assertEquals(1, h.sessionsTracked)
    }

    @Test fun sampleAfterLongGapStartsNewSession() {
        var h = ViewerHistoryAggregator.record(null, "shroud", sample(1000, 42))
        h = ViewerHistoryAggregator.record(h, "shroud", sample(3000, 50)) // +2000s > 30min
        assertEquals(2, h.sessionsTracked)
    }

    @Test fun peakKeepsHighestRegardlessOfOrder() {
        var descending = ViewerHistoryAggregator.record(null, "shroud", sample(1000, 100))
        descending = ViewerHistoryAggregator.record(descending, "shroud", sample(1060, 50))
        assertEquals(100, descending.peakViewers)

        var ascending = ViewerHistoryAggregator.record(null, "shroud", sample(1000, 50))
        ascending = ViewerHistoryAggregator.record(ascending, "shroud", sample(1060, 100))
        assertEquals(100, ascending.peakViewers)
    }

    @Test fun samplesAreCappedKeepingNewest() {
        val total = ViewerHistoryAggregator.MAX_SAMPLES + 10
        var h: ChannelHistory? = null
        for (i in 0 until total) {
            // dense, in-session samples (1s apart) so caps, not sessions, are exercised
            h = ViewerHistoryAggregator.record(h, "shroud", sample(1000L + i, i))
        }
        val result = h!!
        assertEquals(ViewerHistoryAggregator.MAX_SAMPLES, result.samples.size)
        assertEquals(total - 1, result.samples.last().viewers)
        assertEquals(1000L + (total - 1), result.samples.last().epochSec)
    }

    @Test fun averageViewers() {
        var h = ViewerHistoryAggregator.record(null, "shroud", sample(1000, 10))
        h = ViewerHistoryAggregator.record(h, "shroud", sample(1010, 20))
        h = ViewerHistoryAggregator.record(h, "shroud", sample(1020, 30))
        assertEquals(20, ViewerHistoryAggregator.averageViewers(h))

        assertEquals(0, ViewerHistoryAggregator.averageViewers(ChannelHistory(login = "shroud")))
    }
}
