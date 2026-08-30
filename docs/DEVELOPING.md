# Developing PureTV

Everything that used to crowd the README. If you only want to run the app, the
three Gradle commands there are enough.

## Layout

Kotlin Multiplatform:

- `core` holds the Twitch API client, sign-in, the ad-block engine, chat and the
  data models. No UI, no platform types.
- `app-windows` is Compose Multiplatform with VLC (mpv is an opt-in backend).
- `app-android` is Jetpack Compose with ExoPlayer (Media3).
- `app-tv` shares `core` only. It has its own 10-foot Leanback and D-pad UI, and
  duplicates the player wiring because ExoPlayer cannot live in `core`.

## Running

```bash
./gradlew :app-windows:run           # needs JDK 17 and VLC installed
./gradlew :app-android:assembleDebug # needs the Android SDK
./gradlew :app-tv:assembleDebug
```

Point `local.properties` at your SDK with `sdk.dir=...`.

Sign-in needs a Twitch client secret. Copy `secrets.properties.example` to
`secrets.properties` (gitignored) and fill in your own.

A signed release TV APK needs a keystore. Create one and put `storeFile`,
`storePassword`, `keyAlias` and `keyPassword` in a gitignored
`keystore.properties` at the repo root. Without it the release build is
left unsigned.

## Packaging Windows

```bash
./gradlew :app-windows:bundleVlc :app-windows:packageReleaseMsi
```

This bundles VLC, so the installed app needs nothing extra.

## Cutting a release

### Windows

Bump `appVersion` in `app-windows/build.gradle.kts`, commit, then:

```bash
git tag v1.0.1
git push origin v1.0.1
```

CI builds the installer and opens a draft release. Publish it and the in-app
updater picks it up on everyone's next launch.

### Android and TV

There is no CI workflow watching `android-v*` or `tv-v*`. Cut these by hand.

1. Bump `versionCode` and `versionName` in `app-android/build.gradle.kts` or
   `app-tv/build.gradle.kts`, and the matching `versionCode`, `versionName` and
   `notes` in `docs/android-version.json` or `docs/tv-version.json`.

2. Build the signed release APK. Both are signed by the build now, so neither
   needs a manual signing pass.

   `./gradlew :app-tv:assembleRelease` is wired to `keystore.properties`.

   `./gradlew :app-android:assembleRelease` is wired to this machine's
   `~/.android/debug.keystore` (alias `androiddebugkey`, password `android`).
   That is not a mistake: every Android release published so far was signed with
   that key rather than a dedicated release key, and Android identifies an app by
   its signing certificate, so a build signed with anything else will not install
   over an existing copy. People would have to uninstall and lose their session
   first. Changing the Android key is a one-way door, and this is why it stays.

3. **Check the signing key anyway**, because getting it wrong is unrecoverable
   for anyone already running the app:

   ```bash
   apksigner verify --print-certs new.apk
   ```

   must match the same command run against the currently published APK.
   Android is `CN=Android Debug`; TV is `CN=PureTV for Twitch`.

4. Upload in this order. Create the versioned tag as a **pre-release**, then
   update the moving `android-latest` or `tv-latest` release: APK first, verify
   it downloads and its signature checks out, then the manifest JSON last.
   Uploading the manifest first would advertise a version nobody can download
   yet.

   ```bash
   gh release create android-v1.0.2 --prerelease ...
   gh release upload android-latest new.apk --clobber
   # verify, then:
   gh release upload android-latest docs/android-version.json --clobber
   ```

## Release channels, and why pre-release matters

Windows (`v*`), Android (`android-v*` plus the moving `android-latest`) and TV
(`tv-v*` plus `tv-latest`) all publish from this one repo, so they share
GitHub's single "Latest" pointer.

The Windows download button and the Windows in-app updater both resolve
`/releases/latest`, which GitHub points at the newest release that is not a
draft or a pre-release. So an Android or TV release that is not marked
pre-release steals "Latest" from Windows. Those releases carry only an APK, so
the Windows button would land people on an APK and the desktop updater would
find no installer and silently offer nothing.

**Rule: every Android and TV release must be created as a pre-release.**

```bash
gh release create <tag> --prerelease ...
gh release edit <tag> --prerelease     # to fix one after the fact
```

Only Windows `v*` releases stay non-pre-release. This is safe because the
Android and TV download links use pinned tag URLs, and each in-app updater
reads its own pinned `*-latest/*-version.json` manifest. None of them depend on
the "Latest" pointer. The desktop updater also skips any release without a
Windows installer as a backstop, but keep the flag right so the download button
stays correct.
