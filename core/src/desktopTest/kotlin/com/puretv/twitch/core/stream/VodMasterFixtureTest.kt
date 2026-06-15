package com.puretv.twitch.core.stream

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Parses a REAL captured Twitch VOD master playlist
 * (`commonTest/resources/vod/sample-vod-master.m3u8`, see that dir's README for
 * provenance). Guards [HlsMasterParser] against the actual VOD master shape, and
 * documents the spike finding that VOD source manifests carry NO ad markers.
 */
class VodMasterFixtureTest {

    private fun fixture(name: String): String =
        this::class.java.classLoader.getResourceAsStream("vod/$name")!!
            .bufferedReader().readText()

    @Test fun parsesRealVodMasterLadder() {
        val master = fixture("sample-vod-master.m3u8")
        val variants = HlsMasterParser.parseVariants(master)
        // chunked(Source) + 720p60 + 720p30 + 480p + 360p + 160p + audio_only
        assertEquals(7, variants.size, "expected the full VOD quality ladder")
        assertTrue(variants.any { it.resolution == "1920x1080" }, "missing source/1080 variant")
        assertTrue(
            variants.all { it.url.startsWith("https://") && "index-dvr.m3u8" in it.url },
            "VOD variant URLs should be direct CloudFront index-dvr playlists",
        )
    }

    @Test fun realVodManifestsHaveNoAdMarkers() {
        // The crux of the spike: the signed VOD source manifest is ad-free by
        // construction (Twitch VOD ads are injected client-side by the web
        // player, not baked into this manifest). If this ever fails, Twitch
        // changed VOD ad delivery and the design's "play direct" assumption
        // needs revisiting.
        listOf("sample-vod-master.m3u8", "sample-vod-media.m3u8").forEach { name ->
            val text = fixture(name)
            listOf("twitch-stitched-ad", "#EXT-X-CUE-OUT", "#EXT-X-DATERANGE").forEach { marker ->
                assertTrue(marker !in text, "$name unexpectedly contains ad marker $marker")
            }
        }
    }
}
