package main

import (
	"strings"
	"testing"
)

// SECTION 09 — these cases mirror the fixtures that should exist for
// ManifestRewriterTest in core/src/commonTest (Section 13's testing-strategy
// guidance: "ad-block correctness is the single most safety-critical path —
// test it on both the in-app fallback and this server, with the same fixtures").

const samplePlaylistWithAdBreak = `#EXTM3U
#EXT-X-VERSION:3
#EXTINF:2.000,
content-segment-1.ts
#EXTINF:2.000,
content-segment-2.ts
#EXT-X-DATERANGE:ID="stitched-ad-2026",CLASS="twitch-stitched-ad",START-DATE="2026-06-08T00:00:00Z"
#EXT-X-DISCONTINUITY
#EXT-X-CUE-OUT:30
#EXTINF:2.000,
ad-segment-1.ts
#EXTINF:2.000,
ad-segment-2.ts
#EXT-X-CUE-IN
#EXT-X-DISCONTINUITY
#EXTINF:2.000,
content-segment-3.ts
#EXT-X-ENDLIST`

const samplePlaylistClean = `#EXTM3U
#EXT-X-VERSION:3
#EXTINF:4.000,
content-segment-1.ts
#EXTINF:4.000,
content-segment-2.ts
#EXT-X-ENDLIST`

// Real modern midroll format: a stitched-ad DATERANGE, the pod fenced by two
// #EXT-X-DISCONTINUITY tags, and NO CUE-OUT/CUE-IN. Parity fixture with
// ManifestRewriterTest.SAMPLE_PLAYLIST_DATERANGE_ONLY.
const samplePlaylistDaterangeOnly = `#EXTM3U
#EXT-X-VERSION:3
#EXT-X-TARGETDURATION:2
#EXT-X-MEDIA-SEQUENCE:100
#EXTINF:2.000,live
content-1.ts
#EXTINF:2.000,live
content-2.ts
#EXT-X-DATERANGE:ID="stitched-ad-100",CLASS="twitch-stitched-ad",START-DATE="2026-06-08T00:00:00Z",DURATION=6.0
#EXT-X-DISCONTINUITY
#EXTINF:2.000,Amazon
ad-0.ts
#EXTINF:2.000,Amazon
ad-1.ts
#EXTINF:2.000,Amazon
ad-2.ts
#EXT-X-DISCONTINUITY
#EXTINF:2.000,live
content-3.ts
#EXTINF:2.000,live
content-4.ts`

func TestFilterPlaylist_RemovesStitchedAdBreak(t *testing.T) {
	result := FilterPlaylist(samplePlaylistWithAdBreak)

	if !result.ContainedAds {
		t.Error("expected ContainedAds = true for a playlist with a twitch-stitched-ad break")
	}
	if result.AdSegmentsRemoved != 2 {
		t.Errorf("expected 2 ad segments removed, got %d", result.AdSegmentsRemoved)
	}
	for _, forbidden := range []string{"ad-segment-1.ts", "ad-segment-2.ts", "twitch-stitched-ad", "#EXT-X-CUE-OUT", "#EXT-X-CUE-IN"} {
		if strings.Contains(result.Content, forbidden) {
			t.Errorf("filtered playlist still contains %q:\n%s", forbidden, result.Content)
		}
	}
	for _, expected := range []string{"content-segment-1.ts", "content-segment-2.ts", "content-segment-3.ts", "#EXT-X-ENDLIST"} {
		if !strings.Contains(result.Content, expected) {
			t.Errorf("filtered playlist is missing expected content line %q:\n%s", expected, result.Content)
		}
	}
}

func TestFilterPlaylist_RemovesDaterangeOnlyAdBreak(t *testing.T) {
	// No CUE-IN in this format: the pod end must be detected from the closing
	// #EXT-X-DISCONTINUITY, or every content segment after the first midroll is
	// dropped and playback stalls forever.
	result := FilterPlaylist(samplePlaylistDaterangeOnly)

	if !result.ContainedAds {
		t.Error("expected ContainedAds = true for a DATERANGE-only ad break")
	}
	if result.AdSegmentsRemoved != 3 {
		t.Errorf("expected 3 ad segments removed, got %d", result.AdSegmentsRemoved)
	}
	for _, forbidden := range []string{"ad-0.ts", "ad-1.ts", "ad-2.ts", "twitch-stitched-ad"} {
		if strings.Contains(result.Content, forbidden) {
			t.Errorf("filtered playlist still contains %q:\n%s", forbidden, result.Content)
		}
	}
	for _, expected := range []string{"content-1.ts", "content-2.ts", "content-3.ts", "content-4.ts"} {
		if !strings.Contains(result.Content, expected) {
			t.Errorf("content segment %q was wrongly dropped — the ad break never ended:\n%s", expected, result.Content)
		}
	}

	// No orphaned #EXTINF: every #EXTINF must be paired with a URI.
	var extinf, uris int
	for _, line := range strings.Split(result.Content, "\n") {
		switch {
		case strings.HasPrefix(line, "#EXTINF"):
			extinf++
		case line != "" && !strings.HasPrefix(line, "#"):
			uris++
		}
	}
	if extinf != uris {
		t.Errorf("orphaned #EXTINF: %d #EXTINF vs %d URIs:\n%s", extinf, uris, result.Content)
	}
}

func TestFilterPlaylist_PassesCleanPlaylistThrough(t *testing.T) {
	result := FilterPlaylist(samplePlaylistClean)

	if result.ContainedAds {
		t.Error("expected ContainedAds = false for a playlist with no ad markers")
	}
	if result.AdSegmentsRemoved != 0 {
		t.Errorf("expected 0 ad segments removed from a clean playlist, got %d", result.AdSegmentsRemoved)
	}
	if result.Content != samplePlaylistClean {
		t.Errorf("clean playlist should pass through byte-for-byte (modulo the line-join, which uses \\n):\nwant:\n%s\ngot:\n%s", samplePlaylistClean, result.Content)
	}
}

func TestFilterPlaylist_HandlesBareCueOutWithoutDaterange(t *testing.T) {
	// Some live edge nodes emit #EXT-X-CUE-OUT/#EXT-X-CUE-IN without an
	// accompanying #EXT-X-DATERANGE. The filter must still treat that as an
	// ad break — see AdBlockEngine SECTION 04.3's note that "neither signal
	// alone is reliable" but CUE-OUT specifically always brackets a break.
	playlist := "#EXTM3U\n" +
		"#EXTINF:2.000,\ncontent.ts\n" +
		"#EXT-X-CUE-OUT:30\n#EXTINF:2.000,\nad.ts\n#EXT-X-CUE-IN\n" +
		"#EXTINF:2.000,\nmore-content.ts"

	result := FilterPlaylist(playlist)

	if strings.Contains(result.Content, "ad.ts") {
		t.Errorf("ad segment under a bare CUE-OUT/CUE-IN pair should be removed:\n%s", result.Content)
	}
	if !strings.Contains(result.Content, "more-content.ts") {
		t.Errorf("content after CUE-IN should be preserved:\n%s", result.Content)
	}
}
