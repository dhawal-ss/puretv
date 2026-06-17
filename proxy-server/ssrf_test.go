package main

import (
	"encoding/base64"
	"testing"
)

// Audit F1: the proxy must only fetch Twitch playlist hosts, never internal /
// arbitrary hosts (open relay + SSRF to cloud-metadata / localhost / LAN).

func TestIsAllowedUpstreamHost(t *testing.T) {
	allowed := []string{
		"usher.ttvnw.net",
		"video-edge-abc.def.hls.ttvnw.net",
		"www.twitch.tv",
		"twitch.tv",
		"USHER.TTVNW.NET",
		"video-edge.ttvnw.net:443",
	}
	for _, h := range allowed {
		if !isAllowedUpstreamHost(h) {
			t.Errorf("expected allowed: %q", h)
		}
	}

	denied := []string{
		"169.254.169.254",            // cloud metadata
		"127.0.0.1",                  // loopback
		"localhost",                  // loopback name
		"10.0.0.5",                   // private
		"evil.com",                   // arbitrary
		"ttvnw.net.evil.com",         // suffix spoof
		"twitch.tv.evil.com",         // suffix spoof
		"eviltwitch.tv",              // not a .twitch.tv subdomain
		"",                           // empty
	}
	for _, h := range denied {
		if isAllowedUpstreamHost(h) {
			t.Errorf("expected denied: %q", h)
		}
	}
}

func TestDecodePlaylistURLRejectsNonTwitchHosts(t *testing.T) {
	enc := func(s string) string { return base64.StdEncoding.EncodeToString([]byte(s)) }

	if _, err := decodePlaylistURL(enc("http://169.254.169.254/latest/meta-data/")); err == nil {
		t.Error("metadata endpoint must be rejected")
	}
	if _, err := decodePlaylistURL(enc("http://127.0.0.1:8080/admin")); err == nil {
		t.Error("loopback must be rejected")
	}
	got, err := decodePlaylistURL(enc("https://usher.ttvnw.net/api/channel/hls/x.m3u8?token=abc"))
	if err != nil {
		t.Fatalf("valid Twitch URL must be accepted: %v", err)
	}
	if got == "" {
		t.Error("expected the decoded Twitch URL")
	}
}
