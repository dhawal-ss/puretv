package com.puretv.twitch.desktop.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ResumePolicyTest {
    private fun p(positionMs: Long, durationMs: Long) =
        WatchProgress(vodId = "v", positionMs = positionMs, durationMs = durationMs, updatedAt = 0)

    @Test fun midwayIsResumable() = assertEquals(600_000L, ResumePolicy.resumePositionMs(p(600_000, 1_200_000)))
    @Test fun finishedIsNotResumable() = assertNull(ResumePolicy.resumePositionMs(p(1_180_000, 1_200_000)))
    @Test fun tooEarlyIsNotResumable() = assertNull(ResumePolicy.resumePositionMs(p(2_000, 1_200_000)))
    @Test fun zeroDurationIsNotResumable() = assertNull(ResumePolicy.resumePositionMs(p(60_000, 0)))
}
