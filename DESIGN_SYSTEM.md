# PureTV for Twitch — Design System

Section 12.2 of the build spec is deliberate on this point: **no UI code is
shared between the three platform apps** — phone/tablet (`app-android`),
TV (`app-tv`, Leanback/D-pad), and desktop (`app-windows`, mouse/keyboard) each
own their composables outright, because the interaction models are too
different to fight a shared abstraction. What *is* shared is the **visual
language** — the same palette, type scale, and motifs re-declared per platform
so each can adapt them to its own layout constraints without coordination
overhead. This document is the single source of truth for that shared
language; if you change a value here, update all three theme files
(`Theme.kt` ×2, `DesktopTheme.kt`) to match.

## Brand palette

| Token | Hex | Used for |
|---|---|---|
| `Background` | `#0A0A0F` | App background — near-black, lets stream thumbnails and video be the brightest thing on screen |
| `Surface` | `#141420` | Cards, sidebars, chat panels |
| `SurfaceVariant` | `#1E1E2E` | Nested surfaces, input fields, chips (unselected state) |
| `TwitchPurple` | `#9B5DE5` | Primary brand accent — buttons, selected states, focus rings |
| `TwitchPurpleLight` | `#C77DFF` | Secondary accent — links, section headers, usernames in chat (fallback color) |
| `AdBlockGreen` | `#06D6A0` | "Ads blocked / filtered" status — the single most important color in the app (Section 4: ad-blocking is the core value proposition) |
| `TextPrimary` | `#E8E8F0` | Primary text on dark surfaces |
| `TextSecondary` | `#888899` | Secondary/supporting text |
| `TextMuted` | `#555566` | Placeholder text, disabled states, least-emphasis labels |
| `Live` | `#E53935` | "LIVE" badges, ad-block-off warnings, errors |
| `Online` | `#43A047` | Online/positive status (distinct from `AdBlockGreen` — reserved for account/connection state, not ad-block state, so the two never get visually confused) |
| `Warning` | `#FFB703` | Degraded states — e.g. "AD FILTERED" (ads detected and stripped, but the primary proxy strategy isn't healthy) |

### Why near-black and purple?

Twitch's own brand purple is `#9146FF`; PureTV's `#9B5DE5` is deliberately
*close but distinct* — recognizable as "Twitch-adjacent" without implying
official affiliation (every app's about/settings screen and the root README
carry an "Unaffiliated with Twitch Interactive, Inc." disclaimer for this
reason). The near-black background (`#0A0A0F` rather than pure `#000000`)
reduces OLED smearing on TV displays and keeps thumbnails/video the visual
focal point rather than competing with a stark background.

### Status-color discipline

Three different "good" greens would be confusing, so there are exactly two,
with strictly separated meanings:

- `AdBlockGreen` (`#06D6A0`, teal-leaning) — *only* for ad-block status pills
- `Online` (`#43A047`, true green) — account/connection/follow status

Mixing these up is treated as a visual bug, not a style nitpick — users learn
to trust the teal pill at a glance, and that trust breaks if the same color
shows up meaning something else elsewhere.

## Typography

All three platforms use Material 3's default type scale
(`MaterialTheme.typography`) rather than a custom font — this keeps platform
defaults (Roboto on Android/TV, the OS default sans-serif on desktop) and
avoids bundling font files. Components reference scale steps semantically
(`headlineMedium` for screen titles, `titleMedium` for card/row headers,
`bodyMedium`/`bodyLarge` for content, `labelSmall`/`labelMedium` for badges and
chips) rather than hard-coded sizes, so platform-level type-scale tuning
(e.g. TV's larger default sizes for 10-foot viewing) propagates automatically.

## Spacing & shape

- **Corner radii**: `8.dp` for interactive surfaces (buttons, chips, input
  fields, video containers), `12.dp` for cards/sections, `RoundedCornerShape`
  with `width / 2` for circular avatars (`size(N.dp).clip(RoundedCornerShape(N/2.dp))`)
- **Padding rhythm**: `4 / 8 / 12 / 16 / 20 / 24.dp` — content containers use
  `24.dp` (phone/desktop) or larger for TV's overscan-safe margins; internal
  card padding is `16.dp`; tight groupings (chip rows, icon+label pairs) use
  `6–8.dp`
- **`Arrangement.spacedBy(...)`** is preferred over manual `Spacer`s for
  consistent rhythm in rows/columns of repeated elements (chip rows, stat
  groups)

## Motifs that recur across all three apps

- **Status pills**: a small colored dot (`8.dp` circle) + label in a
  `SurfaceVariant`-on-`Surface` rounded-rect chip. Used for ad-block status,
  live/offline badges, and connection state. Always dot-then-label, never the
  reverse — consistent left-to-right "traffic light" scanning.
- **Initial-letter avatars**: when no profile image is available/loaded yet,
  show a circular `SurfaceVariant` box with the channel's first initial,
  uppercased, in `TextSecondary`. This is the same fallback on all three
  platforms and in both light-load and error states — never a generic
  "broken image" icon.
- **The ad-block status pill is always visible during playback.** This is a
  product requirement, not just a design one (Section 10.3 / Final Agent
  Instructions #5: "a stream playing with ads is a failure state — the user
  must always be able to see, at a glance, whether ad-blocking is working").

## Where each platform diverges (intentionally)

| Concern | Phone/tablet | TV | Desktop |
|---|---|---|---|
| Navigation | Bottom bar / drawer | Leanback side drawer, focus-redirected | Fixed left `NavigationRail` |
| Input model | Touch | D-pad / remote — every interactive element needs a visible focus state | Mouse + keyboard — hover states, cursor-aware controls |
| Chrome density | Compact, gesture-driven | Large touch targets, generous spacing for 10-foot viewing | Information-dense, supports resizable windows |
| Text input | System soft keyboard | On-screen keyboard / voice (search) | Native hardware keyboard — `BasicTextField` used directly |

## Updating this document

If you add a new shared color, motif, or spacing convention, update **all
three** theme files first (so the design system reflects shipped reality, not
aspiration), then update this document, in that order. The reverse — designing
here first — tends to produce values that don't survive contact with a
specific platform's constraints (TV's overscan margins being the most common
culprit).
