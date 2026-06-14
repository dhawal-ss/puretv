# Desktop OAuth: migrate to Twitch Device Code Grant (H2)

**Date:** 2026-06-14
**Status:** Approved design — pending spec review
**Scope:** `app-windows` (desktop) only. Android/TV are a separate later pass.
**Audit finding:** H2 — the Twitch `client_secret` is compiled into shipped binaries because the `authorization_code` flow requires it. Device Code Grant is a public-client flow that needs no secret. See `[[puretv-no-servers-constraint]]` — this is the serverless, free fix.

## 1. Goal

Replace the desktop login (auth-code + `client_secret` + loopback redirect server) with Twitch's **Device Code Grant Flow**, which:
- needs **no `client_secret`** at runtime,
- has **no inbound/loopback channel** (the app initiates every call), removing the `127.0.0.1:3000` redirect-interception seam the audit flagged,
- still returns a `refresh_token` (unlike implicit flow), so silent re-auth is preserved.

## 2. Decisions (locked)

| Decision | Choice |
|---|---|
| Scope | **Desktop only** this pass; Android/TV later |
| Existing logged-in users | **One-time re-login** on upgrade (guarded flag) |
| Desktop UX | **Auto-open browser to the activate page + show the `user_code`** (with copy). True pre-fill is best-effort only — see §4. |

## 3. Verified protocol facts (Twitch)

Device authorization — `POST https://id.twitch.tv/oauth2/device`, `application/x-www-form-urlencoded`:
- Request: `client_id`, `scopes` (space-delimited). **No `client_secret`.**
- Response JSON: `device_code`, `user_code`, `verification_uri`, `expires_in`, `interval`.
- **No `verification_uri_complete` is returned** (confirmed against Twitch docs 2026-06-14).

Token poll — `POST https://id.twitch.tv/oauth2/token`:
- Request: `client_id`, `device_code`, `grant_type=urn:ietf:params:oauth:grant-type:device_code`, `scopes`. **No `client_secret`.**
- Pending: `{"status":400,"message":"authorization_pending"}` — keep polling.
- Used/invalid: `{"status":400,"message":"invalid device code"}` — treat as expired; restart.
- Success: `access_token`, `refresh_token`, `expires_in`, `scope[]`, `token_type`.

Refresh — `POST /oauth2/token`, `grant_type=refresh_token`, `client_id`, `refresh_token`. **No `client_secret`** for a public client.

## 4. Desktop UX

Because there is no official `verification_uri_complete`:
1. On `beginLogin`, the app opens the system browser to `verification_uri` (`https://www.twitch.tv/activate`), best-effort appending `?public_code=<user_code>` (pre-fills when Twitch honours it; harmless otherwise).
2. The login screen shows: "We opened your browser — click **Authorize**." plus the `user_code` (e.g. `ABCD-1234`) with a **copy** button, and a fallback line: "Didn't open? Go to twitch.tv/activate and enter the code." plus a "waiting for approval…" state.
3. On success → proceed to the app. On code expiry (`expires_in`) → "Code expired — try again" with a retry button.

## 5. Components

### 5.1 `core/api/DeviceAuth.kt` (new, commonMain — reusable by Android/TV later)
```
object DeviceAuth {
    suspend fun requestDeviceCode(http, clientId, scopes): DeviceCodeResponse
    suspend fun pollOnce(http, clientId, deviceCode, scopes): DevicePollResult
    suspend fun refreshToken(http, clientId, refreshToken): TokenResponse   // no secret
}

data class DeviceCodeResponse(deviceCode, userCode, verificationUri, expiresInSeconds, intervalSeconds)

sealed interface DevicePollResult {
    data class Success(val token: TokenResponse) : DevicePollResult
    data object Pending : DevicePollResult       // authorization_pending
    data object SlowDown : DevicePollResult       // defensive: widen interval if ever returned
    data object Expired : DevicePollResult         // invalid device code / expiry
}
```
- Reuses the existing `TokenResponse` (from `PkceAuth.kt`).
- Parses Twitch's `{status,message}` envelope to classify poll results (read body once, like `PkceAuth.exchangeCodeForToken` does today).
- The *poll loop* (timing, expiry budget) lives in the desktop ViewModel, not here — `DeviceAuth` exposes a single `pollOnce` so it stays pure and unit-testable.

### 5.2 Desktop ViewModel (`app-windows/.../ui/ViewModels.kt`)
- Replace `LoginViewModel.beginLogin()` auth-code logic with: `requestDeviceCode` → open browser → emit a `DevicePrompt(userCode, verificationUri)` login state → loop `pollOnce` every `intervalSeconds` (widen on `SlowDown`) until `Success`/`Expired` or the `expiresIn` budget elapses.
- On `Success`: persist `TokenResponse`, resolve username via `GET /users`, set logged-in state (same downstream path as today).
- Remove `completeWithCode`/state-param handling (no redirect on desktop anymore).

### 5.3 Login UI (`app-windows/.../ui/screens/LoginContent.kt`)
- New states: `Idle` → `Connecting(userCode, verificationUri)` → `Error(retryable)`. Render the code + copy button + fallback link + waiting indicator per §4.

### 5.4 Retire loopback (`app-windows/.../auth/DesktopOAuthManager.kt`)
- Remove the embedded `127.0.0.1:3000` callback server and redirect/state handling. Keep only a small `openInBrowser(url)` helper (or fold it into the ViewModel). This closes the loopback-interception concern.

### 5.5 Existing-user migration (`app-windows/.../data/DesktopSettingsStore.kt`)
- Add a persisted `authSchemaVersion` (int). On startup, if a stored session exists with a version below the device-flow version, clear the session once and bump the flag → user logs in again exactly once. No token data is migrated.

### 5.6 Token refresh wiring
- Desktop refresh uses `DeviceAuth.refreshToken` (no secret). `PkceAuth.refreshToken` (with secret) stays untouched for Android/TV.

## 6. Out of scope (explicit)

- **Android/TV migration** and the **deletion of `TwitchConfig.CLIENT_SECRET` + the `generateTwitchSecrets` Gradle task.** The constant remains compiled into `core` (and thus the desktop binary, now dead) until Android/TV also move to device flow. So this pass *reduces* the secret exposure (desktop runtime no longer uses it) and the **finding fully closes at the end of the Android/TV pass.**
- Changing the token-at-rest encryption (that is the separate DPAPI finding, M3/M4).

## 7. Testing

- **TDD, `core` first:** `DeviceAuthTest` with Ktor `MockEngine` — (a) `requestDeviceCode` parses the device response; (b) `pollOnce` returns `Pending` on `authorization_pending`, `Success` on a token body, `Expired` on `invalid device code`; (c) `refreshToken` omits `client_secret` (assert the outgoing form has no secret); (d) request bodies carry `client_id`/`scopes`/`grant_type` and **no** `client_secret`.
- Desktop ViewModel poll loop: extract the timing/expiry decision into a pure helper if practical and unit-test it; otherwise verify by compile + a real login run.
- **Manual e2e:** real desktop login (fresh + upgrade-from-logged-in), token refresh after expiry, code-expiry retry.

## 8. Rollout / risks

- Users re-login once on upgrade (expected; §2).
- Desktop UX is "open browser + show code," not guaranteed pre-fill (§4).
- No server, no new paid dependency (constraint honoured).
- The embedded secret is not fully gone until the Android/TV pass (§6) — track as the follow-up that closes H2 completely.
