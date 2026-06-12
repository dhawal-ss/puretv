package com.puretv.twitch.desktop.update

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class GithubReleaseParseTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun parses_release_and_prefers_msi_asset() {
        val sample = """
            {
              "tag_name": "v1.3.0",
              "name": "PureTV 1.3.0",
              "draft": false,
              "prerelease": false,
              "html_url": "https://github.com/dhawal-ss/puretv/releases/tag/v1.3.0",
              "assets": [
                {"name": "PureTV-1.3.0.exe", "browser_download_url": "https://example/exe", "size": 100, "content_type": "application/x-msdownload"},
                {"name": "PureTV-1.3.0.msi", "browser_download_url": "https://example/msi", "size": 200, "content_type": "application/x-msi"}
              ]
            }
        """.trimIndent()

        val release = json.decodeFromString(GithubRelease.serializer(), sample)
        assertEquals("v1.3.0", release.tag_name)

        val asset = release.installerAsset()
        assertNotNull(asset)
        assertTrue(asset.name.endsWith(".msi"))
        assertEquals(200L, asset.size)
    }

    @Test
    fun ignores_unknown_api_fields() {
        val withExtra = """{"tag_name":"v1.0.0","unexpected_field":123,"assets":[]}"""
        val release = json.decodeFromString(GithubRelease.serializer(), withExtra)
        assertEquals("v1.0.0", release.tag_name)
        assertEquals(null, release.installerAsset())
    }
}
