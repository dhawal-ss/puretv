package com.puretv.twitch.core.adblock

import com.puretv.twitch.core.model.FilteredPlaylist

/**
 * SECTION 4.3 — Strategy 2 (fallback): strip Twitch's ad-insertion markers
 * from an HLS playlist locally, with no network round-trip.
 *
 * Twitch stitches ads (SSAI) directly into the live media playlist. A pod is
 * fenced like this — note there is usually NO #EXT-X-CUE-IN in the modern
 * (DATERANGE) format, only a pair of discontinuities around the ad segments:
 *
 *   #EXT-X-DATERANGE:...CLASS="twitch-stitched-ad"...   <- pod starts
 *   #EXT-X-DISCONTINUITY                                <- enter ad timeline
 *   #EXTINF:2.000,Amazon
 *   ad-0.ts ... ad-N.ts
 *   #EXT-X-DISCONTINUITY                                <- return to content
 *   #EXTINF:2.000,live
 *   content.ts ...
 *
 * Some edge nodes additionally (or instead) emit SCTE-35-style
 * #EXT-X-CUE-OUT / #EXT-X-CUE-IN brackets. We handle both.
 *
 * The pod is removed wholesale — the #EXTINF tags, the ad URIs, and the inner
 * discontinuities — and replaced with a single #EXT-X-DISCONTINUITY at the
 * splice point so the player knows the post-ad segment is on a fresh timeline.
 * The player stalls briefly at the cut, then resumes on clean content. The two
 * failure modes this guards against, both of which the previous single-marker
 * approach hit on real streams:
 *   1) Never leaving the ad break (no CUE-IN exists), so EVERY content segment
 *      after the first midroll is dropped and playback stalls forever.
 *   2) Orphaning an #EXTINF (dropping only the URI under it), which is
 *      malformed HLS and makes VLC/ExoPlayer error.
 */
class ManifestRewriter {

    fun filter(rawPlaylist: String): FilteredPlaylist {
        val lines = rawPlaylist.lines()
        val cleaned = ArrayList<String>(lines.size)
        var inAdBreak = false
        var adSegmentsThisPod = 0
        var removedSegments = 0
        var sawAdMarkers = false

        // Emit a single discontinuity at the splice, collapsing any run of
        // consecutive ones (e.g. a synthetic splice marker followed by Twitch's
        // own closing #EXT-X-DISCONTINUITY) into one.
        fun appendSpliceDiscontinuity() {
            if (cleaned.lastOrNull() != "#EXT-X-DISCONTINUITY") cleaned += "#EXT-X-DISCONTINUITY"
        }

        for (line in lines) {
            when {
                line.startsWith("#EXT-X-DATERANGE") && line.contains("stitched-ad") -> {
                    inAdBreak = true
                    adSegmentsThisPod = 0
                    sawAdMarkers = true
                    // drop the marker itself
                }
                line.startsWith("#EXT-X-CUE-OUT") -> {
                    // Covers #EXT-X-CUE-OUT and #EXT-X-CUE-OUT-CONT.
                    inAdBreak = true
                    adSegmentsThisPod = 0
                    sawAdMarkers = true
                }
                line.startsWith("#EXT-X-CUE-IN") -> {
                    // Explicit end of an SCTE-style pod.
                    if (inAdBreak) {
                        inAdBreak = false
                        appendSpliceDiscontinuity()
                    }
                }
                inAdBreak && line.startsWith("#EXT-X-DISCONTINUITY") -> {
                    // The discontinuity right after the start marker (no ad
                    // segments seen yet) opens the ad timeline — drop it. The
                    // next discontinuity, once we've removed ad segments, is the
                    // return-to-content boundary — that ends the pod.
                    if (adSegmentsThisPod > 0) {
                        inAdBreak = false
                        appendSpliceDiscontinuity()
                    }
                }
                line.startsWith("#EXT-X-DISCONTINUITY") -> {
                    // Content-side discontinuity (e.g. trailing one after a
                    // CUE-IN). Keep exactly one at the splice.
                    appendSpliceDiscontinuity()
                }
                inAdBreak && line.startsWith("#") -> {
                    // Per-segment ad tags inside the pod (#EXTINF,
                    // #EXT-X-PROGRAM-DATE-TIME, extra DATERANGEs, …). Dropping
                    // them is what prevents an orphaned #EXTINF.
                }
                inAdBreak && line.isNotBlank() -> {
                    // Ad segment URI.
                    adSegmentsThisPod++
                    removedSegments++
                }
                inAdBreak -> {
                    // Blank line inside the pod — swallow it.
                }
                else -> cleaned += line
            }
        }

        return FilteredPlaylist(
            content = cleaned.joinToString("\n"),
            adSegmentsRemoved = removedSegments,
            containedAds = sawAdMarkers || removedSegments > 0,
        )
    }
}

/**
 * SECTION 4.4 — Strategy 3 (last resort), the heuristics used by the player
 * layer's black-frame fallback when an ad segment slips past Strategies 1 & 2.
 *
 * Ad segments stitched by Twitch are near-universally exactly 2.0s, and ad
 * breaks are always bracketed by a pair of #EXT-X-DISCONTINUITY tags. Neither
 * signal alone is reliable (regular content occasionally has 2.0s segments,
 * and discontinuities also occur at quality switches), so we require BOTH:
 * a discontinuity transition AND a run of suspiciously-uniform short segments.
 */
object BlackFrameHeuristics {
    private const val SUSPECT_DURATION_SECONDS = 2.0
    private const val DURATION_TOLERANCE = 0.05
    private const val MIN_SUSPECT_RUN = 2

    data class Verdict(val likelyAd: Boolean, val reason: String)

    /**
     * [segmentDurations] are the #EXTINF values for the most recent N segments,
     * in playback order. [crossedDiscontinuity] is true if a #EXT-X-DISCONTINUITY
     * tag appeared immediately before the current segment.
     */
    fun evaluate(segmentDurations: List<Double>, crossedDiscontinuity: Boolean): Verdict {
        if (!crossedDiscontinuity) return Verdict(false, "no discontinuity boundary")

        val suspectRun = segmentDurations.takeLastWhile { duration ->
            kotlin.math.abs(duration - SUSPECT_DURATION_SECONDS) <= DURATION_TOLERANCE
        }

        return if (suspectRun.size >= MIN_SUSPECT_RUN) {
            Verdict(true, "discontinuity + ${suspectRun.size} consecutive ~2.0s segments")
        } else {
            Verdict(false, "discontinuity present but segment durations look like normal content")
        }
    }
}
