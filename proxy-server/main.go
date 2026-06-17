// Command proxy-server is a self-hostable, TTV-LOL-PRO-compatible playlist
// proxy for PureTV for Twitch (SECTION 09 / Sprint 9).
//
// WHY THIS EXISTS: AdBlockEngine's Strategy 1 ("Proxy Router", see
// core/.../adblock/AdBlockEngine.kt SECTION 04.2) defaults to a public
// TTV-LOL-PRO-compatible endpoint (https://api.ttv.lol/playlist). Public
// endpoints get rate-limited or blocked at scale, and some users would rather
// not depend on a third party at all. Pointing `customProxyEndpoint` /
// AppSettings.customProxyUrl at an instance of *this* server gives users a
// proxy they fully own — same contract, zero trust required beyond their own
// infrastructure.
//
// CONTRACT (must match AdBlockEngine.fetchViaProxy exactly):
//
//	GET {endpoint}/{base64(playlistUrl)}
//	Header: X-Donate-To: https://ttv.lol/donate   (sent by the client; logged, not required)
//
//	-> 200 text/plain; charset=utf-8 — the (ideally ad-free) playlist body
//	-> 4xx/5xx                       — client falls through to Strategy 2 (local manifest rewrite)
//
// WHAT IT DOES: fetches the upstream Twitch playlist server-side (so the
// request "looks like" it's coming from this server's network/region rather
// than the user's — the same trick TTV LOL PRO's public infra relies on to
// dodge ad-stitching that's keyed off requester IP/ASN), then runs it through
// the exact same FilterPlaylist rewrite the in-app fallback uses, so even if
// the region trick stops working the response is still ad-stripped.
package main

import (
	"context"
	"encoding/base64"
	"errors"
	"fmt"
	"io"
	"log"
	"net"
	"net/http"
	"net/url"
	"os"
	"strings"
	"syscall"
	"time"
)

const (
	defaultPort           = "8080"
	upstreamTimeout       = 8 * time.Second
	maxPlaylistBodyBytes  = 2 << 20 // 2 MiB — playlists are ~2KB; this is a generous abuse guard
	donateHeaderName      = "X-Donate-To"
	donateHeaderLogPrefix = "donor-credit: "
)

func main() {
	port := os.Getenv("PORT")
	if port == "" {
		port = defaultPort
	}

	// Default to loopback: the primary use is a localhost sidecar for the desktop
	// app (http://127.0.0.1:8080/playlist). Public exposure (the Docker image)
	// must opt in via BIND_ADDR=0.0.0.0 so a multi-homed/LAN machine isn't an
	// open Twitch relay by default.
	bindAddr := os.Getenv("BIND_ADDR")
	if bindAddr == "" {
		bindAddr = "127.0.0.1"
	}

	upstreamUserAgent := os.Getenv("UPSTREAM_USER_AGENT")
	if upstreamUserAgent == "" {
		// A realistic desktop-browser UA reduces the odds of upstream
		// fingerprinting treating this server differently than a normal
		// viewer's player would be treated.
		upstreamUserAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36"
	}

	// SSRF DEFENCE (audit F1): this server fetches a URL on behalf of anyone who
	// can reach it. Without restriction it is an open relay AND can be aimed at
	// the host's own internal network — cloud metadata (169.254.169.254),
	// 127.0.0.1, 10.x/192.168.x services. Two layers:
	//   1. hostname allowlist — only Twitch playlist hosts (the proxy's sole job),
	//      checked on the initial URL and on every redirect hop.
	//   2. a dial-time Control hook that refuses to connect to any loopback /
	//      private / link-local IP — defeats DNS rebinding (a twitch-looking host
	//      that resolves to an internal address).
	transport := http.DefaultTransport.(*http.Transport).Clone()
	transport.DialContext = (&net.Dialer{
		Timeout:   upstreamTimeout,
		KeepAlive: 30 * time.Second,
		Control:   blockNonPublicAddress,
	}).DialContext

	srv := &server{
		client: &http.Client{
			Timeout:   upstreamTimeout,
			Transport: transport,
			// Twitch playlist URLs sometimes redirect (CDN edge selection);
			// follow redirects but cap them, AND re-validate the host of each hop
			// so a redirect can't bounce us off-Twitch / to an internal host.
			CheckRedirect: func(req *http.Request, via []*http.Request) error {
				if len(via) >= 5 {
					return errors.New("stopped after 5 redirects")
				}
				if !isAllowedUpstreamHost(req.URL.Hostname()) {
					return fmt.Errorf("refusing redirect to untrusted host %q", req.URL.Hostname())
				}
				return nil
			},
		},
		userAgent: upstreamUserAgent,
	}

	mux := http.NewServeMux()
	mux.HandleFunc("/health", srv.handleHealth)
	mux.HandleFunc("/playlist/", srv.handlePlaylist)
	// Bare-root convenience alias — some users configure
	// `customProxyEndpoint` without the `/playlist` suffix by habit (copying
	// the bare host from TTV LOL PRO docs). Both shapes resolve identically.
	mux.HandleFunc("/", srv.handleRoot)

	httpServer := &http.Server{
		Addr:              bindAddr + ":" + port,
		Handler:           logRequests(mux),
		ReadHeaderTimeout: 5 * time.Second,
		// Bound the inbound side too: without these a slow-reading client holds a
		// handler goroutine (and its already-fetched upstream resources) open
		// indefinitely — slowloris on the response side. The 2 MiB body cap makes
		// a generous WriteTimeout safe.
		WriteTimeout: 15 * time.Second,
		IdleTimeout:  60 * time.Second,
	}

	log.Printf("PureTV ad-block proxy listening on %s:%s (contract: GET /playlist/{base64(playlistUrl)})", bindAddr, port)
	if err := httpServer.ListenAndServe(); err != nil && !errors.Is(err, http.ErrServerClosed) {
		log.Fatalf("server error: %v", err)
	}
}

type server struct {
	client    *http.Client
	userAgent string
}

// ---- Routes -----------------------------------------------------------------

func (s *server) handleHealth(w http.ResponseWriter, r *http.Request) {
	w.Header().Set("Content-Type", "application/json")
	fmt.Fprintf(w, `{"status":"ok","service":"puretv-twitch-proxy","contract":"ttv-lol-pro-compatible"}`)
}

// handleRoot accepts `GET /{base64}` as an alias for `GET /playlist/{base64}`,
// and serves a tiny human-readable landing page for everything else (so
// visiting the bare host in a browser doesn't just 404).
func (s *server) handleRoot(w http.ResponseWriter, r *http.Request) {
	encoded := strings.TrimPrefix(r.URL.Path, "/")
	if encoded == "" {
		s.handleLanding(w, r)
		return
	}
	s.proxyPlaylist(w, r, encoded)
}

func (s *server) handlePlaylist(w http.ResponseWriter, r *http.Request) {
	encoded := strings.TrimPrefix(r.URL.Path, "/playlist/")
	if encoded == "" {
		http.Error(w, "missing base64-encoded playlist URL — expected GET /playlist/{base64(playlistUrl)}", http.StatusBadRequest)
		return
	}
	s.proxyPlaylist(w, r, encoded)
}

func (s *server) handleLanding(w http.ResponseWriter, r *http.Request) {
	w.Header().Set("Content-Type", "text/html; charset=utf-8")
	fmt.Fprint(w, `<!DOCTYPE html><html><head><meta charset="utf-8"><title>PureTV ad-block proxy</title></head>
<body style="font:14px/1.5 -apple-system,Segoe UI,Roboto,sans-serif;max-width:640px;margin:48px auto;padding:0 16px;color:#222">
<h1>PureTV for Twitch — ad-block proxy</h1>
<p>This is a self-hosted, TTV-LOL-PRO-compatible playlist proxy. Point your
PureTV app's <code>Custom proxy URL</code> setting at this server's base URL
and it will be used in place of the public default.</p>
<p>Health check: <a href="/health">/health</a></p>
<p>Contract: <code>GET /playlist/{base64(playlistUrl)}</code> &rarr; cleaned m3u8 text.</p>
</body></html>`)
}

// ---- Core proxy logic --------------------------------------------------------

func (s *server) proxyPlaylist(w http.ResponseWriter, r *http.Request, encoded string) {
	if donor := r.Header.Get(donateHeaderName); donor != "" {
		log.Printf("%s%s", donateHeaderLogPrefix, donor)
	}

	playlistURL, err := decodePlaylistURL(encoded)
	if err != nil {
		http.Error(w, fmt.Sprintf("could not decode playlist URL: %v", err), http.StatusBadRequest)
		return
	}

	ctx, cancel := context.WithTimeout(r.Context(), upstreamTimeout)
	defer cancel()

	raw, status, err := s.fetchUpstream(ctx, playlistURL)
	if err != nil {
		log.Printf("upstream fetch failed for %s: %v", redactQuery(playlistURL), err)
		http.Error(w, "upstream fetch failed — client should fall back to local manifest rewrite", http.StatusBadGateway)
		return
	}
	if status != http.StatusOK {
		log.Printf("upstream returned %d for %s", status, redactQuery(playlistURL))
		http.Error(w, fmt.Sprintf("upstream returned %d", status), http.StatusBadGateway)
		return
	}

	filtered := FilterPlaylist(raw)

	w.Header().Set("Content-Type", "application/vnd.apple.mpegurl; charset=utf-8")
	w.Header().Set("Cache-Control", "no-store")
	// Diagnostic headers — not part of the TTV LOL PRO contract, harmless to
	// clients that ignore them, useful for `puretv-proxy-doctor`-style debugging.
	w.Header().Set("X-PureTV-Ad-Segments-Removed", fmt.Sprintf("%d", filtered.AdSegmentsRemoved))
	w.Header().Set("X-PureTV-Contained-Ads", fmt.Sprintf("%t", filtered.ContainedAds))
	w.WriteHeader(http.StatusOK)
	_, _ = io.WriteString(w, filtered.Content)
}

// decodePlaylistURL accepts both standard and URL-safe base64, with or
// without padding — different TTV-LOL-PRO client forks encode slightly
// differently, and PureTV's own `encodeBase64()` (Ktor) emits standard
// padded base64, so we try that first.
func decodePlaylistURL(encoded string) (string, error) {
	for _, enc := range []*base64.Encoding{base64.StdEncoding, base64.URLEncoding, base64.RawStdEncoding, base64.RawURLEncoding} {
		if decoded, err := enc.DecodeString(encoded); err == nil {
			candidate := string(decoded)
			u, perr := url.ParseRequestURI(candidate)
			if perr != nil || (u.Scheme != "http" && u.Scheme != "https") {
				continue
			}
			// SSRF allowlist (audit F1): only proxy Twitch playlist hosts.
			if !isAllowedUpstreamHost(u.Hostname()) {
				return "", fmt.Errorf("refusing to proxy non-Twitch host %q", u.Hostname())
			}
			return candidate, nil
		}
	}
	return "", errors.New("not a valid base64-encoded http(s) Twitch playlist URL")
}

// isAllowedUpstreamHost restricts the proxy to Twitch's playlist/CDN domains —
// usher (`usher.ttvnw.net`), the HLS edges (`*.ttvnw.net`), and `*.twitch.tv`.
// This is the proxy's only job, so the allowlist costs nothing legitimate while
// closing the open-relay / internal-network SSRF surface.
func isAllowedUpstreamHost(host string) bool {
	host = strings.ToLower(strings.TrimSuffix(host, "."))
	if h, _, err := net.SplitHostPort(host); err == nil {
		host = h
	}
	return host == "twitch.tv" || strings.HasSuffix(host, ".twitch.tv") ||
		host == "ttvnw.net" || strings.HasSuffix(host, ".ttvnw.net")
}

// blockNonPublicAddress is a net.Dialer Control hook that refuses to connect to
// loopback / private / link-local / unspecified IPs (audit F1, DNS-rebinding
// defence): even an allowlisted hostname must not resolve to an internal target.
func blockNonPublicAddress(network, address string, _ syscall.RawConn) error {
	host, _, err := net.SplitHostPort(address)
	if err != nil {
		host = address
	}
	ip := net.ParseIP(host)
	if ip == nil {
		return nil // not an IP literal; hostname allowlist already vetted it
	}
	if ip.IsLoopback() || ip.IsPrivate() || ip.IsLinkLocalUnicast() || ip.IsLinkLocalMulticast() || ip.IsUnspecified() {
		return fmt.Errorf("refusing to connect to non-public address %s", address)
	}
	return nil
}

func (s *server) fetchUpstream(ctx context.Context, playlistURL string) (body string, status int, err error) {
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, playlistURL, nil)
	if err != nil {
		return "", 0, err
	}
	req.Header.Set("User-Agent", s.userAgent)
	req.Header.Set("Accept", "application/vnd.apple.mpegurl,application/x-mpegURL,*/*")

	resp, err := s.client.Do(req)
	if err != nil {
		return "", 0, err
	}
	defer resp.Body.Close()

	limited := io.LimitReader(resp.Body, maxPlaylistBodyBytes)
	raw, err := io.ReadAll(limited)
	if err != nil {
		return "", 0, err
	}
	return string(raw), resp.StatusCode, nil
}

// redactQuery trims query strings from logged URLs — Twitch playlist URLs
// carry short-lived signed tokens that shouldn't end up in server logs.
func redactQuery(rawURL string) string {
	u, err := url.Parse(rawURL)
	if err != nil {
		return "(unparseable URL)"
	}
	u.RawQuery = ""
	return u.String() + "?<redacted>"
}

// ---- Middleware --------------------------------------------------------------

func logRequests(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		start := time.Now()
		rec := &statusRecorder{ResponseWriter: w, status: http.StatusOK}
		next.ServeHTTP(rec, r)
		log.Printf("%s %s -> %d (%s)", r.Method, r.URL.Path, rec.status, time.Since(start).Round(time.Millisecond))
	})
}

type statusRecorder struct {
	http.ResponseWriter
	status int
}

func (rec *statusRecorder) WriteHeader(status int) {
	rec.status = status
	rec.ResponseWriter.WriteHeader(status)
}
