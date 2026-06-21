package com.puretv.twitch.core.adblock

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlin.test.Test
import kotlin.test.assertEquals

class AdBlockFilterPathTest {

    private fun engine(): AdBlockEngine =
        // filterPlaylist/reportFiltered do no network; a stub client satisfies the ctor.
        AdBlockEngine(AdBlockConfig(), HttpClient(MockEngine { respond("", HttpStatusCode.OK) }))

    @Test fun reportFilteredSetsAdFilteredWhenAdsWereStripped() {
        val e = engine()
        val filtered = e.filterPlaylist(DATERANGE_AD_PLAYLIST)
        e.reportFiltered(filtered)
        assertEquals(AdBlockStatus.AD_FILTERED, e.status.value)
    }

    @Test fun reportFilteredSetsAdBlockedWhenClean() {
        val e = engine()
        val filtered = e.filterPlaylist(CLEAN_PLAYLIST)
        e.reportFiltered(filtered)
        assertEquals(AdBlockStatus.AD_BLOCKED, e.status.value)
    }

    @Test fun stripsDaterangeStitchedAdPod() {
        val e = engine()
        val filtered = e.filterPlaylist(DATERANGE_AD_PLAYLIST)
        assertEquals(true, filtered.containedAds)
        assertEquals(false, AdMarkers.containsAds(filtered.content), "no ad markers may remain")
        assertEquals(true, filtered.content.contains("content-100.ts"), "content segment must survive")
        assertEquals(false, filtered.content.contains("ad-0.ts"), "ad segment must be gone")
    }

    @Test fun stripsScte35CueOutCueInPod() {
        val e = engine()
        val filtered = e.filterPlaylist(SCTE35_AD_PLAYLIST)
        assertEquals(true, filtered.containedAds)
        assertEquals(false, AdMarkers.containsAds(filtered.content))
        assertEquals(true, filtered.content.contains("content-200.ts"))
        assertEquals(false, filtered.content.contains("scte-ad-0.ts"))
    }

    @Test fun assumeStartInAdBreakDropsLeadingAdSegmentsUntilDiscontinuity() {
        val filtered = ManifestRewriter().filter(MID_POD_PLAYLIST, assumeStartInAdBreak = true)
        assertEquals(false, filtered.content.contains("ad-tail-0.ts"), "leading ad segment must be dropped")
        assertEquals(false, filtered.content.contains("ad-tail-1.ts"), "all leading ad segments must be dropped")
        assertEquals(true, filtered.content.contains("content-300.ts"), "content after the splice must survive")
    }
}

private val CLEAN_PLAYLIST = """
#EXTM3U
#EXT-X-VERSION:3
#EXT-X-TARGETDURATION:2
#EXT-X-MEDIA-SEQUENCE:100
#EXTINF:2.000,live
content-100.ts
#EXTINF:2.000,live
content-101.ts
""".trim()

private val DATERANGE_AD_PLAYLIST = """
#EXTM3U
#EXT-X-VERSION:3
#EXT-X-TARGETDURATION:2
#EXT-X-MEDIA-SEQUENCE:100
#EXT-X-DATERANGE:ID="ad",CLASS="twitch-stitched-ad",DURATION=4.000
#EXT-X-DISCONTINUITY
#EXTINF:2.000,Amazon
ad-0.ts
#EXTINF:2.000,Amazon
ad-1.ts
#EXT-X-DISCONTINUITY
#EXTINF:2.000,live
content-100.ts
""".trim()

private val SCTE35_AD_PLAYLIST = """
#EXTM3U
#EXT-X-VERSION:3
#EXT-X-TARGETDURATION:2
#EXT-X-MEDIA-SEQUENCE:200
#EXT-X-CUE-OUT:4.000
#EXTINF:2.000,Amazon
scte-ad-0.ts
#EXTINF:2.000,Amazon
scte-ad-1.ts
#EXT-X-CUE-IN
#EXTINF:2.000,live
content-200.ts
""".trim()

private val MID_POD_PLAYLIST = """
#EXTM3U
#EXT-X-TARGETDURATION:2
#EXT-X-MEDIA-SEQUENCE:300
#EXTINF:2.000,Amazon
ad-tail-0.ts
#EXTINF:2.000,Amazon
ad-tail-1.ts
#EXT-X-DISCONTINUITY
#EXTINF:2.000,live
content-300.ts
""".trim()
