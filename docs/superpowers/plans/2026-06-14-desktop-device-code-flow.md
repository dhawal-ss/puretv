# Desktop Device Code Grant — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace desktop (`app-windows`) Twitch login with the Device Code Grant flow — no `client_secret` at runtime, no loopback redirect server.

**Architecture:** A new `core/api/DeviceAuth.kt` exposes pure form-builders + pure response parsers (unit-tested) plus thin suspend HTTP wrappers. The desktop `LoginViewModel` drives a request→open-browser→poll loop. `DesktopOAuthManager` is reduced to a browser opener. A pure `needsAuthReset` helper + a marker file force a one-time re-login on upgrade.

**Tech Stack:** Kotlin Multiplatform (`core`), Ktor client (`submitForm`), kotlinx.serialization, Compose Desktop, `kotlin.test`.

**Spec:** `docs/superpowers/specs/2026-06-14-desktop-device-code-flow-design.md`

---

## File Structure

- **Create** `core/src/commonMain/kotlin/com/puretv/twitch/core/api/DeviceAuth.kt` — device-flow form builders, parsers, suspend wrappers, models.
- **Create** `core/src/commonTest/kotlin/com/puretv/twitch/core/api/DeviceAuthTest.kt` — pure-logic tests.
- **Create** `app-windows/src/main/kotlin/com/puretv/twitch/desktop/auth/AuthMigration.kt` — pure `needsAuthReset`.
- **Create** `app-windows/src/test/kotlin/com/puretv/twitch/desktop/auth/AuthMigrationTest.kt`.
- **Modify** `app-windows/.../auth/DesktopOAuthManager.kt` — drop the embedded server; keep `openInBrowser`.
- **Modify** `app-windows/.../ui/ViewModels.kt` — rewrite `LoginViewModel` + `LoginUiState`.
- **Modify** `app-windows/.../ui/screens/LoginContent.kt` — code + copy + waiting + fallback UI.
- **Modify** `app-windows/.../data/DesktopSettingsStore.kt` — call the auth migration on init.

---

## Task 1: DeviceAuth pure form builders (core)

**Files:**
- Create: `core/src/commonMain/kotlin/com/puretv/twitch/core/api/DeviceAuth.kt`
- Test: `core/src/commonTest/kotlin/com/puretv/twitch/core/api/DeviceAuthTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.puretv.twitch.core.api

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DeviceAuthTest {

    @Test
    fun deviceCodeForm_has_client_id_and_scopes_and_no_secret() {
        val form = DeviceAuth.deviceCodeForm("CID", "scope:a scope:b").toMap()
        assertEquals("CID", form["client_id"])
        assertEquals("scope:a scope:b", form["scopes"])
        assertFalse(form.containsKey("client_secret"), "device request must not send a client_secret")
    }

    @Test
    fun pollForm_has_device_grant_type_and_no_secret() {
        val form = DeviceAuth.pollForm("CID", "DEV123", "scope:a").toMap()
        assertEquals("CID", form["client_id"])
        assertEquals("DEV123", form["device_code"])
        assertEquals("urn:ietf:params:oauth:grant-type:device_code", form["grant_type"])
        assertFalse(form.containsKey("client_secret"))
    }

    @Test
    fun refreshForm_is_public_client_refresh_with_no_secret() {
        val form = DeviceAuth.refreshForm("CID", "RT").toMap()
        assertEquals("refresh_token", form["grant_type"])
        assertEquals("RT", form["refresh_token"])
        assertFalse(form.containsKey("client_secret"))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :core:desktopTest --tests "com.puretv.twitch.core.api.DeviceAuthTest" --console=plain`
Expected: FAIL to compile — `DeviceAuth` unresolved.

- [ ] **Step 3: Write minimal implementation**

Create `DeviceAuth.kt` with just the form builders:

```kotlin
package com.puretv.twitch.core.api

/**
 * Twitch Device Code Grant flow (public client — no client_secret). Used by the
 * desktop app instead of the authorization_code + loopback flow. Pure helpers
 * (form builders + parsers) are unit-tested; the suspend wrappers are thin.
 */
object DeviceAuth {

    private const val DEVICE_GRANT_TYPE = "urn:ietf:params:oauth:grant-type:device_code"

    fun deviceCodeForm(clientId: String, scopes: String): List<Pair<String, String>> =
        listOf("client_id" to clientId, "scopes" to scopes)

    fun pollForm(clientId: String, deviceCode: String, scopes: String): List<Pair<String, String>> =
        listOf(
            "client_id" to clientId,
            "device_code" to deviceCode,
            "grant_type" to DEVICE_GRANT_TYPE,
            "scopes" to scopes,
        )

    fun refreshForm(clientId: String, refreshToken: String): List<Pair<String, String>> =
        listOf(
            "client_id" to clientId,
            "grant_type" to "refresh_token",
            "refresh_token" to refreshToken,
        )
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :core:desktopTest --tests "com.puretv.twitch.core.api.DeviceAuthTest" --console=plain`
Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```bash
git add core/src/commonMain/kotlin/com/puretv/twitch/core/api/DeviceAuth.kt core/src/commonTest/kotlin/com/puretv/twitch/core/api/DeviceAuthTest.kt
git commit -m "feat(core): DeviceAuth form builders (no client_secret)"
```

---

## Task 2: DeviceAuth response parsers (core)

**Files:**
- Modify: `core/src/commonMain/kotlin/com/puretv/twitch/core/api/DeviceAuth.kt`
- Test: `core/src/commonTest/kotlin/com/puretv/twitch/core/api/DeviceAuthTest.kt`

- [ ] **Step 1: Add failing tests** (append inside `DeviceAuthTest`)

```kotlin
    @Test
    fun parseDeviceCode_reads_all_fields() {
        val body = """
            {"device_code":"DC","user_code":"ABCD-1234",
             "verification_uri":"https://www.twitch.tv/activate",
             "expires_in":1800,"interval":5}
        """.trimIndent()
        val r = DeviceAuth.parseDeviceCode(body)
        assertEquals("DC", r.deviceCode)
        assertEquals("ABCD-1234", r.userCode)
        assertEquals("https://www.twitch.tv/activate", r.verificationUri)
        assertEquals(1800L, r.expiresInSeconds)
        assertEquals(5L, r.intervalSeconds)
    }

    @Test
    fun parsePollResult_pending() {
        val r = DeviceAuth.parsePollResult("""{"status":400,"message":"authorization_pending"}""")
        assertTrue(r is DevicePollResult.Pending, "got $r")
    }

    @Test
    fun parsePollResult_success_returns_token() {
        val body = """{"access_token":"AT","refresh_token":"RT","expires_in":3600,"token_type":"bearer","scope":["chat:read"]}"""
        val r = DeviceAuth.parsePollResult(body)
        assertTrue(r is DevicePollResult.Success, "got $r")
        assertEquals("AT", (r as DevicePollResult.Success).token.accessToken)
        assertEquals("RT", r.token.refreshToken)
    }

    @Test
    fun parsePollResult_invalid_device_code_is_expired() {
        val r = DeviceAuth.parsePollResult("""{"status":400,"message":"invalid device code"}""")
        assertTrue(r is DevicePollResult.Expired, "got $r")
    }
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew :core:desktopTest --tests "com.puretv.twitch.core.api.DeviceAuthTest" --console=plain`
Expected: FAIL to compile — `parseDeviceCode`, `parsePollResult`, `DeviceCodeResponse`, `DevicePollResult` unresolved.

- [ ] **Step 3: Implement parsers + models** (add to `DeviceAuth.kt`)

Add these imports at the top of `DeviceAuth.kt`:
```kotlin
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
```

Add the models (top level in the file, outside the object):
```kotlin
/** Response from POST /oauth2/device. */
data class DeviceCodeResponse(
    val deviceCode: String,
    val userCode: String,
    val verificationUri: String,
    val expiresInSeconds: Long,
    val intervalSeconds: Long,
)

/** Outcome of a single poll of POST /oauth2/token. */
sealed interface DevicePollResult {
    data class Success(val token: TokenResponse) : DevicePollResult
    data object Pending : DevicePollResult            // authorization_pending — keep polling
    data object SlowDown : DevicePollResult           // defensive: widen the interval
    data class Expired(val reason: String) : DevicePollResult  // invalid/expired — restart
}
```

Add inside the `DeviceAuth` object:
```kotlin
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    @Serializable
    private data class DeviceCodeDto(
        @SerialName("device_code") val deviceCode: String,
        @SerialName("user_code") val userCode: String,
        @SerialName("verification_uri") val verificationUri: String,
        @SerialName("expires_in") val expiresIn: Long = 0,
        val interval: Long = 5,
    )

    fun parseDeviceCode(body: String): DeviceCodeResponse {
        val dto = json.decodeFromString(DeviceCodeDto.serializer(), body)
        return DeviceCodeResponse(dto.deviceCode, dto.userCode, dto.verificationUri, dto.expiresIn, dto.interval)
    }

    fun parsePollResult(body: String): DevicePollResult {
        // Success bodies carry a token (access_token is required on TokenResponse,
        // so decoding a {status,message} envelope throws and falls through).
        runCatching { json.decodeFromString(TokenResponse.serializer(), body) }
            .getOrNull()
            ?.takeIf { it.accessToken.isNotBlank() }
            ?.let { return DevicePollResult.Success(it) }

        val message = runCatching {
            json.parseToJsonElement(body).jsonObject["message"]?.jsonPrimitive?.content
        }.getOrNull().orEmpty().lowercase()

        return when {
            message.contains("authorization_pending") -> DevicePollResult.Pending
            message.contains("slow") -> DevicePollResult.SlowDown
            else -> DevicePollResult.Expired(message.ifBlank { "device authorization expired" })
        }
    }
```

- [ ] **Step 4: Run to verify it passes**

Run: `./gradlew :core:desktopTest --tests "com.puretv.twitch.core.api.DeviceAuthTest" --console=plain`
Expected: PASS (7 tests).

- [ ] **Step 5: Commit**

```bash
git add core/src/commonMain/kotlin/com/puretv/twitch/core/api/DeviceAuth.kt core/src/commonTest/kotlin/com/puretv/twitch/core/api/DeviceAuthTest.kt
git commit -m "feat(core): DeviceAuth response parsers + models"
```

---

## Task 3: DeviceAuth suspend HTTP wrappers (core)

No new unit test (thin I/O over the tested pure helpers); verified by compile.

**Files:**
- Modify: `core/src/commonMain/kotlin/com/puretv/twitch/core/api/DeviceAuth.kt`

- [ ] **Step 1: Add the wrappers**

Add imports:
```kotlin
import io.ktor.client.HttpClient
import io.ktor.client.request.forms.submitForm
import io.ktor.client.statement.bodyAsText
import io.ktor.http.parameters
```

Add inside the `DeviceAuth` object:
```kotlin
    suspend fun requestDeviceCode(
        http: HttpClient,
        clientId: String = TwitchConfig.CLIENT_ID,
        scopes: String = TwitchConfig.SCOPES,
    ): DeviceCodeResponse {
        val response = http.submitForm(
            url = "${TwitchConfig.AUTH_BASE}/device",
            formParameters = parameters { deviceCodeForm(clientId, scopes).forEach { (k, v) -> append(k, v) } },
        )
        return parseDeviceCode(response.bodyAsText())
    }

    suspend fun pollOnce(
        http: HttpClient,
        deviceCode: String,
        clientId: String = TwitchConfig.CLIENT_ID,
        scopes: String = TwitchConfig.SCOPES,
    ): DevicePollResult {
        val response = http.submitForm(
            url = "${TwitchConfig.AUTH_BASE}/token",
            formParameters = parameters { pollForm(clientId, deviceCode, scopes).forEach { (k, v) -> append(k, v) } },
        )
        return parsePollResult(response.bodyAsText())
    }

    suspend fun refreshToken(
        http: HttpClient,
        refreshToken: String,
        clientId: String = TwitchConfig.CLIENT_ID,
    ): TokenResponse {
        val response = http.submitForm(
            url = "${TwitchConfig.AUTH_BASE}/token",
            formParameters = parameters { refreshForm(clientId, refreshToken).forEach { (k, v) -> append(k, v) } },
        )
        return json.decodeFromString(TokenResponse.serializer(), response.bodyAsText())
    }
```

- [ ] **Step 2: Compile core**

Run: `./gradlew :core:compileKotlinDesktop --console=plain`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add core/src/commonMain/kotlin/com/puretv/twitch/core/api/DeviceAuth.kt
git commit -m "feat(core): DeviceAuth suspend HTTP wrappers"
```

---

## Task 4: One-time re-login migration helper (desktop)

**Files:**
- Create: `app-windows/src/main/kotlin/com/puretv/twitch/desktop/auth/AuthMigration.kt`
- Test: `app-windows/src/test/kotlin/com/puretv/twitch/desktop/auth/AuthMigrationTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.puretv.twitch.desktop.auth

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AuthMigrationTest {

    @Test
    fun clears_when_old_schema_and_a_session_exists() {
        assertTrue(needsAuthReset(storedSchema = 0, currentSchema = 1, hasSession = true))
    }

    @Test
    fun does_not_clear_when_no_session() {
        assertFalse(needsAuthReset(storedSchema = 0, currentSchema = 1, hasSession = false))
    }

    @Test
    fun does_not_clear_when_already_on_current_schema() {
        assertFalse(needsAuthReset(storedSchema = 1, currentSchema = 1, hasSession = true))
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew :app-windows:test --tests "com.puretv.twitch.desktop.auth.AuthMigrationTest" --console=plain`
Expected: FAIL to compile — `needsAuthReset` unresolved.

- [ ] **Step 3: Implement**

```kotlin
package com.puretv.twitch.desktop.auth

/**
 * Auth storage schema version. Bumped when a change makes existing stored
 * sessions unusable — e.g. the move to Device Code Grant, after which tokens
 * minted under the old authorization_code flow can't be refreshed (no secret).
 */
const val CURRENT_AUTH_SCHEMA = 1

/**
 * True when the stored session predates [currentSchema] and a session exists —
 * i.e. we should clear it and prompt a one-time re-login on upgrade.
 */
fun needsAuthReset(storedSchema: Int, currentSchema: Int, hasSession: Boolean): Boolean =
    hasSession && storedSchema < currentSchema
```

- [ ] **Step 4: Run to verify it passes**

Run: `./gradlew :app-windows:test --tests "com.puretv.twitch.desktop.auth.AuthMigrationTest" --console=plain`
Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```bash
git add app-windows/src/main/kotlin/com/puretv/twitch/desktop/auth/AuthMigration.kt app-windows/src/test/kotlin/com/puretv/twitch/desktop/auth/AuthMigrationTest.kt
git commit -m "feat(desktop): pure auth-reset migration helper"
```

---

## Task 5: Run the migration in DesktopSettingsStore

**Files:**
- Modify: `app-windows/src/main/kotlin/com/puretv/twitch/desktop/data/DesktopSettingsStore.kt`

No unit test (file I/O glue over the tested helper); verified by compile + manual.

- [ ] **Step 1: Add the marker field + migration call**

In `DesktopSettingsStore`, add a marker file next to the others (near line 44):
```kotlin
    private val authSchemaFile = File(appDataDir, ".authschema")
```

In `init { ... }` (after `appDataDir.mkdirs()`, before `loadTokens()?.let { ... }`), add:
```kotlin
        runMigrations()
```

Add these imports at the top if missing: `com.puretv.twitch.desktop.auth.CURRENT_AUTH_SCHEMA`, `com.puretv.twitch.desktop.auth.needsAuthReset`.

Add the method to the class:
```kotlin
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
        runCatching { authSchemaFile.writeText(CURRENT_AUTH_SCHEMA.toString()) }
    }
```

(Confirm `clearTokens()` exists in this class — it is called by `LoginViewModel.logOut`/`SettingsViewModel.logOut`. If it deletes `tokensFile`, no change needed.)

- [ ] **Step 2: Compile**

Run: `./gradlew :app-windows:compileKotlin --console=plain`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app-windows/src/main/kotlin/com/puretv/twitch/desktop/data/DesktopSettingsStore.kt
git commit -m "feat(desktop): one-time re-login migration on device-flow upgrade"
```

---

## Task 6: Reduce DesktopOAuthManager to a browser opener

**Files:**
- Modify: `app-windows/src/main/kotlin/com/puretv/twitch/desktop/auth/DesktopOAuthManager.kt`

- [ ] **Step 1: Replace the whole file body**

Replace the entire class with the browser-opener only (removes the embedded Netty server, `PORT`, `TIMEOUT_MS`, `RedirectResult`, `PortInUseException`, `launchAndAwaitRedirect`):

```kotlin
package com.puretv.twitch.desktop.auth

import java.awt.Desktop
import java.net.URI

/**
 * Opens URLs in the user's system browser. Device Code Grant (see DeviceAuth)
 * replaced the old authorization_code + localhost:3000 redirect server, so this
 * no longer listens for anything — it only launches the browser.
 */
class DesktopOAuthManager {

    /** Opens [url] in the system browser; returns false if no browser could be launched. */
    fun openInBrowser(url: String): Boolean =
        if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            runCatching { Desktop.getDesktop().browse(URI(url)); true }.getOrDefault(false)
        } else {
            println("Open this URL to sign in to Twitch: $url")
            false
        }
}
```

- [ ] **Step 2: Compile (expected to FAIL — LoginViewModel still calls the old API)**

Run: `./gradlew :app-windows:compileKotlin --console=plain`
Expected: FAIL — `launchAndAwaitRedirect` / `PortInUseException` unresolved in `ViewModels.kt`. That is fixed in Task 7; do not commit yet.

---

## Task 7: Rewrite LoginViewModel for device flow

**Files:**
- Modify: `app-windows/src/main/kotlin/com/puretv/twitch/desktop/ui/ViewModels.kt`

- [ ] **Step 1: Replace `LoginUiState`** (currently near line 428)

```kotlin
data class LoginUiState(
    val isAuthenticating: Boolean = false,
    /** Shown while waiting for browser approval. */
    val userCode: String? = null,
    val verificationUri: String? = null,
    val error: String? = null,
    val isLoggedIn: Boolean = false,
)
```

- [ ] **Step 2: Replace the `LoginViewModel` class body** (the class starting near line 441)

Keep the constructor signature the same (DI is unchanged); replace `beginLogin()` and `completeWithCode(...)`:

```kotlin
class LoginViewModel(
    private val settingsStore: DesktopSettingsStore,
    private val oauthManager: DesktopOAuthManager,
    private val httpClient: HttpClient,
    private val tokenHolder: TokenHolder,
    private val apiClient: TwitchApiClient,
) : DesktopViewModel() {
    private val _state = MutableStateFlow(LoginUiState())
    val state: StateFlow<LoginUiState> = _state.asStateFlow()

    fun beginLogin() {
        if (_state.value.isAuthenticating) return
        _state.update { it.copy(isAuthenticating = true, error = null, userCode = null, verificationUri = null) }

        scope.launch {
            val device = runCatching { DeviceAuth.requestDeviceCode(httpClient) }.getOrNull()
            if (device == null) {
                _state.update { it.copy(isAuthenticating = false, error = "Couldn't start sign-in — check your connection and try again.") }
                return@launch
            }
            // Best-effort pre-fill; the activate page works with or without it.
            oauthManager.openInBrowser("${device.verificationUri}?public_code=${device.userCode}")
            _state.update { it.copy(userCode = device.userCode, verificationUri = device.verificationUri) }
            pollForToken(device)
        }
    }

    private suspend fun pollForToken(device: DeviceCodeResponse) {
        val deadline = System.currentTimeMillis() + device.expiresInSeconds * 1000
        var intervalMs = device.intervalSeconds.coerceAtLeast(1) * 1000
        while (System.currentTimeMillis() < deadline) {
            kotlinx.coroutines.delay(intervalMs)
            when (val result = runCatching { DeviceAuth.pollOnce(httpClient, device.deviceCode) }
                .getOrDefault(DevicePollResult.Pending)) {
                is DevicePollResult.Success -> { onAuthenticated(result.token); return }
                is DevicePollResult.Pending -> {}
                is DevicePollResult.SlowDown -> intervalMs += 5_000
                is DevicePollResult.Expired -> {
                    _state.update { it.copy(isAuthenticating = false, userCode = null, error = "Sign-in code expired — please try again.") }
                    return
                }
            }
        }
        _state.update { it.copy(isAuthenticating = false, userCode = null, error = "Sign-in timed out — please try again.") }
    }

    private suspend fun onAuthenticated(token: TokenResponse) {
        runCatching {
            // ORDERING IS LOAD-BEARING: push the token before the first authed call.
            tokenHolder.update(token.accessToken)
            val me = apiClient.getUsers().firstOrNull()
            val expiresAt = System.currentTimeMillis() / 1000 + token.expiresInSeconds
            settingsStore.saveTokens(
                accessToken = token.accessToken,
                refreshToken = token.refreshToken,
                expiresAtEpochSeconds = expiresAt,
                userId = me?.id,
                login = me?.login,
            )
        }.onSuccess {
            _state.update { it.copy(isAuthenticating = false, userCode = null, isLoggedIn = true) }
        }.onFailure { e ->
            _state.update { it.copy(isAuthenticating = false, userCode = null, error = e.message ?: "Login failed") }
        }
    }
}
```

- [ ] **Step 3: Fix imports** in `ViewModels.kt`

Add: `import com.puretv.twitch.core.api.DeviceAuth`, `import com.puretv.twitch.core.api.DeviceCodeResponse`, `import com.puretv.twitch.core.api.DevicePollResult`, `import com.puretv.twitch.core.api.TokenResponse`.
Remove now-unused: `import com.puretv.twitch.core.api.PkceAuth` (only if no other usage in the file — grep first), and the `TwitchConfig` import only if unused.

- [ ] **Step 4: Compile**

Run: `./gradlew :app-windows:compileKotlin --console=plain`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit Tasks 6+7 together**

```bash
git add app-windows/src/main/kotlin/com/puretv/twitch/desktop/auth/DesktopOAuthManager.kt app-windows/src/main/kotlin/com/puretv/twitch/desktop/ui/ViewModels.kt
git commit -m "feat(desktop): drive login via Device Code Grant; retire loopback server"
```

---

## Task 8: Login UI — show the code, copy, fallback

**Files:**
- Modify: `app-windows/src/main/kotlin/com/puretv/twitch/desktop/ui/screens/LoginContent.kt`

- [ ] **Step 1: Replace the `isAuthenticating` branch** of the `when` block (lines ~48-59)

```kotlin
                state.isAuthenticating -> Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(top = 24.dp),
                ) {
                    CircularProgressIndicator(color = c.twitchPurple)
                    Text(
                        "We opened your browser — click “Authorize” there to finish.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = c.textSecondary,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                    state.userCode?.let { code ->
                        Text(
                            code,
                            style = MaterialTheme.typography.headlineMedium,
                            color = c.textPrimary,
                            modifier = Modifier.padding(top = 12.dp),
                        )
                        Button(
                            onClick = { java.awt.Toolkit.getDefaultToolkit().systemClipboard
                                .setContents(java.awt.datatransfer.StringSelection(code), null) },
                            modifier = Modifier.padding(top = 8.dp),
                        ) { Text("Copy code") }
                        Text(
                            "Didn’t open? Go to twitch.tv/activate and enter this code.",
                            style = MaterialTheme.typography.bodySmall,
                            color = c.textSecondary,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
```

- [ ] **Step 2: Compile**

Run: `./gradlew :app-windows:compileKotlin --console=plain`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app-windows/src/main/kotlin/com/puretv/twitch/desktop/ui/screens/LoginContent.kt
git commit -m "feat(desktop): device-flow login screen (code + copy + activate fallback)"
```

---

## Task 9: Full verification

- [ ] **Step 1: Run all desktop + core tests**

Run: `./gradlew :core:desktopTest :app-windows:test --console=plain`
Expected: BUILD SUCCESSFUL (DeviceAuthTest, AuthMigrationTest, plus the existing suites).

- [ ] **Step 2: Manual e2e checklist** (record results in the PR)
  - Fresh login: launch dev build, click Sign in → browser opens to activate page, code shown → approve → app shows signed in, followed channels load.
  - Upgrade re-login: with an existing `tokens.enc`, launch the new build → exactly one forced re-login.
  - Code expiry: start login, wait past `expires_in` without approving → "code expired, try again".
  - Chat send still works (token has `chat:edit`).

- [ ] **Step 3: Final commit (if any cleanup)**

```bash
git commit -am "test(desktop): verify device-flow login end-to-end" --allow-empty
```

---

## Notes / Out of Scope

- `TwitchConfig.CLIENT_SECRET` and `generateTwitchSecrets` are **not** removed here — Android/TV still use the authorization_code flow. They are deleted in the Android/TV pass, which fully closes audit finding H2.
- `PkceAuth` remains for Android/TV; only desktop stops using it.
- **Token refresh is not wired today** (audit finding L2: no refresh interceptor exists; `PkceAuth.refreshToken` has zero callers). This plan ships `DeviceAuth.refreshToken` (secret-free) as the function a future refresh path should call, but does **not** add that path — desktop behaviour is unchanged (access token used until it 401s / a fresh login). Wiring proactive refresh is a separate follow-up.
- Token-at-rest encryption (DPAPI, M3/M4) is a separate finding, untouched here.
