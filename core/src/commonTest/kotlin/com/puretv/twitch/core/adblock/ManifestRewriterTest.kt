package com.puretv.twitch.core.adblock

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Parity fixtures with proxy-server/rewriter_test.go.
 *
 * If you change one of these strings, change it on both sides. The whole point
 * of the in-app fallback (Strategy 2) and the standalone Go proxy (Strategy 1
 * primary path) sharing logic is that a single Twitch ad-marker change can be
 * fixed in one place and reflected in tests on both runtimes simultaneously.
 */
class ManifestRewriterTest {

    private val rewriter = ManifestRewriter()

    @Test
    fun removesStitchedAdBreak() {
        val result = rewriter.filter(SAMPLE_PLAYLIST_WITH_AD_BREAK)

        assertTrue(result.containedAds, "playlist with twitch-stitched-ad break should report containedAds")
        assertEquals(2, result.adSegmentsRemoved, "exactly the two ad segments should be removed")

        listOf("ad-segment-1.ts", "ad-segment-2.ts", "twitch-stitched-ad", "#EXT-X-CUE-OUT", "#EXT-X-CUE-IN").forEach { forbidden ->
            assertFalse(result.content.contains(forbidden), "filtered playlist still contains \"$forbidden\":\n${result.content}")
        }
        listOf("content-segment-1.ts", "content-segment-2.ts", "content-segment-3.ts", "#EXT-X-ENDLIST").forEach { expected ->
            assertTrue(result.content.contains(expected), "filtered playlist is missing expected line \"$expected\":\n${result.content}")
        }
    }

    @Test
    fun passesCleanPlaylistThrough() {
        val result = rewriter.filter(SAMPLE_PLAYLIST_CLEAN)

        assertFalse(result.containedAds, "clean playlist should not report containedAds")
        assertEquals(0, result.adSegmentsRemoved)
        assertEquals(SAMPLE_PLAYLIST_CLEAN, result.content, "a clean playlist must pass through byte-for-byte (modulo \\n line joining)")
    }

    @Test
    fun removesDaterangeOnlyAdBreakAndKeepsTrailingContent() {
        // REAL-WORLD FORMAT: modern Twitch midrolls are bracketed by a
        // #EXT-X-DATERANGE CLASS="twitch-stitched-ad" and a pair of
        // #EXT-X-DISCONTINUITY tags — with NO #EXT-X-CUE-IN. The rewriter must
        // detect the end of the ad pod from the closing discontinuity, not from
        // a CUE-IN, otherwise every content segment after the first midroll is
        // dropped and the stream stalls forever.
        val result = rewriter.filter(SAMPLE_PLAYLIST_DATERANGE_ONLY)

        assertTrue(result.containedAds, "DATERANGE-only ad break should report containedAds")
        assertEquals(3, result.adSegmentsRemoved, "the three ad segments should be removed")

        listOf("ad-0.ts", "ad-1.ts", "ad-2.ts", "twitch-stitched-ad").forEach { forbidden ->
            assertFalse(result.content.contains(forbidden), "ad artifact \"$forbidden\" survived:\n${result.content}")
        }
        listOf("content-1.ts", "content-2.ts", "content-3.ts", "content-4.ts").forEach { expected ->
            assertTrue(
                result.content.contains(expected),
                "content segment \"$expected\" was wrongly dropped — the ad break never ended:\n${result.content}",
            )
        }
    }

    @Test
    fun leavesNoOrphanedExtinfTags() {
        // Each #EXTINF must be immediately followed by a segment URI; a stripped
        // ad must take its #EXTINF with it. An orphaned #EXTINF is malformed HLS
        // and makes VLC/ExoPlayer stall or error.
        val result = rewriter.filter(SAMPLE_PLAYLIST_DATERANGE_ONLY)

        val lines = result.content.lines()
        val extinfCount = lines.count { it.startsWith("#EXTINF") }
        val uriCount = lines.count { it.isNotBlank() && !it.startsWith("#") }
        assertEquals(
            extinfCount, uriCount,
            "every #EXTINF must be paired with a URI (found $extinfCount #EXTINF / $uriCount URIs):\n${result.content}",
        )
    }

    @Test
    fun handlesBareCueOutWithoutDaterange() {
        // Some edge nodes emit #EXT-X-CUE-OUT/#EXT-X-CUE-IN with no accompanying
        // DATERANGE. The bracketed segment must still be treated as an ad.
        val playlist = buildString {
            appendLine("#EXTM3U")
            appendLine("#EXTINF:2.000,")
            appendLine("content.ts")
            appendLine("#EXT-X-CUE-OUT:30")
            appendLine("#EXTINF:2.000,")
            appendLine("ad.ts")
            appendLine("#EXT-X-CUE-IN")
            appendLine("#EXTINF:2.000,")
            append("more-content.ts")
        }

        val result = rewriter.filter(playlist)

        assertFalse(result.content.contains("ad.ts"), "ad segment under a bare CUE-OUT/CUE-IN pair should be removed:\n${result.content}")
        assertTrue(result.content.contains("more-content.ts"), "content after CUE-IN should be preserved:\n${result.content}")
    }

    private companion object {
        const val SAMPLE_PLAYLIST_WITH_AD_BREAK =
            "#EXTM3U\n" +
                "#EXT-X-VERSION:3\n" +
                "#EXTINF:2.000,\n" +
                "content-segment-1.ts\n" +
                "#EXTINF:2.000,\n" +
                "content-segment-2.ts\n" +
                "#EXT-X-DATERANGE:ID=\"stitched-ad-2026\",CLASS=\"twitch-stitched-ad\",START-DATE=\"2026-06-08T00:00:00Z\"\n" +
                "#EXT-X-DISCONTINUITY\n" +
                "#EXT-X-CUE-OUT:30\n" +
                "#EXTINF:2.000,\n" +
                "ad-segment-1.ts\n" +
                "#EXTINF:2.000,\n" +
                "ad-segment-2.ts\n" +
                "#EXT-X-CUE-IN\n" +
                "#EXT-X-DISCONTINUITY\n" +
                "#EXTINF:2.000,\n" +
                "content-segment-3.ts\n" +
                "#EXT-X-ENDLIST"

        // Mirrors what usher actually returns during a midroll: a single
        // stitched-ad DATERANGE, the ad pod fenced by two DISCONTINUITY tags,
        // and NO CUE-OUT/CUE-IN anywhere. content-3/4 come AFTER the ad pod.
        const val SAMPLE_PLAYLIST_DATERANGE_ONLY =
            "#EXTM3U\n" +
                "#EXT-X-VERSION:3\n" +
                "#EXT-X-TARGETDURATION:2\n" +
                "#EXT-X-MEDIA-SEQUENCE:100\n" +
                "#EXTINF:2.000,live\n" +
                "content-1.ts\n" +
                "#EXTINF:2.000,live\n" +
                "content-2.ts\n" +
                "#EXT-X-DATERANGE:ID=\"stitched-ad-100\",CLASS=\"twitch-stitched-ad\",START-DATE=\"2026-06-08T00:00:00Z\",DURATION=6.0\n" +
                "#EXT-X-DISCONTINUITY\n" +
                "#EXTINF:2.000,Amazon\n" +
                "ad-0.ts\n" +
                "#EXTINF:2.000,Amazon\n" +
                "ad-1.ts\n" +
                "#EXTINF:2.000,Amazon\n" +
                "ad-2.ts\n" +
                "#EXT-X-DISCONTINUITY\n" +
                "#EXTINF:2.000,live\n" +
                "content-3.ts\n" +
                "#EXTINF:2.000,live\n" +
                "content-4.ts"

        const val SAMPLE_PLAYLIST_CLEAN =
            "#EXTM3U\n" +
                "#EXT-X-VERSION:3\n" +
                "#EXTINF:4.000,\n" +
                "content-segment-1.ts\n" +
                "#EXTINF:4.000,\n" +
                "content-segment-2.ts\n" +
                "#EXT-X-ENDLIST"
    }
}
