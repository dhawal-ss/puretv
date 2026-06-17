package com.puretv.twitch.desktop.player

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The /variant route must only proxy the Twitch HLS CDN, or it is an open
 * localhost SSRF proxy. See [LocalStreamProxy.isAllowedVariantHost].
 */
class LocalStreamProxyHostTest {

    @Test fun allowsTwitchCdnHosts() {
        assertTrue(LocalStreamProxy.isAllowedVariantHost("https://video-edge-abc.abs.hls.ttvnw.net/v1/segment/x.ts"))
        assertTrue(LocalStreamProxy.isAllowedVariantHost("https://usher.ttvnw.net/api/channel/hls/x.m3u8"))
        assertTrue(LocalStreamProxy.isAllowedVariantHost("https://ttvnw.net/x"))
    }

    @Test fun blocksSsrfTargets() {
        assertFalse(LocalStreamProxy.isAllowedVariantHost("http://169.254.169.254/latest/meta-data/"))
        assertFalse(LocalStreamProxy.isAllowedVariantHost("http://127.0.0.1:7979/diag"))
        assertFalse(LocalStreamProxy.isAllowedVariantHost("http://localhost/secret"))
    }

    @Test fun blocksSuffixSpoof() {
        // ttvnw.net.evil.com must NOT be treated as a Twitch host.
        assertFalse(LocalStreamProxy.isAllowedVariantHost("https://ttvnw.net.evil.com/x"))
        assertFalse(LocalStreamProxy.isAllowedVariantHost("https://eviltvnw.net/x"))
    }

    @Test fun rejectsGarbage() {
        assertFalse(LocalStreamProxy.isAllowedVariantHost("not a url"))
        assertFalse(LocalStreamProxy.isAllowedVariantHost(""))
    }
}
