package com.puretv.twitch.desktop.player

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HlsTimelineNormalizerTest {
    @Test
    fun replacesSessionLocalSequenceWithTwitchLiveSequence() {
        val result = HlsTimelineNormalizer().normalize(
            "variant-a",
            playlist(mediaSequence = 42, liveSequence = 7_638, elapsed = 15_275.924, firstSegment = 7_638),
        )

        assertTrue(result.sequenceNormalized)
        assertEquals(7_638, result.mediaSequence)
        assertTrue(result.content.contains("#EXT-X-MEDIA-SEQUENCE:7638"))
        assertFalse(result.content.contains("#EXT-X-MEDIA-SEQUENCE:42\n"))
    }

    @Test
    fun derivesCanonicalSequenceForAdPlaylistWithoutLiveSequence() {
        val adPlaylist = """
            #EXTM3U
            #EXT-X-TARGETDURATION:5
            #EXT-X-MEDIA-SEQUENCE:0
            #EXT-X-TWITCH-ELAPSED-SECS:15441.483
            #EXT-X-TWITCH-TOTAL-SECS:15447.489
        """.trimIndent()

        val result = HlsTimelineNormalizer().normalize("variant-a", adPlaylist)

        assertEquals(7_721, result.mediaSequence)
        assertTrue(result.content.contains("#EXT-X-MEDIA-SEQUENCE:7721"))
    }

    @Test
    fun backupSessionCannotMoveStableLocalTimelineBackward() {
        val normalizer = HlsTimelineNormalizer()
        normalizer.normalize(
            "variant-a",
            playlist(mediaSequence = 42, liveSequence = 100, elapsed = 200.0, firstSegment = 100, count = 4),
        )
        // An ad-only primary window moves the canonical floor to the current edge.
        normalizer.normalize(
            "variant-a",
            "#EXTM3U\n#EXT-X-MEDIA-SEQUENCE:0\n#EXT-X-TWITCH-ELAPSED-SECS:210.0",
        )

        val backup = normalizer.normalize(
            "variant-a",
            playlist(mediaSequence = 98, liveSequence = 98, elapsed = 196.0, firstSegment = 98, count = 11),
        )

        assertEquals(105, backup.mediaSequence)
        assertEquals(7, backup.segmentsTrimmed)
        assertFalse(backup.content.contains("segment-104.ts"))
        assertTrue(backup.content.contains("segment-105.ts"))
        assertEquals(1, backup.content.lineSequence().count { it == "#EXTM3U" })
        assertEquals(1, backup.content.lineSequence().count { it.startsWith("#EXT-X-MEDIA-SEQUENCE:") })
    }

    @Test
    fun aNewUpstreamVariantGetsAnIndependentTimeline() {
        val normalizer = HlsTimelineNormalizer()
        normalizer.normalize("old-url", playlist(900, 900, 1_800.0, 900))

        val restarted = normalizer.normalize("new-url", playlist(2, 2, 4.0, 2))

        assertEquals(2, restarted.mediaSequence)
        assertEquals(0, restarted.segmentsTrimmed)
    }

    private fun playlist(
        mediaSequence: Long,
        liveSequence: Long,
        elapsed: Double,
        firstSegment: Long,
        count: Int = 3,
    ): String = buildString {
        appendLine("#EXTM3U")
        appendLine("#EXT-X-VERSION:3")
        appendLine("#EXT-X-TARGETDURATION:2")
        appendLine("#EXT-X-MEDIA-SEQUENCE:$mediaSequence")
        appendLine("#EXT-X-TWITCH-LIVE-SEQUENCE:$liveSequence")
        appendLine("#EXT-X-TWITCH-ELAPSED-SECS:$elapsed")
        repeat(count) { offset ->
            appendLine("#EXT-X-PROGRAM-DATE-TIME:2026-07-22T16:00:${offset.toString().padStart(2, '0')}.000Z")
            appendLine("#EXTINF:2.000,live")
            appendLine("https://video-edge.ttvnw.net/segment-${firstSegment + offset}.ts")
        }
    }.trimEnd()
}
