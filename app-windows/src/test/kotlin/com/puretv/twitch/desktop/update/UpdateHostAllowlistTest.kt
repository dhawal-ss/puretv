package com.puretv.twitch.desktop.update

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Audit F3: update artifacts may only be fetched from GitHub-controlled hosts. */
class UpdateHostAllowlistTest {

    @Test fun acceptsGithubReleaseAndAssetHosts() {
        listOf(
            "https://github.com/owner/repo/releases/download/v1.2.3/PureTV.exe",
            "https://api.github.com/repos/owner/repo/releases/latest",
            "https://objects.githubusercontent.com/abc/PureTV.exe",
            "https://release-assets.githubusercontent.com/x/PureTV.exe.sig",
        ).forEach { assertTrue(isTrustedGithubAssetHost(it), "should trust: $it") }
    }

    @Test fun rejectsNonGithubAndSpoofedHosts() {
        listOf(
            "https://evil.com/PureTV.exe",
            "https://github.com.evil.com/PureTV.exe",     // suffix-spoof
            "https://githubusercontent.com.evil.com/x",   // suffix-spoof
            "http://127.0.0.1/PureTV.exe",
            "not a url",
            "",
        ).forEach { assertFalse(isTrustedGithubAssetHost(it), "should reject: $it") }
    }

    @Test fun rejectsPlaintextHttpEvenOnGithubHost() {
        // Host is github-controlled but the scheme is http -> cleartext fetch.
        listOf(
            "http://github.com/owner/repo/releases/download/v1/PureTV.exe",
            "http://objects.githubusercontent.com/abc/PureTV.exe",
        ).forEach { assertFalse(isTrustedGithubAssetHost(it), "should reject cleartext: $it") }
    }
}
