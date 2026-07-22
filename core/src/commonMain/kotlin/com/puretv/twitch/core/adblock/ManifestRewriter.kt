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

    /**
     * Strip ad pods from a media playlist.
     *
     * [assumeStartInAdBreak] is the fail-safe for the cross-refresh leak (audit
     * AD-1): live playlists are sliding 2s windows, so a pod's opening DATERANGE
     * + discontinuity can scroll out of the window while the *tail* ad segments
     * remain. Those leading segments precede any marker and would be emitted as
     * content. When the caller knows an ad was detected but the normal pass
     * stripped nothing (the opener is missing), it re-runs with this flag so the
     * window is treated as starting mid-pod: everything up to the first
     * return-to-content discontinuity is dropped. Worst case is a brief stall —
     * never a played ad.
     */
    fun filter(rawPlaylist: String, assumeStartInAdBreak: Boolean = false): FilteredPlaylist {
        val lines = rawPlaylist.lines()
        val cleaned = ArrayList<String>(lines.size)
        data class RetainedSegment(val rawIndex: Int, val rawLineIndex: Int, val cleanedLineIndex: Int)
        val retainedSegments = ArrayList<RetainedSegment>()
        var rawSegmentIndex = -1
        var lastRemovedSegmentIndex = -1
        var inAdBreak = assumeStartInAdBreak
        var adSegmentsThisPod = 0
        var removedSegments = 0
        var sawAdMarkers = assumeStartInAdBreak
        // Pod length from the DATERANGE DURATION attribute (0.0 = unknown). The
        // pod is anchored on this so an #EXT-X-DISCONTINUITY *between* stitched
        // creatives (audit AD-2) does not end the pod early — we only close once
        // the ad's worth of segment-seconds has been consumed.
        var podDurationTarget = 0.0
        var consumedAdSeconds = 0.0
        var pendingSegmentSeconds = DEFAULT_AD_SEGMENT_SECONDS

        // Emit a single discontinuity at the splice, collapsing any run of
        // consecutive ones (e.g. a synthetic splice marker followed by Twitch's
        // own closing #EXT-X-DISCONTINUITY) into one.
        fun appendSpliceDiscontinuity() {
            if (cleaned.lastOrNull() != "#EXT-X-DISCONTINUITY") cleaned += "#EXT-X-DISCONTINUITY"
        }

        // True once we've stripped enough ad-segment-seconds to satisfy the
        // declared pod duration (or the duration is unknown, in which case any
        // post-segment discontinuity is treated as the pod boundary).
        fun podDurationConsumed(): Boolean =
            podDurationTarget <= 0.0 || consumedAdSeconds + DURATION_EPSILON >= podDurationTarget

        for ((rawLineIndex, line) in lines.withIndex()) {
            val isSegmentUri = line.isNotBlank() && !line.startsWith("#")
            if (isSegmentUri) rawSegmentIndex++
            when {
                isStructuralHeaderTag(line) -> {
                    // Playlist-level header tags (#EXTM3U, #EXT-X-VERSION,
                    // #EXT-X-MEDIA-SEQUENCE, …). These are never ad markers or
                    // per-segment tags, so they must survive even when we start
                    // mid-pod (assumeStartInAdBreak) — otherwise the leading
                    // '#'-line catch-all below would eat #EXTM3U and hand the
                    // player a headerless playlist it can reject. Must be FIRST
                    // so #EXT-X-DISCONTINUITY-SEQUENCE isn't misread as a
                    // content discontinuity.
                    cleaned += line
                }
                line.startsWith("#EXT-X-DATERANGE") && line.contains(AdMarkers.SIGNIFIER, ignoreCase = true) -> {
                    // Match the SAME canonical `stitched` signifier the detector
                    // (AdMarkers) uses, not the narrower `stitched-ad` literal: if
                    // Twitch ever reformats the CLASS (it has before), the normal
                    // pass would otherwise miss a pod the detector still flags,
                    // leaning entirely on the mid-break fail-safe to catch it.
                    // A repeated DATERANGE inside an already-open pod must not
                    // reset progress — only (re)arm the duration target.
                    if (!inAdBreak) {
                        inAdBreak = true
                        adSegmentsThisPod = 0
                        consumedAdSeconds = 0.0
                    }
                    parseDuration(line)?.let { podDurationTarget = maxOf(podDurationTarget, it) }
                    sawAdMarkers = true
                    // drop the marker itself
                }
                line.startsWith("#EXT-X-CUE-OUT") -> {
                    // Covers #EXT-X-CUE-OUT and #EXT-X-CUE-OUT-CONT.
                    if (!inAdBreak) {
                        inAdBreak = true
                        adSegmentsThisPod = 0
                        consumedAdSeconds = 0.0
                    }
                    parseCueOutDuration(line)?.let { podDurationTarget = maxOf(podDurationTarget, it) }
                    sawAdMarkers = true
                }
                line.startsWith("#EXT-X-CUE-IN") -> {
                    // Explicit end of an SCTE-style pod — authoritative boundary.
                    if (inAdBreak) {
                        inAdBreak = false
                        podDurationTarget = 0.0
                        appendSpliceDiscontinuity()
                    }
                }
                inAdBreak && line.startsWith("#EXT-X-DISCONTINUITY") -> {
                    // A discontinuity inside the pod. It ends the pod ONLY if
                    // we've already removed ad segments AND consumed the declared
                    // duration; otherwise it is either the opening boundary (no
                    // segments yet) or an internal creative boundary (duration
                    // not yet consumed) — drop it and keep stripping.
                    if (adSegmentsThisPod > 0 && podDurationConsumed()) {
                        inAdBreak = false
                        podDurationTarget = 0.0
                        appendSpliceDiscontinuity()
                    }
                }
                line.startsWith("#EXT-X-DISCONTINUITY") -> {
                    // Content-side discontinuity (e.g. trailing one after a
                    // CUE-IN). Keep exactly one at the splice.
                    appendSpliceDiscontinuity()
                }
                inAdBreak && line.startsWith("#EXTINF") -> {
                    // Remember this segment's declared duration so the following
                    // URI line can count toward the pod's consumed seconds.
                    pendingSegmentSeconds = parseExtinfSeconds(line) ?: DEFAULT_AD_SEGMENT_SECONDS
                    // drop the tag (its URI is dropped below)
                }
                inAdBreak && line.startsWith("#") -> {
                    // Other per-segment ad tags inside the pod
                    // (#EXT-X-PROGRAM-DATE-TIME, extra DATERANGEs, …). Dropping
                    // them is what prevents an orphaned #EXTINF.
                }
                inAdBreak && line.isNotBlank() -> {
                    // Ad segment URI.
                    adSegmentsThisPod++
                    removedSegments++
                    lastRemovedSegmentIndex = rawSegmentIndex
                    consumedAdSeconds += pendingSegmentSeconds
                    pendingSegmentSeconds = DEFAULT_AD_SEGMENT_SECONDS
                }
                inAdBreak -> {
                    // Blank line inside the pod — swallow it.
                }
                else -> {
                    cleaned += line
                    if (isSegmentUri) {
                        retainedSegments += RetainedSegment(rawSegmentIndex, rawLineIndex, cleaned.lastIndex)
                    }
                }
            }
        }

        val sequenceSafeContent = repairLiveSequenceAfterRemoval(
            rawLines = lines,
            cleaned = cleaned,
            lastRemovedSegmentIndex = lastRemovedSegmentIndex,
            retainedSegments = retainedSegments.map {
                SegmentLocation(it.rawIndex, it.rawLineIndex, it.cleanedLineIndex)
            },
        )

        return FilteredPlaylist(
            content = sequenceSafeContent.joinToString("\n"),
            adSegmentsRemoved = removedSegments,
            containedAds = sawAdMarkers || removedSegments > 0,
        )
    }

    private companion object {
        // Twitch stitches ad segments at exactly 2.0s; used only as the fallback
        // when an #EXTINF duration can't be parsed.
        const val DEFAULT_AD_SEGMENT_SECONDS = 2.0
        const val DURATION_EPSILON = 0.001

        data class SegmentLocation(val rawIndex: Int, val rawLineIndex: Int, val cleanedLineIndex: Int)

        /**
         * Removing a segment from a live HLS playlist without changing
         * `#EXT-X-MEDIA-SEQUENCE` renumbers every later segment. A player that
         * already consumed the pre-ad window then sees the first post-ad content
         * under an old sequence number and can wait forever. Once post-ad content
         * exists, trim the already-consumed prefix and advance both sequence
         * headers to the original segment's identity.
         *
         * While the playlist still contains only pre-ad content plus the active
         * pod, leave that prefix in place. It gives the player a valid window to
         * keep polling; the sequence repair engages on the first refresh that has
         * real content after the removed pod.
         */
        fun repairLiveSequenceAfterRemoval(
            rawLines: List<String>,
            cleaned: List<String>,
            lastRemovedSegmentIndex: Int,
            retainedSegments: List<SegmentLocation>,
        ): List<String> {
            if (lastRemovedSegmentIndex < 0) return cleaned

            val mediaSequenceLine = rawLines.firstOrNull { it.startsWith("#EXT-X-MEDIA-SEQUENCE:") }
                ?: return cleaned // VOD/event fixtures do not need sliding-window repair.
            val mediaSequence = mediaSequenceLine.substringAfter(':').trim().toLongOrNull() ?: return cleaned
            val firstPostAd = retainedSegments.firstOrNull { it.rawIndex > lastRemovedSegmentIndex }
                ?: return cleaned

            val previousUriLine = retainedSegments
                .lastOrNull { it.cleanedLineIndex < firstPostAd.cleanedLineIndex }
                ?.cleanedLineIndex
                ?: -1
            val suffix = cleaned.subList(previousUriLine + 1, cleaned.size).toMutableList()
            // If the ad pod began at the first segment there is no previous URI,
            // so the slice also contains the playlist headers. They are rebuilt
            // below with corrected sequence values and must not be duplicated.
            suffix.removeAll(::isStructuralHeaderTag)
            val firstSuffixUri = suffix.indexOfFirst { it.isNotBlank() && !it.startsWith("#") }
            if (firstSuffixUri < 0) return cleaned

            // Discontinuities before the new first segment are represented by
            // EXT-X-DISCONTINUITY-SEQUENCE, not by a leading discontinuity tag.
            for (i in firstSuffixUri - 1 downTo 0) {
                if (suffix[i].trim() == "#EXT-X-DISCONTINUITY") suffix.removeAt(i)
            }

            val discontinuitiesBefore = rawLines
                .take(firstPostAd.rawLineIndex)
                .count { it.trim() == "#EXT-X-DISCONTINUITY" }
            val originalDiscontinuitySequence = rawLines
                .firstOrNull { it.startsWith("#EXT-X-DISCONTINUITY-SEQUENCE:") }
                ?.substringAfter(':')
                ?.trim()
                ?.toLongOrNull()
                ?: 0L

            val headers = cleaned.filter(::isStructuralHeaderTag).toMutableList()
            replaceNumericHeader(headers, "#EXT-X-MEDIA-SEQUENCE:", mediaSequence + firstPostAd.rawIndex)
            if (discontinuitiesBefore > 0 || headers.any { it.startsWith("#EXT-X-DISCONTINUITY-SEQUENCE:") }) {
                replaceNumericHeader(
                    headers,
                    "#EXT-X-DISCONTINUITY-SEQUENCE:",
                    originalDiscontinuitySequence + discontinuitiesBefore,
                )
            }
            return headers + suffix
        }

        fun replaceNumericHeader(lines: MutableList<String>, prefix: String, value: Long) {
            val index = lines.indexOfFirst { it.startsWith(prefix) }
            if (index >= 0) {
                lines[index] = "$prefix$value"
            } else {
                val mediaSequenceIndex = lines.indexOfFirst { it.startsWith("#EXT-X-MEDIA-SEQUENCE:") }
                lines.add(if (mediaSequenceIndex >= 0) mediaSequenceIndex + 1 else lines.size, "$prefix$value")
            }
        }

        /**
         * Playlist-level (not per-segment, not ad-related) tags that must never
         * be stripped, even when the fail-safe treats the window as starting
         * mid-ad-break. Order matters at the call site: this is checked before
         * the discontinuity branches so #EXT-X-DISCONTINUITY-SEQUENCE (a header)
         * isn't confused with #EXT-X-DISCONTINUITY (a content boundary).
         */
        fun isStructuralHeaderTag(line: String): Boolean =
            line.startsWith("#EXTM3U") ||
                line.startsWith("#EXT-X-VERSION") ||
                line.startsWith("#EXT-X-TARGETDURATION") ||
                line.startsWith("#EXT-X-MEDIA-SEQUENCE") ||
                line.startsWith("#EXT-X-DISCONTINUITY-SEQUENCE") ||
                line.startsWith("#EXT-X-PLAYLIST-TYPE") ||
                line.startsWith("#EXT-X-INDEPENDENT-SEGMENTS") ||
                line.startsWith("#EXT-X-ALLOW-CACHE") ||
                line.startsWith("#EXT-X-START") ||
                line.startsWith("#EXT-X-TWITCH")

        fun parseExtinfSeconds(line: String): Double? =
            // "#EXTINF:2.000,Amazon" -> 2.0
            line.removePrefix("#EXTINF:").substringBefore(',').trim().toDoubleOrNull()

        // Anchored on a ':'/',' boundary (mirrors LocalStreamProxy.streamInfAttr)
        // so PLANNED-DURATION= is NOT matched as DURATION= — grabbing the planned
        // value mis-anchors the pod length and over/under-strips.
        fun parseDuration(line: String): Double? =
            Regex("(?:^|[,:])DURATION=([0-9]*\\.?[0-9]+)").find(line)?.groupValues?.get(1)?.toDoubleOrNull()

        fun parseCueOutDuration(line: String): Double? =
            // "#EXT-X-CUE-OUT:30.000" or "#EXT-X-CUE-OUT:DURATION=30"
            parseDuration(line)
                ?: line.substringAfter(':', "").trim().toDoubleOrNull()
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
