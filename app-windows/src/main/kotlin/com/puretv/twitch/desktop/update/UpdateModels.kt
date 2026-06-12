package com.puretv.twitch.desktop.update

import kotlinx.serialization.Serializable

/** A newer release the user can install. */
data class UpdateInfo(
    val version: String,
    val downloadUrl: String,
    val sizeBytes: Long,
    val notes: String,
    val htmlUrl: String,
)

/** Lifecycle of an update check/apply, observed by the banner and Settings. */
sealed interface UpdateState {
    data object Idle : UpdateState
    data class Available(val info: UpdateInfo) : UpdateState
    data class Downloading(val progress: Float) : UpdateState
    data class Error(val message: String) : UpdateState
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
