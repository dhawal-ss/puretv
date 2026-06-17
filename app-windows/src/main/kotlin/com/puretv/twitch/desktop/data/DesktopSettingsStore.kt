package com.puretv.twitch.desktop.data

import com.puretv.twitch.core.api.DeviceAuth
import com.puretv.twitch.core.api.TokenRefreshException
import com.puretv.twitch.core.di.TokenHolder
import com.puretv.twitch.core.model.AppSettings
import com.puretv.twitch.core.model.PlaybackBackend
import com.puretv.twitch.core.model.UpscalingMode
import com.puretv.twitch.desktop.auth.CURRENT_AUTH_SCHEMA
import com.puretv.twitch.desktop.auth.needsAuthReset
import io.ktor.client.HttpClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * SECTION 08.5 — desktop persistence. Two files under `%APPDATA%/PureTwitch/`:
 *
 *  - `settings.json`  — plaintext JSON, mirrors [AppSettings] (quality,
 *    ad-block strategy, theme, etc.) — nothing sensitive.
 *  - `tokens.enc`     — AES-256-GCM-encrypted blob holding the OAuth
 *    access/refresh tokens. The key is derived from a per-machine secret
 *    (Section 14 Gotcha #6: true DPAPI requires JNA/JNI; we derive a stable
 *    machine key from `HKEY_LOCAL_MACHINE`'s MachineGuid via `reg query`,
 *    falling back to a generated `.keyseed` file if that fails). This is
 *    encryption-at-rest only — it is NOT a substitute for OS-level credential
 *    storage, and the derived key is recoverable by another process running as
 *    the same user (the seed/MachineGuid are not themselves secret). A
 *    best-effort owner-only permission is applied to the key material, but the
 *    honest protection here is the AES-GCM envelope, not the file ACL. A future
 *    hardening is to wrap the blob with Windows DPAPI (`CryptProtectData`) via
 *    the JNA the app already bundles.
 *
 * This class is intentionally synchronous + file-based: desktop has no
 * DataStore/Room equivalent in this module's dependency set, and settings
 * I/O is small + infrequent enough that blocking calls on `Dispatchers.IO`
 * (handled by callers) are perfectly fine.
 */
class DesktopSettingsStore(
    private val tokenHolder: TokenHolder,
) {

    private val appDataDir: File = File(System.getenv("APPDATA") ?: System.getProperty("user.home"), "PureTwitch")
    private val settingsFile = File(appDataDir, "settings.json")
    private val tokensFile = File(appDataDir, "tokens.enc")
    private val keyFile = File(appDataDir, ".keyseed")
    private val authSchemaFile = File(appDataDir, ".authschema")

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true; encodeDefaults = true }

    // Serializes settings + token read-modify-writes so concurrent callers can't
    // lose an update or interleave writes (audit F2).
    private val lock = Any()

    // In-memory mirror of session state so hot UI flows don't decrypt the token
    // file on every emission (audit U9). Updated on init/save/clear.
    @Volatile private var cachedLoggedIn: Boolean = false
    @Volatile private var cachedLogin: String? = null

    /** Whether a session token is present — no disk/crypto, safe in hot flows. */
    val isLoggedIn: Boolean get() = cachedLoggedIn

    /** The logged-in user's login (cached in memory), or null. */
    val sessionLogin: String? get() = cachedLogin

    private val _settings = MutableStateFlow(loadSettingsFromDisk())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    init {
        appDataDir.mkdirs()
        runMigrations()
        // Cold-start restoration: push the saved access token into the shared
        // TokenHolder so TwitchApiClient/TwitchGqlClient can authenticate on the
        // very first call, before the user has to "log in again" after a restart.
        // Mirrors the Android/TV AppSettingsStore.init pattern.
        loadTokens()?.let { tokenHolder.update(it.accessToken); cachedLoggedIn = true; cachedLogin = it.login }
    }

    /**
     * One-time re-login on the upgrade that introduced Device Code Grant: tokens
     * minted under the old authorization_code flow can't be refreshed without the
     * client_secret, so clear them once and let the user sign in again.
     */
    private fun runMigrations() {
        val storedSchema = runCatching { authSchemaFile.readText().trim().toInt() }.getOrDefault(0)
        if (needsAuthReset(storedSchema, CURRENT_AUTH_SCHEMA, hasSession = tokensFile.exists())) {
            clearTokens()
        }
        runCatching { AtomicFile.writeTextAtomically(authSchemaFile, CURRENT_AUTH_SCHEMA.toString()) }
    }

    // ---- Settings (plaintext JSON) -----------------------------------------
    //
    // [AppSettings] (in `core`) is a plain `data class`, not `@Serializable` —
    // it's normally persisted via Android DataStore on phone/TV. Desktop has
    // no DataStore, so we mirror its fields in a small `@Serializable` DTO
    // and convert both ways. Keep this DTO's fields in lockstep with
    // [AppSettings] if the model ever changes.

    @Serializable
    private data class SettingsDto(
        val preferredQuality: String = "auto",
        val lowLatencyMode: Boolean = true,
        val adBlockEnabled: Boolean = true,
        val adBlockStrategy: String = "proxy",
        val customProxyUrl: String = "",
        val chatEnabled: Boolean = true,
        val chatFontSize: Float = 14f,
        val showBadges: Boolean = true,
        val showBttvEmotes: Boolean = true,
        val show7tvEmotes: Boolean = true,
        val showFfzEmotes: Boolean = true,
        val chatTimestamps: Boolean = false,
        val animateEmotes: Boolean = true,
        val theme: String = "dark",
        val compactMode: Boolean = false,
        val upscalingMode: String = "off",
        val playbackBackend: String = "vlc",
    )

    private fun SettingsDto.toAppSettings() = AppSettings(
        preferredQuality = preferredQuality,
        lowLatencyMode = lowLatencyMode,
        adBlockEnabled = adBlockEnabled,
        adBlockStrategy = adBlockStrategy,
        customProxyUrl = customProxyUrl,
        chatEnabled = chatEnabled,
        chatFontSize = chatFontSize,
        showBadges = showBadges,
        showBttvEmotes = showBttvEmotes,
        show7tvEmotes = show7tvEmotes,
        showFfzEmotes = showFfzEmotes,
        chatTimestamps = chatTimestamps,
        animateEmotes = animateEmotes,
        theme = theme,
        compactMode = compactMode,
        upscalingMode = parseUpscalingMode(upscalingMode),
        playbackBackend = parsePlaybackBackend(playbackBackend),
        // `accessToken`/`username`/`userId` live in the encrypted token store
        // on desktop, not in plaintext settings — left at AppSettings defaults.
    )

    private fun AppSettings.toDto() = SettingsDto(
        preferredQuality = preferredQuality,
        lowLatencyMode = lowLatencyMode,
        adBlockEnabled = adBlockEnabled,
        adBlockStrategy = adBlockStrategy,
        customProxyUrl = customProxyUrl,
        chatEnabled = chatEnabled,
        chatFontSize = chatFontSize,
        showBadges = showBadges,
        showBttvEmotes = showBttvEmotes,
        show7tvEmotes = show7tvEmotes,
        showFfzEmotes = showFfzEmotes,
        chatTimestamps = chatTimestamps,
        animateEmotes = animateEmotes,
        theme = theme,
        compactMode = compactMode,
        upscalingMode = upscalingMode.name.lowercase(),
        playbackBackend = playbackBackend.name.lowercase(),
    )

    fun updateSettings(transform: (AppSettings) -> AppSettings) = synchronized(lock) {
        val updated = transform(_settings.value)
        _settings.value = updated
        runCatching {
            appDataDir.mkdirs()
            AtomicFile.writeTextAtomically(settingsFile, json.encodeToString(updated.toDto()))
        }
    }

    private fun loadSettingsFromDisk(): AppSettings = runCatching {
        if (settingsFile.exists()) json.decodeFromString(SettingsDto.serializer(), settingsFile.readText()).toAppSettings()
        else AppSettings()
    }.getOrElse { AppSettings() }

    // ---- Tokens (AES-256-GCM encrypted) -------------------------------------

    @Serializable
    private data class StoredTokens(
        val accessToken: String,
        val refreshToken: String?,
        val expiresAtEpochSeconds: Long,
        val userId: String?,
        val login: String?,
    )

    fun saveTokens(accessToken: String, refreshToken: String?, expiresAtEpochSeconds: Long, userId: String?, login: String?) {
        synchronized(lock) {
            val payload = StoredTokens(accessToken, refreshToken, expiresAtEpochSeconds, userId, login)
            val plaintext = json.encodeToString(payload).toByteArray(Charsets.UTF_8)
            runCatching {
                appDataDir.mkdirs()
                AtomicFile.writeBytesAtomically(tokensFile, encrypt(plaintext))
                restrictToOwner(tokensFile)
            }
        }
        // Keep the in-memory holder in lockstep with disk so any API call made
        // immediately after this returns sees the new token.
        tokenHolder.update(accessToken)
        cachedLoggedIn = true
        cachedLogin = login
    }

    fun loadTokens(): StoredTokensResult? = runCatching {
        if (!tokensFile.exists()) return null
        val plaintext = decrypt(tokensFile.readBytes())
        val stored = json.decodeFromString(StoredTokens.serializer(), String(plaintext, Charsets.UTF_8))
        StoredTokensResult(
            accessToken = stored.accessToken,
            refreshToken = stored.refreshToken,
            expiresAtEpochSeconds = stored.expiresAtEpochSeconds,
            userId = stored.userId,
            login = stored.login,
        )
    }.getOrNull()

    fun clearTokens() {
        synchronized(lock) { runCatching { tokensFile.delete() } }
        tokenHolder.update(null)
        cachedLoggedIn = false
        cachedLogin = null
    }

    /**
     * Refresh the stored access token if it is at/near expiry (audit F2). Twitch
     * user access tokens live ~4h; without this the desktop session silently
     * dies and every Helix/GQL call 401s until the user logs out and back in —
     * even though a valid refresh token is sitting encrypted on disk. Safe to
     * call at startup: a no-op when not logged in, when there's no refresh token,
     * or when the token is still fresh (or its expiry is unknown — we never
     * gamble a working token on a refresh). A genuinely revoked refresh token
     * (typed [TokenRefreshException]) clears the session so the user is prompted
     * to sign in again; transient network errors leave the token untouched.
     */
    suspend fun refreshIfNeeded(http: HttpClient, nowEpochSeconds: Long = System.currentTimeMillis() / 1000) {
        val current = loadTokens() ?: return
        val refresh = current.refreshToken ?: return
        if (current.expiresAtEpochSeconds <= 0) return
        if (nowEpochSeconds < current.expiresAtEpochSeconds - REFRESH_LEEWAY_SECONDS) return
        runCatching { DeviceAuth.refreshToken(http, refresh) }
            .onSuccess { fresh ->
                saveTokens(
                    accessToken = fresh.accessToken,
                    refreshToken = fresh.refreshToken ?: refresh,
                    expiresAtEpochSeconds = nowEpochSeconds + fresh.expiresInSeconds,
                    userId = current.userId,
                    login = current.login,
                )
            }
            .onFailure { e -> if (e is TokenRefreshException) clearTokens() }
    }

    data class StoredTokensResult(
        val accessToken: String,
        val refreshToken: String?,
        val expiresAtEpochSeconds: Long,
        val userId: String?,
        val login: String?,
    )

    // ---- Encryption helpers --------------------------------------------------

    private fun encrypt(plaintext: ByteArray): ByteArray {
        val key = machineKey()
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
        val ciphertext = cipher.doFinal(plaintext)
        // Layout: [12-byte IV][ciphertext+tag] — self-describing, no extra metadata needed.
        return iv + ciphertext
    }

    private fun decrypt(blob: ByteArray): ByteArray {
        require(blob.size > 12) { "Encrypted token blob too short" }
        val key = machineKey()
        val iv = blob.copyOfRange(0, 12)
        val ciphertext = blob.copyOfRange(12, blob.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
        return cipher.doFinal(ciphertext)
    }

    /**
     * Derives a stable AES-256 key from a per-machine seed. We try the
     * Windows `MachineGuid` registry value first (stable across reinstalls
     * of this app, unique per Windows install); if that's unavailable
     * (non-Windows dev machine, registry access denied), we fall back to a
     * randomly generated seed persisted to `.keyseed` — still "this machine
     * only", just regenerated if the file is lost (which invalidates any
     * previously saved tokens — acceptable for a local cache of refresh
     * tokens that can simply be re-obtained via login).
     */
    private fun machineKey(): SecretKeySpec {
        val seed = readMachineGuid() ?: readOrCreateKeySeedFile()
        val digest = MessageDigest.getInstance("SHA-256").digest(seed.toByteArray(Charsets.UTF_8))
        return SecretKeySpec(digest, "AES")
    }

    private fun readMachineGuid(): String? = runCatching {
        val process = ProcessBuilder(
            "reg", "query", "HKEY_LOCAL_MACHINE\\SOFTWARE\\Microsoft\\Cryptography", "/v", "MachineGuid"
        ).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().readText()
        process.waitFor()
        Regex("MachineGuid\\s+REG_SZ\\s+(\\S+)").find(output)?.groupValues?.get(1)
    }.getOrNull()

    private fun readOrCreateKeySeedFile(): String {
        if (keyFile.exists()) return runCatching { keyFile.readText().trim() }.getOrElse { generateAndPersistSeed() }
        return generateAndPersistSeed()
    }

    private fun generateAndPersistSeed(): String {
        val seed = Base64.getEncoder().encodeToString(ByteArray(32).also { SecureRandom().nextBytes(it) })
        runCatching {
            appDataDir.mkdirs()
            AtomicFile.writeTextAtomically(keyFile, seed)
            restrictToOwner(keyFile)
        }
        return seed
    }

    /**
     * Best-effort owner-only restriction on sensitive key material. On POSIX
     * this becomes `chmod 600`; on Windows the `File` permission bits are a
     * no-op for ACLs (the real protection remains the AES-GCM envelope), so this
     * is defence-in-depth, not the primary control. Never throws.
     */
    private fun restrictToOwner(file: File) {
        runCatching {
            file.setReadable(false, false)
            file.setReadable(true, true)
            file.setWritable(false, false)
            file.setWritable(true, true)
        }
    }
}

/** Refresh the access token this many seconds before its declared expiry. */
private const val REFRESH_LEEWAY_SECONDS = 300L

/** Lenient string -> enum: anything unrecognized (or null/legacy-missing) falls back to OFF. */
internal fun parseUpscalingMode(raw: String?): UpscalingMode =
    UpscalingMode.entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: UpscalingMode.OFF

/** Lenient string -> enum: anything unrecognized (or null/legacy-missing) falls back to VLC. */
internal fun parsePlaybackBackend(raw: String?): PlaybackBackend =
    PlaybackBackend.entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: PlaybackBackend.VLC
