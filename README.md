<div align="center">

# 📺 PureTV for Twitch

### Ad-free Twitch viewing for Windows — clean, fast, and yours.

[![Download PureTV for Windows](https://img.shields.io/badge/⬇%20Download%20PureTV-Windows%20Installer-9147FF?style=for-the-badge&logo=windows&logoColor=white)](https://github.com/dhawal-ss/puretv/releases/latest)

**[👉 Click here to download the latest version](https://github.com/dhawal-ss/puretv/releases/latest)**

*Unaffiliated with Twitch Interactive, Inc. — an independent community project.*

</div>

---

## ⬇️ How to install (takes about a minute)

1. Click the big **Download PureTV** button above.
2. On the page that opens, under **Assets**, click the file ending in **`.msi`**.
3. Open the downloaded file and follow the prompts.
4. **If Windows shows a blue “Windows protected your PC” screen**, click
   **More info → Run anyway**. This appears only because the app isn’t
   code-signed yet — it’s safe to continue.
5. Launch **PureTV for Twitch** from your Start menu and sign in with Twitch.

> 💡 **Playback uses VLC.** If video doesn’t start, install the free
> [VLC media player](https://www.videolan.org/vlc/) and reopen PureTV.

## ✨ What you get

- **Ad-free streams** — mid-roll ads are blocked before the player ever sees
  them, with a live status pill so you always know it’s working.
- **Follow your favorites** — hit **Follow** on any channel and it shows up on
  your Home page, live or offline, so you never have to search again.
- **Automatic updates** — PureTV checks for new versions on launch and updates
  itself in one click. No reinstalling.
- **Live chat** — read and send messages right next to the stream.
- **Theatre & fullscreen** — proper, native-feeling window controls and Aero Snap.

## 🔄 Updating

You don’t have to do anything — when a new version is released, PureTV shows an
**“Update available”** banner at the top. Click **Update** and it installs the
new version and relaunches. You can also check manually in **Settings → About**.

## 🛡️ Privacy

Your Twitch login goes straight to Twitch using standard OAuth — PureTV has no
servers of its own and never sees your credentials. Tokens are stored
encrypted on your own machine.

---

## 🧑‍💻 For developers

PureTV is a Kotlin Multiplatform monorepo: a shared `core` module plus three
platform apps (phone/tablet, Android TV, Windows desktop) and an optional Go
ad-block proxy.

```
PureTVforTwitch/
├── core/            KMP module — API client, auth, ad-block engine, chat, models
├── app-android/     Phone & tablet app (Jetpack Compose)
├── app-tv/          Android TV app (Compose for TV)
├── app-windows/     Windows desktop app (Compose Multiplatform + VLCJ)
├── proxy-server/    Self-hosted ad-block proxy (Go) — optional
└── .github/workflows/  CI + release pipelines
```

### Build & run the Windows app

Prerequisites: **JDK 17**, **Gradle 8.10+** (no wrapper is committed), and
**VLC** installed.

```powershell
gradle :app-windows:run
```

### Package an installer

```powershell
gradle :app-windows:bundleVlc          # bundle VLC into the installer (run once)
gradle :app-windows:packageReleaseMsi  # produces the .msi under app-windows/build/compose/binaries
```

### How ad-blocking works

A three-tier fallback chain (`core/.../adblock/AdBlockEngine.kt`):

1. **Proxy router** — the small stream playlist is fetched through a region
   Twitch doesn’t ad-stitch; video segments still come straight from Twitch’s CDN.
2. **Manifest rewrite** — strips Twitch’s stitched-ad markers from the playlist
   locally if the proxy is unavailable.
3. **Black-frame fallback** — at the player layer, mutes and overlays a notice
   if an ad segment ever slips through.

### Releasing a new version

1. Bump `appVersion` in `app-windows/build.gradle.kts`.
2. Commit, then tag: `git tag vX.Y.Z && git push origin vX.Y.Z`.
3. CI builds the installers and opens a **draft** GitHub Release.
4. **Publish** the draft — the in-app updater only sees published releases.

See `.github/workflows/release.yml` for the full pipeline, and
`DESIGN_SYSTEM.md` for the shared visual language.

## 📄 License

Not yet specified — add one (MIT/Apache-2.0 are common for community tooling)
before a wider public release.
