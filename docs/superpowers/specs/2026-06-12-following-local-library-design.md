# Local "Following" for PureTV Desktop — Design

**Date:** 2026-06-12
**Status:** Approved (design); pending implementation
**Scope:** Windows desktop module (`app-windows`) only

## Problem

To re-watch a streamer the user must search for them every time. The Home page
has a "Continue Watching" rail driven by the user's real Twitch follows
(`UserRepository.loadFollows`), but it only shows followed channels that also
appear in the global top-20 streams (`top.filter { userLogin in follows }`), so
it is usually empty. There is no in-app way to save a channel.

**Constraint:** Twitch removed app-initiated follow/unfollow from its Helix API
(2021–2022). There is no `POST /follows`. An in-app "follow" therefore cannot
mutate the user's real Twitch follow graph and must be a **local** list stored on
the device.

## Goal

A one-click **Follow (+)** toggle on the watch screen and channel page that saves
a channel locally. Saved channels appear in a new **"Following"** rail at the top
of Home — always visible: live channels badged with viewer counts and sorted
first, offline channels dimmed but still one click away.

## Decisions (from brainstorming)

- **Home rail:** always show every saved channel (live badge + viewers when live;
  dimmed "Offline" otherwise).
- **Button placement:** both the watch (stream) screen and the channel page.
- **Vocabulary:** "Follow" / "Following" with a `+` → `✓` icon (local-only;
  not the real Twitch follow graph).
- **"Continue Watching" rail is replaced** by the new local "Following" rail.
- Not importing existing Twitch follows; no polling (YAGNI).

## Architecture

### 1. Persistence — `FollowStore` (new, `desktop/data`)

Single-purpose store mirroring `DesktopSettingsStore`'s file pattern.

- File: `%APPDATA%/PureTwitch/following.json` (plaintext — nothing sensitive).
- Holds `List<FollowedChannel>`; exposes `StateFlow<List<FollowedChannel>>`.
- API: `follow(channel)`, `unfollow(login)`, `toggle(channel)`, dedup by `login`
  (case-insensitive). `isFollowed(login)` derivable from the flow.
- Serializable DTO persisted via the same `Json` config used elsewhere.

```kotlin
@Serializable
data class FollowedChannel(
    val id: String,
    val login: String,
    val displayName: String,
    val profileImageUrl: String = "",
)
```

Storing `displayName` + `profileImageUrl` at follow-time lets offline cards render
with no extra API call (Twitch `getStreams` returns only *live* channels).

Koin: `single { FollowStore() }` in `desktopModule`.

### 2. Follow toggle button (two touchpoints)

- **Watch screen** (`StreamContent.TopBar`): a `+`/`✓` `IconButton` beside the
  chat/theater/fullscreen icons. `StreamViewModel` gains
  `isFollowed: StateFlow<Boolean>` and `toggleFollow()`, sourced from the
  `ChannelInfo`/`StreamInfo` already loaded. Disabled until `channel != null`.
- **Channel page** (`ChannelContent`): a "Follow"/"Following" `Button` next to
  "Watch now". `ChannelViewModel` gains the same `isFollowed` + `toggleFollow()`.

`toggleFollow()` builds a `FollowedChannel` from the loaded channel and calls
`FollowStore.toggle(...)`.

### 3. Home "Following" rail

`HomeViewModel`:
- Dependency change: drop `UserRepository`, add `FollowStore`.
- Combine `FollowStore.followed` with a live-status query
  (`streamRepository.streamsForChannels(savedLogins)`); build a list of
  `FollowCardState`:
  - live → from `StreamInfo` (thumbnail, viewer count, title, game);
  - offline → from the saved `FollowedChannel` (avatar, name).
- Sort: live first by viewer count desc, then offline by display name.
- Re-query live status on Home load and on `refresh()`. No polling in v1.
- Rail hidden when the saved list is empty.

```kotlin
data class FollowCardState(
    val login: String,
    val displayName: String,
    val avatarUrl: String,
    val isLive: Boolean,
    val viewerCount: Int = 0,
    val title: String = "",
    val gameName: String = "",
    val thumbnailUrl: String = "",
)
```

`HomeUiState` gains `following: List<FollowCardState>`; the
`followedLive`/`UserRepository` path is removed.

### 4. `FollowCard` component (new, `desktop/ui/components`)

Renders both states, always clickable → opens the channel:
- **Live:** same visual language as `StreamCard` (16:9 thumbnail, LIVE badge,
  viewer count, name + title).
- **Offline:** dimmed (reduced alpha) — `Avatar` + display name + "Offline"
  label, no thumbnail.

### Data flow

```
click Follow → VM.toggleFollow() → FollowStore.toggle(FollowedChannel)
            → writes following.json + emits new StateFlow value
            → HomeViewModel (observing FollowStore) recomputes rail
            → channel appears in "Following" on next Home visit
```

## Error / edge cases

- **Not logged in:** `getStreams` needs a bearer token; if anonymous or the
  query fails, render all saved channels as offline (still clickable). Following
  itself needs no login (local).
- **Channel info not yet loaded:** Follow button disabled (need id/name/avatar).
- **Duplicate follow:** store dedups by login.
- **Empty list:** rail not rendered.
- **Missing avatar:** existing `Avatar` initial-letter fallback handles null/blank.

## Testing

- **`FollowStore` unit tests** (real logic): follow / unfollow / toggle / dedup,
  and a persistence round-trip against a temp directory (write → new instance →
  read back).
- **ViewModels / UI:** verified by `gradle :app-windows:compileKotlin` + manual
  run, consistent with the rest of this module (no UI test harness).

## Files

**New**
- `app-windows/.../desktop/data/FollowStore.kt`
- `app-windows/.../desktop/ui/components/FollowCard.kt`
- test for `FollowStore`

**Modified**
- `desktop/di/DesktopModule.kt` — register `FollowStore`; update `HomeViewModel`
  factory.
- `desktop/ui/ViewModels.kt` — `HomeViewModel` (rail), `StreamViewModel` +
  `ChannelViewModel` (`isFollowed` / `toggleFollow`).
- `desktop/ui/screens/HomeContent.kt` — "Following" rail; remove "Continue
  Watching".
- `desktop/ui/screens/StreamContent.kt` — Follow toggle in `TopBar`.
- `desktop/ui/screens/ChannelContent.kt` — Follow button.

## Out of scope (YAGNI)

Importing existing Twitch follows; live-status polling/auto-refresh; folders,
reordering, notifications; promoting the feature to `core` for phone/TV.
