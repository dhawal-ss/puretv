package com.puretv.twitch.android.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * SECTION 09, in-app updater for the sideloaded phone/tablet APK, over GitHub
 * Releases. Mirrors `TvUpdateManager` (app-tv's counterpart) almost exactly:
 * same manifest-based version discovery, same `PackageInstaller` install flow.
 * They are deliberately separate classes rather than a shared one: each
 * platform is a distinct sideloaded distribution channel with its own
 * versionCode/signing lineage, and duplicating this ~150-line class is cheaper
 * than coupling two otherwise-independent release channels together.
 *
 * Version discovery uses a small JSON manifest published next to the APK on
 * the moving `android-latest` release (see [VERSION_MANIFEST_URL]) rather than
 * the GitHub API: it's a stable URL, dodges the API's unauthenticated rate
 * limit, and lets us compare `versionCode` (monotonic) instead of parsing a
 * tag. Networking is a plain OkHttp client (NOT the shared Twitch client,
 * whose `Client-Id`/`Bearer` headers GitHub would reject).
 */
class AndroidUpdateManager(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val _state = MutableStateFlow<AndroidUpdateState>(AndroidUpdateState.Idle)
    val state: StateFlow<AndroidUpdateState> = _state.asStateFlow()

    val currentVersionName: String
        get() = runCatching { packageInfo().versionName }.getOrNull().orEmpty().ifBlank { "?" }

    @Suppress("DEPRECATION")
    private val currentVersionCode: Long
        get() = runCatching {
            val pi = packageInfo()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) pi.longVersionCode else pi.versionCode.toLong()
        }.getOrDefault(0L)

    private fun packageInfo() = context.packageManager.getPackageInfo(context.packageName, 0)

    init {
        // Surface a failed system-side install (the user cancelled the confirm
        // dialog, signature mismatch, etc.) back into our UI. Success replaces the
        // running app, so there's nothing to show for it.
        scope.launch {
            AndroidUpdateInstallBus.events.collect { result ->
                _state.value = if (result.success) AndroidUpdateState.Idle
                else AndroidUpdateState.Error(result.message ?: "Install was cancelled or failed.")
            }
        }
    }

    /**
     * Checks the manifest for a newer build. [force] makes a "you're up to date"
     * result explicit (for the Settings button); a silent launch check leaves the
     * state at [AndroidUpdateState.Idle] when there's nothing new. Never overrides
     * an in-flight download/install.
     */
    fun checkForUpdates(force: Boolean = false) {
        when (_state.value) {
            is AndroidUpdateState.Downloading, AndroidUpdateState.Installing -> return
            else -> Unit
        }
        scope.launch {
            _state.value = AndroidUpdateState.Checking
            val info = runCatching { fetchLatest() }.getOrNull()
            _state.value = when {
                info != null && info.versionCode > currentVersionCode -> AndroidUpdateState.Available(info)
                force -> AndroidUpdateState.UpToDate
                else -> AndroidUpdateState.Idle
            }
        }
    }

    /** Downloads [info]'s APK and launches the system installer. */
    fun downloadAndInstall(info: AndroidUpdateInfo) {
        when (_state.value) {
            is AndroidUpdateState.Downloading, AndroidUpdateState.Installing -> return
            else -> Unit
        }
        // Anti-downgrade: never install something that isn't strictly newer, even
        // if the manifest was edited to point at an older APK.
        if (info.versionCode <= currentVersionCode) {
            _state.value = AndroidUpdateState.UpToDate
            return
        }
        scope.launch {
            runCatching {
                _state.value = AndroidUpdateState.Downloading(0f)
                val apk = downloadApk(info)
                _state.value = AndroidUpdateState.Installing
                installApk(apk)
            }.onFailure { e ->
                _state.value = AndroidUpdateState.Error(e.message ?: "Update failed.")
            }
        }
    }

    /** Reset a terminal error / "up to date" back to idle (e.g. dismiss a banner). */
    fun dismiss() {
        when (_state.value) {
            is AndroidUpdateState.Error, AndroidUpdateState.UpToDate -> _state.value = AndroidUpdateState.Idle
            else -> Unit
        }
    }

    private fun fetchLatest(): AndroidUpdateInfo? {
        val request = Request.Builder()
            .url(VERSION_MANIFEST_URL)
            .header("User-Agent", "PureTV-Android-Updater")
            .header("Accept", "application/json")
            .build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val body = response.body?.string()?.takeIf { it.isNotBlank() } ?: return null
            val obj = runCatching { JSONObject(body) }.getOrNull() ?: return null
            val versionCode = obj.optLong("versionCode", 0L)
            val apkUrl = obj.optString("apkUrl").takeIf { it.isNotBlank() } ?: return null
            // Only ever fetch the APK from GitHub over https (mirrors the desktop
            // updater's host allowlist): a tampered manifest can't redirect the
            // download to an arbitrary host.
            if (versionCode <= 0L || !isTrustedGithubHost(apkUrl)) return null
            return AndroidUpdateInfo(
                versionCode = versionCode,
                versionName = obj.optString("versionName").ifBlank { versionCode.toString() },
                apkUrl = apkUrl,
                notes = obj.optString("notes"),
            )
        }
    }

    private suspend fun downloadApk(info: AndroidUpdateInfo): File = withContext(Dispatchers.IO) {
        require(isTrustedGithubHost(info.apkUrl)) { "Refusing to download from an untrusted host." }
        val request = Request.Builder()
            .url(info.apkUrl)
            .header("User-Agent", "PureTV-Android-Updater")
            .build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("Download failed (HTTP ${response.code}).")
            val body = response.body ?: error("Empty download response.")
            val total = body.contentLength()
            // updates/ under the app's own cache dir, private, auto-cleaned by the OS.
            val dir = File(context.cacheDir, "updates").apply { mkdirs() }
            val target = File(dir, "puretv-android-${info.versionCode}.apk")
            body.byteStream().use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var downloaded = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        downloaded += read
                        if (total > 0) {
                            _state.value = AndroidUpdateState.Downloading((downloaded.toFloat() / total).coerceIn(0f, 1f))
                        }
                    }
                }
            }
            target
        }
    }

    /**
     * Streams the APK into a [PackageInstaller] session and commits it. Android
     * verifies the new APK is signed with the SAME key as the installed app and
     * shows the confirm UI (routed through [AndroidUpdateInstallReceiver]); a
     * mismatched or corrupted download is rejected by the OS, so no extra
     * signature check is needed on our side.
     */
    private fun installApk(apk: File) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
        }
        val sessionId = installer.createSession(params)
        installer.openSession(sessionId).use { session ->
            apk.inputStream().use { input ->
                session.openWrite("puretv-android-update", 0, apk.length()).use { out ->
                    input.copyTo(out, bufferSize = 64 * 1024)
                    session.fsync(out)
                }
            }
            val intent = Intent(context, AndroidUpdateInstallReceiver::class.java)
                .setAction(AndroidUpdateInstallReceiver.ACTION_INSTALL_STATUS)
            var flags = PendingIntent.FLAG_UPDATE_CURRENT
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) flags = flags or PendingIntent.FLAG_MUTABLE
            val pending = PendingIntent.getBroadcast(context, sessionId, intent, flags)
            session.commit(pending.intentSender)
        }
    }

    private companion object {
        const val VERSION_MANIFEST_URL =
            "https://github.com/dhawal-ss/puretv/releases/download/android-latest/android-version.json"
    }
}

/** The parsed update manifest. */
data class AndroidUpdateInfo(
    val versionCode: Long,
    val versionName: String,
    val apkUrl: String,
    val notes: String,
)

/** Observable updater state for the Settings section and Home banner. */
sealed interface AndroidUpdateState {
    data object Idle : AndroidUpdateState
    data object Checking : AndroidUpdateState
    data object UpToDate : AndroidUpdateState
    data class Available(val info: AndroidUpdateInfo) : AndroidUpdateState
    data class Downloading(val progress: Float) : AndroidUpdateState
    data object Installing : AndroidUpdateState
    data class Error(val message: String) : AndroidUpdateState
}

/**
 * True only for an https URL on a GitHub-controlled host that serves release
 * assets. `browser_download_url`s 302 from github.com to *.githubusercontent.com,
 * so both are allowed; the scheme MUST be https (mirrors the desktop updater).
 */
internal fun isTrustedGithubHost(url: String): Boolean {
    val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return false
    if (!uri.scheme.equals("https", ignoreCase = true)) return false
    val host = uri.host?.lowercase().orEmpty()
    if (host.isBlank()) return false
    return host == "github.com" || host.endsWith(".github.com") ||
        host == "githubusercontent.com" || host.endsWith(".githubusercontent.com")
}
