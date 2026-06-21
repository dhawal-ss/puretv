package com.puretv.twitch.core.adblock

/**
 * DEBUG-only helper to exercise the ad-block path on demand during a live smoke,
 * without waiting for a real Twitch ad break. Off by default; the Android debug
 * Settings screen flips [enabled]. When enabled, [inject] splices a synthetic
 * DATERANGE stitched-ad pod into a clean media playlist so the interceptor's
 * filter has something to strip.
 */
object AdSimulator {
    // Written from the Compose main thread (debug Settings toggle), read from the
    // OkHttp dispatcher thread (the interceptor). Volatile guarantees visibility
    // across those threads. Mirrors TokenHolder in CoreModule.
    @Volatile
    var enabled: Boolean = false

    private val POD = listOf(
        "#EXT-X-DATERANGE:ID=\"sim-ad\",CLASS=\"twitch-stitched-ad\",DURATION=4.000",
        "#EXT-X-DISCONTINUITY",
        "#EXTINF:2.000,Amazon",
        "sim-ad-0.ts",
        "#EXTINF:2.000,Amazon",
        "sim-ad-1.ts",
        "#EXT-X-DISCONTINUITY",
    )

    /** No-op unless [enabled]; also a no-op if the playlist already has ads. */
    fun inject(playlist: String): String {
        if (!enabled || AdMarkers.containsAds(playlist)) return playlist
        val lines = playlist.lines().toMutableList()
        val afterHeader = lines.indexOfLast { it.startsWith("#EXT-X-MEDIA-SEQUENCE") }
        val insertAt = if (afterHeader >= 0) afterHeader + 1 else 1
        lines.addAll(insertAt, POD)
        return lines.joinToString("\n")
    }
}
