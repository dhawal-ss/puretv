package com.puretv.twitch.core.adblock

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Regression tests for the two silent ad-leak paths found in the 2026-06-16
 * production audit (P0-3), plus the detection/removal-divergence (AD-3):
 *
 *  - AD-2: an #EXT-X-DISCONTINUITY *inside* an ad pod (between stitched
 *    creatives) must NOT end the pod early — the remaining ad segments would
 *    otherwise be emitted as content. The pod length is anchored on the
 *    DATERANGE DURATION so internal discontinuities are ignored until the ad's
 *    duration is consumed.
 *  - AD-1: a 2s playlist refresh whose pod *opener* has already scrolled out of
 *    the live window still carries the repeated stitched-ad DATERANGE. The
 *    leading (already-mid-pod) ad segments precede any marker, so a stateless
 *    pass emits them as content. `assumeStartInAdBreak` strips them.
 *  - AD-3: detection (`AdMarkers.containsAds`) must recognise a bare SCTE
 *    #EXT-X-CUE-OUT pod, not only the `stitched` literal, or a pure-SCTE pod is
 *    served raw.
 */
class AdLeakHardeningTest {

    private val rewriter = ManifestRewriter()

    @Test
    fun keepsStrippingAcrossIntraPodDiscontinuity() {
        // DURATION=6.0 → three 2.0s ad segments. An internal discontinuity sits
        // between the first and second ad creative. The OLD logic ended the pod
        // at that first post-segment discontinuity and leaked ad-1/ad-2.
        val playlist = buildString {
            appendLine("#EXTM3U")
            appendLine("#EXTINF:2.000,live")
            appendLine("content-1.ts")
            appendLine("#EXT-X-DATERANGE:ID=\"ad\",CLASS=\"twitch-stitched-ad\",START-DATE=\"2026-06-16T00:00:00Z\",DURATION=6.0")
            appendLine("#EXT-X-DISCONTINUITY")
            appendLine("#EXTINF:2.000,Amazon")
            appendLine("ad-0.ts")
            appendLine("#EXT-X-DISCONTINUITY") // <-- INTERNAL boundary between creatives
            appendLine("#EXTINF:2.000,Amazon")
            appendLine("ad-1.ts")
            appendLine("#EXTINF:2.000,Amazon")
            appendLine("ad-2.ts")
            appendLine("#EXT-X-DISCONTINUITY") // <-- real return-to-content
            appendLine("#EXTINF:2.000,live")
            append("content-2.ts")
        }

        val result = rewriter.filter(playlist)

        assertEquals(3, result.adSegmentsRemoved, "all three ad segments must be removed despite the intra-pod discontinuity:\n${result.content}")
        listOf("ad-0.ts", "ad-1.ts", "ad-2.ts").forEach {
            assertFalse(result.content.contains(it), "ad segment \"$it\" leaked through an intra-pod discontinuity:\n${result.content}")
        }
        listOf("content-1.ts", "content-2.ts").forEach {
            assertTrue(result.content.contains(it), "content segment \"$it\" was wrongly dropped:\n${result.content}")
        }
    }

    @Test
    fun assumeStartInAdBreakStripsLeadingMidPodSegments() {
        // The pod opener (DATERANGE + first discontinuity) scrolled out; this
        // window starts with leftover ad segments, then returns to content.
        val tailWindow = buildString {
            appendLine("#EXTM3U")
            appendLine("#EXT-X-MEDIA-SEQUENCE:204")
            appendLine("#EXTINF:2.000,Amazon")
            appendLine("ad-7.ts")
            appendLine("#EXTINF:2.000,Amazon")
            appendLine("ad-8.ts")
            appendLine("#EXT-X-DISCONTINUITY")
            appendLine("#EXTINF:2.000,live")
            append("content-9.ts")
        }

        val result = rewriter.filter(tailWindow, assumeStartInAdBreak = true)

        listOf("ad-7.ts", "ad-8.ts").forEach {
            assertFalse(result.content.contains(it), "leading mid-pod ad segment \"$it\" leaked as content:\n${result.content}")
        }
        assertTrue(result.content.contains("content-9.ts"), "post-pod content must resume after the return discontinuity:\n${result.content}")
        assertEquals(2, result.adSegmentsRemoved)
    }

    @Test
    fun assumeStartInAdBreakPreservesPlaylistHeaders() {
        // AD-4: when the fail-safe fires mid-pod it must NOT eat the playlist
        // header tags (#EXTM3U et al.). The old logic dropped every leading
        // '#'-line, handing VLC a headerless playlist it can reject (brief
        // freeze on every cross-refresh that triggers the fail-safe).
        val tailWindow = buildString {
            appendLine("#EXTM3U")
            appendLine("#EXT-X-VERSION:3")
            appendLine("#EXT-X-TARGETDURATION:2")
            appendLine("#EXT-X-MEDIA-SEQUENCE:204")
            appendLine("#EXT-X-DISCONTINUITY-SEQUENCE:5")
            appendLine("#EXTINF:2.000,Amazon")
            appendLine("ad-7.ts")
            appendLine("#EXT-X-DISCONTINUITY")
            appendLine("#EXTINF:2.000,live")
            append("content-9.ts")
        }

        val result = rewriter.filter(tailWindow, assumeStartInAdBreak = true)

        assertEquals("#EXTM3U", result.content.lineSequence().first(), "playlist must still begin with #EXTM3U:\n${result.content}")
        listOf("#EXT-X-VERSION:3", "#EXT-X-TARGETDURATION:2", "#EXT-X-MEDIA-SEQUENCE:204", "#EXT-X-DISCONTINUITY-SEQUENCE:5").forEach {
            assertTrue(result.content.contains(it), "header \"$it\" must be preserved through the fail-safe:\n${result.content}")
        }
        assertFalse(result.content.contains("ad-7.ts"), "leading mid-pod ad segment must still be stripped")
        assertTrue(result.content.contains("content-9.ts"), "post-pod content must resume")
    }

    @Test
    fun parsesDurationNotPlannedDuration() {
        // AD-5: a DATERANGE carries both PLANNED-DURATION and DURATION. The pod
        // is really 6s (3x2s). If the rewriter grabbed PLANNED-DURATION=300 it
        // would keep stripping CONTENT past the pod (over-strip → black/stall).
        val playlist = buildString {
            appendLine("#EXTM3U")
            appendLine("#EXT-X-DATERANGE:ID=\"ad\",CLASS=\"twitch-stitched-ad\",PLANNED-DURATION=300.0,DURATION=6.0")
            appendLine("#EXT-X-DISCONTINUITY")
            appendLine("#EXTINF:2.000,Amazon")
            appendLine("ad-0.ts")
            appendLine("#EXTINF:2.000,Amazon")
            appendLine("ad-1.ts")
            appendLine("#EXTINF:2.000,Amazon")
            appendLine("ad-2.ts")
            appendLine("#EXT-X-DISCONTINUITY")
            appendLine("#EXTINF:2.000,live")
            appendLine("content-0.ts")
            appendLine("#EXTINF:2.000,live")
            append("content-1.ts")
        }

        val result = rewriter.filter(playlist)

        assertEquals(3, result.adSegmentsRemoved, "only the 3 ad segments (DURATION=6.0) should be removed, not content (PLANNED-DURATION=300):\n${result.content}")
        assertTrue(result.content.contains("content-0.ts"), "content must not be over-stripped:\n${result.content}")
        assertTrue(result.content.contains("content-1.ts"), "content must not be over-stripped:\n${result.content}")
    }

    @Test
    fun detectsBareCueOutPodAsAd() {
        // A pure SCTE-35 CUE-OUT pod has no `stitched` text. Detection must still
        // flag it, otherwise resolveCleanVariant serves it raw (ad leaks).
        val scteOnly = "#EXTM3U\n#EXT-X-CUE-OUT:30.000\n#EXTINF:2.000,\nad.ts\n#EXT-X-CUE-IN\n"
        assertTrue(AdMarkers.containsAds(scteOnly), "a bare #EXT-X-CUE-OUT pod must be detected as containing an ad")
    }

    @Test
    fun cleanPlaylistStillReportsNoAds() {
        val clean = "#EXTM3U\n#EXTINF:2.000,live\ncontent.ts\n"
        assertFalse(AdMarkers.containsAds(clean), "a clean playlist must not be flagged")
        val result = rewriter.filter(clean)
        assertEquals(0, result.adSegmentsRemoved)
        assertFalse(result.containedAds)
    }
}
