# PureTV ad-block proxy (self-hosted)

A small, self-hostable Go server implementing the same playlist-proxy contract
as the public TTV LOL PRO endpoints that `AdBlockEngine` (Section 04) talks to
by default. Point PureTV's **Settings → Ad Block → Custom proxy URL** at your
own instance to stop depending on a third party entirely.

## Why you'd run this

PureTV's Strategy 1 ad-block path ("Proxy Router") fetches the ~2KB stream
playlist through a proxy server in a region Twitch doesn't ad-stitch for, then
plays the (clean) result. By default it uses a public, TTV-LOL-PRO-compatible
endpoint. Public endpoints can be slow, rate-limited, or disappear. Running
your own gives you:

- A proxy you control and trust
- No reliance on third-party uptime
- The same manifest-rewrite safety net the in-app fallback uses, applied
  *server-side* too — so even if the "different region" trick stops working,
  this server still strips Twitch's stitched-ad markers before the playlist
  reaches your player

## Quick start (Docker — recommended)

```bash
git clone <this-repo> && cd PureTVforTwitch/proxy-server
docker compose up -d
```

The server listens on `:8080`. Health check: `curl http://localhost:8080/health`.

## Quick start (Go toolchain)

Requires Go 1.22+.

```bash
cd PureTVforTwitch/proxy-server
go run .
# or build a binary:
go build -o proxy-server .
./proxy-server
```

Set `PORT` to change the listen port (default `8080`).

## Pointing PureTV at your instance

In any of the three apps: **Settings → Ad Block → Custom proxy URL**, enter:

```
http://your-host:8080/playlist
```

(or `https://...` if you've put it behind TLS — strongly recommended for
anything reachable from the public internet; see below).

## API contract

```
GET /playlist/{base64(playlistUrl)}
Header (optional): X-Donate-To: <url>   — logged for informational purposes only

200 OK
Content-Type: application/vnd.apple.mpegurl; charset=utf-8
X-PureTV-Ad-Segments-Removed: <int>
X-PureTV-Contained-Ads: <bool>

<cleaned m3u8 playlist text>
```

Both standard and URL-safe base64 (padded or unpadded) are accepted, since
different TTV LOL PRO client forks encode slightly differently. `GET /{base64}`
(without the `/playlist/` prefix) also works, as a convenience alias.

Any failure returns a `4xx`/`5xx` — PureTV's client automatically falls
through to its local manifest-rewrite strategy (Strategy 2) when that happens,
so a proxy outage degrades gracefully rather than breaking playback.

## Exposing this publicly

If you want to use this from outside your home network (e.g. so your phone can
use it on cellular data), put it behind a TLS-terminating reverse proxy. A
minimal Caddy example (`Caddyfile`):

```
proxy.yourdomain.com {
    reverse_proxy localhost:8080
}
```

Caddy handles Let's Encrypt certificate provisioning automatically. Traefik,
nginx + certbot, or your cloud provider's load balancer all work equally well
— this server speaks plain HTTP and expects TLS to be terminated in front of it.

## Operational notes

- **Stateless.** No database, no persistent storage, no user accounts —
  restarts and horizontal scaling are trivial.
- **Logs redact query strings.** Twitch playlist URLs carry short-lived signed
  tokens; the server strips them before writing upstream-fetch failures to
  logs.
- **Runtime image is `FROM scratch`.** No shell, no package manager — smallest
  practical attack surface for something you might expose to the internet.
- **The rewrite logic is a deliberate line-for-line port** of
  `core/.../adblock/ManifestRewriter.kt`'s `filter()` (see `rewriter.go`'s
  doc comment). If Twitch changes its ad-marker format, both must be updated
  together — `rewriter_test.go` mirrors the fixtures the Kotlin
  `ManifestRewriterTest` should use, specifically so a regression in one
  surfaces the need to check the other.

## Running the tests

```bash
go test ./...
```
