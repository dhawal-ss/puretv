package com.puretv.twitch.desktop.update

import kotlinx.serialization.Serializable

/** A newer release the user can install. */
data class UpdateInfo(
    val version: String,
    val downloadUrl: String,
    val sizeBytes: Long,
    val notes: String,
    val htmlUrl: String,
    /** URL of the detached Ed25519 signature (`<installer>.sig`), if published. */
    val signatureUrl: String? = null,
)

/** Lifecycle of an update check/apply, observed by the banner and Settings. */
sealed interface UpdateState {
    data object Idle : UpdateState
    data class Available(val info: UpdateInfo) : UpdateState
    data class Downloading(val progress: Float) : UpdateState
    data class Error(val message: String, val releaseUrl: String? = null) : UpdateState
}

// ── GitHub Releases API DTOs (subset) ────────────────────────────────────────

@Serializable
data class GithubRelease(
    val tag_name: String = "",
    val name: String = "",
    val body: String = "",
    val html_url: String = "",
    val draft: Boolean = false,
    val prerelease: Boolean = false,
    val assets: List<GithubAsset> = emptyList(),
)

@Serializable
data class GithubAsset(
    val name: String = "",
    val browser_download_url: String = "",
    val size: Long = 0,
    val content_type: String = "",
)

/**
 * The Windows installer asset to download. Prefers the MSI (it carries the
 * `upgradeUuid` for a clean in-place upgrade); falls back to the EXE installer.
 */
fun GithubRelease.installerAsset(): GithubAsset? =
    assets.firstOrNull { it.name.endsWith(".msi", ignoreCase = true) }
        ?: assets.firstOrNull { it.name.endsWith(".exe", ignoreCase = true) }

/**
 * The detached Ed25519 signature for [installer], published as `<installer>.sig`
 * (see release.yml). Returns null when the release carries no matching signature
 * — the updater treats that as "refuse to install" (fail-closed).
 */
fun GithubRelease.signatureAsset(installer: GithubAsset): GithubAsset? =
    assets.firstOrNull { it.name.equals("${installer.name}.sig", ignoreCase = true) }

/**
 * From a `/releases` list (any order), the release the desktop updater should
 * offer: the highest-version PUBLISHED (non-draft, non-prerelease) release that
 * actually carries a Windows installer asset ([installerAsset]).
 *
 * Deliberately NOT "whatever `/releases/latest` returns". Windows, Android, and TV
 * all publish from one repo and share GitHub's single "Latest" pointer, so a newer
 * APK-only TV/Android release makes `/releases/latest` resolve to a release with no
 * `.msi`/`.exe` — which silently killed desktop auto-update (installerAsset() → null
 * → no update ever offered). Filtering for an installer asset makes the updater
 * immune to that, no matter how the other channels' releases are flagged.
 *
 * Picks by semantic version (not list order) so it's correct even if the API ever
 * returns releases out of created-at order; the caller's [Semver.isNewer] gate still
 * decides whether the result is actually newer than the running build.
 */
fun latestInstallerRelease(releases: List<GithubRelease>): GithubRelease? =
    releases
        .filter { !it.draft && !it.prerelease && it.installerAsset() != null }
        .maxWithOrNull { a, b ->
            // isNewer(current, candidate) is true when candidate > current, so
            // isNewer(b, a) means a is the newer of the two → a sorts greater.
            when {
                Semver.isNewer(b.tag_name, a.tag_name) -> 1
                Semver.isNewer(a.tag_name, b.tag_name) -> -1
                else -> 0
            }
        }
