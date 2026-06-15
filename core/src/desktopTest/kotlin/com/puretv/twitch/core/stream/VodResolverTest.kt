package com.puretv.twitch.core.stream

import com.puretv.twitch.core.model.StreamQuality
import com.puretv.twitch.core.model.StreamToken
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class VodResolverTest {

    private fun realMaster(): MasterPlaylistResult {
        val raw = this::class.java.classLoader
            .getResourceAsStream("vod/sample-vod-master.m3u8")!!
            .bufferedReader().readText()
        return MasterPlaylistResult(
            masterUrl = "https://usher.ttvnw.net/vod/x.m3u8",
            rawContent = raw,
            variants = HlsMasterParser.parseVariants(raw),
        )
    }

    @Test fun buildsVodUsherUrl() {
        val url = VodResolver.buildVodMasterUrl("123456789", StreamToken(value = "tok en", signature = "sig123"))
        assertTrue(url.startsWith("https://usher.ttvnw.net/vod/123456789.m3u8"), url)
        assertTrue(url.contains("sig=sig123"), url)
        assertTrue(url.contains("token=tok%20en"), "token must be URL-encoded: $url")
        assertTrue(url.contains("allow_source=true"), url)
    }

    @Test fun playableUrlForSourcePicksChunkedVariant() {
        val url = VodResolver.playableUrlFor(realMaster(), StreamQuality.SOURCE)
        assertTrue(url.endsWith("chunked/index-dvr.m3u8"), url)
    }

    @Test fun playableUrlForAutoReturnsMasterUrl() {
        assertEquals(
            "https://usher.ttvnw.net/vod/x.m3u8",
            VodResolver.playableUrlFor(realMaster(), StreamQuality.AUTO),
        )
    }
}
