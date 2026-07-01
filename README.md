<div align="center">

# PureTV

A clean, ad-free way to watch live streams on Windows, Android, and Android TV / Fire TV.

[![Download for Windows](https://img.shields.io/badge/Download%20for%20Windows-Installer-7C3AED?style=for-the-badge&logo=windows&logoColor=white)](https://github.com/dhawal-ss/puretv/releases/latest)
[![Download for Android](https://img.shields.io/badge/Download%20for%20Android-APK-3DDC84?style=for-the-badge&logo=android&logoColor=white)](https://github.com/dhawal-ss/puretv/releases/download/android-v1.0.0/PureTV-for-Twitch-1.0.0.apk)
[![Download for Android TV / Fire TV](https://img.shields.io/badge/Download%20for%20Android%20TV%20%2F%20Fire%20TV-APK-6441A5?style=for-the-badge&logo=androidtv&logoColor=white)](https://github.com/dhawal-ss/puretv/releases/download/tv-v1.0.0/PureTV-FireTV-AndroidTV-1.0.0.apk)

**Windows: [download the latest installer](https://github.com/dhawal-ss/puretv/releases/latest). Android: [download the APK](https://github.com/dhawal-ss/puretv/releases/download/android-v1.0.0/PureTV-for-Twitch-1.0.0.apk). Android TV / Fire TV: [download the TV APK](https://github.com/dhawal-ss/puretv/releases/download/tv-v1.0.0/PureTV-FireTV-AndroidTV-1.0.0.apk).**

</div>

---

## Install (about a minute)

1. Click the Download PureTV button above.
2. On the page that opens, under "Assets", click the file that ends in `.exe`.
3. Open the downloaded file and follow the prompts.
4. If Windows shows a blue "Windows protected your PC" screen, click "More info", then "Run anyway". This only appears because the app is not code-signed yet, and it is safe to continue.
5. Open PureTV from your Start menu and sign in.

Everything the app needs is included in the installer, so there is nothing else to set up.

## Install on Android (sideload, about a minute)

PureTV for Android is a sideloaded APK, not on the Play Store.

1. On your phone, tap the "Download for Android" button above to get the `.apk` file.
2. Open the downloaded file. The first time, Android asks to allow installs from your browser or Files app, turn that on, then tap Install.
3. Open PureTV, then sign in by entering the on-screen code at twitch.tv/activate.

A few notes:

- This build is signed with a development key, so Android labels it an app from an "unknown source". That is normal for sideloaded apps and is safe to allow.
- It needs Android 8.0 or newer.
- To update later, download the newest APK from the releases page and install it over the existing app.

On Android you get the same ad-free playback, plus Picture-in-Picture, a fill-to-edge fullscreen that uses the whole display including the camera cutout (double-tap the video to toggle it), and chat beside the stream.

## Install on Android TV & Fire TV (Downloader app, a couple of minutes)

PureTV has a separate build made for the TV, with a 10-foot layout you drive entirely with the remote. It installs by sideloading with the free **Downloader** app (by AFTVnews), which works on Fire TV, Fire TV Stick, and Google TV / Android TV.

1. On your TV, install **Downloader** from the Amazon Appstore (Fire TV) or the Google Play Store (Google TV), and open it.
2. First time only: your TV has to allow installs from Downloader. Fire TV walks you through this the first time; on Google TV go to *Settings, then Apps, then Security & restrictions, then Unknown sources*, and turn Downloader on.
3. In Downloader's URL box, enter the link to the TV APK and press Go:

   ```
   https://github.com/dhawal-ss/puretv/releases/download/tv-v1.0.0/PureTV-FireTV-AndroidTV-1.0.0.apk
   ```

   > **Easier: use a short Downloader code instead of typing that whole link.** Downloader (AFTVnews) has a companion URL shortener at **[aftv.news](https://aftv.news)**. On your phone or computer, open aftv.news, paste the link above, and it gives you a short numeric code (usually 6 to 7 digits). Then in Downloader on your TV, just type that code in the URL box and press Go, and it jumps straight to this download, no long link to peck out with the remote.

4. It downloads the APK, then asks to install it. Choose Install, and when it finishes you can delete the downloaded file to save space.
5. Open PureTV from your app list. To sign in (optional, only needed to see the channels you follow), scan the on-screen QR code with your phone, or go to **twitch.tv/activate** and enter the short code shown. You can also just start watching top and category streams without signing in.

A few notes:

- Using the remote: D-pad to move, Select to open, Back to go back. On a stream, press Left/Right or Menu to show chat, the Play/Pause button to pause, and Fast-Forward/Rewind to change video quality. Search brings up an on-screen keyboard, and browsing a category shows every channel live in it. Ad blocking runs automatically, shown by the small pill on the player.
- This build is signed with a development key, so the TV labels it an app from an "unknown source". That is normal for sideloaded apps and safe to allow.
- It needs Android TV / Fire OS based on Android 8.0 or newer (every current Fire TV Stick and Google TV qualifies).
- To update later, sideload the newest TV APK the same way and install it over the existing app.

## What you get

- Ad-free playback. Mid-roll ads are filtered out before the player ever sees them, and a small status indicator on the player shows you it is working.
- Follow your favorites. Add any channel and it shows up on your Home page, live or offline, so you never have to search for it again.
- Automatic updates. PureTV checks for a new version when it starts and updates itself in one click.
- Live chat right beside the stream.
- Real window controls: theatre mode, borderless fullscreen, and snap-to-edge dragging that feels native.

## Updating

You do not have to do anything. When a new version is out, PureTV shows an "Update available" banner at the top of the window. Click Update and it installs the new version and reopens. You can also check any time under Settings, then About.

## Privacy

Your sign-in goes straight to the streaming service using standard OAuth. PureTV runs no servers of its own and never sees your password. Your session is stored encrypted on your own computer.

---

## For developers

PureTV is a Kotlin Multiplatform project. A shared `core` module holds the API client, sign-in, ad-block engine, chat, and data models, and each platform app builds its own UI on top. The Windows app uses Compose Multiplatform with VLC for playback; the Android app uses Jetpack Compose with ExoPlayer (Media3).

Run the desktop app (needs JDK 17 and VLC installed):

```
./gradlew :app-windows:run
```

Build the Android app (needs the Android SDK; point `local.properties` at it with `sdk.dir=...`):

```
./gradlew :app-android:assembleDebug
```

Build the Android TV / Fire TV app (same SDK; it shares the `core` module but has its own 10-foot Leanback/D-pad UI):

```
./gradlew :app-tv:assembleDebug
```

A signed release TV APK (`./gradlew :app-tv:assembleRelease`) needs a keystore. Create one and put its details in a gitignored `keystore.properties` at the repo root (`storeFile`, `storePassword`, `keyAlias`, `keyPassword`); without it the release build is left unsigned.

Sign-in needs a client secret, kept in a gitignored `secrets.properties` (copy `secrets.properties.example` and fill in your own).

Package the installer (this bundles VLC, so the installed app needs nothing extra):

```
./gradlew :app-windows:bundleVlc :app-windows:packageReleaseMsi
```

To cut a release, bump `appVersion` in `app-windows/build.gradle.kts`, commit, then tag and push, for example:

```
git tag v1.0.1
git push origin v1.0.1
```

CI builds the installers and opens a draft release. Publish it, and the in-app updater picks it up on everyone's next launch.

## License

PureTV is open source under the MIT License (see [LICENSE](LICENSE)). Contributions are very welcome, so feel free to open an issue or a pull request.
