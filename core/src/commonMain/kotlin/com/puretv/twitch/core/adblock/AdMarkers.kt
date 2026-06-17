package com.puretv.twitch.core.adblock

/**
 * Twitch ad-detection signifiers, kept in one place so the manifest rewriter,
 * the backup-swap resolver, and the diagnostics all agree on what "this
 * playlist contains an ad" means.
 *
 * The canonical signifier is the substring `stitched` (from
 * `#EXT-X-DATERANGE:...CLASS="twitch-stitched-ad"...`). This mirrors
 * `AdSignifier = 'stitched'` in the reference userscript
 * (pixeltris/TwitchAdSolutions, vaft) — broader and more change-resistant than
 * matching the full `CLASS="twitch-stitched-ad"` literal, which Twitch has
 * reformatted before.
 */
object AdMarkers {
    const val SIGNIFIER = "stitched"

    /**
     * All substrings whose presence flags an ad pod. `stitched` covers the
     * DATERANGE `CLASS="twitch-stitched-ad"` format; `#EXT-X-CUE-OUT` covers the
     * SCTE-35 bracket format that some edge nodes emit WITHOUT a `stitched`
     * DATERANGE (audit AD-3: detection must agree with what [ManifestRewriter]
     * actually strips, or a pure-SCTE pod is detected as clean and served raw).
     */
    val SIGNIFIERS: List<String> = listOf(SIGNIFIER, "#EXT-X-CUE-OUT")

    /** True if [playlist] (a media playlist) currently contains an ad pod. */
    fun containsAds(playlist: String): Boolean =
        SIGNIFIERS.any { playlist.contains(it, ignoreCase = true) }
}
