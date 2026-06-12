package com.puretv.twitch.desktop.update

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SemverTest {

    @Test fun newer_patch() = assertTrue(Semver.isNewer("v1.2.0", "v1.2.1"))

    @Test fun newer_minor() = assertTrue(Semver.isNewer("1.2.9", "1.3.0"))

    @Test fun newer_major() = assertTrue(Semver.isNewer("v1.9.9", "v2.0.0"))

    @Test fun compares_numerically_not_lexically() = assertTrue(Semver.isNewer("1.9.0", "1.10.0"))

    @Test fun equal_is_not_newer() = assertFalse(Semver.isNewer("v1.2.3", "v1.2.3"))

    @Test fun older_is_not_newer() = assertFalse(Semver.isNewer("v1.2.3", "v1.2.2"))

    @Test fun prerelease_suffix_is_ignored() {
        assertFalse(Semver.isNewer("1.0.0", "1.0.0-beta"))
        assertTrue(Semver.isNewer("0.9.0", "1.0.0-rc1"))
    }

    @Test fun missing_components_pad_with_zero() {
        assertFalse(Semver.isNewer("1.2", "1.2.0"))
        assertTrue(Semver.isNewer("1.2", "1.2.1"))
    }

    @Test fun malformed_versions_are_not_newer() {
        assertFalse(Semver.isNewer("abc", "1.0.0"))
        assertFalse(Semver.isNewer("1.0.0", "xyz"))
    }
}
