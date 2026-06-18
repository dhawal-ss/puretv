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
        // Force HTTP/1.1. The JDK client defaults to HTTP/2, and GitHub's API +
        // asset CDN recycle h2 connections aggressively (GOAWAY). The client will
        // reuse a pooled connection the server has already half-closed and throw
        // IOException ("GOAWAY received" / "EOF reached") on the NEXT request —
        // the root cause of "the first download attempt errors, a retry works".
        // HTTP/1.1 avoids the stale-h2-connection failure mode entirely; the
        // retry below handles any remaining transient blips.
        .version(HttpClient.Version.HTTP_1_1)
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
        val response = withUpdateRetry {
            val r = http.send(request, HttpResponse.BodyHandlers.ofString())
            // 5xx is a transient GitHub hiccup worth retrying; 4xx (e.g. rate
            // limit) is not and falls through to the null-handling below.
            if (r.statusCode() in 500..599) throw TransientUpdateException("GitHub API ${r.statusCode()}")
            r
        }
        if (response.statusCode() != 200) return@withContext null
        val release = json.decodeFromString(GithubRelease.serializer(), response.body())
        if (release.draft || release.prerelease) return@withContext null
        val asset = release.installerAsset() ?: return@withContext null
        UpdateInfo(
            version = release.tag_name,
            downloadUrl = asset.browser_download_url,
            sizeBytes = asset.size,
            // Friendly changelog (from CHANGELOG.md via the release body); the
            // banner shows its first line. Falls back to the release name/tag.
            notes = release.body.trim().ifBlank { release.name.ifBlank { release.tag_name } },
            htmlUrl = release.html_url,
            signatureUrl = release.signatureAsset(asset)?.browser_download_url,
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
                // Anti-rollback (audit F2): refuse to install a version that is not
                // strictly newer than the running one — even a validly-signed older
                // build. Ed25519 signing prevents forgery but NOT replay of a
                // genuinely-signed, known-vulnerable old artifact. checkForUpdates
                // only gates the banner; this gates the actual install.
                if (!Semver.isNewer(AppBuildConfig.VERSION, info.version)) {
                    error("Refusing to install ${info.version}: not newer than the current ${AppBuildConfig.VERSION}.")
                }
                updateLog("=== apply ${AppBuildConfig.VERSION} -> ${info.version}  url=${info.downloadUrl} ===")
                _state.value = UpdateState.Downloading(0f)
                val installer = downloadAndVerify(info)
                launchInstaller(installer)
                updateLog("installer launched; exiting app to apply")
            }.onSuccess {
                SwingUtilities.invokeLater { exitApplication() }
            }.onFailure { e ->
                updateLog("apply FAILED: ${e.message}")
                _state.value = UpdateState.Error(e.message ?: "Update failed")
            }
        }
    }

    private suspend fun downloadInstaller(info: UpdateInfo): File = withContext(Dispatchers.IO) {
        // Reject artifacts hosted anywhere but GitHub before fetching a byte
        // (audit F3). Signature verification is the decisive control, but this
        // removes the cheapest backstop being bypassed by a substituted URL.
        requireTrustedAssetHost(info.downloadUrl)
        // Audit P0-7 (TOCTOU): use a fresh, RANDOM per-run directory instead of
        // the predictable %TEMP%\PureTV-update. The old fixed path let a local
        // process pre-plant or swap the installer/scripts between verify and
        // execute. A random name (plus owner-default ACL) makes the path
        // unguessable so it can't be staged ahead of time.
        val dir = java.nio.file.Files.createTempDirectory("PureTV-update-").toFile()
        runCatching { hardenDirOwnerOnly(dir) }
        val ext = if (info.downloadUrl.endsWith(".exe", ignoreCase = true)) "exe" else "msi"
        val target = File(dir, "PureTV-${info.version}.$ext")

        // Retry the whole download: a stale connection, reset, or truncation
        // throws a transient error and we re-attempt from scratch (the file is
        // re-opened/truncated each time) instead of erroring out to the user.
        withUpdateRetry {
            val request = HttpRequest.newBuilder(URI.create(info.downloadUrl))
                .header("User-Agent", "PureTV-Updater")
                // Bounds connect + time-to-first-byte so a hung start fails into
                // a retry rather than hanging the download forever.
                .timeout(Duration.ofMinutes(5))
                .GET()
                .build()
            val response = http.send(request, HttpResponse.BodyHandlers.ofInputStream())
            if (response.statusCode() !in 200..299) {
                throw TransientUpdateException("Download failed (HTTP ${response.statusCode()})")
            }

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
            if (info.sizeBytes > 0 && target.length() != info.sizeBytes) {
                throw TransientUpdateException("Download incomplete (${target.length()}/${info.sizeBytes} bytes)")
            }
            target
        }
    }

    /**
     * Downloads the installer and verifies it against the embedded publisher key
     * before it is allowed to execute. Fail-closed: returns only a verified file.
     *
     * Crucially, it distinguishes a signature MISMATCH (the crypto ran and the
     * bytes didn't validate — almost always a corrupted/truncated download) from
     * an Errored verify (the runtime can't do Ed25519 / malformed inputs). A
     * mismatch is RETRIED with a fresh download once (self-healing the most common
     * real cause); an Errored is NOT retried (re-downloading can't fix a runtime
     * problem) and surfaces a distinct, reportable message. Every step is written
     * to %APPDATA%/PureTwitch/update.log so a failure is diagnosable, not a mystery.
     */
    private suspend fun downloadAndVerify(info: UpdateInfo): File = withContext(Dispatchers.IO) {
        // Preconditions a re-download can't fix — fail fast with a clear message.
        if (!UpdateSigning.isConfigured) {
            updateLog("no signing key embedded in this build")
            error("This build can't verify updates (no signing key configured). Download the latest version manually from ${info.htmlUrl}.")
        }
        val signatureUrl = info.signatureUrl ?: run {
            updateLog("release ${info.version} has no .sig asset")
            error("This release isn't signed — refusing to install an unverified update. Download manually from ${info.htmlUrl}.")
        }

        var lastReason = "signature did not match"
        repeat(2) { attempt ->
            val installer = downloadInstaller(info)
            val signatureBase64 = downloadText(signatureUrl)
            // Stream the installer into the digest (for the log) and the verifier; never
            // materialize the whole ~170MB file as one heap ByteArray. Doing so, on top of
            // Ed25519's own internal buffering, OOM'd the player's 1GB heap mid-update.
            updateLog("attempt ${attempt + 1}: installer=${installer.length()}B expected=${info.sizeBytes}B sha256=${sha256HexOfFile(installer)} sigChars=${signatureBase64.trim().length}")
            when (val result = UpdateSignatureVerifier.verify(installer, signatureBase64, UpdateSigning.PUBLIC_KEY_BASE64)) {
                UpdateSignatureVerifier.VerifyResult.Valid -> {
                    updateLog("attempt ${attempt + 1}: signature VALID")
                    return@withContext installer
                }
                UpdateSignatureVerifier.VerifyResult.Invalid -> {
                    lastReason = "signature did not match (the download looks corrupted or incomplete)"
                    updateLog("attempt ${attempt + 1}: signature INVALID — ${if (attempt == 0) "re-downloading" else "giving up"}")
                    installer.delete()
                    // loop: one fresh download + re-verify
                }
                is UpdateSignatureVerifier.VerifyResult.Errored -> {
                    updateLog("attempt ${attempt + 1}: verification ERRORED — ${result.reason}")
                    installer.delete()
                    error("Update verification couldn't run on this PC (${result.reason}). This is an app/runtime problem, not a bad download — please report it with %APPDATA%\\PureTwitch\\update.log. Meanwhile, install manually from ${info.htmlUrl}.")
                }
            }
        }
        error("Update signature check FAILED even after re-downloading — $lastReason. Get the latest version manually from ${info.htmlUrl} (details in %APPDATA%\\PureTwitch\\update.log).")
    }

    private fun sha256HexOfFile(file: File): String {
        val md = java.security.MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                md.update(buf, 0, n)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it.toInt() and 0xFF) }
    }

    /** Append a timestamped line to %APPDATA%/PureTwitch/update.log. Never throws —
     *  diagnostics must not break the updater. This is the cure for "it just fails":
     *  the next failure records exactly which step + why. */
    private fun updateLog(message: String) {
        runCatching {
            val dir = File(System.getenv("APPDATA") ?: System.getProperty("user.home"), "PureTwitch")
            dir.mkdirs()
            File(dir, "update.log").appendText("${java.time.Instant.now()} [v${AppBuildConfig.VERSION}] $message\n")
        }
    }

    private suspend fun downloadText(url: String): String = withUpdateRetry {
        requireTrustedAssetHost(url)
        val request = HttpRequest.newBuilder(URI.create(url))
            .header("User-Agent", "PureTV-Updater")
            .timeout(Duration.ofSeconds(30))
            .GET()
            .build()
        val response = http.send(request, HttpResponse.BodyHandlers.ofString())
        // Transient so a stale-connection blip on the signature fetch (right
        // after the big download) retries instead of failing the whole update.
        if (response.statusCode() !in 200..299) {
            throw TransientUpdateException("Signature download failed (HTTP ${response.statusCode()})")
        }
        response.body()
    }

    private fun launchInstaller(installer: File) {
        val exe = resolveAppExecutable()
        val batFile = File(installer.parentFile, "apply-update.bat")
        val vbsFile = File(installer.parentFile, "apply-update.vbs")
        val scripts = buildUpdateScripts(
            installerPath = installer.absolutePath,
            isMsi = installer.extension.equals("msi", ignoreCase = true),
            appExePath = exe,
            batPath = batFile.absolutePath,
        )
        batFile.writeText(scripts.bat)
        vbsFile.writeText(scripts.vbs)
        // wscript.exe is the WINDOWLESS script host; the .vbs runs the .bat with a
        // hidden window, so applying an update never flashes — or leaves open — a
        // console window. ProcessBuilder uses a fixed arg array (no shell).
        ProcessBuilder("wscript.exe", vbsFile.absolutePath).start()
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

    private fun requireTrustedAssetHost(url: String) {
        require(isTrustedGithubAssetHost(url)) {
            "Refusing to fetch update artifact from an untrusted host: $url"
        }
    }

    /**
     * Best-effort owner-only restriction on the update staging dir. On Windows
     * uses the NIO ACL view to grant only the file owner; failures are swallowed
     * (the random unguessable name remains the primary defence). Never throws.
     */
    private fun hardenDirOwnerOnly(dir: File) {
        val path = dir.toPath()
        val aclView = java.nio.file.Files.getFileAttributeView(path, java.nio.file.attribute.AclFileAttributeView::class.java)
        if (aclView != null) {
            val owner = java.nio.file.Files.getOwner(path)
            val entry = java.nio.file.attribute.AclEntry.newBuilder()
                .setType(java.nio.file.attribute.AclEntryType.ALLOW)
                .setPrincipal(owner)
                .setPermissions(java.util.EnumSet.allOf(java.nio.file.attribute.AclEntryPermission::class.java))
                .build()
            aclView.acl = listOf(entry)
        } else {
            dir.setReadable(false, false); dir.setReadable(true, true)
            dir.setWritable(false, false); dir.setWritable(true, true)
        }
    }

    private companion object {
        /** jpackage launcher name — derives from build.gradle.kts `packageName`. */
        const val LAUNCHER_EXE_NAME = "PureTV for Twitch.exe"
    }
}

/**
 * True only if [url] is an HTTPS URL whose host is a GitHub-controlled domain
 * that serves release assets (audit F3). Release `browser_download_url`s
 * 302-redirect from `github.com` to `*.githubusercontent.com`, so both are
 * allowed. The scheme MUST be https — otherwise `http://github.com/...` would
 * pass the host check and the installer/signature would be fetched in cleartext
 * (the host-only check relied on GitHub's JSON always returning https).
 */
internal fun isTrustedGithubAssetHost(url: String): Boolean {
    val uri = runCatching { URI.create(url) }.getOrNull() ?: return false
    if (!uri.scheme.equals("https", ignoreCase = true)) return false
    val host = uri.host?.lowercase().orEmpty()
    if (host.isBlank()) return false
    return host == "github.com" || host.endsWith(".github.com") ||
        host == "githubusercontent.com" || host.endsWith(".githubusercontent.com")
}

/** The two scripts the updater drops next to the installer. */
internal data class UpdateScripts(val bat: String, val vbs: String)

/**
 * Builds the detached self-update scripts.
 *
 * The .bat waits (via `ping`, NOT `timeout` — timeout needs console input that a
 * hidden launch doesn't have), runs the installer silently, relaunches the app,
 * then exits. The .vbs runs that .bat through a HIDDEN window (style 0) via
 * WScript.Shell, so the update never shows — or leaves open — a console window.
 * (The old `cmd /c start "" /min <bat>` left a prompt window sitting open.)
 *
 * Built with string concatenation (not templates) and launched via
 * [ProcessBuilder] with a fixed arg array — no shell, no interpolated command.
 */
internal fun buildUpdateScripts(
    installerPath: String,
    isMsi: Boolean,
    appExePath: String?,
    batPath: String,
): UpdateScripts {
    val lines = mutableListOf<String>()
    lines += "@echo off"
    // ~2s so this process fully exits before the installer touches locked files.
    lines += "ping 127.0.0.1 -n 3 >nul"
    lines += if (isMsi) {
        "msiexec /i \"" + installerPath + "\" /passive /norestart"
    } else {
        "\"" + installerPath + "\" /VERYSILENT /SUPPRESSMSGBOXES /NORESTART"
    }
    if (appExePath != null) {
        lines += "start \"\" \"" + appExePath + "\""
    }
    lines += "exit /b 0"
    val bat = lines.joinToString("\r\n") + "\r\n"

    // VBScript escapes a literal double-quote as a doubled double-quote.
    val escapedBat = batPath.replace("\"", "\"\"")
    val vbs = "CreateObject(\"WScript.Shell\").Run \"cmd /c \"\"" + escapedBat + "\"\"\", 0, False"
    return UpdateScripts(bat = bat, vbs = vbs)
}
