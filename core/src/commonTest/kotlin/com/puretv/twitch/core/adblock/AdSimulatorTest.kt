package com.puretv.twitch.core.adblock

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AdSimulatorTest {

    @AfterTest fun reset() { AdSimulator.enabled = false }

    private val clean = """
#EXTM3U
#EXT-X-VERSION:3
#EXT-X-MEDIA-SEQUENCE:500
#EXTINF:2.000,live
content-500.ts
""".trim()

    @Test fun disabledIsANoOp() {
        AdSimulator.enabled = false
        assertEquals(clean, AdSimulator.inject(clean))
    }

    @Test fun enabledInjectsADetectablePodThatTheFilterThenRemoves() {
        AdSimulator.enabled = true
        val injected = AdSimulator.inject(clean)
        assertTrue(AdMarkers.containsAds(injected), "simulator must inject a detectable pod")

        val engine = AdBlockEngine(AdBlockConfig(), HttpClient(MockEngine { respond("", HttpStatusCode.OK) }))
        val filtered = engine.filterPlaylist(injected)
        assertFalse(AdMarkers.containsAds(filtered.content), "filter must remove the simulated pod")
        assertTrue(filtered.content.contains("content-500.ts"), "content must survive")
    }
}
