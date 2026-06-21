package com.puretv.twitch.core.adblock

import kotlin.test.Test
import kotlin.test.assertEquals

class PlaylistDetectTest {

    @Test fun tsSegmentIsSkippedByPathEvenWithQueryString() {
        assertEquals(
            PlaylistAction.SKIP_SEGMENT,
            PlaylistDetect.classifyRequest("/v1/segment/abc.ts"),
        )
    }

    @Test fun m4sAndMp4SegmentsAreSkipped() {
        assertEquals(PlaylistAction.SKIP_SEGMENT, PlaylistDetect.classifyRequest("/seg/x.m4s"))
        assertEquals(PlaylistAction.SKIP_SEGMENT, PlaylistDetect.classifyRequest("/seg/x.mp4"))
    }

    @Test fun playlistPathIsNotPreClassifiedAsSegment() {
        assertEquals(null, PlaylistDetect.classifyRequest("/api/channel/hls/foo.m3u8"))
    }

    @Test fun mpegurlContentTypeIsAPlaylist() {
        assertEquals(
            PlaylistAction.FILTER_PLAYLIST,
            PlaylistDetect.classifyResponse("application/vnd.apple.mpegurl", null),
        )
    }

    @Test fun extm3uBodyIsAPlaylistEvenWithoutContentType() {
        assertEquals(
            PlaylistAction.FILTER_PLAYLIST,
            PlaylistDetect.classifyResponse(null, "#EXTM3U"),
        )
    }

    @Test fun htmlBodyIsPassthrough() {
        assertEquals(
            PlaylistAction.PASSTHROUGH,
            PlaylistDetect.classifyResponse("text/html", "<!DOCTYPE html>"),
        )
    }
}
