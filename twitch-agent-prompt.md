# AI AGENT BUILD PROMPT

## Ad-Free Twitch Viewer — Windows · Android · Android TV

### Author: Dhawal Ranka

\---

> \\\*\\\*HOW TO USE THIS PROMPT\\\*\\\*
> Feed this entire document as your system/context prompt to your AI coding agent
> (Claude Code, OpenCode, Cursor, etc.). The agent should read every section before
> writing a single line of code. Sections marked \\\[CRITICAL] must be followed exactly.
> Do not skip or summarize sections — each one has build decisions the code depends on.

\---

## SECTION 00 — PROJECT IDENTITY

**App Name:** PureTwitch (working title, can be changed)
**Tagline:** Twitch. No ads. No nonsense.

**What you are building:**
A Kotlin Multiplatform (KMP) + Compose Multiplatform project that produces three
separate artifacts from a single shared codebase:

1. `app-android` — Android phone/tablet APK (Jetpack Compose UI)
2. `app-tv` — Android TV APK (Compose for TV + Leanback navigation)
3. `app-windows` — Windows desktop EXE/MSI (Compose for Desktop + VLCJ)

All three share one core module (`core/`) containing:

* Twitch API client
* Stream URL resolver (Streamlink-equivalent logic)
* Ad-block engine (HLS playlist proxy + segment filtering)
* Chat IRC client
* Data models and repositories

**Distribution:** Sideload only. No Google Play, no Microsoft Store. GitHub Releases
for APKs and a Windows installer. This avoids Twitch ToS enforcement via store policies.

**Primary goals in order:**

1. Block all Twitch ads (pre-roll and mid-roll) reliably
2. Low-latency stream playback with adaptive bitrate
3. Full Twitch chat with BTTV/7TV/FFZ emote support
4. Clean TV 10-foot UI navigable by D-pad/remote
5. Windows desktop viewer as a bonus target

\---

## SECTION 01 — MONOREPO PROJECT STRUCTURE \[CRITICAL]

Create this exact Gradle multi-module structure. Do not deviate.

```
PureTwitch/
├── gradle/
│   └── libs.versions.toml            ← Version catalog (ALL deps defined here)
├── build.gradle.kts                   ← Root build file
├── settings.gradle.kts                ← Module declarations
│
├── core/                              ← KMP shared logic (no UI)
│   ├── build.gradle.kts
│   └── src/
│       ├── commonMain/kotlin/
│       │   └── com/puretwitch/core/
│       │       ├── api/               ← Twitch Helix REST + GQL clients
│       │       ├── stream/            ← Stream URL resolver + token fetcher
│       │       ├── adblock/           ← HLS playlist proxy + ad segment engine
│       │       ├── chat/              ← Twitch IRC WebSocket client
│       │       ├── emotes/            ← BTTV / 7TV / FFZ API clients
│       │       ├── model/             ← Data models (Stream, Channel, Message, etc.)
│       │       ├── repository/        ← Repos wrapping API + DB
│       │       └── di/                ← Koin module definitions
│       ├── androidMain/kotlin/        ← Android-specific implementations
│       └── desktopMain/kotlin/        ← Desktop (JVM) specific implementations
│
├── app-android/                       ← Phone + Tablet APK
│   ├── build.gradle.kts
│   └── src/main/
│       ├── kotlin/com/puretwitch/android/
│       │   ├── ui/                    ← Compose screens
│       │   ├── player/                ← ExoPlayer wrapper
│       │   └── MainActivity.kt
│       └── res/
│
├── app-tv/                            ← Android TV APK
│   ├── build.gradle.kts
│   └── src/main/
│       ├── kotlin/com/puretwitch/tv/
│       │   ├── ui/                    ← Compose for TV screens
│       │   ├── player/                ← ExoPlayer TV wrapper
│       │   └── TvMainActivity.kt
│       └── res/
│
├── app-windows/                       ← Windows Desktop
│   ├── build.gradle.kts
│   └── src/main/kotlin/com/puretwitch/desktop/
│       ├── ui/                        ← Compose Desktop screens
│       ├── player/                    ← VLCJ player wrapper
│       └── main.kt
│
└── proxy-server/                      ← Optional self-hosted Docker proxy (Go)
    ├── Dockerfile
    ├── main.go
    └── README.md
```

\---

## SECTION 02 — VERSION CATALOG \[CRITICAL]

Create `gradle/libs.versions.toml` with these exact versions. Do not use newer
versions unless a build error requires it — version mismatches are the #1 failure mode.

```toml
\\\[versions]
kotlin = "2.0.21"
compose-multiplatform = "1.7.0"
agp = "8.7.0"
koin = "4.0.0"
ktor = "3.0.1"
coroutines = "1.9.0"
serialization = "1.7.3"
room = "2.7.0"
datastore = "1.1.1"
exoplayer = "1.4.1"
leanback = "1.2.0"
compose-tv = "1.0.0"
vlcj = "4.8.3"
okio = "3.9.1"
workmanager = "2.10.0"
coil = "3.0.4"

\\\[libraries]
# Kotlin
kotlin-stdlib = { module = "org.jetbrains.kotlin:kotlin-stdlib", version.ref = "kotlin" }

# Compose Multiplatform
compose-ui = { module = "org.jetbrains.compose.ui:ui", version.ref = "compose-multiplatform" }
compose-material3 = { module = "org.jetbrains.compose.material3:material3", version.ref = "compose-multiplatform" }
compose-foundation = { module = "org.jetbrains.compose.foundation:foundation", version.ref = "compose-multiplatform" }
compose-runtime = { module = "org.jetbrains.compose.runtime:runtime", version.ref = "compose-multiplatform" }

# Koin DI
koin-core = { module = "io.insert-koin:koin-core", version.ref = "koin" }
koin-android = { module = "io.insert-koin:koin-android", version.ref = "koin" }
koin-compose = { module = "io.insert-koin:koin-compose", version.ref = "koin" }

# Ktor (networking in KMP)
ktor-client-core = { module = "io.ktor:ktor-client-core", version.ref = "ktor" }
ktor-client-okhttp = { module = "io.ktor:ktor-client-okhttp", version.ref = "ktor" }
ktor-client-websockets = { module = "io.ktor:ktor-client-websockets", version.ref = "ktor" }
ktor-client-content-negotiation = { module = "io.ktor:ktor-client-content-negotiation", version.ref = "ktor" }
ktor-serialization-json = { module = "io.ktor:ktor-serialization-kotlinx-json", version.ref = "ktor" }
ktor-client-logging = { module = "io.ktor:ktor-client-logging", version.ref = "ktor" }

# Serialization
kotlinx-serialization-json = { module = "org.jetbrains.kotlinx:kotlinx-serialization-json", version.ref = "serialization" }

# Coroutines
kotlinx-coroutines-core = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-core", version.ref = "coroutines" }
kotlinx-coroutines-android = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-android", version.ref = "coroutines" }

# Room (Android only)
room-runtime = { module = "androidx.room:room-runtime", version.ref = "room" }
room-ktx = { module = "androidx.room:room-ktx", version.ref = "room" }
room-compiler = { module = "androidx.room:room-compiler", version.ref = "room" }

# DataStore (Android only)
datastore-preferences = { module = "androidx.datastore:datastore-preferences", version.ref = "datastore" }

# ExoPlayer / Media3 (Android only)
media3-exoplayer = { module = "androidx.media3:media3-exoplayer", version.ref = "exoplayer" }
media3-exoplayer-hls = { module = "androidx.media3:media3-exoplayer-hls", version.ref = "exoplayer" }
media3-ui = { module = "androidx.media3:media3-ui", version.ref = "exoplayer" }
media3-datasource-okhttp = { module = "androidx.media3:media3-datasource-okhttp", version.ref = "exoplayer" }

# Android TV
leanback = { module = "androidx.leanback:leanback", version.ref = "leanback" }
compose-tv-foundation = { module = "androidx.tv:tv-foundation", version.ref = "compose-tv" }
compose-tv-material = { module = "androidx.tv:tv-material", version.ref = "compose-tv" }

# VLCJ (Desktop/Windows only)
vlcj = { module = "uk.co.caprica:vlcj", version.ref = "vlcj" }

# WorkManager (Android only)
workmanager = { module = "androidx.work:work-runtime-ktx", version.ref = "workmanager" }

# Image loading
coil-compose = { module = "io.coil-kt.coil3:coil-compose", version.ref = "coil" }
coil-network = { module = "io.coil-kt.coil3:coil-network-ktor", version.ref = "coil" }

\\\[plugins]
kotlin-multiplatform = { id = "org.jetbrains.kotlin.multiplatform", version.ref = "kotlin" }
kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
compose-multiplatform = { id = "org.jetbrains.compose", version.ref = "compose-multiplatform" }
android-application = { id = "com.android.application", version.ref = "agp" }
android-library = { id = "com.android.library", version.ref = "agp" }
ksp = { id = "com.google.devtools.ksp", version = "2.0.21-1.0.28" }
```

\---

## SECTION 03 — TWITCH API INTEGRATION \[CRITICAL]

### 3.1 Credentials Setup

Create `core/src/commonMain/kotlin/com/puretwitch/core/api/TwitchConfig.kt`:

```kotlin
object TwitchConfig {
    // Register a Twitch app at https://dev.twitch.tv/console
    // Set OAuth Redirect URI to: http://localhost:3000
    // Use Authorization Code + PKCE flow (no client secret needed)
    const val CLIENT\\\_ID = "YOUR\\\_CLIENT\\\_ID\\\_HERE"
    const val REDIRECT\\\_URI = "http://localhost:3000"
    const val SCOPES = "user:read:follows chat:read chat:edit"

    const val API\\\_BASE = "https://api.twitch.tv/helix"
    const val AUTH\\\_BASE = "https://id.twitch.tv/oauth2"
    const val IRC\\\_ENDPOINT = "wss://irc-ws.chat.twitch.tv:443"
    const val GQL\\\_ENDPOINT = "https://gql.twitch.tv/gql"

    // GQL client ID — this is the web client ID Twitch uses publicly
    // It is not a secret; it is sent from every browser session
    const val GQL\\\_CLIENT\\\_ID = "kimne78kx3ncx6brgo4mv6wki5h1ko"
}
```

### 3.2 OAuth Flow

**Android/TV:** Use Chrome Custom Tab to open the Twitch auth URL. Handle the redirect
via an intent-filter on a custom URI scheme `puretwitch://auth`. Extract the `code`
param and exchange it for a token via the Helix token endpoint.

**Windows Desktop:** Spin up a local `HttpServer` on port 3000 using Ktor, open the
system browser to the auth URL, wait for the redirect callback, extract the code,
then shut down the local server.

Both flows must implement PKCE (code\_verifier + code\_challenge). Store tokens in:

* Android/TV: EncryptedSharedPreferences (androidx.security)
* Windows: System credential store via `java.awt.Desktop` is NOT sufficient;
write to `%APPDATA%/PureTwitch/tokens.enc` encrypted with a machine-derived key.

### 3.3 Helix API Client

Build a `TwitchApiClient` using Ktor with:

* Auto-refreshing token interceptor (refresh 5 minutes before expiry)
* Rate limit awareness (429 → backoff + retry with jitter)
* Required endpoints to implement:

  * `GET /streams` — live streams, by game or user
  * `GET /users` — user info
  * `GET /users/follows` — followed channels
  * `GET /games/top` — top categories
  * `GET /search/channels` — search
  * `GET /streams?user\\\_login=` — check if specific channel is live

### 3.4 GQL Stream Token \[CRITICAL]

The Helix API does NOT give you a playable stream URL. You must use GQL:

```kotlin
suspend fun fetchStreamToken(channelLogin: String, oauthToken: String?): StreamToken {
    val query = """
        {
          "operationName": "PlaybackAccessToken",
          "variables": {
            "isLive": true,
            "login": "$channelLogin",
            "isVod": false,
            "vodID": "",
            "playerType": "site"
          },
          "extensions": {
            "persistedQuery": {
              "version": 1,
              "sha256Hash": "0828119ded1c13477966434e15800ff57ddacf13ba1911c129dc2200705b0712"
            }
          }
        }
    """.trimIndent()

    // POST to GQL\\\_ENDPOINT
    // Headers: Client-ID: GQL\\\_CLIENT\\\_ID
    // If user is logged in, also add: Authorization: OAuth <token>
    // Response contains: data.streamPlaybackAccessToken.value and .signature
}
```

Once you have the token and signature, construct the m3u8 URL:

```
https://usher.twitchsvc.net/api/channel/hls/{channelLogin}.m3u8
  ?sig={signature}
  \\\&token={urlEncodedToken}
  \\\&allow\\\_source=true
  \\\&allow\\\_spectre=false
  \\\&fast\\\_bread=true
  \\\&p={random6DigitNumber}
  \\\&player\\\_backend=mediaplayer
  \\\&playlist\\\_include\\\_framerate=true
  \\\&reassignments\\\_supported=true
  \\\&sig={signature}
  \\\&supported\\\_codecs=avc1
  \\\&transcode\\\_mode=cbr\\\_v1
```

This returns a master playlist listing quality variants. Parse it and let the user
pick quality (Source, 1080p60, 720p60, 480p, 360p, Audio Only).

\---

## SECTION 04 — AD BLOCK ENGINE \[CRITICAL — MOST IMPORTANT SECTION]

This is the core feature. Implement all three strategies in a fallback chain.

### 4.1 Architecture

```kotlin
// core/src/commonMain/kotlin/com/puretwitch/core/adblock/AdBlockEngine.kt

class AdBlockEngine(
    private val config: AdBlockConfig,
    private val httpClient: HttpClient
) {
    // Returns a clean m3u8 URL that ExoPlayer/VLC can play without ads
    suspend fun resolveCleanStream(
        masterPlaylistUrl: String,
        quality: StreamQuality
    ): CleanStreamResult

    // Called on every playlist refresh (every \\\~2 seconds for live streams)
    suspend fun filterPlaylist(playlistContent: String): FilteredPlaylist
}

data class AdBlockConfig(
    val strategy: AdBlockStrategy = AdBlockStrategy.PROXY\\\_PRIMARY,
    val proxyEndpoint: String = "https://api.ttv.lol/playlist",  // TTV LOL PRO compatible
    val customProxyEndpoint: String? = null,  // Self-hosted proxy
    val fallbackToManifestRewrite: Boolean = true,
    val fallbackToBlackFrame: Boolean = true
)

enum class AdBlockStrategy {
    PROXY\\\_PRIMARY,          // Route playlist through proxy server first
    MANIFEST\\\_REWRITE\\\_ONLY,  // Only strip tags locally (less reliable)
    DISABLED                // No ad blocking (for debugging)
}
```

### 4.2 Strategy 1 — Proxy Router (Primary)

Route the m3u8 playlist URL through a proxy server that fetches it from an
ad-light region. The actual video segments still come directly from Twitch CDN.

```kotlin
// Request format for TTV LOL PRO compatible proxies:
// GET https://api.ttv.lol/playlist/{base64EncodedPlaylistUrl}
// Headers:
//   X-Donate-To: https://ttv.lol/donate
//
// The proxy fetches the playlist from Twitch using a server IP in an ad-light
// region (Poland, Lithuania) and returns it clean.
//
// IMPORTANT: Only the \\\~2KB playlist file goes through the proxy.
// The actual video segments are fetched directly from Twitch CDN.
// This means:
//   - Minimal latency impact
//   - No bandwidth cost on the proxy for actual video data
//   - If proxy goes down, fall through to Strategy 2

suspend fun fetchViaProxy(playlistUrl: String): String? {
    val encoded = Base64.encode(playlistUrl.toByteArray())
    return try {
        httpClient.get("${config.proxyEndpoint}/$encoded") {
            header("X-Donate-To", "https://ttv.lol/donate")
        }.bodyAsText()
    } catch (e: Exception) {
        null  // Proxy unavailable, fall through
    }
}
```

### 4.3 Strategy 2 — Manifest Rewriter (Fallback)

If the proxy is down or rate-limited, rewrite the playlist locally to strip ad markers:

```kotlin
fun rewritePlaylist(rawPlaylist: String): String {
    val lines = rawPlaylist.lines().toMutableList()
    val cleaned = mutableListOf<String>()
    var inAdBreak = false

    for (line in lines) {
        when {
            // Twitch marks ad segments with these tags:
            line.startsWith("#EXT-X-DATERANGE") \\\&\\\&
            line.contains("CLASS=\\\\"twitch-stitched-ad\\\\"") -> {
                inAdBreak = true
                // Skip this tag — don't add to output
            }
            line.startsWith("#EXT-X-CUE-OUT") -> {
                inAdBreak = true
                // Skip
            }
            line.startsWith("#EXT-X-CUE-IN") -> {
                inAdBreak = false
                // Skip — the next segment should be clean content
            }
            line.startsWith("#EXT-X-DISCONTINUITY") \\\&\\\& inAdBreak -> {
                // Skip discontinuity markers during ad break
            }
            inAdBreak \\\&\\\& !line.startsWith("#") -> {
                // This is an ad segment URL — skip it
                // ExoPlayer will stall briefly but resume on the next clean segment
            }
            else -> cleaned.add(line)
        }
    }

    return cleaned.joinToString("\\\\n")
}
```

### 4.4 Strategy 3 — Black Frame Fallback (Last Resort)

If both strategies above fail to fully remove an ad, display a black frame with
a "Blocking Ad..." overlay instead of showing the ad. This is the least desirable
but ensures the user never sees ad content.

Implement in the player layer (ExoPlayer / VLCJ) by:

* Monitoring segment duration anomalies (ad segments are often exactly 2.0s)
* Checking for `#EXT-X-DISCONTINUITY` pairs (always bracket ad insertions)
* When detected: mute audio + render black frame overlay

### 4.5 Self-Hosted Proxy (Optional Power User Feature)

Include a minimal Go proxy server in `proxy-server/`:

```go
// proxy-server/main.go
// A lightweight proxy that fetches Twitch m3u8 playlists and returns them
// with ad segments stripped. Runs on port 8888 by default.
//
// The user runs: docker run -p 8888:8888 puretwitch/proxy
// Then sets Custom Proxy Endpoint in app settings to: http://localhost:8888/playlist
//
// This is fully private — no third-party proxy dependency.
```

The proxy should:

1. Accept `GET /playlist?url={encodedUrl}\\\&sig={sig}\\\&token={token}`
2. Fetch the m3u8 from Twitch (using a VPN-like regional IP header trick if available)
3. Apply the manifest rewriter (same logic as Section 4.3)
4. Return the cleaned playlist
5. Pass video segment requests directly (NOT proxied — too much bandwidth)

\---

## SECTION 05 — CHAT ENGINE

### 5.1 IRC WebSocket Client

```kotlin
// core/src/commonMain/kotlin/com/puretwitch/core/chat/TwitchChatClient.kt

class TwitchChatClient(private val httpClient: HttpClient) {
    // Connect: wss://irc-ws.chat.twitch.tv:443
    // Auth sequence (send in order):
    //   CAP REQ :twitch.tv/tags twitch.tv/commands twitch.tv/membership
    //   PASS oauth:{accessToken}   ← omit if anonymous
    //   NICK {username}            ← use "justinfan{random}" for anonymous
    //   JOIN #{channelName}

    // Parse these IRC message types:
    // PRIVMSG   → regular chat message
    // USERNOTICE → sub, resub, giftsub, raid
    // CLEARCHAT → timeout or ban (handle gracefully)
    // CLEARMSG  → single message deleted
    // ROOMSTATE → slow mode, emote-only mode flags
    // GLOBALUSERSTATE → your own user badges/color

    // Emit as Flow<ChatEvent> to the UI layer
    val events: Flow<ChatEvent>

    suspend fun connect(channel: String, token: String?)
    suspend fun disconnect()
    suspend fun sendMessage(channel: String, message: String)
}

// Parsing: IRC messages with Twitch tags look like:
// @badge-info=subscriber/12;badges=broadcaster/1;color=#FF0000;display-name=User;
// emotes=25:0-4,12-16;id=uuid;...;tmi-sent-ts=1234567 :user!user@user.tmi.twitch.tv
// PRIVMSG #channel :Hello Kappa
//
// Parse the @key=value pairs first, then standard IRC format
```

### 5.2 Chat Data Model

```kotlin
data class ChatMessage(
    val id: String,
    val username: String,
    val displayName: String,
    val color: String,          // Hex color like "#FF0000"
    val message: String,
    val parsedParts: List<MessagePart>,  // Text + emotes interleaved
    val badges: List<Badge>,
    val timestamp: Long,
    val isSubscriber: Boolean,
    val isModerator: Boolean,
    val isBroadcaster: Boolean
)

sealed class MessagePart {
    data class Text(val content: String) : MessagePart()
    data class TwitchEmote(val id: String, val name: String) : MessagePart()
    data class ThirdPartyEmote(val url: String, val name: String, val provider: EmoteProvider) : MessagePart()
}

enum class EmoteProvider { BTTV, FFZ, SEVENTV }
```

### 5.3 Emote Loading

Fetch and cache emotes from all three providers for each channel:

```kotlin
// BTTV Global:  GET https://api.betterttv.net/3/cached/emotes/global
// BTTV Channel: GET https://api.betterttv.net/3/cached/users/twitch/{channelId}
// FFZ Channel:  GET https://api.frankerfacez.com/v1/room/{channelLogin}
// 7TV Global:   GET https://7tv.io/v3/emote-sets/global
// 7TV Channel:  GET https://7tv.io/v3/users/twitch/{channelId}

// Cache strategy:
// - Store emote metadata in Room DB (Android) / SQLite (Desktop)
// - Cache emote images via Coil's disk cache
// - Refresh channel emotes on each channel join
// - Refresh global emotes once per app session
```

\---

## SECTION 06 — ANDROID APP (app-android) \[CRITICAL]

### 6.1 Manifest

```xml
<!-- app-android/src/main/AndroidManifest.xml -->
<manifest>
    <uses-permission android:name="android.permission.INTERNET"/>
    <uses-permission android:name="android.permission.FOREGROUND\\\_SERVICE"/>
    <uses-permission android:name="android.permission.FOREGROUND\\\_SERVICE\\\_MEDIA\\\_PLAYBACK"/>

    <application
        android:name=".PureTwitchApp"
        android:theme="@style/Theme.PureTwitch">

        <activity android:name=".MainActivity"
            android:configChanges="orientation|screenSize|screenLayout"
            android:launchMode="singleTop">
            <!-- Deep link for OAuth callback -->
            <intent-filter>
                <action android:name="android.intent.action.VIEW"/>
                <category android:name="android.intent.category.DEFAULT"/>
                <category android:name="android.intent.category.BROWSABLE"/>
                <data android:scheme="puretwitch" android:host="auth"/>
            </intent-filter>
        </activity>

    </application>
</manifest>
```

### 6.2 Navigation Structure

Use Jetpack Navigation Compose. Define these routes:

```
HomeScreen         → Followed channels + Featured streams grid
BrowseScreen       → Categories / games grid
SearchScreen       → Search channels + games
StreamScreen       → Full-screen player + chat sidebar
ChannelScreen      → Channel profile + recent clips
SettingsScreen     → Quality, proxy config, ad-block mode, account
LoginScreen        → OAuth entry point
```

### 6.3 ExoPlayer Setup \[CRITICAL]

```kotlin
// app-android/src/main/kotlin/.../player/TwitchPlayer.kt

class TwitchPlayer(
    private val context: Context,
    private val adBlockEngine: AdBlockEngine
) {
    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(AdBlockInterceptor(adBlockEngine))  // KEY — intercept playlist requests
        .build()

    val exoPlayer: ExoPlayer = ExoPlayer.Builder(context)
        .setMediaSourceFactory(
            DefaultMediaSourceFactory(context)
                .setDataSourceFactory(
                    OkHttpDataSource.Factory(okHttpClient)
                )
        )
        .setLoadControl(
            DefaultLoadControl.Builder()
                .setBufferDurationsMs(
                    /\\\* minBuffer  \\\*/ 2\\\_000,
                    /\\\* maxBuffer  \\\*/ 15\\\_000,
                    /\\\* playback   \\\*/ 500,
                    /\\\* rebuffer   \\\*/ 1\\\_000
                )
                .build()
        )
        .build()
        .apply {
            playWhenReady = true
            // Enable low-latency live mode
            setLivePlaybackSpeedControl(
                DefaultLivePlaybackSpeedControl.Builder()
                    .setFallbackMaxPlaybackSpeed(1.04f)
                    .build()
            )
        }
}

// The interceptor is where ad blocking happens for Strategy 1 + 2:
class AdBlockInterceptor(private val adBlockEngine: AdBlockEngine) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val url = request.url.toString()

        // Only intercept Twitch HLS playlist requests (not segment files)
        if (!url.contains("usher.twitchsvc.net") \\\&\\\& !url.endsWith(".m3u8")) {
            return chain.proceed(request)
        }

        // Try proxy strategy first
        val cleanPlaylist = runBlocking { adBlockEngine.resolveCleanStream(url) }
        if (cleanPlaylist != null) {
            return Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP\\\_1\\\_1)
                .code(200)
                .message("OK")
                .body(cleanPlaylist.toResponseBody("application/x-mpegurl".toMediaType()))
                .build()
        }

        // Fall through to actual request (manifest rewrite applied server-side or in engine)
        return chain.proceed(request)
    }
}
```

### 6.4 StreamScreen Layout

```kotlin
@Composable
fun StreamScreen(channelLogin: String) {
    // Portrait: player takes 40% height, chat takes 60% below
    // Landscape: player takes 70% width (left), chat sidebar takes 30% (right)
    // Fullscreen gesture: double-tap hides chat entirely

    val isLandscape = LocalConfiguration.current.orientation == ORIENTATION\\\_LANDSCAPE

    if (isLandscape) {
        Row(Modifier.fillMaxSize().background(Color.Black)) {
            PlayerView(Modifier.weight(0.7f))
            ChatPanel(Modifier.weight(0.3f))
        }
    } else {
        Column(Modifier.fillMaxSize().background(Color.Black)) {
            PlayerView(Modifier.fillMaxWidth().aspectRatio(16f/9f))
            StreamInfo(channelLogin)  // Title, game, viewer count
            ChatPanel(Modifier.weight(1f))
        }
    }
}
```

### 6.5 Picture in Picture

Enable PiP mode when user presses home during stream:

```kotlin
override fun onUserLeaveHint() {
    if (isStreaming) {
        enterPictureInPictureMode(
            PictureInPictureParams.Builder()
                .setAspectRatio(Rational(16, 9))
                .build()
        )
    }
}
```

\---

## SECTION 07 — ANDROID TV APP (app-tv) \[CRITICAL]

### 7.1 TV Manifest

```xml
<manifest>
    <!-- Required for TV discovery -->
    <uses-feature android:name="android.hardware.touchscreen" android:required="false"/>
    <uses-feature android:name="android.software.leanback" android:required="true"/>

    <application android:banner="@drawable/banner">  <!-- 320x180 banner image -->
        <activity android:name=".TvMainActivity"
            android:theme="@style/Theme.Leanback">
            <!-- Required for TV launcher -->
            <intent-filter>
                <action android:name="android.intent.action.MAIN"/>
                <category android:name="android.intent.category.LEANBACK\\\_LAUNCHER"/>
            </intent-filter>
        </activity>
    </application>
</manifest>
```

### 7.2 TV Navigation Model

Use `androidx.tv:tv-material` for the shell. The navigation must be FULLY
operable with D-pad only (no touch required):

```
TvMainActivity
└── TvNavDrawer (left side, collapsible)
    ├── "Live Channels"   → TvBrowseScreen
    ├── "Following"       → TvFollowingScreen
    ├── "Categories"      → TvCategoriesScreen
    └── "Settings"        → TvSettingsScreen

TvStreamScreen
└── ImmersiveFullscreen
    ├── PlayerSurface (full screen)
    ├── TvControlsOverlay (auto-hide after 3s of no input)
    │   ├── Quality selector (focusable row)
    │   ├── Stream title + viewer count
    │   └── Back button
    └── TvChatOverlay (right side, semi-transparent, toggled with MENU button)
```

### 7.3 TV Focus Management \[CRITICAL]

This is the hardest part of TV development. Rules to follow:

```kotlin
// 1. Every interactive element must have a defined focus state
// 2. Use Modifier.focusable() + indication for visual focus feedback
// 3. Define explicit focusRestorer() on lists so focus returns to last position
// 4. D-pad LEFT on leftmost item should open the nav drawer (not do nothing)
// 5. BACK button should: dismiss overlays → exit stream → go to home → exit app
// 6. DPAD\\\_CENTER / ENTER = confirm. Never use onClick alone without key handler.

// Use this pattern for TV cards:
@Composable
fun TvStreamCard(stream: Stream, onClick: () -> Unit) {
    val focusRequester = remember { FocusRequester() }
    var isFocused by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .focusRequester(focusRequester)
            .onFocusChanged { isFocused = it.isFocused }
            .scale(if (isFocused) 1.08f else 1.0f)  // Scale on focus — TV convention
            .clickable(onClick = onClick),
        border = CardDefaults.border(
            focusedBorder = Border(BorderStroke(2.dp, Color(0xFF9B5DE5)))
        )
    ) {
        // Thumbnail + title + viewer count
    }
}
```

### 7.4 TV Stream Screen

```kotlin
// The TV stream screen should:
// - Start in fullscreen immediately (no chrome)
// - Show controls overlay on any D-pad input
// - Auto-hide controls after 3 seconds of inactivity
// - DPAD\\\_RIGHT on player when chat is hidden → show chat
// - DPAD\\\_LEFT when chat focused → return focus to player controls
// - Remote FAST\\\_FORWARD / REWIND → change stream quality up/down
// - Remote PLAY/PAUSE → actually pause/resume stream
// - Long-press BACK → exit to home (not just dismiss overlay)
```

\---

## SECTION 08 — WINDOWS DESKTOP APP (app-windows) \[CRITICAL]

### 8.1 Main Entry Point

```kotlin
// app-windows/src/main/kotlin/com/puretwitch/desktop/main.kt

fun main() = application {
    val windowState = rememberWindowState(
        width = 1280.dp,
        height = 720.dp,
        placement = WindowPlacement.Floating
    )

    Window(
        onCloseRequest = ::exitApplication,
        state = windowState,
        title = "PureTwitch",
        icon = painterResource("icon.ico"),
        undecorated = false,
    ) {
        App()  // Compose Desktop root composable
    }
}
```

### 8.2 VLCJ Player Integration \[CRITICAL]

VLC must be installed on the user's Windows machine (download from videolan.org).
VLCJ discovers it via the system `PATH` or a config override.

```kotlin
// app-windows/src/main/kotlin/.../player/VlcPlayer.kt

class VlcPlayer(private val adBlockEngine: AdBlockEngine) {
    private val factory = MediaPlayerFactory(
        "--no-video-title-show",
        "--network-caching=2000",   // 2s buffer for low latency
        "--live-caching=1000",
        "--sout-mux-caching=500",
    )
    private val mediaPlayer: EmbeddedMediaPlayer = factory.mediaPlayers().newEmbeddedMediaPlayer()

    // VLCJ plays the m3u8 directly
    // Ad blocking happens by preprocessing the playlist URL:
    // Instead of giving VLC the raw Twitch URL, give it a local proxy URL
    // that your app serves on localhost:
    //
    //   http://localhost:7979/stream?channel={channelLogin}\\\&token={token}
    //
    // Your local Ktor server handles: token fetch → proxy route → return clean m3u8
    // VLC then fetches clean segments directly from Twitch CDN

    fun play(channel: String, quality: String) {
        val localProxyUrl = "http://localhost:7979/stream?channel=$channel\\\&quality=$quality"
        mediaPlayer.media().play(localProxyUrl)
    }

    fun attachToPanel(panel: java.awt.Panel) {
        mediaPlayer.videoSurface().set(
            VideoSurfaceFactory().newVideoSurface(panel)
        )
    }
}
```

### 8.3 Local Proxy Server (Windows-specific)

```kotlin
// app-windows/src/main/kotlin/.../player/LocalStreamProxy.kt
// Ktor embedded server on localhost:7979
// Serves clean m3u8 playlists to VLCJ

fun startLocalProxyServer(adBlockEngine: AdBlockEngine, streamResolver: StreamResolver) {
    embeddedServer(Netty, port = 7979) {
        routing {
            get("/stream") {
                val channel = call.parameters\\\["channel"]!!
                val quality = call.parameters\\\["quality"] ?: "best"

                // 1. Fetch GQL stream token
                val token = streamResolver.fetchToken(channel)

                // 2. Build master playlist URL
                val masterUrl = streamResolver.buildMasterUrl(channel, token)

                // 3. Route through ad-block engine
                val cleanPlaylist = adBlockEngine.resolveCleanStream(masterUrl, quality)

                // 4. Rewrite segment URLs to go through this local proxy too
                // (so we can monitor and filter mid-roll ads during playback)
                val rewrittenPlaylist = rewriteSegmentUrls(cleanPlaylist, "http://localhost:7979")

                call.respondText(rewrittenPlaylist, ContentType.parse("application/x-mpegurl"))
            }

            get("/segment") {
                // Proxy individual segment requests if needed for ad detection
                // Usually not needed — VLCJ fetches segments directly from CDN
            }
        }
    }.start(wait = false)
}
```

### 8.4 Windows UI Layout

```kotlin
// Two-panel layout: left nav + main content, with detachable stream window

@Composable
fun DesktopApp() {
    Row(Modifier.fillMaxSize().background(Color(0xFF0A0A0F))) {

        // Left navigation sidebar (collapsible, 200dp when open, 60dp when collapsed)
        NavigationSidebar(Modifier.width(if (navExpanded) 200.dp else 60.dp))

        // Main content area
        Box(Modifier.weight(1f)) {
            when (currentScreen) {
                Screen.Home -> HomeContent()
                Screen.Browse -> BrowseContent()
                Screen.Stream -> StreamContent()  // Player + chat side by side
            }
        }
    }
}

// Stream view on desktop:
// Player on left (resizable), chat on right (fixed 320dp or user-draggable)
// Support picture-in-picture: detach player into a separate always-on-top Window
// Theater mode: hide sidebar, expand player
// Fullscreen: F11 toggles, ESC exits
```

\---

## SECTION 09 — DATA LAYER

### 9.1 Room Database (Android/TV)

```kotlin
@Database(
    entities = \\\[
        CachedStream::class,
        CachedChannel::class,
        CachedEmote::class,
        WatchHistory::class,
        SearchHistory::class,
    ],
    version = 1,
    exportSchema = true
)
abstract class PureTwitchDatabase : RoomDatabase() {
    abstract fun streamDao(): StreamDao
    abstract fun channelDao(): ChannelDao
    abstract fun emoteDao(): EmoteDao
    abstract fun historyDao(): HistoryDao
}
```

### 9.2 DataStore Settings Schema

```kotlin
// These settings are available in all three apps
data class AppSettings(
    // Playback
    val preferredQuality: String = "auto",        // auto, 1080p60, 720p60, 480p, 360p
    val lowLatencyMode: Boolean = true,

    // Ad Block
    val adBlockEnabled: Boolean = true,
    val adBlockStrategy: String = "proxy",         // proxy, rewrite, disabled
    val customProxyUrl: String = "",               // empty = use default TTV LOL PRO compatible

    // Chat
    val chatEnabled: Boolean = true,
    val chatFontSize: Float = 14f,
    val showBadges: Boolean = true,
    val showBttvEmotes: Boolean = true,
    val show7tvEmotes: Boolean = true,
    val showFfzEmotes: Boolean = true,
    val chatTimestamps: Boolean = false,

    // UI
    val theme: String = "dark",                    // dark, darker (amoled), purple
    val compactMode: Boolean = false,              // smaller thumbnails, denser layout

    // Account
    val accessToken: String = "",                  // empty = anonymous viewer
    val username: String = "",
    val userId: String = "",
)
```

### 9.3 WorkManager Jobs (Android/TV only)

```kotlin
// Schedule these recurring jobs:
// 1. TokenRefreshWorker    — runs 5 min before OAuth token expiry
// 2. EmoteCacheSyncWorker  — refreshes global emotes once every 24h
// 3. ProxyHealthWorker     — pings proxy endpoint every 30 min, logs latency
//    If proxy is unhealthy for 3 consecutive checks → switch to manifest rewrite strategy
//    Notify user via NotificationCompat that ad-block strategy has changed
```

\---

## SECTION 10 — UI DESIGN SYSTEM

Apply this design system consistently across all three apps.

### 10.1 Color Palette

```kotlin
object PureTwitchColors {
    // Backgrounds
    val Background = Color(0xFF0A0A0F)      // Near-black base
    val Surface = Color(0xFF141420)          // Card surfaces
    val SurfaceVariant = Color(0xFF1E1E2E)   // Elevated surfaces

    // Brand
    val TwitchPurple = Color(0xFF9B5DE5)     // Primary brand
    val TwitchPurpleLight = Color(0xFFC77DFF) // Light variant
    val AdBlockGreen = Color(0xFF06D6A0)     // Used for "Ad blocked" indicator

    // Text
    val TextPrimary = Color(0xFFE8E8F0)
    val TextSecondary = Color(0xFF888899)
    val TextMuted = Color(0xFF555566)

    // Semantic
    val Live = Color(0xFFE53935)             // Red "LIVE" badge
    val Online = Color(0xFF43A047)           // Green dot
    val Warning = Color(0xFFFFB703)
}
```

### 10.2 Typography

```kotlin
// Use Google Fonts: "Space Grotesk" for display, "JetBrains Mono" for chat
// Load via downloadable fonts on Android, bundled TTF on Desktop

val PureTwitchTypography = Typography(
    headlineLarge = TextStyle(fontFamily = SpaceGrotesk, fontSize = 28.sp, fontWeight = FontWeight.Bold),
    headlineMedium = TextStyle(fontFamily = SpaceGrotesk, fontSize = 22.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = TextStyle(fontFamily = SpaceGrotesk, fontSize = 18.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontFamily = SpaceGrotesk, fontSize = 15.sp),
    bodyMedium = TextStyle(fontFamily = SpaceGrotesk, fontSize = 13.sp),
    labelSmall = TextStyle(fontFamily = SpaceGrotesk, fontSize = 10.sp, letterSpacing = 1.sp),
    // Chat-specific — monospace for readability at small sizes
    // Reference as: MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMono)
)
```

### 10.3 Ad Block Status Indicator

Display a small, unobtrusive pill in the top-right of the stream view:

```kotlin
// States:
// "AD BLOCKED"   → green pill  (proxy successfully stripped ads)
// "AD FILTERED"  → yellow pill (manifest rewrite fallback active)
// "AD BLOCK OFF" → red pill    (both strategies failed)
// Show for 3 seconds when status changes, then fade out
// Tap/click the pill to open ad-block settings
```

\---

## SECTION 11 — KOIN DEPENDENCY INJECTION

Wire everything together with Koin. Define modules in `core/` that all three apps use.

```kotlin
// core/src/commonMain/kotlin/com/puretwitch/core/di/CoreModule.kt

val coreModule = module {
    // Networking
    single { buildKtorClient() }  // OkHttp engine on both Android + Desktop

    // Twitch
    single { TwitchApiClient(get(), get()) }
    single { TwitchGqlClient(get()) }
    single { StreamResolver(get(), get()) }

    // Ad Block
    single { AdBlockConfig() }  // pulled from DataStore/settings
    single { AdBlockEngine(get(), get()) }

    // Chat
    factory { TwitchChatClient(get()) }

    // Emotes
    single { EmoteRepository(get(), get()) }

    // Repositories
    single { StreamRepository(get(), get()) }
    single { ChannelRepository(get(), get()) }
    single { UserRepository(get(), get()) }
}

// Android-specific additions in app-android/di/AndroidModule.kt:
val androidModule = module {
    single { PureTwitchDatabase.build(androidContext()) }
    single { TwitchPlayer(androidContext(), get()) }
    single { DataStoreSettings(androidContext()) }
    viewModel { HomeViewModel(get(), get()) }
    viewModel { StreamViewModel(get(), get(), get()) }
    viewModel { ChatViewModel(get(), get()) }
    viewModel { SearchViewModel(get()) }
}

// Desktop-specific in app-windows/di/DesktopModule.kt:
val desktopModule = module {
    single { VlcPlayer(get()) }
    single { LocalStreamProxy(get(), get()) }
    single { DesktopSettingsStore() }
}
```

\---

## SECTION 12 — BUILD \& RELEASE CONFIGURATION

### 12.1 Android Build Config

```kotlin
// app-android/build.gradle.kts
android {
    defaultConfig {
        applicationId = "com.puretwitch.android"
        minSdk = 26       // Android 8.0 — required for ExoPlayer low-latency
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")
        }
        debug {
            applicationIdSuffix = ".debug"
            isDebuggable = true
        }
    }

    // Important: only armeabi-v7a + arm64-v8a for release (skip x86 to reduce APK size)
    defaultConfig {
        ndk { abiFilters += listOf("armeabi-v7a", "arm64-v8a") }
    }
}
```

### 12.2 Android TV Build Config

```kotlin
// app-tv/build.gradle.kts — Same as Android but:
android {
    defaultConfig {
        applicationId = "com.puretwitch.tv"
        minSdk = 26
    }
}
// app-tv shares NO UI code with app-android intentionally
// They share only core/ module
// This lets the TV UI be purpose-built for 10-foot without compromise
```

### 12.3 Desktop Build Config

```kotlin
// app-windows/build.gradle.kts
compose.desktop {
    application {
        mainClass = "com.puretwitch.desktop.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Exe)
            packageName = "PureTwitch"
            packageVersion = "1.0.0"
            windows {
                iconFile.set(file("src/main/resources/icon.ico"))
                menuGroup = "PureTwitch"
                upgradeUuid = "GENERATE-A-NEW-UUID-HERE"
                dirChooser = true
                perUserInstall = true
                shortcut = true
            }
        }

        // Bundle JVM — user does NOT need Java installed
        jvmArgs += listOf("-Xmx512m", "-Xms128m")
    }
}
```

### 12.4 ProGuard Rules

```proguard
# app-android/proguard-rules.pro
-keep class com.puretwitch.core.model.\\\*\\\* { \\\*; }
-keep class com.puretwitch.core.api.\\\*\\\* { \\\*; }
-keepclassmembers class \\\* {
    @kotlinx.serialization.Serializable \\\*;
}
-dontwarn okhttp3.\\\*\\\*
-dontwarn okio.\\\*\\\*
```

\---

## SECTION 13 — SPRINT PLAN (BUILD ORDER FOR THE AGENT)

Follow this exact order. Do not skip ahead. Each sprint must compile and run before
proceeding. Mark each sprint complete before starting the next.

```
SPRINT 1 — Foundation (target: all 3 apps launch with a blank screen)
  \\\[ ] Set up monorepo Gradle structure (Section 01)
  \\\[ ] Version catalog (Section 02)
  \\\[ ] Koin DI wired across all modules (Section 11)
  \\\[ ] All three apps build and launch: blank screen with correct background color

SPRINT 2 — Twitch API + Auth
  \\\[ ] OAuth PKCE flow on Android
  \\\[ ] OAuth flow on Desktop (localhost callback)
  \\\[ ] TwitchApiClient: /streams, /users, /users/follows
  \\\[ ] GQL stream token fetch (Section 03.4)
  \\\[ ] Store and refresh tokens

SPRINT 3 — Stream Playback (no ad blocking yet)
  \\\[ ] ExoPlayer setup on Android (Section 06.3)
  \\\[ ] VLCJ + local proxy server on Desktop (Section 08.2 + 08.3)
  \\\[ ] Playback works: pick a channel, stream plays

SPRINT 4 — Ad Block Engine
  \\\[ ] AdBlockEngine core (Section 04.1)
  \\\[ ] Strategy 1: proxy router (Section 04.2)
  \\\[ ] Strategy 2: manifest rewriter (Section 04.3)
  \\\[ ] Strategy 3: black frame fallback (Section 04.4)
  \\\[ ] AdBlockInterceptor wired into ExoPlayer (Section 06.3)
  \\\[ ] Local proxy wires in AdBlockEngine on Desktop
  \\\[ ] Test: open a stream, confirm no ads play

SPRINT 5 — Chat
  \\\[ ] TwitchChatClient IRC WebSocket (Section 05.1)
  \\\[ ] Message parsing with emote positions
  \\\[ ] BTTV/FFZ/7TV emote fetching + caching (Section 05.3)
  \\\[ ] ChatPanel composable: scrolling, emote rendering, badges
  \\\[ ] Send message (logged-in users)

SPRINT 6 — Android Phone/Tablet UI (Section 06)
  \\\[ ] HomeScreen: followed channels + top streams grid
  \\\[ ] StreamScreen: player + chat (portrait + landscape)
  \\\[ ] BrowseScreen: categories
  \\\[ ] SearchScreen
  \\\[ ] SettingsScreen (quality, proxy config)
  \\\[ ] PiP mode (Section 06.5)

SPRINT 7 — Android TV UI (Section 07)
  \\\[ ] TvMainActivity with Leanback nav drawer
  \\\[ ] TvBrowseScreen: channel rows, D-pad focus
  \\\[ ] TvStreamScreen: fullscreen + controls overlay
  \\\[ ] TvChatOverlay: toggled with MENU button
  \\\[ ] Full D-pad navigation test on real TV device or emulator

SPRINT 8 — Windows Desktop UI (Section 08)
  \\\[ ] Sidebar + main content layout
  \\\[ ] VLCJ player panel (awt embedded in Compose)
  \\\[ ] Chat panel on right
  \\\[ ] Fullscreen / theater mode (F11)
  \\\[ ] Detachable picture-in-picture window

SPRINT 9 — Polish + Release
  \\\[ ] Ad block status indicator (Section 10.3)
  \\\[ ] WorkManager jobs: token refresh, emote sync, proxy health (Section 09.3)
  \\\[ ] Error states: stream offline, network lost, proxy failed
  \\\[ ] GitHub Actions: build APKs + Windows MSI on push to main
  \\\[ ] README with sideload instructions
```

\---

## SECTION 14 — KNOWN GOTCHAS \& FAILURE MODES

Read these before writing any code. These are known failure patterns.

1. **GQL hash changes:** Twitch occasionally rotates the `sha256Hash` in the PlaybackAccessToken
query. If stream token fetching returns 400/403, the hash needs to be updated.
Mitigation: fetch the hash dynamically from Twitch's web client bundle rather than hardcoding.
2. **Proxy fingerprinting:** Twitch can detect proxy IPs and start sending ads through them.
Mitigation: rotate proxy endpoints. Implement the fallback chain (Section 04) robustly.
3. **m3u8 URL expiry:** Stream URLs expire in \~30 seconds. ExoPlayer handles this automatically
via HLS playlist refreshes, but the ad-block interceptor must handle re-fetching too.
4. **VLCJ on Windows ARM:** VLCJ 4.x does not officially support Windows ARM.
If targeting ARM Windows, use MPV via process spawn as an alternative player.
5. **ExoPlayer and signed manifests:** Twitch's signed stream manifests invalidate if any
query parameter is modified. The proxy must preserve the original token/sig exactly and
only modify the playlist content, not the URL used to fetch it.
6. **TV focus trap in dialogs:** When showing a dialog on TV, explicitly request focus on the
first focusable element. Without this, the dialog appears but the remote can't navigate it.
7. **Chat IRC rate limits:** Twitch limits non-VIP users to 20 messages per 30 seconds.
Implement a local token bucket in TwitchChatClient. Send messages through it.
8. **Desktop AWT + Compose threading:** VLCJ requires AWT Panel on the Swing EDT.
Compose Desktop renders on a different thread. Use `SwingPanel {}` composable to bridge.
Never call VLC APIs from a Compose coroutine without dispatching to Dispatchers.Main (AWT).
9. **Emote rendering:** Twitch emote images from `cdn.discordapp.com` and `cdn.7tv.app`
use animated WebP/AVIF. Coil 3 supports animated WebP on Android 28+ but not below.
Fallback to static PNG for Android < 28.
10. **Windows VLC path:** VLCJ auto-discovers VLC via registry on Windows. If the user has a
portable VLC install, it won't be found. Add a setting to manually specify VLC path.

\---

## SECTION 15 — OPTIONAL SELF-HOSTED PROXY (Go)

```go
// proxy-server/main.go
// Run with: go run main.go
// Or build Docker: docker build -t puretwitch-proxy .
// Then: docker run -p 8888:8888 puretwitch-proxy
//
// The user sets Custom Proxy Endpoint in app settings to:
//   http://<your-server-ip>:8888/playlist
//
// This proxy:
// 1. Receives GET /playlist/{base64EncodedUrl}
// 2. Fetches the m3u8 from Twitch using Go's http.Client
// 3. Strips #EXT-X-DATERANGE ad markers and #EXT-X-CUE-OUT/IN tags
// 4. Returns the clean playlist
// 5. Does NOT proxy video segments (those come from Twitch CDN directly)
//
// For best results, run this on a VPS in Poland or Lithuania.
// Hetzner Helsinki or OVH Warsaw both work well.

package main

import (
    "encoding/base64"
    "fmt"
    "io"
    "net/http"
    "strings"
)

func main() {
    http.HandleFunc("/playlist/", handlePlaylist)
    fmt.Println("PureTwitch proxy listening on :8888")
    http.ListenAndServe(":8888", nil)
}

func handlePlaylist(w http.ResponseWriter, r \\\*http.Request) {
    encoded := strings.TrimPrefix(r.URL.Path, "/playlist/")
    decoded, err := base64.StdEncoding.DecodeString(encoded)
    if err != nil {
        http.Error(w, "bad url", 400)
        return
    }

    resp, err := http.Get(string(decoded))
    if err != nil {
        http.Error(w, "fetch failed", 502)
        return
    }
    defer resp.Body.Close()

    body, \\\_ := io.ReadAll(resp.Body)
    cleaned := stripAdSegments(string(body))

    w.Header().Set("Content-Type", "application/x-mpegurl")
    w.Header().Set("Access-Control-Allow-Origin", "\\\*")
    fmt.Fprint(w, cleaned)
}

func stripAdSegments(playlist string) string {
    lines := strings.Split(playlist, "\\\\n")
    var result \\\[]string
    inAd := false

    for \\\_, line := range lines {
        switch {
        case strings.Contains(line, "EXT-X-DATERANGE") \\\&\\\& strings.Contains(line, "twitch-stitched-ad"):
            inAd = true
        case strings.HasPrefix(line, "#EXT-X-CUE-OUT"):
            inAd = true
        case strings.HasPrefix(line, "#EXT-X-CUE-IN"):
            inAd = false
        case inAd \\\&\\\& !strings.HasPrefix(line, "#"):
            // skip ad segment URL
        default:
            result = append(result, line)
        }
    }
    return strings.Join(result, "\\\\n")
}
```

\---

## FINAL AGENT INSTRUCTIONS

1. Read ALL sections before writing any code
2. Follow the Sprint Plan in Section 13 exactly — do not skip sprints
3. Every class and function should have a single responsibility
4. All network calls must be wrapped in try/catch with graceful fallback
5. The ad-block engine (Section 04) is the #1 priority feature — it must always
have a working fallback. A stream playing with ads is a failure state.
6. Test on real hardware: ExoPlayer TV emulators do not replicate D-pad focus bugs
7. When in doubt about ad-block strategy, default to proxy (Strategy 1). It is the
most reliable and has the least impact on stream quality.
8. Never store the OAuth access token in plaintext. Use EncryptedSharedPreferences
on Android and an encrypted file on Desktop.
9. Attribution: Created by Dhawal Ranka

```


