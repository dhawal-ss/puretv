# PureTV — Premium Experience Plan

> Goal: make PureTV feel like a Fortune-500 tech-company product — so polished, fast, and customizable that Twitch users switch. This plan synthesizes five research streams (visual design, motion, theming/customization, components, and product/UX) into one prioritized roadmap, grounded in the actual `app-windows` codebase.

## The thesis

PureTV already has three things most "Twitch alternatives" never get: a genuinely native shell (real Aero Snap, custom chrome), an ad-free playback pipeline (the stateless `LocalStreamProxy` on `:7979`), and a clean token-based theme. What's missing is the **premium finish** (type, depth, motion), the **"it's mine" customization layer**, and **2-3 signature features Twitch fundamentally can't match**. We win on all three at once.

**Positioning line:** *"Watch four streams at once, ad-free, switch instantly, and never lose your place."*

## Three load-bearing technical facts (design within the grain)

1. **The VLC video surface is a heavyweight AWT Canvas that renders *above* Compose/Skia.** You cannot draw Compose UI *on top* of live video. Everything premium must go *beside/around* the canvas — or use *multiple canvases side-by-side*. This is why Multi-view, PiP, and Quick-Switch are cheap, and "stats overlay on the picture" is not.
2. **The proxy is stateless and per-request**, so N VLC instances can pull N streams concurrently with zero backend work. Multi-stream is almost free architecturally.
3. **A lot is half-built and unused:** `EmoteRepository` is already loaded but chat renders raw text; `lowLatencyMode`, `chatFontSize`, `showBadges`, BTTV/7TV/FFZ, `compactMode` are already persisted but never surfaced; `UserRepository.loadFollows` (real Twitch follow graph) exists but is never called. Big value sitting on the floor.

---

## Pillar 1 — Premium visual finish ("looks expensive")

The single biggest "this is just another Material app" tell is the **default Roboto font + flat surfaces**. Fix type, depth, and accent discipline and the app jumps a tier.

**Highest-leverage moves (ranked):**
1. **Bundle Inter Variable** as the app font (Linear/Raycast/Superhuman all run Inter; SIL OFL). Wire into `PureTvDesktopTypography`. Mono (Geist Mono / JetBrains Mono) for timestamps/stats.
2. **Type discipline:** negative tracking on large sizes (−1.0 to −0.3sp), generous line-height on body, positive tracking on tiny labels; weights clustered at 400/600/700 only. (Full scale table below.)
3. **Fix the ink ladder:** current `textSecondary #7B7B90` / `textMuted #484858` are too dim & too blue. Move to a 4-rung near-neutral ladder (add `textTertiary`).
4. **Depth via the surface ladder + hairlines, not shadows.** Linear/Raycast use *zero* drop shadows on flat elements — each step lighter = one step closer. Cards: `surfaceVariant` + 1px `hairline` + a 6% top inner-highlight; hover steps to `surfaceRaised`.
5. **Accent discipline:** purple becomes a scalpel — primary CTAs, active nav indicator, focus rings, the live scrubber. Everything else neutral. The teal ad-block green appears *only* on the ads-blocked affordance (your one proprietary signal).
6. **Accent glow:** a soft radial bloom of the accent behind the focused card / play button (`Brush.radialGradient`, drawn — cheap).
7. **Thumbnails:** 10dp radius, hover `scale(1.03)`, bottom gradient scrim (you already have `bottomScrim`), redesigned LIVE pill (bold, +0.5sp tracking, 1px inner border).
8. **Glassmorphism** on the title bar + chat panel + auto-hiding player controls via **Haze** (chrisbanes, Skiko/Desktop backend) — the strongest "native premium OS app" cue. (GPU-costly; gate behind a "reduce transparency" toggle.)
9. **Skeleton shimmer** for grid loads (you have a `Skeleton`; refine to draw in `drawWithCache`, no per-frame brush alloc).
10. **Custom soft shadows** for floating surfaces only (menus, modals, player bar) via skia `MaskFilter.makeBlur` — Material's `shadow` can't tune blur/spread.
11. **Lucide line-icon set** (ISC license) replacing Material filled icons — uniform 1.75px stroke.
12. **Subtle full-window grain/noise** overlay (2-4% alpha, SkSL shader) — kills dark-gradient banding, adds the "expensive paper" finish.

**Proposed type system (Inter Variable):**

| Style | Size | Weight | Tracking | Line | Use |
|---|---|---|---|---|---|
| displayLarge | 32 | 700 | −1.0 | 38 | hero / empty-state |
| headlineLarge | 26 | 700 | −0.6 | 32 | screen titles |
| headlineMedium | 21 | 600 | −0.4 | 28 | row headers |
| titleLarge | 18 | 600 | −0.3 | 24 | channel/card title |
| titleMedium | 15 | 600 | −0.1 | 22 | active nav, subtitles |
| bodyLarge | 15 | 400 | 0 | 23 | body, chat |
| bodyMedium | 13.5 | 400 | 0 | 20 | secondary text |
| labelLarge | 13 | 600 | +0.1 | 16 | buttons |
| labelMedium | 12 | 500 | +0.2 | 16 | metadata |
| labelSmall | 10.5 | 700 | +0.6 | 14 | LIVE badge, eyebrows |

**Color/material refinement (PURE_DARK):** deepen surfaces (`background #07070E`, `surface #0E0E18`, `surfaceVariant #16161F`, `surfaceRaised #1D1D29`, `surfaceHover #25253A`); ink ladder (`#F4F4F8 / #C2C4CE / #8A8C99 / #5E6070`); hairlines `0x14FFFFFF` / `0x24FFFFFF` + new `innerHighlight 0x0FFFFFFF`; standardized radius scale (xs6 / sm8 / md10 / lg14 / pill).

> ⚠️ **Reconcile the accent:** `DESIGN_SYSTEM.md` says `#9B5DE5` but `DesktopTheme.kt` `buildColors` actually uses `#9147FF`. Pick one before the glow/gradient work so it's consistent.

---

## Pillar 2 — Motion & animation ("feels alive")

Two gaps win the most: **route changes are instant cuts**, and **hover/press are hand-rolled `pointerInput` loops duplicated in 5 files**. Fix both.

**Philosophy:** physics over duration (springs, not linear tweens); choreography over simultaneity (stagger); purposeful not decorative; interruptible/continuous; restraint + 2-3 earned "delight" moments.

**Catalog (prioritized):**
- **Tier 1 (hero):** shared-element thumbnail→player morph (`SharedTransitionLayout` + `sharedBounds`, morphing the *thumbnail into a placeholder* the VLC surface then fills); LIVE pulse (`rememberInfiniteTransition`); card hover-lift via spring (scale 1.03 + shadow + accent overlay) in `graphicsLayer`.
- **Tier 2 (structure):** directional route transitions (`AnimatedContent` + slide/fade by nav depth); grid entrance staggering (capped at ~12); sidebar rail↔expanded width spring; refined shimmer.
- **Tier 3 (feedback):** button press/hover; volume/seek interactions; animated viewer counts (`AnimatedContent` per digit); ambient now-playing glow *behind* the player; theme-switch crossfade; toast slide-ins; animated focus/hover ring; subtle scroll parallax.

**Build:** a `ui/motion/` package of reusable primitives — `Modifier.pressScale()`, `Modifier.hoverLift()`, `Shimmer()`, `AnimatedNumber()`, `LivePulse()`, `Modifier.focusRing()`, `Modifier.animateEntrance(index)`.

**Motion tokens:** replace the 3-int `PureTvMotion` with structured durations (instant 80 / fast 150 / medium 250 / slow 400 / ambient 1100) + easings (standard/emphasized/decelerate/accelerate) + spring presets (snappy/standard/gentle/bouncy). Add a **reduce-motion** `CompositionLocal` that every primitive consults (`snap()` for springs; static end-state for infinite effects; `Crossfade` fallback for shared-element).

**Performance:** animate in `graphicsLayer`/`drawBehind`/`drawWithCache` (skip recomposition+layout); prefer `MutableInteractionSource` + `collectIs{Hovered,Pressed}AsState` over raw pointer loops; `derivedStateOf` for scroll-driven values; never allocate in draw/measure.

---

## Pillar 3 — Theming & customization ("it's mine")

The wedge against Twitch: Twitch gives **zero** appearance control. PureTV makes the whole app reskinnable.

**Engine architecture (4 layers):** seed inputs (accent, mode, surface style, contrast, amoled) → `MaterialKolor.dynamicColorScheme(...)` (WCAG-safe tonal Material3 scheme) → derived semantic tokens (`PureTvColors`, now *generated* not hand-authored) → CompositionLocals. The 5 existing variants become **named presets** (seed tuples) — fully backward compatible. Status colors (live/online/warning/adBlockGreen) stay **pinned** and contrast-guarded so "live red" never turns purple.

**Theme features:** Light/Dark/**System-follow** (detect via `jSystemThemeDetector` or `reg query …AppsUseLightTheme`); **full accent picker** (presets + custom hex + `java.awt.Robot` eyedropper); AMOLED/true-black; high-contrast; reduce-transparency; reduce-motion; density (comfortable/compact); UI font scaling (via `LocalDensity` fontScale); custom **background image** with blur+scrim (Telegram-style); **per-streamer accent** (auto-extract from avatar via `kmpalette`); user-created themes with **import/export** (`.puretvtheme.json`) and **share-as-code** (`puretv1:<base64>` paste string, Slack-style).

**Customization features ranked by "would make someone switch":** accent picker → light/dark/system → ambient mode + player prefs → per-streamer themes → chat appearance suite (compact/cozy, font/badge/emote size, readable usernames, blocked-word filter, pop-out) → customizable Home (reorder/hide sections) → AMOLED/a11y → custom background → sidebar customization → notifications → theme sharing.

**Implementation:** add `com.materialkolor:material-kolor`; extend `SettingsDto` (+ `AppSettings` + both mappers — keep in lockstep) with the new fields (the store already does `ignoreUnknownKeys` so old `settings.json` keeps working); write large data (themes, backgrounds) to sibling files under `%APPDATA%/PureTwitch/`; rebuild `SettingsContent` into a tabbed IA with a **live preview pane**.

---

## Pillar 4 — Premium components & interaction ("feels tactile")

Currently buttons are bare Material3 `IconButton`/clickable `Box` with `indication = null` (no feedback), inputs are bare `BasicTextField`, and hover is reimplemented by hand everywhere.

**Foundation first:** a `ui/components/Interactions.kt` with `Modifier.premiumHover()`, `pressScale()`, `focusRing()`, `glowElevation()`, `hoverLift()`, `tooltipOnHover()` — one `MutableInteractionSource` per component drives hover+press+focus consistently, replacing the ~5x duplicated boilerplate.

**Signature components:**
- **`PureButton` system** — variants (Primary gradient / Secondary / Ghost / Destructive / IconOnly / Toggle), 3 sizes, all 6 states (rest/hover/pressed/focused/disabled/loading). Press-scale 0.96 + accent glow + focus ring; **ripple removed** (ripple reads "Android," not desktop-premium).
- **Command Palette (Ctrl-K)** — the signature Fortune-500 moment: fuzzy filter, keyboard nav, grouped Recent/Navigation/Actions, per-row shortcut chips, `>` to scope to actions. Doubles as instant channel quick-switch.
- **`PureTextField`** — leading icon, clear button, animated focus-accent border.
- **`PureTooltip`** — delayed, themed, shows the keyboard shortcut inline (`Fullscreen [F]`).
- **HoverCard** (refactor `StreamCard`), collapsible **NavigationSidebar** with a sliding selection indicator, right-click **context menu** (`CursorDropdownMenu`), **Toast** system, **SegmentedControl** (quality), upgraded Slider/Switch, standardized Empty/Error states, **Onboarding** coach-marks.

**Keyboard-first UX:** a centralized `Shortcut` enum (single source of truth for tooltips + a `?` cheat-sheet overlay); global chords via `onPreviewKeyEvent` on the Window root; **keep the existing AWT `KeyEventDispatcher`** for player hotkeys (it survives the VLC canvas stealing focus — you already solved this).

---

## Pillar 5 — Switch-worthy product features

Honest current state: the **Channel page is a stub** (letter-tile avatar, no preview/title/VODs/clips), **Home isn't personalized** (just top-20 global), **chat is text-only**, **no history/PiP/multi-view**, and **no keyboard nav outside the player**.

**The 3 signature "WOW" features (lead the repositioning with these):**
1. 🥇 **Multi-Stream Mosaic ("Watch Wall")** — 2-4 streams at once, one audio-focused tile. The thing Twitch fundamentally can't do, and the architecture is *accidentally perfect* for it (stateless proxy + N side-by-side VLC canvases = no overlay conflict). Make `VlcPlayer` per-tile instantiable. **The flagship demo.**
2. 🥈 **Ad-free + Picture-in-Picture together** — playback never stops while you browse. The singleton `VlcPlayer` already survives navigation; hoist its surface above the route `when` and dock a mini-player bottom-right. Ad-free hooks them; PiP makes them stay.
3. 🥉 **Command-K Quick-Switch** — instant, keyboard-driven, zero-reload, zero-ad channel hopping. "Twitch at the speed of thought."

**Full feature ranking (impact vs effort):** Multi-view → PiP/mini-player → Home personalization (wire the real follow graph) → Watch History + Resume (clone `FollowStore`) → keyboard nav + Ctrl-K → instant quick-switch → pop-out chat → rich chat (emotes/badges — half-built) → ambient/theater+ → live "going-live" toasts + system tray → stream stats in the controls bar → VOD/Clip viewing (needs new Helix endpoints — last).

---

## Unified roadmap

### NOW — credibility layer (cheap, removes the "unfinished" smell)
- **Visual foundation:** bundle Inter + type scale + ink/surface ladder + accent discipline + radius standardization (token-only edits in `DesktopTheme.kt`).
- **Interaction foundation:** build `Interactions.kt` modifiers; ship `PureButton` + `PureTextField` + `PureTooltip`; swap into existing screens (kills duplicated hover code).
- **LIVE pulse, hover-lift, skeleton shimmer, thumbnail polish.**
- **Fix the unfinished bits:** real profile images on Channel/Search (data already present), real channel metadata, auto-redirect to Home after login.
- **Watch History + Continue Watching** (local `FollowStore` clone) and **wire the real Twitch follow graph** into Home.
- **Navigation back-stack** (replace the flat `Route` — prerequisite for PiP/Mosaic).
- **App-level keyboard shell + Ctrl-K command palette.**

### NEXT — the signature launch
- **Picture-in-Picture / persistent mini-player.**
- **Multi-Stream Mosaic** (the flagship).
- **Instant Quick-Switch overlay.**
- **Rich chat** (connect the already-loaded `EmoteRepository` + persisted settings).
- **Theme engine** (MaterialKolor): light/dark/system + accent picker + AMOLED/a11y; tabbed Settings with live preview.
- **Route transitions + shared-element thumbnail→player.**

### LATER — depth & new infrastructure
- Live "going-live" notifications + system tray; pop-out chat window; stream stats in controls bar; ambient/theater+ polish; custom backgrounds + per-streamer accents + theme sharing; VOD & clip viewing (new endpoints); reduce-motion pass across everything.

## Quick wins to ship this week (token/Modifier edits, low risk)
1. Bundle **Inter** + apply the type scale (tracking + line-height).
2. Retune **ink + surface ladders**, add `textTertiary`.
3. **Accent audit** — strip purple from decorative spots.
4. **LIVE badge** redesign + thumbnail bottom scrim + hover scale.
5. Build `Interactions.kt` (`pressScale`/`hoverLift`/`premiumHover`/`focusRing`) and adopt in `StreamCard`/`NavItem`/`WinButton`.
6. `pointerHoverIcon(PointerIcon.Hand)` on every clickable (one-line desktop premium cue).
7. Reconcile the **accent hex** discrepancy.

## New deps to add
- `Inter` + mono font (Compose resources) · Lucide icons · `dev.chrisbanes.haze:haze` (glass) · `com.materialkolor:material-kolor` + `kmpalette` (theming/extraction) · optional `jSystemThemeDetector` (system light/dark).

## Files this touches most
- `ui/theme/DesktopTheme.kt` — type, tokens, motion, theme engine
- `ui/components/` — new `Interactions.kt`, `Buttons.kt`, `Inputs.kt`, `Tooltip.kt`, `CommandPalette.kt`, `Feedback.kt`; refactor `Premium.kt`
- `ui/App.kt` — route transitions, back-stack, keyboard shell, PiP surface hoist
- `ui/screens/*` — adopt components; Channel/Home/Search enrichment
- `player/VlcPlayer.kt` — per-instance for Mosaic/PiP
- `data/DesktopSettingsStore.kt` (+ core `AppSettings`) — customization fields
- `data/FollowStore.kt` pattern → new `HistoryStore`
