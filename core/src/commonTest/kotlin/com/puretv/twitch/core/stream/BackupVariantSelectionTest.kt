package com.puretv.twitch.core.stream

import com.puretv.twitch.core.adblock.AdMarkers
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Pure-logic tests for the backup player-type swap: matching the same quality
 * across a backup manifest ([HlsMasterParser.selectVariantUrl]) and detecting
 * the ad signifier ([AdMarkers]). The end-to-end swap needs a live Twitch
 * session + an active ad to verify; these lock down the deterministic parts.
 */
class BackupVariantSelectionTest {

    @Test
    fun picksExactResolutionAndFrameRate() {
        val url = HlsMasterParser.selectVariantUrl(MASTER, targetResolution = "1920x1080", targetFrameRate = 60.0)
        assertEquals("https://cdn.example/1080p60.m3u8", url)
    }

    @Test
    fun fallsBackToResolutionMatchWhenFrameRateDiffers() {
        // Master only has 1280x720 at 30fps; asking for 720p60 should still map
        // to the 720p variant rather than jumping to a different resolution.
        val url = HlsMasterParser.selectVariantUrl(MASTER, targetResolution = "1280x720", targetFrameRate = 60.0)
        assertEquals("https://cdn.example/720p30.m3u8", url)
    }

    @Test
    fun picksClosestByPixelCountWhenResolutionAbsent() {
        // 640x360 isn't in the ladder; closest by pixel count is 852x480.
        val url = HlsMasterParser.selectVariantUrl(MASTER, targetResolution = "640x360", targetFrameRate = 30.0)
        assertEquals("https://cdn.example/480p30.m3u8", url)
    }

    @Test
    fun returnsNullForMasterWithoutVariants() {
        assertNull(HlsMasterParser.selectVariantUrl("#EXTM3U\n#EXT-X-VERSION:3", "1920x1080", 60.0))
    }

    @Test
    fun adSignifierDetectsStitchedAds() {
        assertTrue(AdMarkers.containsAds(MEDIA_WITH_AD))
        assertFalse(AdMarkers.containsAds(MEDIA_CLEAN))
    }

    private companion object {
        const val MASTER =
            "#EXTM3U\n" +
                "#EXT-X-STREAM-INF:BANDWIDTH=6000000,RESOLUTION=1920x1080,FRAME-RATE=60.000,CODECS=\"avc1.4d402a,mp4a.40.2\",VIDEO=\"chunked\"\n" +
                "https://cdn.example/1080p60.m3u8\n" +
                "#EXT-X-STREAM-INF:BANDWIDTH=3000000,RESOLUTION=1280x720,FRAME-RATE=30.000,CODECS=\"avc1.4d401f,mp4a.40.2\",VIDEO=\"720p30\"\n" +
                "https://cdn.example/720p30.m3u8\n" +
                "#EXT-X-STREAM-INF:BANDWIDTH=1500000,RESOLUTION=852x480,FRAME-RATE=30.000,CODECS=\"avc1.4d401e,mp4a.40.2\",VIDEO=\"480p30\"\n" +
                "https://cdn.example/480p30.m3u8"

        const val MEDIA_WITH_AD =
            "#EXTM3U\n" +
                "#EXT-X-DATERANGE:ID=\"stitched-ad-1\",CLASS=\"twitch-stitched-ad\",START-DATE=\"2026-06-08T00:00:00Z\"\n" +
                "#EXTINF:2.000,Amazon\n" +
                "ad-0.ts"

        const val MEDIA_CLEAN =
            "#EXTM3U\n" +
                "#EXTINF:2.000,live\n" +
                "content-0.ts"
    }
}
