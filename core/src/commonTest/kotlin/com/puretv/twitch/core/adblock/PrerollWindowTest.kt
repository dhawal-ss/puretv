package com.puretv.twitch.core.adblock

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Regression fixture captured from LIVE Twitch traffic (2026-09-01, playerType
 * "site", anonymous viewer joining a live channel).
 *
 * Every other fixture in this package places content segments AFTER the pod, so
 * the rewriter always had somewhere to splice back to. Real prerolls do not look
 * like that: the pod is declared at DURATION=30.235 while the sliding window
 * holds only ~4 segments, so for the first ~30 seconds of every stream open
 * EVERY segment in the window is an ad and there is no post-ad content to
 * return to.
 */
class PrerollWindowTest {

    private val rewriter = ManifestRewriter()

    /**
     * Documents the degenerate case rather than asserting it away: with every
     * segment in the window belonging to the pod there is no content to splice
     * back to, so stripping CANNOT produce a playable window. The rewriter is
     * doing the only correct thing available to it — removing all four ad
     * segments — and the result is a playlist with no segments at all.
     *
     * That is a valid outcome for [ManifestRewriter] and an invalid thing to
     * hand a player. The decision therefore belongs one layer up, in
     * `TvAdBlockInterceptor.resolveCleanMedia`, which is the only place that
     * knows the seamless backup swap was already tried and failed. It currently
     * returns this content verbatim, which is why an all-ad window reaches
     * ExoPlayer as an unplayable manifest.
     */
    @Test
    fun allAdWindowStripsEverythingAndLeavesNoSegments() {
        val result = rewriter.filter(REAL_PREROLL_WINDOW)

        assertTrue(result.containedAds, "a stitched-ad pod must be reported as ads")
        assertEquals(4, result.adSegmentsRemoved, "all four in-window ad segments should be removed")

        val segmentUris = result.content.lines().filter { it.isNotBlank() && !it.startsWith("#") }
        assertTrue(
            segmentUris.isEmpty(),
            "fixture invariant: this window is entirely ad, so nothing should survive:\n${result.content}",
        )
        assertTrue(
            result.content.startsWith("#EXTM3U"),
            "even the degenerate result must stay a well-formed playlist:\n${result.content}",
        )
    }

    private companion object {
        val REAL_PREROLL_WINDOW = """
            #EXTM3U
            #EXT-X-VERSION:3
            #EXT-X-TARGETDURATION:5
            #EXT-X-MEDIA-SEQUENCE:0
            #EXT-X-TWITCH-ELAPSED-SECS:5926.466
            #EXT-X-TWITCH-TOTAL-SECS:5934.466
            #EXT-X-START:TIME-OFFSET=0.000
            #EXT-X-DATERANGE:ID="playlist-creation-1788289296",CLASS="timestamp",START-DATE="2026-09-01T19:01:36.832Z",END-ON-NEXT=YES,X-SERVER-TIME="1788289296.83"
            #EXT-X-DATERANGE:ID="playlist-session-1788289296",CLASS="twitch-session",START-DATE="2026-09-01T19:01:36.832Z",END-ON-NEXT=YES,X-TV-TWITCH-SESSIONID="5197382620532188731"
            #EXT-X-DATERANGE:ID="stitched-ad-1788289291-30235000000",CLASS="twitch-stitched-ad",START-DATE="2026-09-01T19:01:31.355Z",DURATION=30.235,X-TV-TWITCH-AD-CLICK-TRACKING-URL="https://example.invalid/c"
            #EXT-X-DATERANGE:ID="source-1788289291",CLASS="twitch-stream-source",START-DATE="2026-09-01T19:01:31.355Z",END-ON-NEXT=YES,X-TV-TWITCH-STREAM-SOURCE="Amazon|2474283100494"
            #EXT-X-DATERANGE:ID="quartile-1788289291-0",CLASS="twitch-ad-quartile",START-DATE="2026-09-01T19:01:31.355Z",DURATION=2.000,X-TV-TWITCH-AD-QUARTILE="0"
            #EXT-X-DISCONTINUITY
            #EXT-X-PROGRAM-DATE-TIME:2026-09-01T19:01:31.355Z
            #EXTINF:2.000,Amazon|2474283100494
            https://cdn.invalid/v1/segment/ad-0
            #EXT-X-PROGRAM-DATE-TIME:2026-09-01T19:01:33.355Z
            #EXTINF:2.000,Amazon|2474283100494
            https://cdn.invalid/v1/segment/ad-1
            #EXT-X-PROGRAM-DATE-TIME:2026-09-01T19:01:35.355Z
            #EXTINF:2.000,Amazon|2474283100494
            https://cdn.invalid/v1/segment/ad-2
            #EXT-X-DATERANGE:ID="quartile-1788289297-1",CLASS="twitch-ad-quartile",START-DATE="2026-09-01T19:01:37.355Z",DURATION=2.000,X-TV-TWITCH-AD-QUARTILE="1"
            #EXT-X-PROGRAM-DATE-TIME:2026-09-01T19:01:37.355Z
            #EXTINF:2.000,Amazon|2474283100494
            https://cdn.invalid/v1/segment/ad-3
        """.trimIndent()
    }
}
