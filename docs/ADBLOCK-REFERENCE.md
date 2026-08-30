# Ad-block reference: TwitchAdSolutions (vaft)

PureTV's ad-block engine is a Kotlin port of the approach used by
[pixeltris/TwitchAdSolutions](https://github.com/pixeltris/TwitchAdSolutions),
specifically the `vaft` variant. That script is the reference implementation
these files mirror:

- `core/src/commonMain/kotlin/com/puretv/twitch/core/adblock/AdMarkers.kt`
- `core/src/commonMain/kotlin/com/puretv/twitch/core/adblock/BackupStreamResolver.kt`
- `core/src/commonMain/kotlin/com/puretv/twitch/core/stream/StreamResolver.kt`
- `app-windows/src/main/kotlin/com/puretv/twitch/desktop/player/LocalStreamProxy.kt`

## Pinned version

| | |
|---|---|
| Upstream | https://github.com/pixeltris/TwitchAdSolutions |
| Variant | `vaft` |
| Version | 37.0.0 |
| sha256 | `06861594a5e6e984ec3da73a4f25d84bea6a816b97ab7cbf4c80fa205583b3ee` |

Fetch the exact reference the port was written against:

```bash
curl -sL https://github.com/pixeltris/TwitchAdSolutions/raw/master/vaft/vaft.user.js -o vaft.user.js
sha256sum vaft.user.js   # expect 06861594a5e6e984ec3da73a4f25d84bea6a816b97ab7cbf4c80fa205583b3ee
```

`vaft.user.js` is deliberately **not** committed to this repo. It is a browser
userscript, MIT licensed and maintained upstream. It was never compiled,
bundled or executed by any PureTV target: no Gradle script, manifest, proguard
config or CI workflow has ever referenced it. Keeping a local copy in the repo
root added nothing but a vendored duplicate that drifts from upstream, so the
version and hash above are pinned here instead. `.gitignore` keeps a local
working copy out of version control if you download one.

## Why the port is server-side

The userscript hooks `window.Worker` in a browser page. PureTV has no browser:
the Windows app proxies the HLS playlist locally (`LocalStreamProxy`) and the
mobile and TV apps resolve streams through `StreamResolver` before handing a
manifest to ExoPlayer. The ad-segment detection and the backup player-type
fallback are the parts worth porting; the DOM and Worker plumbing is not.
