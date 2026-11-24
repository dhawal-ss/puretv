# PureTV Performance Audit

November 2026 · covers Android phone/tablet, Android TV, and desktop (Windows, macOS, Linux)

**How this was done.** Five reviewers read the code in parallel, one each for the phone app, the TV app, desktop video, the desktop UI and startup, and the shared `core` module with the build setup. The findings were then checked against the source. Nothing has been measured on a device yet. The impact figures are estimates from reading the code. Before and after any fix, measure with Macrobenchmark on Android and `timing.log` / JFR on desktop. Items marked ✔ were re-read line by line while writing this report.

Severity: **High** means users notice it today. **Medium** means it's noticeable on slow devices, busy chats or large follow lists. **Low** is polish. Effort: **S** is about an hour to half a day, **M** is one to three days, **L** is a week or more.

---

## 1. Top 12: best return for the effort

| # | What | Platforms | Severity | Effort |
|---|------|-----------|----------|--------|
| 1 | Home makes 4 network calls one after another, and one of them is never used ✔ | Phone | High | S |
| 2 | TV keeps the stream, chat and sockets running after the user presses Home ✔ | TV | High | S |
| 3 | `PlayerView` is never detached from the shared player, so every stream open (and every rotation on phone) leaks a view ✔ | Phone, TV | High | S |
| 4 | Every chat batch (about 10 a second) recomposes the whole stream screen | Phone, TV, Desktop | High | M |
| 5 | VOD chat replay filters and sorts the entire comment history every second on the main thread ✔ | All | High | M |
| 6 | macOS video copies every frame on the CPU and paints it on the UI thread, under a lock it shares with VLC ✔ | macOS | High | M |
| 7 | No baseline profile for either Android app, which are sideloaded so Play cloud profiles never apply ✔ | Phone, TV | High (TV) | M |
| 8 | A LIVE dot pulses on every card, so list screens never stop drawing frames ✔ | Phone, TV | Medium–High | S |
| 9 | Windows startup runs `reg.exe` and an fsync before the first window ✔ | Windows | High | S |
| 10 | The ad backup lookup tries player types one after another, with no tight timeout | All | Medium | S–M |
| 11 | VLC's `--network-caching=2000` adds about 1 s to every start and channel switch ✔ | Desktop | Medium | S |
| 12 | Following screens compose every followed channel at once instead of lazily | Phone, TV, Desktop | Medium | S–M |

Fixing 1, 2, 3, 8, 9 and 11 is mostly small, contained edits. Together they should give a clearly snappier app on every platform within a few days.

---

## 2. Shared code (`core/`) and builds: affects every platform

### 2.1 VOD chat replay does work proportional to the whole history on each tick ✔ (High, M)
- **Where:** `core/src/commonMain/kotlin/com/puretv/twitch/core/stream/VodComments.kt:99-114` and `core/.../vod/VodChatReplayController.kt:78,112-114`.
- **What happens:**
  - Every position tick runs `byId.values.filter { … }.sortedBy { … }` over every comment loaded so far.
  - `add()` copies the whole map for each page that loads.
  - `emitDue` re-tokenises 200 rows and produces 200 new row objects.
  - Comments are only cleared on a backward seek.
  - The callers run on `viewModelScope`, which is the main thread.
- **Effect:** Main-thread CPU use grows the longer someone watches. In a busy multi-hour VOD this becomes visible jank on phones and TVs.
- **Fix:**
  - Keep comments in an offset-sorted list with a "next due" cursor, and track the max offset as comments arrive.
  - Drop comments far behind the playhead.
  - Tokenise each comment once, when it's added.
  - Run the controller on `Dispatchers.Default`.

### 2.2 Ad backup resolution runs one player type at a time (Medium, S–M)
- **Where:** `core/.../adblock/BackupStreamResolver.kt:85-104, 467-486`. On desktop it's called from `app-windows/.../player/LocalStreamProxy.kt:477-484`.
- **What happens:**
  - `for (playerType in backupPlayerTypes)` tries `embed` and then `site`, each needing a token mint, a master fetch and a variant fetch.
  - The backup variant fetch has no per-request timeout, so it inherits the client's 15 s. The primary fetch uses 3 s.
- **Effect:**
  - On a pre-roll, the first clean playlist waits for up to six round trips.
  - A hung backup request stalls VLC's playlist refresh for up to 15 s, which is a visible freeze when low latency is on.
- **Fix:**
  - Resolve both player types concurrently and take the first clean result.
  - Give backup fetches the same 3 s timeout.
  - Optionally fetch the `embed` master in the background as soon as a stream opens.

### 2.3 Large emote JSON is parsed on the main thread (Medium, S)
- **Where:** `core/.../emotes/EmoteRepository.kt:127-189, 262-273` and `TwitchGqlDirectoryClient.kt:229`. Callers collect on `viewModelScope`.
- **What happens:** Ktor decodes the response body on the caller's dispatcher. The 7TV channel payload can be hundreds of KB to MB. The disk cache does the file read on IO but decodes back on Main.
- **Effect:** An estimated 50–300 ms hitch when opening a big channel, worst on TV.
- **Fix:** Wrap the fetch, parse and map in `withContext(Dispatchers.Default)` inside `EmoteRepository` and `PersistentEmoteCache`. Fixing it in core fixes all three apps.

### 2.4 Chat emotes and badges wait for an uncached `/users` lookup (Medium, S–M)
- **Where:** `core/.../repository/Repositories.kt:181-194` (memory-only cache) and `EmoteRepository.kt:59-63` (disk cache keyed by numeric id).
- **What happens:** Every cold open waits one Helix round trip before badges and channel emotes can start, even when emotes are already on disk.
- **Effect:** Chat shows up bare for a moment on every cold open.
- **Fix:**
  - Persist the login → id mapping, since Twitch ids never change.
  - Key the emote cache by login as well.
  - Start the global emote loads before the channel lookup returns.

### 2.5 Emotes are fetched at the largest size ✔ (Medium, S)
- **Where:** `EmoteRepository.kt:146` (BTTV `3x`) and `:206` (7TV `4x`).
- **What happens:** Emotes are drawn at about 28 dp. On desktop, animated frames are decoded at full size, up to 120 frames each. The frame cache is capped at 64 entries by count, not by bytes (`app-windows/.../ui/emotes/EmoteFrameCache.kt:36`).
- **Effect:** Megabytes of downloads in busy chats, which competes with video segments on mobile. On desktop this can reach hundreds of MB of native memory.
- **Fix:** Request `2x` for chat and `1x`/`2x` for the picker, and cap the desktop frame cache by bytes (for example 64–96 MB).
- **Phone note:** animated emotes render as still images on the phone because `coil-gif` isn't a dependency, and the "Animate emotes" setting is never read there.

### 2.6 Following loads in sequence and is never persisted (Medium, S–M)
- **Where:** `core/.../follows/FollowedChannelsService.kt:79-135`.
- **What happens:**
  - The live check only starts after every page of follows has loaded.
  - Profile lookups only start after the live check finishes.
  - Nothing survives a restart.
- **Effect:** Following and "Live now" are slow on cold start, worst for people who follow hundreds of channels.
- **Fix:**
  - Start the live check first.
  - Start profile lookups as soon as the follow list arrives.
  - Persist the list and show it immediately, then refresh it in the background.

### 2.7 HTTP client allows only 5 requests at a time per host (Low–Medium, S)
- **Where:** `OkHttp.create()` with no config in `app-android/.../di/AndroidModule.kt:60`, `app-tv/.../di/TvModule.kt:57` and `app-windows/.../di/DesktopModule.kt:83`.
- **What happens:** OkHttp's default `maxRequestsPerHost = 5`. Requests beyond that queue, even over HTTP/2.
- **Effect:** Stream opens (about six Helix calls), Following fan-out and desktop multi-view queue in waves.
- **Fix:** Configure the dispatcher with `maxRequestsPerHost = 16` in one shared helper.

### 2.8 Compose sees all core models as unstable (Medium, S)
- **What happens:** `core` isn't compiled with the Compose compiler, so `ChatMessage`, `StreamInfo` and similar classes are only compared by identity. Every rebuilt but equal object recomposes its row.
- **Fix:** Add a `compose-stability.conf` listing the `core.model`, `core.follows` and `core.chat` packages and `kotlin.collections.*`, and point all three apps' `composeCompiler { stabilityConfigurationFiles }` at it.

### 2.9 Smaller core items (Low, S)
- **`VideoModels.kt:42-44`:** compiles 3 `Regex` objects per video duration. Hoist them to constants.
- **`ManifestRewriter.kt:307-308`:** compiles a `Regex` for each ad line. Hoist it.
- **`SevenTvEventClient.kt:69`:** builds a new `Json` instance per stream open. Share one.
- **`TwitchChatClient.kt:483-495`:** boxes every character into an `ArrayList<Int>` for each emote-tagged message. Use an `IntArray`.
- **`ChannelStatsRepository.kt:269-274`:** makes three independent calls one after another. Run them with `async`.
- **`AdBlockEngine.kt:151-160`:** with a proxy configured, re-fetches the master playlist it already has, adding one or two round trips (opt-in users only).

### 2.10 Build configuration
- **R8 keep rules are too broad ✔ (Medium, S).**
  - The rules are `-keep class androidx.media3.** { *; }` and `org.koin.**` in both apps, plus `androidx.tv.**` and `androidx.leanback.**` on TV, and all of `core.model` and `core.api`.
  - R8 can't shrink or optimise these libraries, which means a bigger dex and slower class loading at startup.
  - The libraries already ship their own consumer rules. Remove the blanket keeps and smoke-test a release build.
  - On TV, `media3-session` is unused and `leanback` is only used for a theme. Drop both.
- **No baseline profiles ✔ (High on TV, M).**
  - Neither app has the `androidx.baselineprofile` plugin or a `baseline-prof.txt`.
  - Because the APKs are sideloaded, app code runs interpreted or JIT-compiled after every update. Typical gains are 20–40% faster cold start and less first-scroll jank.
  - Add a `:baselineprofile` Macrobenchmark module covering launch, Home scroll, opening a stream and chat.
- **The toolchain is about two years old (Low–Medium, L).**
  - Kotlin 2.0.21, Compose Multiplatform 1.7.0, AGP 8.7, Media3 1.4.1.
  - Newer Compose releases bring lazy-list prefetch and text-layout speedups. Upgrade after adding baseline profiles, so the gain can be measured.

---

## 3. Android phone and tablet (`app-android/`)

| # | Finding | Where | Sev. | Effort |
|---|---------|-------|------|--------|
| P1 | **Home loads serially ✔.** `refreshFollowedLive` → `topGames` → `refreshTopStreams` → `loadFollowsForCurrentUser`. The last call (2+N requests) fills `followedLogins`, which no app reads. Fix: delete it and run the other three with `async`/`awaitAll`, in both `loadLoggedInHome` and `refresh()`. | `ui/ViewModels.kt:209-246` | High | S |
| P2 | **Chat recomposes the whole stream screen.** `StreamScreen` collects one `StreamUiState` that holds `chatMessages`, so the player chrome and the whole `ChatPanel` body re-run about 10 times a second. Fix: give chat its own `StateFlow`, collected inside the chat host, and pass `PlayerArea` only the fields it uses. | `ui/screens/StreamScreen.kt:104,172-256` | High | M |
| P3 | **First frame waits on EncryptedSharedPreferences.** `RootScreen` injects `SessionManager`, which builds `AppSettingsStore`, which builds the Keystore master key and decrypts. The IO pre-warm only races it. Fix: gate on a cheap plain-prefs "has session" flag, or keep the splash screen up until the IO build finishes. | `ui/Navigation.kt:144`, `data/SessionManager.kt:21-31`, `data/AppSettingsStore.kt:93-99` | High | M |
| P4 | **PlayerView leak ✔.** The `AndroidView` has no `onRelease = { it.player = null }`, so every rotation, PiP switch and stream open leaves an old `PlayerView` (and its Activity) registered on the singleton player. | `player/PlayerSurface.kt` (`AndroidView`) | High | S |
| P5 | **LIVE pulse on every card ✔.** `LivePill` defaults to `pulse = true`, which creates one infinite transition per card. Fix: `pulse = false` on list cards; keep it on the hero and the channel header. | `ui/components/Expressive.kt:448,676` | Medium | S |
| P6 | **Chat snaps to the bottom while you read ✔.** Auto-scroll runs on every batch with no "at bottom" check. Fix: only follow while `!listState.canScrollForward`, otherwise show a "new messages" pill. | `ui/components/ChatPanel.kt:149-156` | Medium | S |
| P7 | **Returning from background play reloads the stream.** Switching to audio-only re-prepares the master playlist, and switching back re-prepares it again, giving a 1–3 s black rebuffer after a quick screen-off. Fix: delay the audio-only switch by 30–60 s, or disable the video track instead of reloading. | `player/BackgroundPlayback.kt:105-152`, `TwitchPlayer.kt:406-429` | Medium | S |
| P8 | **No preparation before the tap.** `PlayableStreamCache.prewarm` is never called on phone (TV and desktop use it). The ExoPlayer is built and the `PlayerView` inflated only once the URL arrives. Fix: prewarm on press-down; build the player after Home's first frame; mount the view straight away. | `player/PlayerSurface.kt`, `TwitchPlayer.kt:144` | Medium | S–M |
| P9 | **Following and Channel lists aren't lazy.** All follows are composed in one item, and the Channel page uses `verticalScroll` + `forEach` for videos. Fix: keyed `items(...)` and a `LazyColumn`. | `FollowingScreen.kt:154-190,405`, `ChannelScreen.kt:143,353` | Medium | M |
| P10 | **Chat rows are heavier than needed.** Each row has a press animation, a no-op `clickable`, and a new `SimpleDateFormat`. `rebuildEmotes` publishes a new map even when nothing changed. | `ChatPanel.kt:343-360`, `ViewModels.kt:824-832` | Low–Med | S |
| P11 | **Shimmer recomposes every block every frame.** Fix: one transition per screen, read in `drawWithCache`. | `ui/components/Shimmer.kt:40-58` | Low | S |
| P12 | **Pressing Home builds an ExoPlayer on any screen ✔.** `twitchPlayer.exoPlayer.isPlaying` is evaluated before the route check. | `MainActivity.kt:272` | Low | S |
| P13 | **Heavy startup also runs in the go-live worker.** WorkManager is initialised on the main thread, and `TwitchPlayer` plus 7 settings collectors start on every process start, including the 15-minute worker. Fix: on-demand WorkManager init and a lazy player. | `PureTvApp.kt:94-175` | Low | S–M |
| P14 | **Chat keeps processing in the background.** `collectAsState` isn't lifecycle-aware. Fix: `collectAsStateWithLifecycle`, and pause chat after a few minutes in the background. | `StreamScreen.kt:104` | Low | S |

---

## 4. Android TV (`app-tv/`)

TV hardware (Fire TV Stick, Chromecast, cheap boxes) has the least headroom, so these matter most here.

| # | Finding | Where | Sev. | Effort |
|---|---------|-------|------|--------|
| T1 | **Stream keeps running after Home ✔.** `onStop` only stops the go-live monitor. The player stops only when the composable is disposed, which pressing Home doesn't do. Decoding, segment downloads, chat and 7TV sockets all keep running, which makes the app a top candidate for being killed. `TvPlayer.release()` is never called. Fix: a `LifecycleStartEffect` in the stream and VOD screens that stops on stop and re-prepares on start; disconnect chat. | `TvMainActivity.kt:123-126`, `TvStreamScreen.kt:200-228` | High | S |
| T2 | **PlayerView leak ✔.** Same problem as P4. Add `onRelease = { it.player = null }`. | `TvStreamScreen.kt:422-438`, `TvVodScreen.kt:283-296` | High | S |
| T3 | **Hidden chat runs at full cost.** IRC, 7TV, badges and emotes all start alongside video resolve, but chat starts hidden and most TV viewers never open it. The timeline work runs on Main, and the whole screen recomposes about 10 times a second. Fix: connect chat when it's first shown (or after the first frame), use `flowOn(Default)`, and give chat a separate state flow. | `ui/ViewModels.kt:927-990` | High | M |
| T4 | **LIVE pulse on every card.** Same as P5; the screens never go idle, which takes GPU time from D-pad scrolling. | `ui/components/TvExpressive.kt:437-456` | High | S |
| T5 | **Following composes every follow.** `verticalScroll` + `FlowRow` + `forEachIndexed`. Fix: one keyed `LazyColumn`. | `TvFollowingScreen.kt:164,280-305,404-427` | High (heavy users) | M |
| T6 | **Home and Following load twice on open ✔.** The ViewModel's `init` calls `refresh()`, then the screen calls it again, which cancels the first load. Every Back to the screen repeats this. Fix: refresh in one place, and skip if the last refresh was under about 60 s ago. | `ui/ViewModels.kt:212`, `TvHomeScreen.kt:149`, `TvFollowingScreen.kt:98` | Medium | S |
| T7 | **Every channel flip waits 450 ms first ✔.** `PendingFlip.SETTLE_MS = 450` applies even to a single press. Fix: open right away on the first press (leading edge), debounce only bursts, or drop to about 250 ms. | `core/.../playback/ChannelFlip.kt:131` | Medium | S |
| T8 | **Main thread work at cold start.** `onCreate` resolves `PlayableStreamCache`, which builds the whole Ktor stack. Home's ViewModel builds `AppSettingsStore` (EncryptedSharedPreferences) during first composition. | `TvMainActivity.kt:86`, `TvHomeScreen.kt:106` | Medium | M |
| T9 | **Focus isn't restored on Back.** `remember` instead of `rememberSaveable` for the focus claim, and no `focusRestorer()` on rows, so Back jumps to the hero and scrolls the page. | `TvHomeScreen.kt:133-139`, `TvFollowingScreen.kt:120-124` | Medium | S–M |
| T10 | **Player build and view inflation are on the first-play path.** Includes a full `PlayerControlView` that is never shown. Fix: prebuild the player when Home is idle, and inflate with `use_controller=false` or use a bare surface. | `TvStreamScreen.kt:201,421-438`, `TvPlayer.kt:114` | Medium | S–M |
| T11 | **VOD scrubbing recomposes the whole screen.** Position and pending-seek state live at screen level. Fix: read them only inside `VodBar`. | `TvVodScreen.kt:173-179,220,421` | Low–Med | S |
| T12 | **Smaller items.** The nav-rail width animation is read in composition. Chat rows use `.clip().background()`. The theme loads asynchronously, causing a flash. Koin `androidLogger()` runs at INFO in release. The settings flow isn't shared (`stateIn`). | various | Low | S |

---

## 5. Desktop: Windows, macOS and Linux (`app-windows/`)

**How video reaches the screen.**
- **Windows and Linux:** VLC (or mpv) draws straight into a native window, so the JVM never touches frames. This path is in good shape.
- **macOS:** frames go through Java memory and Java2D. This is the slowest path in the app.

### 5.1 Video
| # | Finding | Where | OS | Sev. | Effort |
|---|---------|-------|----|------|--------|
| D1 | **macOS frame path ✔.** `present()` copies the whole frame (about 8 MB at 1080p) inside `synchronized(lock)` on VLC's thread. `paint()` scales and draws it on the AWT/Compose UI thread inside the same lock. That's about 500 MB/s of copying at 1080p60. UI work delays video and video painting delays UI, and the lock makes VLC drop frames. Fix: double-buffer with an atomic swap (no lock during copy or draw), paint from a render thread with `BufferStrategy`, and later move to a Metal/Skia texture. | `player/VideoCanvas.kt:57-97,117-125` | macOS | High | M (L for GPU path) |
| D2 | **macOS tiles get full-size frames.** The mini player and multi-view tiles receive source-resolution buffers and are downscaled on the UI thread. Fix: request a `BufferFormat` no larger than the canvas. | `VideoCanvas.kt:118` | macOS | Medium | M |
| D3 | **VLC buffering ✔.** `--network-caching=2000` (VLC's default is 1000) adds about 1 s to every start, switch, quality change and retry. `--live-caching` and `--sout-mux-caching` don't apply to this playback. Fix: about 1000 ms, or set it per media item (lower with low latency on, higher for VODs). | `player/VlcEngine.kt:134-143` | All | Medium | S |
| D4 | **mpv backend blocks the UI on leave.** Teardown `.get()` waits on the UI thread, and each mount creates a new mpv context and recompiles shaders. libmpv calls run on the UI thread under a lock the event thread also holds. Every property change re-reads 6 properties. No demuxer cache cap (about 200 MB per player). Fix: keep the context alive and reparent it, route calls through a worker like `VlcPlayer` does, observe typed properties, set `demuxer-max-bytes`. | `player/MpvPlayer.kt:252-326,427-493,181-221` | Win, Linux (mpv only) | Medium | M |
| D5 | **mpv Anime mode** runs a full-resolution CNN pass every frame even with no upscaling (no `//!WHEN` gate), plus `ewa_lanczossharp`. | `shaders/Anime4K_Restore_CNN_M.glsl`, `MpvMappers.kt:96-105` | Win, Linux | Low | S |
| D6 | **Constant audio sync.** VLC audio sync makes 2 native calls per player at 4 Hz for the whole session. Stop once the readback matches. | `player/VlcPlayer.kt:126-139,856-866` | All | Low | S |
| D7 | **HDR polling on the UI thread.** The Windows HDR state is queried every 2 s on the UI thread. Move it to a background thread. | `VlcPlayer.kt:225-230`, `platform/DisplayHdr.kt` | Windows | Low | S |
| D8 | **libVLC instances never released.** Each normalizer or RTX combination tried stays loaded until exit. Release them when idle. | `player/VlcEngines.kt:59-83` | All | Low | M |

### 5.2 Startup, interface and chat
| # | Finding | Where | OS | Sev. | Effort |
|---|---------|-------|----|------|--------|
| D9 | **`reg.exe` before the first window ✔.** `DesktopSettingsStore` init → `readMachineGuid()` → `ProcessBuilder("reg", …).waitFor()` on the main thread. `.authschema` is also rewritten (with fsync) on every launch. Fix: read the registry with JNA's `Advapi32Util` (already a dependency), and write the schema file only when it changes. | `data/DesktopSettingsStore.kt:117-144,423-430` | Windows (fsync: all) | High | S |
| D10 | **HTTP stack built before the window.** `koin.get<LiveFollowsMonitor>()` and `koin.get<LocalStreamProxy>()` in `main()` pull in Ktor, OkHttp and serialization (thousands of classes on a cold JVM). Fix: resolve lazily, or warm on a background thread. | `main.kt:143,166-168,205-206` | All | Medium–High | S |
| D11 | **Chat recomposes the whole stream screen.** Same as P2: one `StreamUiState` holds chat. | `ui/screens/StreamContent.kt:235,802,828` | All | High | M |
| D12 | **Composer autocomplete rescans everything ✔.** `composerSuggestions(word, emotes, recentChatters(…))` runs in composition with no `remember`, on every chat batch while a word is in the box. It lowercases thousands of emote codes each time. Fix: pre-lowercase codes once, `remember(word, emotes)`, and compute chatters only for `@`. | `StreamContent.kt:1448-1453`, `core/.../chat/ChatComposer.kt` | All | Medium–High | S |
| D13 | **Packaged runtime.** No AppCDS archive, `includeAllModules = true`, ProGuard off, and all of `materialIconsExtended` shipped for about 49 icons. Fix: generate a CDS archive in CI (typically 20–30% faster JVM start), list JDK modules explicitly, drop the icon pack. | `app-windows/build.gradle.kts:234-343` | All | Medium | M |
| D14 | **Window-procedure hook.** The JNA hook boxes arguments and allocates a `RECT` per `WM_NCHITTEST`, which means thousands of reflective calls a second during mouse movement. Fix: direct-mapped functions, cached bounds, and a fast pass-through for other messages. | `platform/WindowsNative.kt:165-272` | Windows | Medium | M |
| D15 | **Following / Channel not virtualised.** Same as P9. | `ui/screens/FollowingContent.kt:115-378`, `ChannelContent.kt:116,344` | All | Medium | S |
| D16 | **`expressiveSurface` cost.** Each card or chip uses `composed {}` with about 9 coroutines and 5 animatables. Port it to a `Modifier.Node`. | `ui/components/Expressive.kt:87-189` | All | Low–Med | M |
| D17 | **Chat row cost.** Each row has a hover coroutine, a reply `IconButton`, and resolves badges on every recomposition. | `ui/components/Cinematheque.kt:414-530` | All | Low–Med | S |
| D18 | **Window bounds written on the UI thread.** Bounds are saved with fsync on the UI thread. Route them through the existing `AtomicJsonWriter`. | `ui/PopoutLayout.kt:184-191`, `MiniPlayerController.kt` | All (worst on Windows) | Low–Med | S |
| D19 | **One frame loop per skeleton.** Each skeleton block runs its own `withFrameNanos` loop. Share one phase. | `ui/components/Premium.kt:158-198` | All | Low | S |
| D20 | **Emote clock keeps ticking while minimised.** It runs at 50 Hz with the window minimised. Pause it on `isMinimized`. | `ui/emotes/AnimatedEmote.kt:58-66` | All | Low | S |
| D21 | **Misc.** `defaults`/`gsettings` spawned on every focus gain (macOS/Linux). No `G1PeriodicGCInterval`, so idle heap is never returned. Synchronous `TimingLog` file appends. | various | Mixed | Low | S |

---

## 6. Already good (don't redo these)

- **Stream start pipeline:**
  - `PlayableStreamCache` de-duplicates resolves and hands the player the cached master (no second usher trip).
  - TV and desktop prewarm on focus or hover.
  - Resolve starts before metadata and emotes.
  - ExoPlayer starts with 1.5 s buffered.
  - TV prewarms neighbouring channels on a flip.
- **Chat:**
  - 100 ms batching, a 200-row cap, keyed lists.
  - Regex-free IRC parsing on `Dispatchers.Default`.
  - HashMap emote tokenising.
  - Persistent emote cache that emits the cached copy first.
  - Parallel BTTV/FFZ/7TV fetches.
- **Networking:** one shared Ktor/OkHttp client with HTTP/2, gzip and timeouts. The Go proxy reuses connections and has sensible limits.
- **Desktop:**
  - Native rendering on Windows and Linux with hardware decoding.
  - One shared libVLC factory, with every libVLC call made off the UI thread.
  - Player position ticks are filtered out of screen state.
  - A shared emote clock drawn in the draw phase.
  - Disk writes go through a conflated background writer.
  - The player and proxy start after the first window.
- **Android:**
  - R8 minify and resource shrinking are on.
  - Library baseline profiles are installed.
  - No re-prepare on rotation or PiP.
  - TV focus animations run in the draw phase.
  - Image URLs are sized to the surface.
  - Polling is scoped to visibility.

---

## 7. Suggested order

1. **Week 1: small fixes, big feel.** P1, P4/T2, T1, T6, T7, P5/T4, P6, P12, D3, D9, D10, D12, 2.3 and 2.7. All are S, mostly local edits.
2. **Week 2: measurement and structure.**
   - Add the `:baselineprofile` module and Macrobenchmarks (phone and TV), and record a desktop `timing.log` baseline on each OS.
   - Then do the chat-state split (P2/T3/D11), VOD replay (2.1), the ad backup changes (2.2) and the R8 keep-rule cleanup.
3. **Week 3: platform depth.**
   - The macOS frame path (D1/D2).
   - Lazy Following on all three apps (P9/T5/D15).
   - Main-thread startup on Android (P3/T8).
   - Desktop AppCDS (D13).
   - Emote sizes and the frame cache byte cap (2.5).
4. **Later.** Toolchain upgrade (measure against the baseline), mpv backend items, and polish.
