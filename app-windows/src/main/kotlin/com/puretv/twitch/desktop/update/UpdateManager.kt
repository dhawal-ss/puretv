package com.puretv.twitch.desktop.update

import com.puretv.twitch.desktop.AppBuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import javax.swing.SwingUtilities

/**
 * SECTION 09 — in-app auto-updater over GitHub Releases (notify + one-click).
 *
 * Koin singleton so the update banner (App) and Settings observe one [state].
 *
 * Networking uses the JDK [HttpClient], deliberately NOT the shared Twitch Ktor
 * client: that client injects a Twitch `Authorization` header, which `api.github.com`
 * would reject with 401. This client is unauthenticated and sends only the
 * `Accept`/`User-Agent` GitHub expects.
 *
 * Self-update is gated to PACKAGED builds (jpackage sets
 * `compose.application.resources.dir`); a `gradle run` dev session never nags or
 * tries to msiexec over itself.
 */
class UpdateManager {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val json = Json { ignoreUnknownKeys = true }
    private val http = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        // A GitHub release asset URL (browser_download_url) does NOT serve the
        // file: it 302-redirects to a short-lived signed release-assets.github
        // usercontent.com URL. The JDK HttpClient defaults to Redirect.NEVER,
        // so without this the download aborts on the 302 with
        // "Download failed (HTTP 302)". NORMAL follows the HTTPS->HTTPS redirect
        // while still refusing any HTTPS->HTTP downgrade.
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build()

    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    val currentVersion: String get() = AppBuildConfig.VERSION

    private val isPackaged: Boolean =
        System.getProperty("compose.application.resources.dir") != null

    /**
     * Checks GitHub for a newer release. [force] bypasses the packaged-build gate
     * (used by the manual "Check for updates" button so it works in dev too).
     * Any failure (offline, rate-limited, no asset) leaves state at [UpdateState.Idle].
     */
    fun checkForUpdates(force: Boolean = false) {
        if (!isPackaged && !force) return
        scope.launch {
            val info = runCatching { fetchLatest() }.getOrNull()
            _state.value =
                if (info != null && Semver.isNewer(AppBuildConfig.VERSION, info.version)) UpdateState.Available(info)
                else UpdateState.Idle
        }
    }

    private suspend fun fetchLatest(): UpdateInfo? = withContext(Dispatchers.IO) {
        val url = "https://api.github.com/repos/${AppBuildConfig.GITHUB_OWNER}/${AppBuildConfig.GITHUB_REPO}/releases/latest"
        val request = HttpRequest.newBuilder(URI.create(url))
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "PureTV-Updater")
            .timeout(Duration.ofSeconds(15))
            .GET()
            .build()
        val response = http.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() != 200) return@withContext null
        val release = json.decodeFromString(GithubRelease.serializer(), response.body())
        if (release.draft || release.prerelease) return@withContext null
        val asset = release.installerAsset() ?: return@withContext null
        UpdateInfo(
            version = release.tag_name,
            downloadUrl = asset.browser_download_url,
            sizeBytes = asset.size,
            notes = release.name.ifBlank { release.tag_name },
            htmlUrl = release.html_url,
        )
    }

    /**
     * Downloads [info]'s installer with progress, then hands off to a detached
     * script that waits for this process to exit, runs the installer (per-user →
     * no UAC), and relaunches. Quits the app so the in-place upgrade can replace
     * locked files.
     */
    fun downloadAndInstall(info: UpdateInfo, exitApplication: () -> Unit) {
        scope.launch {
            runCatching {
                _state.value = UpdateState.Downloading(0f)
                val installer = downloadInstaller(info)
                launchInstaller(installer)
            }.onSuccess {
                SwingUtilities.invokeLater { exitApplication() }
            }.onFailure { e ->
                _state.value = UpdateState.Error(e.message ?: "Update failed")
            }
        }
    }

    private suspend fun downloadInstaller(info: UpdateInfo): File = withContext(Dispatchers.IO) {
        val dir = File(System.getProperty("java.io.tmpdir"), "PureTV-update").apply { mkdirs() }
        val ext = if (info.downloadUrl.endsWith(".exe", ignoreCase = true)) "exe" else "msi"
        val target = File(dir, "PureTV-${info.version}.$ext")

        val request = HttpRequest.newBuilder(URI.create(info.downloadUrl))
            .header("User-Agent", "PureTV-Updater")
            .GET()
            .build()
        val response = http.send(request, HttpResponse.BodyHandlers.ofInputStream())
        if (response.statusCode() !in 200..299) error("Download failed (HTTP ${response.statusCode()})")

        response.body().use { input ->
            target.outputStream().use { output ->
                val buffer = ByteArray(64 * 1024)
                var total = 0L
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    output.write(buffer, 0, read)
                    total += read
                    if (info.sizeBytes > 0) {
                        _state.value = UpdateState.Downloading((total.toFloat() / info.sizeBytes).coerceIn(0f, 1f))
                    }
                }
            }
        }
        if (info.sizeBytes > 0 && target.length() != info.sizeBytes) error("Download incomplete — please retry")
        target
    }

    private fun launchInstaller(installer: File) {
        val exe = resolveAppExecutable()
        val script = File(installer.parentFile, "apply-update.bat")
        // Quoting lives INSIDE the .bat (paths can contain spaces); we invoke the
        // bat by path so cmd never mangles quotes. `timeout` lets this process
        // fully exit so msiexec doesn't hit a locked, still-running app.
        script.writeText(
            buildString {
                appendLine("@echo off")
                appendLine("timeout /t 2 /nobreak >nul")
                if (installer.extension.equals("msi", ignoreCase = true)) {
                    appendLine("msiexec /i \"${installer.absolutePath}\" /passive /norestart")
                } else {
                    // Inno Setup installer: silent in-place update, no prompts.
                    appendLine("\"${installer.absolutePath}\" /VERYSILENT /SUPPRESSMSGBOXES /NORESTART")
                }
                if (exe != null) {
                    // Relaunch the freshly-installed app. The installer upgrades
                    // in place (same {app} dir — see installer/puretv.iss), so this
                    // path is still valid after the silent install completes.
                    appendLine("start \"\" \"$exe\"")
                }
            },
        )
        // `start "" /min "<bat>"` spawns an independent process tree that survives
        // our exit; Java quotes the bat path for us.
        ProcessBuilder("cmd.exe", "/c", "start", "", "/min", script.absolutePath).start()
    }

    /**
     * Resolves the running app's launcher .exe so the post-update batch can
     * relaunch it. The previous build relied solely on
     * `ProcessHandle.current().command()` — a JDK `Optional` that comes back
     * *empty* on some JVMs/launchers. When empty, no relaunch line was written,
     * so the app silently shut down after every update and never reopened.
     *
     * Resolution order, most-reliable first:
     *   1. `jpackage.app-path` — set by jpackage launchers (JDK 18+) to the exe's
     *      absolute path. Stable across the in-place upgrade.
     *   2. Derived from the packaged layout: `compose.application.resources.dir`
     *      points at `<install>\app\resources`, so the launcher sits two levels
     *      up as `<install>\$LAUNCHER_EXE_NAME`. JDK-version independent.
     *   3. `ProcessHandle.current().command()` — last-ditch fallback for dev.
     */
    private fun resolveAppExecutable(): String? {
        System.getProperty("jpackage.app-path")?.takeIf { it.isNotBlank() }?.let { return it }
        System.getProperty("compose.application.resources.dir")?.let { res ->
            val install = File(res).parentFile?.parentFile
            val launcher = install?.let { File(it, LAUNCHER_EXE_NAME) }
            if (launcher != null && launcher.exists()) return launcher.absolutePath
        }
        return ProcessHandle.current().info().command().orElse(null)
    }

    private companion object {
        /** jpackage launcher name — derives from build.gradle.kts `packageName`. */
        const val LAUNCHER_EXE_NAME = "PureTV for Twitch.exe"
    }
}
