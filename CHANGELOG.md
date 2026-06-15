# What's New in PureTV for Twitch

Plain-language notes for each version — the same words users see in the app's
update prompt and on the download page.

**When you cut a release:** add a new `## <version> — <date>` section at the top,
write a one-line summary, then list changes under **New / Improved / Fixed** in
everyday language (e.g. "Sign-in is faster now", not "migrated OAuth to PKCE").
Keep it short and friendly. The release pipeline turns the top section into the
release notes automatically.

---

## 1.7.2 - 2026-06-15

Streams play again.

### Fixed
- **Streams load again.** Twitch changed something on their side that stopped PureTV from starting any stream — the player stayed black and the ad-blocker was stuck on "Checking…". PureTV now asks Twitch for streams in a way that doesn't depend on that, so live channels and past broadcasts play normally again.
- **Clearer when something's wrong.** If a stream genuinely can't be reached, the ad-block badge no longer hangs on "Checking…" forever, so a real outage is easier to spot.

## 1.7.1 - 2026-06-15

A quick fix for the new chat.

### Fixed
- **Your own messages now show up.** When you send a message in chat, it appears on your screen right away, with your name, color and badges. Twitch never sends your own messages back to your app, so PureTV now adds them for you.

## 1.7.0 - 2026-06-15

A big chat upgrade, plus a volume fix.

### New
- **Emotes when you chat.** Open the new emote picker in the chat box to browse and search your channel's emotes, Twitch global emotes, and BTTV, FFZ and 7TV emotes, then click to drop one in. You can also just start typing an emote name and press Tab to complete it.
- **Reply to messages.** Reply to a chat message directly, the same way you can on Twitch, and replies show who they are answering.

### Improved
- **Chat scrolling that behaves.** Chat stays pinned to the newest message while you are at the bottom. If you scroll up to read something, it waits instead of yanking you back down, and shows a "New messages" button so you can jump to the latest when you are ready.
- **Sign-in prompt in chat.** When you are browsing signed out, the chat box now shows a quick "Sign in to chat" prompt instead of quietly doing nothing.

### Fixed
- **Volume now matches the slider.** Streams start at 50% with the slider actually showing 50%, so the sound no longer jumps the first time you nudge it. The speaker icon mutes and unmutes too, and remembers your level.

## 1.6.0 - 2026-06-15

See what any channel is up to at a glance, with a new stats panel.

### New
- **Channel stats.** Open any channel for a live "Audience" panel: how many people are watching right now, the channel's follower count, what they're playing, how long they've been live, how long they've been on Twitch, and whether they're a partner. While you watch, PureTV draws a little chart of the viewer count over time and remembers the peak and average. All of it stays privately on your own computer.

### Improved
- **Simpler ad-blocking setting.** The ad-blocking section in Settings now just shows that it's always on, without the extra technical options.

## 1.5.0 - 2026-06-15

Fullscreen and theater modes for past videos, and a quieter update.

### New
- **Fullscreen, theater, and windowed modes for past videos.** The video player now has the same view modes as live streams, with on-screen toggle buttons and keyboard shortcuts: F for fullscreen, T for theater, C for chat, Space for play/pause, and Esc to exit.

### Fixed
- **Updates no longer leave a command-prompt window open.** Applying an in-app update now runs quietly in the background.

## 1.4.0 - 2026-06-15

Past broadcasts just got a lot better: pick up where you left off, preview as you scrub, and watch the chat play back.

### New
- **Continue watching.** Past videos remember where you stopped. Reopen one to resume from that spot or start over, and find your in-progress videos in a "Continue watching" row on Home.
- **Scrub previews.** Drag the seek bar on a past video to see a thumbnail of that moment before you let go.
- **Chat replay.** The original chat plays back next to a past broadcast, in sync with the video. Toggle it on or off from the player.

## 1.3.0 - 2026-06-15

Watch a channel's past broadcasts, ad-free.

### New
- **Past videos.** Open any channel to browse its past broadcasts, highlights, and uploads, then play any of them right inside PureTV.
- **Seek bar.** Scrub to any moment in a video, with play and pause and the current time and total length shown on screen.
- **Filter and load more.** Switch between past broadcasts, highlights, and uploads, and keep loading older videos as you go.

### Improved
- The quality switcher and volume control work on past videos just like they do on live streams.

## 1.2.0 — 2026-06-14

A complete visual redesign — PureTV now looks like a proper cinema.

### New
- **A brand-new editorial look.** Every screen has been redesigned around a refined dark theme, confident typography, and stream thumbnails treated like cover art.
- **Richer chat.** Messages now show timestamps, moderator/subscriber badges, and emotes.

### Improved
- **Cleaner player controls**, including an easier quality switcher.
- **Home, Browse, Channel, Search, Settings and Sign-in** are all more readable, more spacious, and more polished.
- Crisper buttons, cards, and hover effects throughout.

### Fixed
- Loading and empty screens no longer show blank gray boxes.

## 1.1.0 — 2026-06-14

A smoother, more secure sign-in and safer automatic updates.

### New
- **Simpler sign-in.** PureTV now opens Twitch in your browser, you tap **Authorize**, and you're in — no more local pop-up window during login.

### Improved
- **Safer updates.** PureTV now checks that every update really came from us before installing it, so a tampered download can't sneak in.

### Fixed
- Various behind-the-scenes security and reliability fixes.

> Heads up: this update signs you out once. Just sign in again with the new, quicker flow.

## 1.0.8 — 2026-06-08

- A cleaner, more polished look and smoother window behavior.
