package com.puretv.twitch.core.adblock

/** How the Android transport should treat an HLS response. */
enum class PlaylistAction { SKIP_SEGMENT, FILTER_PLAYLIST, PASSTHROUGH }

/**
 * Pure, platform-agnostic detection for the in-process ad-block transport.
 * Free of OkHttp/Android types so it is unit-testable on any target. The old
 * interceptor guessed playlist-ness from `url.endsWith(".m3u8")`, which missed
 * media playlists served from `*.hls.ttvnw.net/...m3u8?token=...` (different
 * host + query string) and let their ads through. Here we (a) skip obvious
 * media segments by extension and (b) decide everything else from the RESPONSE,
 * never the URL string.
 */
object PlaylistDetect {
    private val SEGMENT_EXTENSIONS = listOf(".ts", ".m4s", ".mp4", ".aac")

    /**
     * [requestPath] is the URL path WITHOUT the query string. Returns
     * [PlaylistAction.SKIP_SEGMENT] for obvious media segments (so their bodies
     * are never buffered), or null when the response must be inspected.
     */
    fun classifyRequest(requestPath: String): PlaylistAction? {
        val lower = requestPath.lowercase()
        return if (SEGMENT_EXTENSIONS.any { lower.endsWith(it) }) PlaylistAction.SKIP_SEGMENT else null
    }

    /**
     * Called only when [classifyRequest] returned null. [firstBodyLine] is the
     * first non-blank line of the response body (peeked, not consumed).
     */
    fun classifyResponse(contentType: String?, firstBodyLine: String?): PlaylistAction {
        val ct = contentType?.lowercase().orEmpty()
        val isPlaylistCt = "mpegurl" in ct
        val isPlaylistBody = firstBodyLine?.trimStart()?.startsWith("#EXTM3U") == true
        return if (isPlaylistCt || isPlaylistBody) PlaylistAction.FILTER_PLAYLIST else PlaylistAction.PASSTHROUGH
    }
}
