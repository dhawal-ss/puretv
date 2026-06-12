# In-App Auto-Updater (GitHub Releases) — Design

**Date:** 2026-06-12
**Status:** Approved (design); implementing
**Scope:** Windows desktop module (`app-windows`) only
**Repo for releases:** `dhawal-ss/puretv`

## Problem

Users have to manually re-download and reinstall to get new versions. We want
updates delivered in-app: the running app notices a newer release and offers a
one-click update.

## Constraints / context (existing setup)

- Packaged with Compose Desktop / jpackage → **MSI + EXE**, with `upgradeUuid`
  set (in-place upgrade) and **`perUserInstall = true`** (no UAC on reinstall).
- Releases published to **GitHub Releases** by `.github/workflows/release.yml`
  on `v*` tags; the MSI is attached as an asset.
- Installers are currently **unsigned** (SmartScreen may warn).

## Decisions (from brainstorming)

- **Strategy A:** custom in-app updater over GitHub Releases — reuse the existing
  MSI + release pipeline, no new packaging tool.
- **UX:** notify + one-click (a dismissible banner + a Settings control), not
  silent background install.
- Windows-only; no delta updates; signing-cert setup is a separate follow-up.

## Flow

```
launch (packaged builds only)
  → GET /repos/dhawal-ss/puretv/releases/latest
  → parse tag_name + .msi asset; semver-compare to AppBuildConfig.VERSION
  → if newer: UpdateState.Available  → banner "Update available — vX.Y.Z [Update]"
  → user clicks Update
      → download MSI to %TEMP% (progress)  → UpdateState.Downloading
      → spawn detached: msiexec /i <msi> /passive /norestart && start "" "<exe>"
      → exitApplication()  → MSI upgrades in place (per-user, no UAC) → relaunch
```

## Components (new `desktop/update/` package)

### Version source (Gradle)
A single `appVersion` value in `app-windows/build.gradle.kts` feeds both
`packageVersion` and a generated `AppBuildConfig.kt`:

```kotlin
object AppBuildConfig {
    const val VERSION = "1.0.0"
    const val GITHUB_OWNER = "dhawal-ss"
    const val GITHUB_REPO = "puretv"
}
```

A `generateAppBuildConfig` task writes it to a generated source dir wired into
the main Kotlin source set — no runtime cost, no version drift.

### `Semver.kt` (pure, unit-tested)
`isNewerVersion(current: String, candidate: String): Boolean` — parses
`vX.Y.Z` / `X.Y.Z` (ignoring a `-suffix` pre-release tag), compares numerically.

### `UpdateModels.kt`
- `UpdateInfo(version, downloadUrl, sizeBytes, notes, htmlUrl)`
- `sealed interface UpdateState { Idle; Available(info); Downloading(progress: Float); Error(message) }`
- `@Serializable` GitHub DTOs: `GithubRelease(tag_name, name, body, html_url, draft, prerelease, assets)`, `GithubAsset(name, browser_download_url, size, content_type)`.

### `UpdateManager.kt` (Koin `single`)
Owns app-lifetime state so the banner (App) and Settings observe one source.
- Uses the **JDK `java.net.http.HttpClient`** — NOT the shared Twitch Ktor client
  (which injects a Twitch `Authorization` header that GitHub would 401 on). Sends
  `Accept: application/vnd.github+json` + a `User-Agent` (GitHub requires it).
- `state: StateFlow<UpdateState>`.
- `checkForUpdates()` — fetch latest non-draft/non-prerelease release, pick the
  `.msi` asset, compare versions; set `Available` or stay `Idle`. All failures
  (offline, rate-limited, no asset) → stay `Idle` (never crash startup).
- `downloadAndInstall(info)` — stream MSI to `%TEMP%/PureTV-update/`, emit
  `Downloading(progress)`, then spawn `msiexec` detached and call the supplied
  `exitApplication`. Relaunch target from `ProcessHandle.current().info().command()`.
- **Gated to packaged builds:** active only when
  `System.getProperty("compose.application.resources.dir") != null` (set by
  jpackage; same signal `VlcPlayer` uses). Dev (`gradle run`) skips the check.

### UI
- **Banner** in `App.kt` under the title bar: shown when state is `Available`
  ("Update available — vX.Y.Z · What's new" + **Update** + dismiss) or
  `Downloading` (progress) / `Error` (message + retry). Styled with `PureTvTheme`.
- **Settings** (`SettingsContent`): current version line + "Check for updates"
  button reflecting the same `UpdateState`.

### DI / wiring
- `single { UpdateManager() }` in `desktopModule`.
- `App.kt` resolves it, kicks `checkForUpdates()` in a `LaunchedEffect(Unit)`,
  passes `exitApplication` into `downloadAndInstall`.

## Error handling

| Case | Behavior |
|------|----------|
| Offline / DNS / timeout | stay `Idle`, no banner |
| GitHub rate limit (403) | stay `Idle` |
| Latest release has no `.msi` asset | stay `Idle` |
| Already on latest / newer | stay `Idle` |
| Download fails | `Error(message)` + retry from banner |
| Not a packaged build (dev) | skip check entirely |

## Security

HTTPS for API + download; verify the downloaded file size matches the asset’s
`size`. Code signing is out of scope but recommended next — the design needs no
change to adopt it (sign the MSI in CI; SmartScreen warnings then disappear).

## Testing

- **`Semver` unit tests:** `v1.2.0` > `v1.1.9`, equal, `1.10.0` > `1.9.0`,
  `v2.0.0` > `v1.9.9`, pre-release suffix ignored, malformed → false.
- **Release-parse test:** decode a sample GitHub `releases/latest` JSON and
  assert tag + `.msi` asset selection.
- Download/install verified manually (real msiexec).

## Files

**New**
- `desktop/update/Semver.kt`, `UpdateModels.kt`, `UpdateManager.kt`
- `desktop/ui/components/UpdateBanner.kt`
- tests: `SemverTest.kt`, `GithubReleaseParseTest.kt`

**Modified**
- `app-windows/build.gradle.kts` — `appVersion` constant + `generateAppBuildConfig` task + generated source dir.
- `desktop/di/DesktopModule.kt` — register `UpdateManager`.
- `desktop/ui/App.kt` — update banner + launch check.
- `desktop/ui/screens/SettingsContent.kt` — version + "Check for updates".

## Out of scope (YAGNI)

Silent/background install, delta updates, staged rollout, the code-signing-cert
setup itself, non-Windows platforms, in-app changelog rendering beyond the
release name/notes snippet.
