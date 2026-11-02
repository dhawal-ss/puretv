# What's New in PureTV for Twitch

Plain-language notes for each version, in the same words users see in the app's
update prompt and on the download page.

**When you cut a release:** add a new `## <version> - <date>` section at the top,
write a one-line summary, then list changes under **New / Improved / Fixed** in
everyday language (e.g. "Sign-in is faster now", not "migrated OAuth to PKCE").
Keep it short and friendly. The release pipeline turns the top section into the
release notes automatically.

---

## 1.17.0 - 2026-11-02

Minimizing keeps your stream playing again, Search finds categories, Browse shows every category your way, and audio levelling has three strengths and is one click away while you watch.

### Fixed
- **Minimizing keeps your stream playing.** Since 1.13, minimizing PureTV hid it
  in the tray and closed the stream, so opening it again landed you on Home.
  Minimizing now goes to the taskbar like any other app and the stream keeps
  playing. If you liked the tray, turn on **Minimize to tray** in Settings,
  Notifications: the stream stops while PureTV is hidden and opens again when
  you bring PureTV back. Thanks to Akaneias for reporting it
  ([#40](https://github.com/dhawal-ss/puretv/issues/40)).
- **Search finds categories.** Typing a game or category, like "Battlefield
  REDSEC", now shows matching categories above the channels. Click one to see
  who is live in it. Thanks to Tohidmzn and peter-g1h2r
  ([#41](https://github.com/dhawal-ss/puretv/issues/41)).
- **Browse shows every category.** Browse used to stop at the top 20. It now
  loads the top 100 at once, and when you're signed in it keeps loading more as
  you scroll, all the way to the end.

### New
- **Sort Browse.** Pick **Most viewers**, **Fewest viewers** (great for finding
  smaller games) or **A to Z** at the top of Browse. PureTV remembers your pick.
  When you're signed out, each category also shows how many people are watching.
- **Audio levelling strength.** "Even out loud and quiet audio" now comes in
  three strengths: **Light** takes the edge off sudden loud moments, **Balanced**
  (the default) brings quiet voices and music much closer to loud ones, and
  **Strong** keeps everything at about one level, good for quiet listening.
  Light is how it worked before. At every strength a normal voice stays about
  as loud as it was.
- **Switch audio levelling while you watch.** There is a button for it in the
  player controls, the strength is in the player menu, and the A key toggles it.
  With the default engine it now switches in a second or two instead of after a
  restart.
- **Chat: `:` for emotes, Tab to cycle.** Type a colon and two letters, like
  `:kek`, to see only emotes, including ones with those letters anywhere in the
  name. Press Tab to fill in the highlighted suggestion and keep pressing Tab to
  move through the rest (Shift+Tab goes back). The suggestion strip now says
  "Tab to complete" so it's easier to find
  ([discussion #35](https://github.com/dhawal-ss/puretv/discussions/35)).

## Android 1.4.0 - 2026-11-02

Search finds categories, Browse shows every category your way, and audio levelling has three strengths and is one tap away while you watch.

### Improved
- **Search finds categories.** Matching categories show above the channels; tap
  one to see who is live in it
  ([#41](https://github.com/dhawal-ss/puretv/issues/41)).
- **Browse shows every category.** Browse used to stop at the top 20. It now
  loads the top 100, and keeps loading more as you scroll when you're signed in.
- **Sort Browse** by most viewers, fewest viewers or A to Z. PureTV remembers
  your pick.
- **Audio levelling strength.** Pick Light, Balanced or Strong in Settings.
  Balanced, the default, brings quiet music and voices much closer to the
  loudest moments.
- **Switch audio levelling while you watch.** Tap the new button in the player
  controls.
- **Chat: `:` for emotes.** Type a colon and two letters, like `:kek`, to see
  only emotes in the suggestions.

## Android TV 1.4.0 - 2026-11-02

Search finds categories, Browse shows every category your way, and audio levelling has three strengths and is one press away while you watch.

### Improved
- **Search finds categories.** Matching categories show above the channels.
- **Browse shows every category.** Browse used to stop at the top 20. It now
  loads the top 100, and keeps loading more as you scroll when you're signed in.
- **Sort Browse** by most viewers, fewest viewers or A to Z. PureTV remembers
  your pick.
- **Audio levelling strength.** Pick Light, Balanced or Strong in Settings.
  Balanced, the default, brings quiet music and voices much closer to the
  loudest moments.
- **Switch audio levelling while you watch.** A new button in the player
  controls steps through Off, Light, Balanced and Strong.

## Android 1.3.1 - 2026-10-27

Past broadcasts are one tap away from Following, and chat holds up better on the move. This release includes the first community contribution to the phone app: thank you, [@laryaonyx5-ux](https://github.com/laryaonyx5-ux)!

### Fixed
- **View channel opens the channel page.** On Following, tapping an offline
  channel or its View channel button opened the stream screen, so the channel
  page with past broadcasts and highlights was only reachable through Search.
  It now opens the channel page, like the TV and Windows apps. Contributed by
  [@laryaonyx5-ux](https://github.com/laryaonyx5-ux) in
  [#32](https://github.com/dhawal-ss/puretv/pull/32).
- **Chat recovers after sleep or a Wi-Fi change.** A dropped chat connection is
  noticed within about a minute and a half instead of six minutes, and chat no
  longer redials nonstop when Twitch refuses a sign-in; it keeps reading instead.
- **Ad breaks.** A backup stream that Twitch had already closed is no longer
  used, so an ad break falls back cleanly instead of stalling.

## 1.16.1 - 2026-10-26

Streams start right away after you open PureTV, with no more long freeze, and playback, chat and emotes all got lighter and steadier.

### Fixed
- **No more freeze on the first stream.** Right after turning on your PC, the
  first stream you opened could freeze the whole window for 10 to 30 seconds.
  The video player now starts in the background in a fraction of a second, and
  if it ever needs a moment, you see "Starting the video player..." with a Back
  button instead of a frozen window.
- **Chat keeps working in long sessions.** After a few hours, chat could stop
  receiving messages without telling you. It now signs in again by itself, and
  after sleep or a Wi-Fi change it reconnects within a minute and a half instead
  of six.
- **Fewer frozen pictures mid-stream.** A hiccup on Twitch's side no longer
  freezes the video for 15 seconds; PureTV rides it out and keeps playing.
- **Ad breaks.** A backup stream that Twitch had already closed is no longer
  used, so ad breaks fall back cleanly instead of freezing.
- **Opening a channel from a shortcut.** `PureTV for Twitch --channel <name>`
  sometimes only brought the running window forward instead of opening that
  channel. It now opens the channel.

### Improved
- **Streams open faster.** The first stream after launch starts sooner, and
  every stream starts fetching the moment you click it.
- **Smoother with animated emotes.** Busy chats full of animated emotes used to
  make the whole window redraw as fast as your monitor refreshes. They now use
  far less of your CPU and GPU, animate in step with each other, and look
  sharper at chat size.
- **Hidden chat costs nothing.** With chat closed, or in the mini player, chat
  no longer keeps working in the background. It is up to date when you open it
  again. (A message you were typing is cleared when you close chat.)
- **Updates.** Installing an update takes a little longer at the end, while it
  prepares the video player. That is the work that used to happen every time
  you started PureTV.

## 1.15.0 - 2026-10-23

NVIDIA RTX Video comes to PureTV on Windows, streams open at the quality you pick, and the whole app got a new typeface and quicker, smoother motion.

### New
- **NVIDIA RTX Video (Windows, RTX graphics cards).** Super Resolution uses your
  RTX card to sharpen streams shown bigger than they were sent, like 1080p on a
  1440p or 4K screen, and it is on by default. RTX HDR turns normal streams into
  HDR on a display that has Windows HDR turned on; it is off by default because it
  changes the look a lot. Both are switches in Settings and in the player menu,
  which also tells you what each one is doing right now. How strong Super
  Resolution is comes from the NVIDIA app: System, Video, RTX Video Enhancement.
  Multi-stream tiles and extra windows stay as they are, to save your GPU.
- **Stats on the default engine.** Press F3 on a stream to see the source and
  screen size, what is scaling the picture, and dropped frames.
- **Restart now.** Switching the playback engine offers a one-click restart
  instead of asking you to restart later.

### Improved
- **New typeface.** The whole app now uses Google Sans Flex, the Material 3
  Expressive font, with even-width numbers so viewer counts and timers sit still.
- **Quicker, smoother motion.** Animations follow Material 3 Expressive's spring
  timing, and opening chat, theater mode or fullscreen resizes the video once
  instead of on every animation frame.
- **VLC 3.0.24.** The built-in player is updated, which also brings VLC's latest
  security fixes.

### Fixed
- **Streams start at the quality you pick.** Choosing Source or 1080p60 no longer
  starts at 160p and climbs on channels whose top quality isn't labelled the way
  PureTV expected. A quality the channel doesn't have plays the nearest one below.
- **No white box when you leave a stream.** The window no longer freezes while the
  video shuts down.
- **The picture fills the player again after you open and close the player menu.**

## 1.14.0 - 2026-10-22

PureTV now runs on Mac and Linux too, and on Windows the keyboard, the stream bar and the ad-blocking connection all got better.

### New
- **PureTV for Mac (Apple Silicon).** Download the .dmg, drag PureTV into
  Applications, and on first open allow it under System Settings, Privacy &
  Security (it is not notarized by Apple yet). VLC is built in. It uses the Mac
  window buttons, the Mac's own fullscreen, Cmd for shortcuts, and lives in the
  menu bar for go-live alerts.
- **PureTV for Linux.** Install the .deb with `sudo apt install ./puretv-for-twitch_*_amd64.deb`
  on Ubuntu 22.04 or newer, Debian 12, Mint or Pop!_OS; it brings VLC with it.
  It uses your desktop's window frame and fullscreen.
- These are the first Mac and Linux versions. They have been tested on real
  machines for installing, playing streams and quitting, but if anything feels
  off, please open an issue on GitHub so it can be fixed quickly.
- **Open a channel from a shortcut or script.** `PureTV for Twitch --channel <name>`
  opens that stream, even when PureTV is already running.

### Improved
- **Keyboard.** Tab now reaches everything after a video, such as Watch now on
  Home.
- **Smaller windows.** The stream's top bar keeps the channel name readable by
  turning its widest buttons into icons when space is tight.
- **Sign-in.** If no browser can be opened, the sign-in screen says so and tells
  you where to enter the code, instead of claiming it opened one.
- **Safer local video connection.** The part of PureTV that removes ads runs on
  your computer; it now only answers PureTV itself and is on an updated network
  library with recent security fixes.

### Fixed
- On Mac and Linux, leaving fullscreen with the window's own controls now returns
  the player to its normal layout.

## 1.13.0 - 2026-10-21

Streams start sooner, ads get skipped more cleanly, and live streams play closer to live, on Windows, phone and TV.

### New
- **More than one PureTV window (Windows).** Press Ctrl + N, the plus button in
  the title bar, or New window in the tray menu to open another full PureTV
  window, on another monitor when you have one. Each window has its own screens,
  its own stream or past broadcast with its own volume, and its own theater,
  fullscreen and mini player, so you can browse, search and watch different
  creators in each. Right click (or middle click, or Ctrl + click) a stream,
  channel, category, search result or followed channel to open it in a new
  window. Closing a window closes only that one, and windows reopen where you
  left them. Up to 6 windows and 8 videos at once, with a friendly note if you
  go past that.
- **Past broadcasts and highlights in multi-stream (Windows).** Watch a past
  broadcast, highlight or upload next to live streams, or in a window of its
  own on any monitor. Add one from the plus button on a creator's video list,
  from the video player, or from Add channel > Past broadcasts and highlights
  (pick from a creator's latest videos or paste a twitch.tv/videos link). Each
  video tile has play and pause, a seek bar, the time, its own volume, Pop out
  and Dock. With a video selected, Space plays or pauses, Left and Right skip 5
  seconds (Shift for 30), J and L skip 10, and its chat replay shows beside
  it. Videos pick up where you left off and show in Continue Watching.
- **Pop multi-stream out onto other monitors (Windows).** Every channel in
  multi-stream has a Pop out button that moves it into its own PureTV window.
  Drag that window to any monitor, resize it, or press F (or double click the
  video) for fullscreen on the monitor it is on, Esc to come back. "Pop out
  all" spreads every channel over your monitors and leaves the main window
  free to browse while they keep playing; "Dock all" brings them back. Each
  window has its own volume, mute and an Add another channel button, Up, Down
  and M work in the window you are in, D docks it back and Ctrl + W closes it.
  Sound and the selected channel are shared, and windows reopen where you left
  them. A stream rebuffers for a second or two when it moves.
- **Know when the channels you pick go live.** Open any channel, followed or
  not, and tap the bell. PureTV then tells you when that channel starts
  streaming: a Windows notification from the tray, a phone notification you
  can tap to start watching, or a banner in the corner on TV while PureTV is
  open (it never moves your selection). Alerts are per channel, so nothing
  fires for channels you did not pick. The bell is also on the watch screen,
  and on Following you can long-press a channel (right-click on Windows, Menu
  on TV) to switch its bell. Settings lists your channels with a way to
  remove them or turn them all off. PureTV asks Twitch itself; nothing goes
  through a push service or anyone else's server. On the phone, alerts while
  PureTV is in the background may arrive a few minutes late when the phone is
  idle. If you tried the earlier version that alerted for everyone you follow,
  alerts are now silent until you pick channels.
- **Low latency.** Streams now play several seconds closer to live, so what
  you see keeps up with chat. It's on for Windows and phone. On TV it's off
  to start with, because TV sticks on Wi-Fi are more likely to pause and
  buffer; switch it on in Settings if your connection is solid. Turn it off
  anywhere if streams keep buffering.
- **Real audio only.** Picking Audio only now downloads just the sound,
  instead of the whole video with the picture hidden. Much less data.
- **Audio only in the background (phone).** When you're listening with the
  screen off or another app open, PureTV downloads just the sound and brings
  the picture back when you return. You can turn this off in Settings.
- **Quality menu on the phone player.** Pick Auto, Source, a resolution, or
  Audio only right from the player.
- **Flip channels without leaving the picture.** On TV, press Up or Down
  while you watch (or the Channel keys on your remote) to jump to the next or
  previous channel. On Windows, use Page Up and Page Down. It flips through the
  channels you follow that are live, or else the row you opened the stream from,
  and the next channels are made ready ahead of time so a flip is quick. On TV,
  OK now shows the controls instead of pausing, and Settings can turn the
  Up/Down flipping off.
- **Sleep timer (TV and Windows).** Pick 15, 30, 60, 90 or 120 minutes and the
  stream stops by itself. A minute before the end PureTV says so, and any key
  press (or moving the mouse on Windows) gives you another 15 minutes.
- **Keyboard shortcuts on Windows.** Up and Down change the volume, M mutes,
  Z sets the sleep timer, and ? shows all the shortcuts.
- **Continue watching on TV.** Home now shows channels you watched recently
  (live ones first, with their live picture) and past broadcasts you left part
  way, so you can pick up where you were.
- **Past broadcasts on phone and TV.** Channel pages now list a streamer's
  past broadcasts and highlights. You can scrub through them (with preview
  pictures), pick up where you left off, and replay the chat alongside.
  Phone Home shows a Continue watching card for each one you left part way.
- **Browse without signing in (Windows and TV).** Live channels, categories,
  search and a channel's past broadcasts all work before you sign in. Sign in
  only when you want your follows, chat or alerts.
- **Watch several streams at once (Windows).** Open multi-stream from a
  stream's controls and add up to four channels in one window. Click a tile
  to listen to it; the others stay muted without reloading. Chat follows the
  tile you are listening to, and each tile shows its own ad-skip badge.
- **Mini player (Windows).** Press I or the Mini player button to shrink
  PureTV into a small always-on-top window in the corner of your screen.
  Press I, Esc or Expand to come back.
- **Even out loud and quiet audio.** A new setting turns down sudden loud
  parts and brings up quiet ones. It is off to start with. On Windows with the
  VLC engine it applies after a restart; everywhere else it switches at once.
- **7TV name paints (Windows).** Chatters' coloured and gradient names from
  7TV now show in chat. You can turn this off in Settings. PureTV only listens
  for them and never tells 7TV what you are watching.
- **Chat looks the same everywhere.** Phone and TV chat now show real badge
  pictures, deleted messages, subs, raids and highlights for mentions, and
  the room's slow, emote-only, followers-only and subscribers-only modes. 7TV
  emote changes appear live. On TV you can put chat over the video or beside
  it and choose how see-through it is.

### Improved
- **Streams start sooner.** The video starts loading the moment you open a
  channel, instead of waiting for the channel's details, emotes and badges.
- **A head start before you click.** On Windows, resting the pointer on a live
  channel (or opening its page) gets the stream ready in the background. On TV,
  stopping on a channel does the same, so pressing OK plays almost at once.
- **Emotes from the very first chat line.** PureTV remembers each channel's
  emotes, so they show right away and quietly update in the background. If an
  emote service is down, the ones you already had stay.
- **Fewer ads to skip on phone and TV.** They now ask Twitch for the same kind
  of stream Windows does, which gets far fewer ads.
- **Smoother ad skipping on phone and TV.** Switching around an ad no longer
  makes the video jump or freeze.
- **Animated emotes download once on Windows,** instead of again every time
  you start PureTV.
- **Errors say what actually happened.** Instead of "Check your connection"
  for everything, PureTV now tells you when a channel is offline, limited to
  subscribers, blocked in your region, when Twitch itself is having trouble,
  when your sign-in expired, or when you're actually offline.
- **Streams fix themselves.** If a stream drops, PureTV retries with a short
  pause and then reconnects with a fresh link, instead of leaving a black
  screen until you reopen it. If it really can't, it says so with a Try again
  button.
- **Pick your chat text size** in Settings, on Windows, phone and TV. Emotes
  grow with the text. On Windows you can also turn timestamps and badges on or
  off and make chat more compact. Timestamps are now off unless you turn them
  on.
- **"Live now" stays current** on phone and TV, and the follows list loads
  with far fewer requests.
- **Copy diagnostics (Windows).** Settings has a button that copies versions,
  recent errors and logs for a bug report. Sign-in details are left out, and
  nothing is sent anywhere.
- **Chat explains itself (Windows).** If Twitch won't post your message (for
  example a subscribers-only room), chat now tells you why, and resub and
  announcement messages show the person's own words too.
- **Your quality choice sticks.** The quality you pick in the player is the
  one the next stream opens at, on every app (Audio only excepted). Phone and
  TV now open streams at your preferred quality from Settings, and Source
  really plays the original quality instead of acting like Auto.
- **Smoother busy chats.** Very fast chats update in small batches instead
  of redrawing for every message, so the video and chat stay smooth.

- **Multi-stream grows up (Windows).** The sleep timer now follows you into multi-stream and stops every tile.
  Up, Down and M change the volume and mute of the tile you are listening to, and Ctrl with Left or Right moves a tile.
- **Badges and Twitch emotes before you sign in.** Chat shows Twitch's own badges and most emotes even when signed out.
- **Show badges** can now be switched off on phone and TV, and a text chip still shows for any badge picture that did not load.
- **7TV names stay readable.** Very dark or very light name colours are nudged so they can be read on the chat background.
- **Volume for each multi-stream tile (Windows).** Every tile has its own volume slider and a listen-to button, and adjusting a tile's sound also makes it the one you hear.
- **Smoother motion everywhere.** Screens slide and fade into place, lists ease in as they load, placeholders shimmer while content arrives, and buttons and cards react to presses and hover on Windows, phone and TV. It follows your system's reduce-motion setting and stays still if you turn animations off.
- **Move the mini player anywhere (Windows).** Drag it by the video or the control strip, across all your monitors.
  It stays where you drop it and remembers its place the next time. Double-click to bring PureTV back.
- **Close quits, minimize keeps PureTV in the tray (Windows).** The X button always quits PureTV.
  Minimizing it puts PureTV in the system tray instead, and anything playing stops while it is tucked away.
  Click the tray icon, or open PureTV again, to bring it back. The first time it tucks itself away it tells you where it went.

### Fixed
- **Fullscreen controls come back.** In fullscreen the bottom bar hid after a few seconds and
  moving the mouse or pressing a key did not bring it back. Moving the mouse over the video or pressing any key now shows it again,
  on live streams and past broadcasts.
- **Multi-stream sound could end up on the wrong tile (Windows).** Quick clicks between tiles could leave the wrong one audible or two playing at once. Audio changes are now applied one at a time, so only the tile you picked is heard.
- **The phone app stays signed in.** It only renewed your sign-in when it
  started, so with background play, follows and chat stopped working after a
  few hours. It now renews whenever it needs to, and if Twitch signs you out,
  the app tells you so instead of looking broken.
- **Only one PureTV runs at a time on Windows.** Opening it again brings the
  window you already have to the front.
- **TV search no longer shows results for what you typed earlier.**
- **TV chat stopped scrolling in busy channels.** Once a chat filled up, new
  messages no longer scrolled into view. They now always do.
- **Black screen when a stream opens straight into an ad (phone and TV).**
  PureTV now waits for the ad to finish and then starts the stream, instead of
  leaving you on a black screen. You still never see the ad.
- **The ad-block badge tells the truth.** It no longer carries the last
  channel's state over to a new one, and on Windows it no longer stays green
  while an ad is being skipped. It says "SKIPPING AD" while that happens.
- **Moving from one stream to another on Windows could stop the new stream.**
  Clicking another channel in the rail while watching could end with the new
  stream stopped. The old stream's clean-up no longer gets in the way.
- **TV Back button now steps out gently.** Back closes chat first, then hides
  the controls, then leaves the stream. The quality buttons can be reached with
  the D-pad now.

## 1.12.1 - 2026-10-17

Updates install properly again, even if you reopen PureTV while one is running.

### Fixed
- **The update to 1.12.0 kept undoing itself.** Updating closes PureTV for a
  few seconds while the new version is copied in. If you opened PureTV again
  during that time, the install gave up and put the old version back, so you
  were offered the same update every time. The installer now closes that extra
  copy and carries on, then opens the new version for you.

### Improved
- **You can see an update happening.** A progress window now shows while the
  new version installs, instead of PureTV just disappearing.
- **A failed update tells you.** If an install ever can't finish, PureTV
  reopens on the old version and says so, rather than quietly offering the
  same update again.

## 1.12.0 - 2026-10-17

You stay signed in now. No more linking PureTV to Twitch again every day.

### Fixed
- **Signing in really is a one-time thing again.** Your sign-in renews itself
  every few hours behind the scenes, and on Windows that renewal was always
  failing, so the app quietly forgot you and asked you to link it again,
  usually the next day. Renewal now works, it also happens while the app is
  open, and a hiccup on Twitch's side or a dropped connection can no longer
  sign you out. If you were signed out before updating, sign in one more time
  and it will stick.
- **The sign-in code fits in a small window.** If the window wasn't
  maximised, the code and its Copy button ended up off the bottom of the
  screen. The page now scrolls and tightens up to fit.

### Improved
- **Signing in is quicker.** The code is copied for you the moment it
  appears, so on Twitch's page you just paste and click Authorize. You can
  also select the code yourself, and a button reopens the Twitch page if you
  closed it.
- **@names autocomplete in chat.** Type @ and the start of someone's name to
  pick from people who are chatting, or press Tab to fill in the first match.

## Android 1.2.0 - 2026-10-17

Background play is here, along with a much better emote picker. Windows has
its own update today, and the TV app is unchanged.

### New
- **Keep listening when you leave the app.** A stream now keeps playing when
  you switch to another app or turn the screen off, with play, pause and stop
  in the notification and on the lock screen. You can turn it off in
  Settings, under Playback.
- **Picture-in-picture is now optional.** Going Home still floats the video
  in a small window by default. Turn "Picture-in-picture on leave" off in
  Settings to keep just the audio going while you use other apps.

### Improved
- **A real emote picker.** Instead of one row of about eight emotes, the
  picker is now a scrollable grid with a search box, grouped by where the
  emotes come from.
- **Autocomplete for emotes and names.** Suggestions appear above the chat
  box as you type, and @ suggests people who are chatting. Tap one to fill it
  in.

### Fixed
- **You can see your own messages.** Messages you sent never showed up in
  your own chat, because Twitch does not send them back to you. They now
  appear as soon as you send them.

## 1.11.2 - 2026-09-01

Windows catches up with a fix the phone and TV apps already shipped. Nothing
else changed on the desktop app.

### Fixed
- **A stream Twitch refuses no longer pretends to load.** When Twitch turned
  down a request for a stream, the app took the refusal for the stream itself
  and handed the player an address that could never produce video, so the
  window simply sat there. Those refusals are now recognised and reported.

## Android 1.1.3 and Android TV 1.2.4 - 2026-09-01

Updating itself is the fix, on both the phone and TV apps. Windows is unchanged.

### Fixed
- **Updating no longer strands the app.** The app used to start downloading an
  update and only then ask Android for permission to install it, so the trip to
  the system permission screen happened part-way through, with a downloaded file
  and a half-open install already in progress. Some televisions did not come back
  from that, leaving an app that would not open at all. Permission is now asked
  for first, before anything is downloaded, so there is nothing in progress to
  lose.
- **Allowing it is only asked for once.** Coming back from the permission screen
  used to land you right back on the screen asking for the permission you had
  just given. The app now sees that it has been granted and gets on with the
  update, at most asking you to press install one more time.
- **The permission screen is found on more devices.** Some televisions list it
  per-app and some only have a general one, and only the first was looked for.
  Both are now, and on sets that have neither the app prints the exact menu path
  instead of offering a button that does nothing.
- **Unfinished updates are cleared away.** Every interrupted attempt used to
  leave something behind, and enough of them would eventually stop new updates
  from starting at all.

## Android TV 1.2.3 - 2026-09-01

A black screen now explains itself. Android and Windows are unchanged.

### Fixed
- **A stream that will not load now tells you why.** If the stream could not be
  started, the player showed a black screen with no sound and no message, while
  chat carried on scrolling beside it. The reason is now on screen, and OK tries
  again.
- **A refused stream is no longer treated as a working one.** When Twitch turned
  down a request for a stream, the app accepted the refusal as if it were the
  stream itself and handed the player something it could never play. Those
  refusals are now recognised and reported.

## Android 1.1.2 and Android TV 1.2.2 - 2026-08-30

Sign-in fixes and a sturdier start-up, on the phone and TV apps. Windows is
unchanged and stays on 1.11.1.

### Fixed
- **Signing in on the TV works again.** The QR code and the activation code sat
  below the bottom edge of the screen, and a remote had no way to scroll down to
  them, so there was no way to finish signing in. The screen is now laid out
  across instead of down, and it shrinks to fit whatever size your television
  reports. Thanks to Nyarlathoteddy for the report.
- **twitch.tv/activate is easy to read on the TV again**, next to the QR code,
  for when it is easier to type the code than to scan.
- **The QR code stays sharp** at any screen size, so phone cameras pick it up
  more reliably.
- **The sign-in screens scroll on Android.** In landscape, or in a split-screen
  window, the activation code could sit below the bottom of the screen with no
  way to reach it.

### Improved
- **Start-up is sturdier on both apps.** The theme is no longer loaded during
  the first frame, which is work that could stall a slower device before it drew
  anything. If the stored settings or the saved sign-in ever become unreadable,
  the app now clears them and carries on, where before it could fail to start at
  all. Background start-up work can no longer take the app down with it.

## 1.11.1 - 2026-08-28

Bug fixes across all three apps.

### Fixed
- **Signing in is more reliable.** Twitch hands back an activate link with the
  code already in it, and that page often failed to load. PureTV now opens the
  plain twitch.tv/activate and shows the code separately for you to type. On the
  TV the QR code points at the plain page too, so it appears straight away
  instead of waiting for Twitch to answer.
- **Copy code is the main button on Android**, and it tells you when it has
  copied.
- **Past broadcasts skip with the arrow keys on Windows.** Left and Right jump
  10 seconds, hold Shift for 30.
- **The screensaver no longer interrupts a stream on Fire TV** while it is
  playing. Thanks to xexuu for the fix.
- **Android layout.** Fixed the gap that could stick to one edge after using
  fullscreen, and the dead space between the content and the navigation buttons.

## 1.11.0 - 2026-08-28

A complete visual redesign, built on Material 3 Expressive, across Windows,
Android and Android TV.

### New
- **A brand new design across all three apps.** Every screen has been rebuilt,
  with rounder shapes, bigger type and a real sense of depth.
- **Five colour themes.** Violet Dusk, Ember, Teal Deep, Pure Black and Midnight
  Forest. Each is derived from one source colour, so picking a theme re-tones the
  whole app instead of swapping an accent. Settings, then Colour.
- **An Expressiveness setting** on Windows and Android: Calm, Expressive or
  Maximal controls how far corners round and how much the interface moves.
- **Following is its own page** on all three apps now, showing offline channels
  as well as live ones. On Windows it also gets a Grid and List switch, and on
  Android TV it is a new destination in the navigation rail.
- **A collapsible navigation rail on Windows**, with a Resume button for whatever
  you were last watching.
- **Bundled typefaces**, shared by all three apps, so text looks the same
  everywhere instead of borrowing whatever the system provides.

### Improved
- Buttons, cards and chips change shape as you use them: on hover and press on
  Windows, under your finger on Android, and on focus on the television, where
  the focused item also grows and takes a coloured ring so you can follow it from
  across the room.
- The Windows player uses one slider design everywhere.

### Fixed
- Viewer counts on stream cards were hard to read over bright thumbnails, because
  the shading ran the wrong way and left them on bare artwork.
- The volume handle on live streams was invisible on Windows.
- Long stream titles no longer push the buttons off the bottom of the Home
  banner.
- On Android TV, pressing Left now moves along a row first and only reaches the
  navigation rail when there is nothing further left.

## 1.10.4 - 2026-08-24

Improves how chat emotes look and how quickly badges show up.

### Fixed
- **7TV and BTTV emotes no longer look squished.** Wide third-party emotes (like widepeepoHappy) were being squashed into a square box. They now render at their real width, the same way Chatterino and Twitch's own chat show them. Twitch's own emotes are unaffected since they are always square.
- **Subscriber, moderator, and other badges show up faster.** Badge art used to load after several other chat setup steps finished, so early messages in a channel could briefly show plain text labels ("MOD", "SUB") instead of the real badge icons. Badge art now loads at the same time as everything else, so it appears sooner.

## 1.10.3 - 2026-07-22

Makes the Windows update installation itself interruption-safe.

### Fixed
- **Updating can no longer leave PureTV unable to open.** The installer now preserves the working app until every replacement file is ready, switches the launcher configuration last, waits for every PureTV process to close, and verifies the finished installation before reopening. This prevents the missing `PureTV for Twitch.cfg` error seen during the 1.10.2 update.

## 1.10.2 - 2026-07-22

Keeps live streams playing through ad transitions and makes Windows updates dependable across every PureTV release channel.

### Fixed
- **Streams no longer freeze a few minutes after opening.** Twitch can number the same live broadcast differently when PureTV switches between clean backup playlists. PureTV now keeps one continuous playback timeline through those switches, repairs playlist numbering when ad segments are removed, and automatically reconnects if the video clock genuinely stops advancing.
- **The Windows updater always finds the newest Windows installer.** Android and TV releases can no longer hide a newer Windows update, even though every PureTV app shares the same GitHub releases page.

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
- **In-app updates that failed with a "signature check failed" error now recover on their own.** If a download arrives corrupted or incomplete (the usual cause), PureTV re-downloads once and tries again instead of giving up, so you shouldn't need to grab updates from the website anymore.
- **Clear, specific reasons when an update can't proceed.** Instead of one generic error, PureTV now tells you whether the download was bad versus a problem on your PC, and writes the details to a log (`%APPDATA%\PureTwitch\update.log`) so any remaining issue can be pinned down immediately.
- **Updates no longer leave old files behind.** Installing over a previous version now cleans up the old program files first, so your install doesn't slowly bloat with every update.

## 1.8.0 - 2026-06-17

GPU upscaling arrives, the emotes you type finally show up, and a big round of reliability and safety fixes under the hood.

### New
- **Sharper streams with your graphics card.** In the player's gear menu you can switch the playback engine to mpv and turn on GPU upscaling. Pick **Sharp** for live content (gaming, IRL, sports) or **Anime** for animation, **hold X** to instantly compare against off, and press **F3** to see exactly what's happening (source size, output size and the scaler in use). It's off by default, and the classic player is untouched until you opt in.

### Fixed
- **Your own emotes show when you send them.** Emotes you type or pick (Twitch, BTTV, FFZ and 7TV) now appear (animated) in your own messages instead of as plain text.
- **Settings no longer freezes the window.** Opening Settings while a stream was playing could briefly hang the whole app. That's gone.
- **Cleaner chat.** Replies and channel notices no longer show stray characters like `\s` in the quoted text.
- **Even fewer ads slip through.** Several edge cases where an ad could briefly play, or where a refresh could stutter, have been closed.
- **Your saved data is safer.** Follows, watch progress and settings are now written so they can't be left half-finished or corrupted if the app or PC stops unexpectedly.
- **Safer updates.** Update downloads are now locked to secure (HTTPS) connections.

## 1.7.2 - 2026-06-15

Streams play again, chat gets Enter-to-send, and in-app updates install reliably.

### New
- **Press Enter to send chat.** Send a message with the Enter key instead of clicking the arrow. Tab still completes an emote suggestion, and the arrow button is still there if you prefer it.

### Fixed
- **Streams load again.** Twitch changed something on their side that stopped PureTV from starting any stream: the player stayed black and the ad-blocker was stuck on "Checking…". PureTV now asks Twitch for streams in a way that doesn't depend on that, so live channels and past broadcasts play normally again.
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

## 1.2.0 - 2026-06-14

A complete visual redesign. PureTV now looks like a proper cinema.

### New
- **A brand-new editorial look.** Every screen has been redesigned around a refined dark theme, confident typography, and stream thumbnails treated like cover art.
- **Richer chat.** Messages now show timestamps, moderator/subscriber badges, and emotes.

### Improved
- **Cleaner player controls**, including an easier quality switcher.
- **Home, Browse, Channel, Search, Settings and Sign-in** are all more readable, more spacious, and more polished.
- Crisper buttons, cards, and hover effects throughout.

### Fixed
- Loading and empty screens no longer show blank gray boxes.

## 1.1.0 - 2026-06-14

A smoother, more secure sign-in and safer automatic updates.

### New
- **Simpler sign-in.** PureTV now opens Twitch in your browser, you tap **Authorize**, and you're in, with no more local pop-up window during login.

### Improved
- **Safer updates.** PureTV now checks that every update really came from us before installing it, so a tampered download can't sneak in.

### Fixed
- Various behind-the-scenes security and reliability fixes.

> Heads up: this update signs you out once. Just sign in again with the new, quicker flow.

## 1.0.8 - 2026-06-08

- A cleaner, more polished look and smoother window behavior.
