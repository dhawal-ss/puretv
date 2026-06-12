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

    /** True if [playlist] (a media playlist) currently contains a stitched ad. */
    fun containsAds(playlist: String): Boolean = playlist.contains(SIGNIFIER, ignoreCase = true)
}
