# What's New in PureTV for Twitch

Plain-language notes for each version — the same words users see in the app's
update prompt and on the download page.

**When you cut a release:** add a new `## <version> — <date>` section at the top,
write a one-line summary, then list changes under **New / Improved / Fixed** in
everyday language (e.g. "Sign-in is faster now", not "migrated OAuth to PKCE").
Keep it short and friendly. The release pipeline turns the top section into the
release notes automatically.

---

## 1.10.1 - 2026-07-01

The first published build of the 1.10 line. It brings the new 7TV / Chatterino-style chat and makes updating reliable.

### New
- **A much better chat experience.** Live 7TV emote updates (new emotes appear the moment a channel adds them, no restart), real Twitch, subscriber, and moderator badge icons instead of plain text, sub and raid announcements, a highlight when someone mentions you, and a Mentions tab next to chat.

### Fixed
- **Updates install cleanly now.** Before, installing an update could leave PureTV unable to open (a "cannot open ...cfg" error) until you reinstalled it by hand, because the new files were written while the app was still closing. PureTV now shuts down fully before an update, and the installer force-closes any leftover PureTV first, so updates just work, including the update onto this version from an older one.

Updating from an older version and PureTV will not open afterward? Download the installer above and run it once. That fixes the install, and updates from then on are seamless.

## 1.10.0 - 2026-06-30

Brings the chat experience closer to 7TV and Chatterino, with live emotes, real badge icons, and proper moderation and event messages.

### New
- **Emotes update live, no restart needed.** When a channel adds, removes, or renames a 7TV emote, it now appears in chat right away, the same way Chatterino and the 7TV extension work.
- **Real badge icons in chat.** Subscriber, moderator, VIP, and other badges now show their actual Twitch icons instead of plain text labels.
- **Sub, resub, and raid announcements show up.** Chat now displays the same celebratory lines you see on Twitch when someone subscribes, gifts subs, or raids the channel.
- **Messages that mention you stand out.** Any message that tags your username is highlighted so you do not miss replies.

### Improved
- **Deleted and timed-out messages are handled properly.** When a moderator removes a message or times someone out, those messages now show as removed instead of staying on screen, and a note appears when a moderator clears the chat.
- **Your emote on/off switches actually work.** The 7TV, BTTV, and FrankerFaceZ toggles in Settings now take effect immediately while you watch, and when two providers share an emote name, the 7TV version wins.

### Fixed
- **Installing an update no longer breaks the app.** After a one-click update, opening PureTV from the Start menu, a shortcut, or Windows search could fail with a "cannot open ...cfg" error, and the only fix was reinstalling from GitHub by hand. The updater now waits for the app to fully close before applying the update, so its files are never replaced while they are still in use.

## 1.9.5 - 2026-06-30

Fixes staying signed in between launches, and makes your followed list react the moment you sign in or out.

### Fixed
- **Staying signed in between launches now works.** A start-up problem was throwing away your saved sign-in every time PureTV opened, so it kept asking you to reconnect Twitch even though your login was stored safely on your PC. Your session is now restored correctly on launch, so you sign in once and it sticks.
- **Your followed list reacts instantly to signing in and out.** It fills in right after you sign in, and clears right away when you sign out, instead of lagging behind or briefly showing the previous account's channels.

## 1.9.4 - 2026-06-30

Fixes the two biggest annoyances (being asked to sign in every time, and a blank white box over the video), plus a freeze and a round of reliability fixes.

### Fixed
- **You stay signed in.** PureTV no longer makes you reconnect your Twitch account every time you open it. Your session is now kept and refreshed properly, so you sign in once and it sticks across restarts.
- **No more white box over the player.** The video area could show a blank white square instead of the stream (most noticeable right when a stream opens or while resizing). It now stays black until the picture appears.
- **The window no longer freezes or stops responding.** Clicking the title bar or the menus could briefly lock everything up, and switching streams on the GPU player could stall the app. The player and window handling now do that heavy work off the main thread, so the interface stays responsive.
- **Search shows what you actually typed.** Typing quickly could leave older results on screen; search now follows your latest query and waits for you to stop typing before looking.

### Improved
- **Clearer when something fails instead of looking empty.** Your followed/live lists and the category pages now show an error you can retry, rather than a blank that looks like "nothing's on."
- **Chat is more dependable.** Messages can no longer end up in the wrong channel after reconnecting, emoji and emotes line up correctly, and your saved emotes aren't dropped by a temporary hiccup.

## 1.9.3 - 2026-06-19

Makes recovering from a failed update simple, with a one-click way to grab the latest version.

### Improved
- **If an update ever fails, getting back on track is now one click.** When an in-app update can't finish, PureTV shows an "Open download page" button that takes you straight to the latest installer in your browser, so there's no more hunting for the website or fishing a link out of an error message. It's also the fastest fix if you're stuck on an older version whose updater keeps failing: open the page, run the installer once, and you're back to automatic updates.
- **Update messages are clearer.** When something goes wrong, the on-screen message is shorter and easier to read, while the full technical details still go to the update log for troubleshooting.

## 1.9.2 - 2026-06-18

Fixes in-app updates that could fail with an out-of-memory error.

### Fixed
- **In-app updates no longer fail with an "out of memory" error.** When checking a downloaded update, PureTV used to load the whole installer into memory at once. On a busy session that could run the app out of room and stop the update, even though the download itself was perfectly fine. It now reads the file in small pieces and runs with more memory headroom, so updates install reliably.

If your current version keeps failing to update with that memory error, this is the one update you'll want to install by hand: download the installer below and run it once, and after that in-app updates work normally again.

## 1.9.1 - 2026-06-18

Fixes a freeze with animated emotes, makes the followed list load faster with clearer feedback, and toughens up your saved data.

### Fixed
- **The app no longer freezes on busy chat.** A large animated emote could lock up the whole window for a while. The decoding was happening on the wrong thread; it now runs in the background, so chat stays smooth no matter how many emotes are flying.
- **One network blip no longer empties your "Live now" list.** If a single part of the followed-channels load failed, the whole rail used to come back empty. Now the parts that succeed still show up.
- **Browse, Search and category pages tell you when something went wrong.** Instead of looking empty, as if there were nothing there, they show a short message with a Retry button when the network is the problem.

### Improved
- **The followed sidebar gives you feedback the moment you sign in.** You now see a loading bar and placeholder rows right away, then your live channels stream in, instead of the list sitting blank until the next refresh.
- **Animated emotes load faster.** Large animated emotes decode a lot quicker and use less memory.
- **You see who's live sooner.** Live channels show up first and their avatars fill in right after, so you're not waiting on profile pictures to find out who's streaming.
- **Your saved data is harder to lose.** If a follows, history or settings file ever gets damaged, PureTV sets the bad copy aside and keeps going instead of silently wiping it, and it closes cleanly without hanging on the way out.

## 1.8.1 - 2026-06-17

Makes in-app updates reliable and self-explaining.

### Fixed
- **In-app updates that failed with a "signature check failed" error now recover on their own.** If a download arrives corrupted or incomplete (the usual cause), PureTV re-downloads once and tries again instead of giving up — so you shouldn't need to grab updates from the website anymore.
- **Clear, specific reasons when an update can't proceed.** Instead of one generic error, PureTV now tells you whether the download was bad versus a problem on your PC, and writes the details to a log (`%APPDATA%\PureTwitch\update.log`) so any remaining issue can be pinned down immediately.
- **Updates no longer leave old files behind.** Installing over a previous version now cleans up the old program files first, so your install doesn't slowly bloat with every update.

## 1.8.0 - 2026-06-17

GPU upscaling arrives, the emotes you type finally show up, and a big round of reliability and safety fixes under the hood.

### New
- **Sharper streams with your graphics card.** In the player's gear menu you can switch the playback engine to mpv and turn on GPU upscaling. Pick **Sharp** for live content (gaming, IRL, sports) or **Anime** for animation, **hold X** to instantly compare against off, and press **F3** to see exactly what's happening (source size, output size and the scaler in use). It's off by default — the classic player is untouched until you opt in.

### Fixed
- **Your own emotes show when you send them.** Emotes you type or pick — Twitch, BTTV, FFZ and 7TV — now appear (animated) in your own messages instead of as plain text.
- **Settings no longer freezes the window.** Opening Settings while a stream was playing could briefly hang the whole app. That's gone.
- **Cleaner chat.** Replies and channel notices no longer show stray characters like `\s` in the quoted text.
- **Even fewer ads slip through.** Several edge cases where an ad could briefly play — or where a refresh could stutter — have been closed.
- **Your saved data is safer.** Follows, watch progress and settings are now written so they can't be left half-finished or corrupted if the app or PC stops unexpectedly.
- **Safer updates.** Update downloads are now locked to secure (HTTPS) connections.

## 1.7.2 - 2026-06-15

Streams play again, chat gets Enter-to-send, and in-app updates install reliably.

### New
- **Press Enter to send chat.** Send a message with the Enter key instead of clicking the arrow. Tab still completes an emote suggestion, and the arrow button is still there if you prefer it.

### Fixed
- **Streams load again.** Twitch changed something on their side that stopped PureTV from starting any stream — the player stayed black and the ad-blocker was stuck on "Checking…". PureTV now asks Twitch for streams in a way that doesn't depend on that, so live channels and past broadcasts play normally again.
- **Updates install on the first try.** In-app updates often failed the first time (and sometimes the second), and only went through after a few attempts. PureTV now downloads updates over a more reliable connection and retries automatically, so updating works the first time.
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
