package com.puretv.twitch.desktop.update

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * latestInstallerRelease is the guard against a sibling channel (Android / TV)
 * hijacking the desktop updater: Windows, Android, and TV publish from one repo and
 * share GitHub's single "Latest" pointer, so the updater must pick the newest
 * release that actually carries a Windows installer — not just whatever is newest.
 */
class LatestInstallerReleaseTest {

    private fun release(
        tag: String,
        prerelease: Boolean = false,
        draft: Boolean = false,
        assets: List<String> = emptyList(),
    ) = GithubRelease(
        tag_name = tag,
        prerelease = prerelease,
        draft = draft,
        assets = assets.map { GithubAsset(name = it, browser_download_url = "https://e/$it", size = 1) },
    )

    @Test
    fun skips_apk_only_release_and_picks_the_windows_installer() {
        // The exact production shape on 2026-07: a newer APK-only TV release plus an
        // Android APK release, with the real Windows installer released just before.
        val releases = listOf(
            release("tv-v1.1.0", assets = listOf("PureTV-FireTV-AndroidTV.apk")),
            release("v1.10.1", assets = listOf("PureTV-Setup-1.10.1.exe", "PureTV-Setup-1.10.1.exe.sig")),
            release("android-v1.0.0", assets = listOf("PureTV-for-Twitch-1.0.0.apk")),
        )
        val picked = latestInstallerRelease(releases)
        assertNotNull(picked)
        assertEquals("v1.10.1", picked.tag_name)
    }

    @Test
    fun picks_highest_version_regardless_of_list_order() {
        val releases = listOf(
            release("v1.9.0", assets = listOf("PureTV-Setup-1.9.0.exe")),
            release("v1.10.1", assets = listOf("PureTV-Setup-1.10.1.exe")),
            release("v1.8.0", assets = listOf("PureTV-Setup-1.8.0.exe")),
        )
        assertEquals("v1.10.1", latestInstallerRelease(releases)?.tag_name)
    }

    @Test
    fun ignores_prerelease_and_draft_installer_releases() {
        val releases = listOf(
            release("v2.0.0-rc1", prerelease = true, assets = listOf("PureTV-Setup-2.0.0-rc1.exe")),
            release("v1.10.1", assets = listOf("PureTV-Setup-1.10.1.exe")),
            release("v2.1.0", draft = true, assets = listOf("PureTV-Setup-2.1.0.exe")),
        )
        assertEquals("v1.10.1", latestInstallerRelease(releases)?.tag_name)
    }

    @Test
    fun returns_null_when_no_release_carries_an_installer() {
        val releases = listOf(
            release("tv-v1.1.0", assets = listOf("PureTV-FireTV-AndroidTV.apk")),
            release("android-v1.0.0", assets = listOf("PureTV-for-Twitch-1.0.0.apk")),
        )
        assertEquals(null, latestInstallerRelease(releases))
    }

    @Test
    fun empty_list_returns_null() {
        assertEquals(null, latestInstallerRelease(emptyList()))
    }
}
