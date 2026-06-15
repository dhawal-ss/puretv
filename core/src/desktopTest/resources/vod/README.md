# VOD test fixtures + ad-insertion spike findings

Captured 2026-06-15 during the VOD-feature ad spike (Phase 0 of the VOD core plan).

## Files
- `sample-vod-master.m3u8` — a real signed Twitch VOD **master** playlist
  (channel `twitch`, vod `2794432005`), full quality ladder. Variant URLs are
  plain CloudFront `index-dvr.m3u8` links (no token) → directly playable.
- `sample-vod-media.m3u8` — a real VOD **media** playlist for the same VOD,
  trimmed to the header + first 12 segments + `#EXT-X-ENDLIST`.

These are static text manifests; segment URIs are relative (`N.ts`), so the
fixtures don't "expire" the way a signed URL would.

## Finding (load-bearing for the design)
Surveyed **12 VODs across 12 channels** — including the most heavily-monetized
partners (xqc, summit1g, zackrawrr, sodapoppin, pokimane, hasanabi, nmplol,
lirik, shroud, tarik) plus riotgames + twitch. In **every** case the signed VOD
source manifest had:
- `twitch-stitched-ad`: 0
- `#EXT-X-DATERANGE`: 0
- `#EXT-X-CUE-OUT` / `#EXT-X-CUE-IN`: 0
- `#EXT-X-DISCONTINUITY`: 0

**Conclusion:** Twitch VOD ads are injected **client-side by the web player's ad
SDK**, not baked into the signed `index-dvr` manifest. PureTV plays the source
manifest directly through VLC, so **VOD playback is ad-free by construction** —
no manifest rewriting/proxy is required for VODs. (Contrast with live, where the
manifest IS ad-stitched and needs the proxy + player-type swap.)

If `VodMasterFixtureTest.realVodManifestsHaveNoAdMarkers` ever fails on fresh
captures, Twitch changed VOD ad delivery and the "play direct" design must be
revisited (re-introduce a strip path).
