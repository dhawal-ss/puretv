package com.puretv.twitch.core.adblock

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * Resilience guard for the GOTCHA #1 outage: when stream resolution fails
 * BEFORE the ad-block engine runs (e.g. the GQL playback-token mint throws),
 * the status must NOT sit at UNKNOWN ("Checking…") forever — that silent state
 * is exactly what masked the broken-hash outage. A reported failure must move
 * the pill to a visible failure state.
 */
class AdBlockEngineResilienceTest {

    private fun engine() = AdBlockEngine(AdBlockConfig(), HttpClient(OkHttp))

    @Test fun startsInCheckingState() {
        assertEquals(AdBlockStatus.UNKNOWN, engine().status.value)
    }

    @Test fun reportedResolutionFailureLeavesCheckingState() {
        val e = engine()
        e.reportResolutionFailure()
        assertNotEquals(
            AdBlockStatus.UNKNOWN,
            e.status.value,
            "a resolution failure must move the pill off 'Checking…' so the outage is visible",
        )
        assertEquals(AdBlockStatus.AD_BLOCK_OFF, e.status.value)
    }
}
