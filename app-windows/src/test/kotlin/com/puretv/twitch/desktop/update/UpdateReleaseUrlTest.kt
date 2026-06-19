package com.puretv.twitch.desktop.update

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * resolveReleaseUrl decides what the "Open download page" recovery button opens:
 * the release's own page when known, otherwise the repo's /releases/latest.
 */
class UpdateReleaseUrlTest {

    @Test fun usesTheReleasePageWhenPresent() {
        val url = "https://github.com/dhawal-ss/puretv/releases/tag/v1.9.2"
        assertEquals(url, resolveReleaseUrl(url))
    }

    @Test fun fallsBackToReleasesLatestWhenBlank() {
        assertEquals(
            "https://github.com/dhawal-ss/puretv/releases/latest",
            resolveReleaseUrl(""),
        )
    }

    @Test fun fallsBackWhenOnlyWhitespace() {
        assertEquals(
            "https://github.com/dhawal-ss/puretv/releases/latest",
            resolveReleaseUrl("   "),
        )
    }
}
