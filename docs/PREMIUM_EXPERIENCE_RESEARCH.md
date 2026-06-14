# PureTV — Premium Experience: Full Research & Recommendations

> Companion to `PREMIUM_EXPERIENCE_PLAN.md` (the executive synthesis + roadmap). This file preserves the **complete** detail from all five research streams — every ranked recommendation, Compose-Desktop code sketch, token table, and named exemplar. Grounded in the actual `app-windows` codebase.
>
> Status: the visual-foundation slice (Part 1 tokens + Part 4 interaction primitives + `PureButton`) shipped in **v1.0.8**. Everything else is proposed.

**Contents**
1. Visual design language
2. Motion & animation
3. Theming & customization
4. Components & interaction patterns
5. Product/UX audit & switch-worthy features
6. Cross-cutting technical constraints

---

# Part 1 — Visual Design Language

Anchored in Linear, Vercel/Geist, Raycast, Spotify, Superhuman. Current state: Material-3 default font (Roboto), no tracking discipline, an unused `surfaceRaised` ladder, dim/blue text colors. The bones are right; what's missing is **type, optical depth, accent discipline**.

## Top visual upgrades (ranked by impact-to-effort)

1. **Bundle Inter Variable as the app font** — the single biggest "just another Material app" tell is Roboto. Linear/Raycast/Superhuman all run Inter (SIL OFL). Enable OpenType `ss03`/`cv05`. *Compose:* drop `Inter-VariableFont.ttf` into `src/main/composeResources/font/`, build a `FontFamily`, set on every `TextStyle`. Geist is the alternative; Inter is the safer pick. **[shipped partially in 1.0.8: type discipline applied; font binary still to bundle]**
2. **Tracking + line-height discipline** — negative tracking on display/headings (−1.0 to −0.3sp), generous line-height on body, positive tracking on tiny labels. **[shipped 1.0.8]**
3. **Fix the text colors** — old `textSecondary #7B7B90` / `textMuted #484858` were too dim and too blue. Move to a 4-rung near-neutral ink ladder (add `textTertiary`). **[shipped 1.0.8]**
4. **Depth from the surface ladder + hairlines, not shadows** — Linear/Raycast use *zero* drop shadows on flat elements; each step lighter = one step closer. Card = `surfaceVariant` + 1px `hairline` + 6% top inner-highlight; hover → `surfaceRaised`. **[partially shipped 1.0.8: ladder + innerHighlight token]**
5. **Accent discipline** — purple becomes a scalpel: primary CTAs, active nav indicator, focus rings, the live scrubber. Everything else neutral. Teal ad-block green appears *only* on the ads-blocked affordance.
6. **Glow accents** — soft radial bloom of the accent behind the focused thumbnail/play button. *Compose:* `Modifier.drawBehind { drawCircle(Brush.radialGradient(listOf(accent.copy(0.16f), Color.Transparent), radius = size.maxDimension*0.7f)) }` — no blur pass needed.
7. **Thumbnail treatment** — 10dp radius, hover `scale(1.03)`, bottom gradient scrim, redesigned LIVE badge (pill, +0.5sp tracking, 1px inner border). **[shipped 1.0.8: badge + scrim + hover lift]**
8. **Glassmorphism on title bar / chat panel / player controls via Haze** — frosted translucency is the strongest "native premium OS app" cue (Arc, Apple Music). *Compose:* `dev.chrisbanes.haze:haze` (Skiko/Desktop backend); `Modifier.hazeEffect(state){ blurRadius = 16.dp; noiseFactor = 0.05f; backgroundColor = colors.surface.copy(0.6f) }`. GPU-costly — gate behind a "reduce transparency" toggle.
9. **Skeleton shimmer placeholders** — never show a blank grid or spinner. *Compose:* animate a diagonal gradient offset via `rememberInfiniteTransition`, draw in `drawWithCache`. **[exists; refine to drawWithCache]**
10. **Soft colored shadows on floating elements only** (menus, modals, player bar): `rgba(0,0,0,0.5) 0 8px 24px`. Material's `Modifier.shadow` can't tune blur/spread — use `drawIntoCanvas` + `org.jetbrains.skia.MaskFilter.makeBlur`. Flat cards stay shadowless.
11. **Custom line-icon set (Lucide, ISC license)** — uniform ~1.75px stroke replacing Material's filled icons. Keep 20–24dp, `tint = textSecondary`, accent only when active.
12. **Subtle full-window grain/noise overlay** (2–4% alpha SkSL shader) — kills dark-gradient banding, adds the "expensive paper" finish. *Compose:* `RuntimeEffect.makeForShader(...)` → `ShaderBrush` in a top-level `Box.drawBehind`.

## Proposed type system (Inter Variable)

Mono: Geist Mono / JetBrains Mono for timestamps/stats. Weights cluster at 400/500/600/700.

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

Reserve uppercase + wide tracking for `labelSmall` only.

## Color / material refinement (PURE_DARK)

**Surfaces** (each rung ~+8–10 lightness — this *is* the elevation system): `background #07070E`, `surface #0E0E18`, `surfaceVariant #16161F`, `surfaceRaised #1D1D29`, `surfaceHover #25253A`. **[shipped 1.0.8]**

**Hairlines:** `hairline 0x14FFFFFF` (~8%), `hairlineStrong 0x24FFFFFF` (~14%), `innerHighlight 0x0FFFFFFF` (~6% top-edge glow). **[shipped 1.0.8]**

**Ink ladder:** `textPrimary #F4F4F8`, `textSecondary #C2C4CE`, `textTertiary #8A8C99`, `textMuted #5E6070`. **[shipped 1.0.8]**

**Accent (disciplined use):** `accent #9B5DE5` (primary action / focus / active nav / scrubber), `accentLight #C77DFF` (gradient end only), `adBlockGreen #00C896` (ads-blocked ONLY), `live #EB0400` (LIVE pill). **[shipped 1.0.8 — reconciled the #9B5DE5 vs #9147FF mismatch]**

**Gradients (Brush):**
```kotlin
Brush.linearGradient(listOf(Color(0xFF9B5DE5), Color(0xFFC77DFF)))               // accent CTA
Brush.radialGradient(listOf(Color(0x299B5DE5), Color.Transparent))              // glow behind active card
Brush.verticalGradient(0.45f to Color.Transparent, 1f to Color(0xE6000000))    // thumbnail bottom scrim
```

**Shadows:** soft = skia `MaskFilter` blur sigma ~12, `0x66000000`, y+8dp — floating surfaces only. Flat cards: NO shadow.

**Glass (Haze):** `blurRadius 16dp`, tint `surface.copy(0.62f)`, `noiseFactor 0.05f`, 1px top hairline.

**Radius scale:** xs 6 (chips/badges) · sm 8 (buttons/inputs) · md 10 (thumbnails/cards) · lg 14 (panels/modals) · pill 999. **[shipped 1.0.8 as PureTvShape]**

## Quick wins vs larger investments

- **Quick (token/TextStyle edits):** Inter wiring, type scale, ink/surface ladders, LIVE badge + scrim, accent audit, radius scale. *(most shipped in 1.0.8)*
- **This sprint (custom drawing/anim):** hover scale + radial accent glow, skeleton shimmer refine, card inner top-highlight, Lucide icons.
- **Larger (new deps/GPU):** Haze glassmorphism, custom skia soft shadows, full-window SkSL noise.

**Sources:** Linear redesign; awesome-design-md token extracts; Geist/Inter (OFL); Haze for CMP; Skia shaders in Compose Desktop; colored blurred shadows in CMP.

---

# Part 2 — Motion & Animation

Biggest gaps: **route changes are instant cuts**, and **hover/press are hand-rolled `pointerInput` loops** duplicated in 5 files. On Desktop, prefer `Modifier.hoverable` + `MutableInteractionSource` over raw `awaitPointerEvent` loops. `SharedTransitionLayout`/`sharedBounds`/`animateBounds` are available in Compose 1.7+/1.8.

## Philosophy
1. **Physics, not duration** — default to `spring()` for interactive motion; reserve `tween`+easing for choreographed entrances.
2. **Choreography over simultaneity** — stagger (20–30ms apart).
3. **Purposeful, not decorative** — every animation answers where/what changed.
4. **Interruptible & continuous** — velocity-preserving; never block input.
5. **Restraint + earned delight** — 95% sub-200ms and near-invisible; spend the delight budget on 2–3 hero moments.

## Catalog (prioritized)

### Tier 1 — hero
**1. Shared-element thumbnail → player** (highest impact). `SharedTransitionLayout` + `Modifier.sharedBounds(key = "thumb-$login")` driven by the `route` swap in `AnimatedContent`. **Caveat:** the live VLC video is a heavyweight AWT surface *above* Skia — you cannot morph into the video itself. Morph the *thumbnail* into a placeholder Box at the player's final bounds; crossfade it out under the AWT surface once VLC reports first frame.
```kotlin
SharedTransitionLayout {
  AnimatedContent(route, transitionSpec = { fadeIn(tween(250)) togetherWith fadeOut(tween(250)) }) { r -> /* screens */ }
}
// thumbnail Box:
Modifier.sharedBounds(rememberSharedContentState("thumb-${login}"), animatedScope, resizeMode = ResizeMode.ScaleToBounds())
```

**2. LIVE pulse** — red badge + under-card dot breathe.
```kotlin
val t = rememberInfiniteTransition("live")
val a by t.animateFloat(1f, 0.4f, infiniteRepeatable(tween(1100, easing = FastOutSlowInEasing), RepeatMode.Reverse))
Box(Modifier.size(7.dp).drawBehind { drawCircle(color.copy(alpha = a*0.5f), size.minDimension); drawCircle(color, size.minDimension/2) })
```

**3. Card hover lift** — replace flat tween-scale with a spring lift (scale 1.03 + shadow + accent overlay), all in `graphicsLayer` (no relayout). **[shipped 1.0.8 via hoverLift]**
```kotlin
val lift by animateFloatAsState(if (hovered) 1f else 0f, spring(Spring.DampingRatioLowBouncy, Spring.StiffnessMediumLow))
Modifier.hoverable(src).graphicsLayer { val s = 1f + 0.03f*lift; scaleX=s; scaleY=s; shadowElevation = 18.dp.toPx()*lift; clip=false }
```

### Tier 2 — structure
**4. Route transitions** — wrap `when(route)` in `AnimatedContent` with directional slide+fade by nav depth (forward = in from right; back = reverse).
**5. Grid entrance staggering** — per-item `animateFloatAsState` gated by `index*25ms` (cap index ~12); `LazyGridItemScope.animateItem()` for re-layout.
```kotlin
var show by remember { mutableStateOf(false) }
LaunchedEffect(Unit) { delay(i.coerceAtMost(12)*25L); show = true }
val p by animateFloatAsState(if (show) 1f else 0f, spring(stiffness = Spring.StiffnessMediumLow))
StreamCard(s, Modifier.graphicsLayer { alpha = p; translationY = (1-p)*24.dp.toPx() }.animateItem())
```
**6. Sidebar rail↔expanded** — `animateDpAsState(spring(...))` for width 64↔200, labels cross-fade.
**7. Skeleton refine** — switch `RepeatMode.Restart` → continuous; build brush in `drawWithCache` (no per-frame alloc).

### Tier 3 — feedback & polish
**8. Button press/hover** — `collectIsPressedAsState` + spring scale 0.96. **[shipped 1.0.8 via pressScale]**
**9. Volume/seek** — thumb springs on grab; floating "62%" bubble (`AnimatedContent` on value).
**10. Animated counts** — viewer numbers roll via per-digit `AnimatedContent` vertical slide.
```kotlin
AnimatedContent(count, transitionSpec = { if (targetState>initialState) (slideInVertically{it}+fadeIn()) togetherWith (slideOutVertically{-it}+fadeOut()) else (slideInVertically{-it}+fadeIn()) togetherWith (slideOutVertically{it}+fadeOut()) }) { Text(format(it)) }
```
**11. Ambient now-playing glow** (delight) — slow-drifting purple→green radial behind the player, `drawBehind` only. Gate behind reduce-motion.
**12. Theme-switch crossfade** — `animateColorAsState` per token so palettes morph.
**13. Toast slide-ins** — `AnimatedVisibility(slideInVertically(spring)+fadeIn, ...)`.
**14. Focus/hover ring** — animated accent ring primitive. **[shipped 1.0.8 via focusRing]**
**15. Page parallax** — hero translates slower than grid; read scroll via `derivedStateOf`, apply in `graphicsLayer` (factor ~0.3).

## Reusable motion primitives (build in `ui/motion/`)
- `Modifier.pressScale()` + `Modifier.hoverLift()` **[shipped]**
- `Shimmer()` / `Modifier.shimmer()` (drawWithCache)
- `AnimatedNumber(value)` (#10)
- `SharedElement` helpers (`Modifier.sharedThumb(key, scope)`)
- `Modifier.focusRing()` **[shipped]**
- `LivePulse()` (reduce-motion aware)
- `Modifier.animateEntrance(index)` (#5)

## Motion token system + reduce-motion
```kotlin
object Motion {
  const val instant=80; const val fast=150; const val medium=250; const val slow=400; const val ambient=1100L
  val standard   = CubicBezierEasing(0.2f,0f,0f,1f)
  val emphasized = CubicBezierEasing(0.05f,0.7f,0.1f,1f)
  val decelerate = CubicBezierEasing(0f,0f,0f,1f)
  val accelerate = CubicBezierEasing(0.3f,0f,1f,1f)
  fun <T> snappy()   = spring<T>(Spring.DampingRatioNoBouncy,   Spring.StiffnessHigh)
  fun <T> standard() = spring<T>(Spring.DampingRatioNoBouncy,   Spring.StiffnessMediumLow)
  fun <T> gentle()   = spring<T>(Spring.DampingRatioLowBouncy,  Spring.StiffnessMediumLow)
  fun <T> bouncy()   = spring<T>(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow)
}
val LocalReduceMotion = staticCompositionLocalOf { false }
@Composable fun <T> motionSpec(full: FiniteAnimationSpec<T>) = if (LocalReduceMotion.current) snap() else full
```
**Reduce-motion contract:** springs/tweens → `snap()`; infinite effects → static end-state; parallax → factor 0; shared-element/route → `Crossfade` fallback; stagger → delay 0. Wire `LocalReduceMotion` from `App.kt` next to `LocalPureTvColors`; toggle in `SettingsContent`.

## Performance (Compose Desktop / Skia)
- Animate in `graphicsLayer`/`drawBehind`/`drawWithCache`, NOT layout (skips recomposition+layout). #1 rule for the hover grid + parallax.
- Prefer `MutableInteractionSource` + `collectIs{Hovered,Pressed}AsState` over raw pointer loops.
- `derivedStateOf` for scroll-driven values.
- Don't allocate in draw/measure.
- Cap stagger ~12; stable `key`s for `animateItem()`.
- The VLC/AWT surface is above Skia — animate Compose placeholders *around* video, never over the live surface.

**Build order:** primitives → swap into existing hover/press sites → route transitions + shared element → stagger/pulse/counts → ambient/polish → reduce-motion last.

---

# Part 3 — Theming & Customization

Wedge against Twitch: Twitch gives **zero** appearance control. Exemplars: Discord custom themes (seed colors → role set), Spotify (per-content accent extraction), Arc (semantic palette roles), VS Code (theme = JSON role map; import/export), Telegram (chat theming + wallpaper), Slack (theme as a paste-able string).

## Engine architecture (4 layers)
```
Layer 0  Seed inputs       accentSeed, mode(Light/Dark/System), surfaceStyle, contrastLevel, isAmoled
Layer 1  Generated scheme  MaterialKolor dynamicColorScheme(...) → Material3 ColorScheme (WCAG-safe tonal)
Layer 2  Semantic tokens   PureTvColors (now DERIVED, not hand-authored)
Layer 3  CompositionLocals LocalPureTvColors + LocalPureTvDensity + LocalPureTvMotion + LocalChatStyle
```
The shift: today `PureTvDesktopColors` is hand-authored per variant; the engine **derives** Layer 2 from a seed via `MaterialKolor.dynamicColorScheme(seedColor, isDark, isAmoled, style, contrastLevel, modifyColorScheme)`. The 5 existing variants become **named presets** (seed tuples) — backward compatible.

## Semantic token model (grouped)
| Group | Token | Derived from |
|---|---|---|
| Surface | background / surface / surfaceVariant / surfaceRaised / surfaceHover | scheme.surface* (or #000 if AMOLED) |
| Outline | hairline / hairlineStrong | scheme.outlineVariant/outline @ low alpha |
| Accent | accent / accentLight / onAccent / accentContainer / accentGradient | scheme.primary family |
| Text | textPrimary / textSecondary / textMuted / textOnAccent | scheme.onSurface* |
| Status (pinned, NOT accent-tinted) | live / online / warning / adBlockGreen | fixed brand hex, contrast-clamped |
| Scrim | scrim / scrimSoft / topScrim / bottomScrim | opacity ramps |
| Chat | chatBg / chatHover / chatMention / usernameColors[] | derived around accent hue |

**Rule:** accent recolors structure & emphasis; status colors stay semantically fixed (live red never turns purple). Contrast guard: compute WCAG ratio; if `accent`-on-`surface` for text < 4.5:1, substitute `accentLight`/`accentContainer`.

## Theme features
Light/Dark/**System-follow** (detect via `jSystemThemeDetector` or `reg query …\Personalize /v AppsUseLightTheme`); **accent picker** (presets + custom hex + `java.awt.Robot.getPixelColor` eyedropper); AMOLED/true-black; high-contrast; reduce-transparency; reduce-motion; density (comfortable/compact); UI font scaling (via `LocalDensity` fontScale); custom **background image** (blur + scrim); **per-streamer accent** (auto-extract from avatar via `kmpalette`); user-created themes with import/export + share-as-code.

**ThemeSpec (portable — seed inputs, not baked colors):**
```kotlin
@Serializable data class ThemeSpec(
  val id: String, val name: String, val author: String? = null,
  val mode: String, val accentHex: String, val surfaceStyle: String,
  val isAmoled: Boolean = false, val contrast: Double = 0.0,
  val backgroundImage: String? = null, val backgroundBlur: Float = 0f, val backgroundScrim: Float = 0.4f,
  val overrides: Map<String,String> = emptyMap())
```
- **Share as code:** `puretv1:<base64url(json)>` paste string.
- **Export/import:** `*.puretvtheme.json` under `%APPDATA%/PureTwitch/themes/`.
- **Create/name/duplicate** from current.

## Customization feature catalog (ranked by "would make someone switch")
| # | Feature | Why it beats Twitch | Effort |
|---|---|---|---|
| 1 | Full accent picker (presets + hex + eyedropper) | Twitch has zero color choice | M |
| 2 | Light / Dark / System-follow | desktop has none; "matches my OS" | M |
| 3 | Ambient mode + ad-free player polish (default quality/volume, auto-theater, hotkeys, mini-player) | Twitch player is locked + ad-interrupted | L |
| 4 | Per-streamer accent (manual or auto from avatar) | Twitch can't; themed spaces | M |
| 5 | Chat appearance suite (density, font/badge/emote size, readable usernames, timestamps, blocked words, pop-out) | Twitch chat is rigid | M |
| 6 | Customizable Home (reorder/hide sections, density) | Twitch home is immovable | M |
| 7 | AMOLED / high-contrast / a11y / font scaling / density | Twitch desktop has none | S–M |
| 8 | Custom background / wallpaper (blur + scrim) | pure identity; nothing comparable | M |
| 9 | Sidebar customization (reorder/hide, collapse, pin favorites) | fixed on Twitch | M |
| 10 | Notification preferences (per-channel) | granular control Twitch buries | M |
| 11 | Theme share/import (code + file + gallery) | viral; community lock-in | S |

Ship #1, #2, #5, #7 first; then #3/#4 as marquee.

## Settings IA (tabbed, with live preview pane)
Appearance (mode · accent · surface style · AMOLED/contrast/transparency/motion · background · My Themes) · Layout & Density · Chat · Player · Notifications · Following/Per-channel · Playback & Ad-block · Account/About/Updates.

## Implementation
Add `com.materialkolor:material-kolor` (+ `kmpalette`). Extend `SettingsDto` (+ core `AppSettings` + both mappers, in lockstep) with the appearance/layout/chat/player/per-channel/customThemes fields — the store already does `ignoreUnknownKeys` so old `settings.json` keeps working. Large data (themes, backgrounds) → sibling files under `%APPDATA%/PureTwitch/`. Rebuild `PureTvDesktopTheme` to build from `AppSettings` + `systemDark`. Eyedropper via `java.awt.Robot`; ambient via downscaled VLCJ frame → blurred gradient; pop-out chat via a second Compose `Window {}`.

**Files:** `ui/theme/DesktopTheme.kt`, `data/DesktopSettingsStore.kt`, `ui/App.kt` (pass settings + systemDark), `ui/screens/SettingsContent.kt` (tabbed + preview), new `SystemThemeService` (DI), `ThemeSpec`/`ThemeCodec`, eyedropper overlay, ambient sampler.

**Sources:** Discord custom themes; Spotify backdrops (k-means/Vibrant); Arc palette; VS Code themes; Slack theme string; MaterialKolor `dynamicColorScheme`; kmpalette; jSystemThemeDetector.

---

# Part 4 — Components & Interaction Patterns

Current: hover hand-rolled in every component (`StreamCard`, `WinButton`, `NavItem`); inputs are bare `BasicTextField`; buttons are plain Material3 `IconButton`/clickable `Box` with `indication = null` (no feedback). Keep the existing AWT `KeyEventDispatcher` for player hotkeys (the VLC canvas steals Compose focus).

## Ranked component upgrades
1. Button system + interaction Modifiers **[shipped 1.0.8]** · 2. Reusable Modifiers **[shipped]** · 3. Command Palette (Ctrl-K) · 4. Input/Search field · 5. Tooltip · 6. Hover-lift Card **[shipped]** · 7. Sidebar polish (collapsible rail + sliding indicator) · 8. Toast system · 9. Quality → Segmented/Dropdown · 10. Context menu (right-click) · 11. Skeleton/Empty/Error · 12. Keyboard cheat-sheet (`?`) · 13. Modal/Dialog/Sheet + Onboarding.

## Design tokens to add
```kotlin
object PureTvShape { val xs=RoundedCornerShape(6.dp); val sm=8.dp...; val md=12.dp; val lg=16.dp }   // [shipped]
object PureTvElevation { val restGlow=0.dp; val hoverGlow=16.dp; val pressGlow=8.dp }
val PremiumEasing = CubicBezierEasing(0.2f,0f,0f,1f)
```

## Button system (`PureButton`) **[shipped 1.0.8]**
Variants: Primary (accent gradient), Secondary (surfaceRaised + hairline), Ghost (transparent, hover-fill), Destructive (live-red), IconOnly, Toggle. Sizes Sm/Md/Lg. States:

| State | Treatment |
|---|---|
| Rest | base fill, no glow, scale 1.0 |
| Hover | +6–8% lighter / gradient brightens, soft accent glow, hand cursor |
| Pressed | scale 0.96, glow shrinks, fill darkens |
| Focused | 2dp accent ring offset 2dp |
| Disabled | 38% alpha, no interaction |
| Loading | content → 16dp spinner, sized, disabled |

**Ripple removed** (`indication = null`) — ripple reads "Android," not desktop-premium. Feedback via scale + glow + ring. (Full sketch in `ui/components/Buttons.kt`.)

## HoverCard (refactor `StreamCard`) **[shipped 1.0.8]**
Spring lift (translationY −4 + scale 1.03) + accent glow + thumbnail brighten + on-hover reveal of a centered ▶ + "⋯" context trigger. Drive from one `MutableInteractionSource`.

## Command Palette (Ctrl-K) — the signature component
Centered `Popup` over a dimmed scrim (~560dp, lg radius, surfaceRaised, hairline, accent glow). Auto-focus input; fuzzy subsequence filter; grouped Recent/Navigation/Actions; ↑/↓/Enter/Esc; per-row `KbdChip` shortcut; `>` scopes to actions. Recents from `FollowStore`/history.
```kotlin
data class Command(val id:String, val title:String, val subtitle:String?=null, val icon:ImageVector, val shortcut:String?=null, val group:String, val run:()->Unit)
// Popup(focusable=true) → PureTextField (autofocus) + LazyColumn(grouped) with onPreviewKeyEvent nav.
```

## Input field (`PureTextField`)
Leading icon, animated placeholder, clear (×) when non-empty, focus ring that animates the border accent, trailing slot, bg lift on focus. Replaces bare `BasicTextField` in `SearchContent`/`ChatInputBar`.

## Tooltip (`PureTooltip`)
`TooltipArea(delayMillis=500)` themed (surfaceRaised, hairline) showing the shortcut inline (`Fullscreen [F]`). `KbdChip` reused in tooltips, cheat-sheet, palette, menus.

## Other signature components (concise)
- **Sidebar:** width 200↔64 animate; collapsed = icons + tooltips; replace hard-cut selected bar with a **sliding indicator** (animate pill Y); accent-tinted selected bg + 3dp left bar.
- **Context menu:** `detectTapGestures(secondary)` → `CursorDropdownMenu` (Watch / Follow / Copy link / Open channel).
- **Toast:** `ToastHost` at root backed by a `ToastController` CompositionLocal (mirror `LocalAppShell`); slide-in bottom-right, auto-dismiss 4s, info/success(adBlockGreen)/error(live).
- **Segmented control:** quality pills → single container with a sliding selected-segment bg (`animateDpAsState`).
- **Slider/Switch:** custom track (accent gradient active), thumb scales on hover/drag; switch thumb springs, track animates to accent.
- **Empty/Error:** `EmptyState(icon,title,subtitle,action)` + `ErrorState(message,onRetry)`.
- **Dialog/Sheet:** scrim + `Popup` (more modern than desktop `Dialog` window); Esc closes, Enter = primary; right-side Sheet for filters.
- **Onboarding:** 3–4 step spotlight overlay on first run (Welcome → connect Twitch → "Press Ctrl-K" coach-mark → done); persist `hasOnboarded`.

## Keyboard-first UX
Two layers: window-level `onPreviewKeyEvent` for global chords (Ctrl-K, `?`, Ctrl-,, Esc); the existing AWT `KeyEventDispatcher` for player hotkeys (survives canvas focus loss). Centralize a `Shortcut` enum as the single source of truth for tooltips + the `?` cheat-sheet overlay. Card focus via `focusable()` + `onKeyEvent`; restore focus to trigger when overlays close.
```kotlin
enum class Shortcut(val keys:String, val label:String, val scope:Scope) {
  CommandPalette("Ctrl K","Command palette",Global), Help("?","Keyboard shortcuts",Global),
  Search("Ctrl F","Focus search",Global), Settings("Ctrl ,","Open settings",Global),
  PlayPause("Space","Play / pause",Player), Fullscreen("F","Fullscreen",Player),
  Theater("T","Theater",Player), ToggleChat("C","Toggle chat",Player), Escape("Esc","Exit / close",Global) }
```

## Reusable interaction Modifiers (`ui/components/Interactions.kt`) **[shipped 1.0.8: pressScale, hoverLift, focusRing, handCursor]**
Still to add: `premiumHover(interaction, hoverColor, shape)` (bg fill), `glowElevation(glow, color, shape)` (colored shadow), `tooltipOnHover(text, shortcut)`. Convention: one `MutableInteractionSource` per component → `.clickable(indication = null)` + `.hoverable(it)` + layered modifiers.

**Repo notes:** reconciled accent (#9B5DE5) **[done 1.0.8]**; kill ripple project-wide (replace Material `IconButton` with `PureIconButton`); add `pointerHoverIcon(Hand)` everywhere **[partly done]**; glow is subtle on AMOLED — pair with gradient border. New files: `Inputs.kt`, `Tooltip.kt`, `CommandPalette.kt`, `Feedback.kt`, `Shortcuts.kt`.

**Sources:** Build Cmd-K palette (Linear/Vercel); Raycast search bar; UX of keyboard shortcuts; Superhuman cheat sheet; Compose handling interactions / InteractionSource / keyboard events.

---

# Part 5 — Product/UX Audit & Switch-Worthy Features

## Stack reality (verified from code)
- **Playback is VLC-native (heavyweight AWT Canvas), renders above Compose** — UI can't overlay video; go beside/around, or use multiple canvases side-by-side.
- **One `VlcPlayer` singleton** — multi-stream/PiP needs N `EmbeddedMediaPlayer`s (VLCJ supports it; the factory call already exists), but DI/`StreamViewModel` assume one.
- **The proxy (`LocalStreamProxy` :7979) is stateless/per-request** — N VLC instances can hit it concurrently → multi-view is cheap.
- **Data layer is thin** — only streams, users, followed channels, top games, search/channels. **No VODs, no clips, no category-search.** Clip creation needs `clips:edit` + POST /clips.
- **Follows are local-only** (`FollowStore` JSON); `UserRepository.loadFollows` (real follow graph, `user:read:follows`) exists but **is never called**.
- **Unused-but-built:** `EmoteRepository` loaded but chat renders raw text; `lowLatencyMode`, `chatFontSize`, `showBadges`, BTTV/7TV/FFZ, `chatTimestamps`, `compactMode` persisted but unsurfaced.

## Current-state UX audit (per screen)
- **Shell:** polished custom title bar (real snap). But navigation is a flat `when(Route)` with **no history stack** (tab switch resets to Top, loses state); **no global keyboard nav**; Account-as-nav-item is odd IA.
- **Home:** adaptive grid, Favorites + Live Now, skeletons. But "Live Now" is just global top-20 (no personalization/"because you follow"); **gated behind login** (empty wall for new users); **no history/resume**; capped at 20, no pagination.
- **Browse:** category box-art grid. But only top-20 games, **no search/filter/tags/scroll**.
- **Category:** instant title + live grid + skeletons. But no sort/filter/pagination; text back-button feels heavy.
- **Channel (weakest):** letter-tile avatar (real image *is* available), name, LIVE/Offline, description, Watch/Follow. **No preview/title/category/viewers/uptime/VODs/clips/schedule.** A stub.
- **Stream/player (most mature):** stable VLC, Default/Theater/Fullscreen + auto-hide, play/pause/volume/quality, follow, chat toggle, robust hotkeys, ad-block pill, IRC chat. But **chat is text-only** (no emotes/badges/3rd-party/system msgs despite plumbing); no seek/DVR/latency/buffer UI; **no PiP**; no pop-out chat; quality from a fixed enum not real variants.
- **Search:** debounced channel search. But **channels only**; letter-tile avatars (real `thumbnail_url` unused); no recents/suggestions/keyboard nav.
- **Settings (2nd-most polished):** theme swatches, quality, ad-block strategy + proxy explainer, account, About/updates. But surfaces ~30% of what's persisted (no chat/latency/compact/notifications/shortcuts).
- **Login:** clean PKCE OAuth, good states. But full-screen destination; **no auto-redirect to Home after login**.

**Top friction:** Home not personalized · no keyboard nav outside player · text-only chat · Channel is a stub · no history/resume/PiP · no multi-view (which the architecture is uniquely suited for).

## Switch-worthy features (ranked, impact vs effort)
1. **Multi-Stream Mosaic ("Watch Wall")** — 2–4 streams, one audio-focused tile. *Twitch can't do this.* HIGH feasibility: stateless proxy + N side-by-side VLC canvases (sidesteps the overlay constraint). Make `VlcPlayer` per-tile. **The flagship.**
2. **Picture-in-Picture / mini-player** — playback survives navigation. HIGH: the singleton already survives nav; hoist its surface above the route `when`, dock bottom-right when `route != Stream`.
3. **Real Home personalization** — Continue Watching → Live Follows → "more from your categories" → Trending; wire the real follow graph. MEDIUM (loaders exist).
4. **Watch History + Resume** — clone `FollowStore` → `history.json`. HIGH, self-contained.
5. **Keyboard-driven everything + Ctrl-K palette** — promote the player `KeyEventDispatcher` to app level; card focus via Compose. MEDIUM.
6. **Instant Quick-Switch overlay** — Tab → live-follows filmstrip → swap stream in place. HIGH (route key swap).
7. **Pop-out chat (second window)** — Compose multi-window; chat state already flows. MEDIUM.
8. **Rich chat (emotes/badges/3rd-party/system)** — connect the already-loaded `EmoteRepository` + persisted settings; render `InlineTextContent`. MEDIUM, half-built. Reaches parity (removes the biggest downgrade objection).
9. **Ambient / Theater+** — gradient frame + "now watching" presence chip + minimal fade HUD (chrome around the canvas, not over). MEDIUM.
10. **Live "going-live" notifications + system tray** — `HomeViewModel` already polls; diff live state + fire Windows toast. MEDIUM (native bits).
11. **Stream stats overlay** — VLCJ `media().info()`/stats in the controls bar (not over video). MEDIUM.
12. **VOD & Clip viewing** — needs new Helix `/videos` + `/clips` endpoints (VODs reuse the proxy/HLS path). Clip creation = stretch (`clips:edit` + POST /clips). LOW–MEDIUM, most backend work. Last.

## The 3 signature "WOW" features
- 🥇 **Multi-Stream Mosaic** — the one thing Twitch fundamentally can't do; the 5-second demo.
- 🥈 **Ad-free + PiP together** — ad-free hooks them, PiP makes them stay.
- 🥉 **Command-K Quick-Switch** — instant, zero-reload, zero-ad channel hopping.

> Positioning: **"Watch four streams at once, ad-free, switch instantly, and never lose your place."**

## Phased roadmap
- **NOW:** real avatars + channel metadata + login redirect; Watch History + Continue Watching; wire real follow graph; app-level keyboard shell + Ctrl-K; navigation back-stack.
- **NEXT:** PiP/mini-player; **Multi-Stream Mosaic**; Quick-Switch; rich chat; settings expansion.
- **LATER:** live notifications + tray; pop-out chat; stats overlay; ambient/theater+; VOD/clips (clip creation last).

---

# Part 6 — Cross-Cutting Technical Constraints

1. **AWT canvas is above Skia** — never draw Compose UI over live video. Put it beside/around, or use multiple side-by-side canvases. This is why Mosaic/PiP/Quick-Switch are HIGH feasibility and "overlay on video" is not.
2. **Stateless proxy** = cheap multi-view.
3. **`Modifier.blur` is fragile across Skia bumps** — use Haze for glass.
4. **Material `Modifier.shadow` can't tune blur/spread** — use skia `MaskFilter` for soft colored shadows.
5. **Keep the AWT `KeyEventDispatcher`** for player hotkeys (canvas steals Compose focus); use `onPreviewKeyEvent` for global chords.
6. **Settings store is forward-compatible** (`ignoreUnknownKeys`) — add fields with defaults freely; keep `SettingsDto` ↔ `AppSettings` ↔ mappers in lockstep.

## Key files
`ui/theme/DesktopTheme.kt` · `ui/components/{Interactions,Buttons,Premium}.kt` (+ new Inputs/Tooltip/CommandPalette/Feedback) · `ui/App.kt` (routing/shell/keyboard/PiP hoist) · `ui/screens/*` · `player/VlcPlayer.kt` (per-instance) · `data/DesktopSettingsStore.kt` (+ core `AppSettings`) · `data/FollowStore.kt` (pattern → `HistoryStore`) · core `api/TwitchApiClient.kt` (VOD/clip ceiling).
