package main

import "strings"

// FilteredPlaylist mirrors core/.../adblock/ManifestRewriter's FilteredPlaylist —
// see SECTION 04.3 in the Kotlin source (core/src/commonMain/kotlin/com/puretv/twitch/core/adblock/ManifestRewriter.kt).
// Keeping the shape identical means the *behavior* of "manifest rewrite" is
// provably the same whether it runs in-app (Strategy 2 fallback) or here on
// the self-hosted proxy (Strategy 1 primary path) — one playlist, one set of
// rules, two execution sites.
type FilteredPlaylist struct {
	Content           string
	AdSegmentsRemoved int
	ContainedAds      bool
}

// FilterPlaylist strips Twitch's stitched-ad bracketing from an HLS playlist.
//
// Twitch stitches ads (SSAI) into the live media playlist. A pod is fenced
// like this — note there is usually NO #EXT-X-CUE-IN in the modern (DATERANGE)
// format, only a pair of discontinuities around the ad segments:
//
//	#EXT-X-DATERANGE:...CLASS="twitch-stitched-ad"...   <- pod starts
//	#EXT-X-DISCONTINUITY                                <- enter ad timeline
//	#EXTINF:2.000,Amazon
//	ad-0.ts ... ad-N.ts
//	#EXT-X-DISCONTINUITY                                <- return to content
//	#EXTINF:2.000,live
//	content.ts ...
//
// Some edge nodes additionally (or instead) emit SCTE-35-style
// #EXT-X-CUE-OUT / #EXT-X-CUE-IN brackets. Both are handled. The pod is removed
// wholesale (#EXTINF tags, ad URIs, inner discontinuities) and replaced with a
// single #EXT-X-DISCONTINUITY at the splice. This is a line-for-line port of
// ManifestRewriter.filter — do not let the two drift apart; if Twitch changes
// its ad-marker format, update both.
func FilterPlaylist(raw string) FilteredPlaylist {
	lines := strings.Split(raw, "\n")
	cleaned := make([]string, 0, len(lines))

	inAdBreak := false
	adSegmentsThisPod := 0
	removedSegments := 0
	sawAdMarkers := false

	// Emit a single discontinuity at the splice, collapsing consecutive ones.
	appendSpliceDiscontinuity := func() {
		if len(cleaned) == 0 || cleaned[len(cleaned)-1] != "#EXT-X-DISCONTINUITY" {
			cleaned = append(cleaned, "#EXT-X-DISCONTINUITY")
		}
	}

	for _, line := range lines {
		switch {
		// Match the canonical `stitched` signifier (parity with AdMarkers.SIGNIFIER
		// in core) rather than the narrower `stitched-ad` literal, so a Twitch CLASS
		// reformat does not slip a pod past the rewriter.
		case strings.HasPrefix(line, "#EXT-X-DATERANGE") && strings.Contains(line, "stitched"):
			inAdBreak = true
			adSegmentsThisPod = 0
			sawAdMarkers = true
			// drop the marker itself

		case strings.HasPrefix(line, "#EXT-X-CUE-OUT"):
			// Covers #EXT-X-CUE-OUT and #EXT-X-CUE-OUT-CONT.
			inAdBreak = true
			adSegmentsThisPod = 0
			sawAdMarkers = true

		case strings.HasPrefix(line, "#EXT-X-CUE-IN"):
			// Explicit end of an SCTE-style pod.
			if inAdBreak {
				inAdBreak = false
				appendSpliceDiscontinuity()
			}

		case inAdBreak && strings.HasPrefix(line, "#EXT-X-DISCONTINUITY"):
			// First discontinuity (no ad segments yet) opens the ad timeline —
			// drop it. A later one, after ad segments, ends the pod.
			if adSegmentsThisPod > 0 {
				inAdBreak = false
				appendSpliceDiscontinuity()
			}

		case strings.HasPrefix(line, "#EXT-X-DISCONTINUITY"):
			// Content-side discontinuity — keep exactly one at the splice.
			appendSpliceDiscontinuity()

		case inAdBreak && strings.HasPrefix(line, "#"):
			// Per-segment ad tags inside the pod (#EXTINF, etc.) — drop so we
			// never orphan an #EXTINF.

		case inAdBreak && strings.TrimSpace(line) != "":
			// Ad segment URI.
			adSegmentsThisPod++
			removedSegments++

		case inAdBreak:
			// Blank line inside the pod — swallow it.

		default:
			cleaned = append(cleaned, line)
		}
	}

	return FilteredPlaylist{
		Content:           strings.Join(cleaned, "\n"),
		AdSegmentsRemoved: removedSegments,
		ContainedAds:      sawAdMarkers || removedSegments > 0,
	}
}
