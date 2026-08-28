package com.puretv.twitch.desktop.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * PureTV's Material 3 Expressive design system.
 *
 * Three things make this "expressive" rather than plain Material 3:
 *
 *  1. **A full tonal role set derived from one seed.** Every palette below is a
 *     complete M3 scheme (primary / secondary / tertiary, each with a container
 *     pair, plus a five-step surface-container ladder). Nothing is a one-off hex
 *     at a call site; picking a different palette re-tones the entire app.
 *  2. **Shape as a live axis.** [ShapeIntensity] scales the corner vocabulary from
 *     Calm to Maximal, and interactive surfaces MORPH their radius on hover/press
 *     (see `Modifier.expressiveSurface` in `ui/components/Expressive.kt`). Shape change
 *     is the primary feedback channel, ahead of color.
 *  3. **Spring motion with overshoot.** [PureTvMotion.MorphSpring] settles past its
 *     target and back, which is what gives the morph its bounce.
 *
 * Implementation note: Compose Multiplatform is pinned at 1.7.0 (material3 1.7),
 * which predates the `material3` 1.4 Expressive APIs (`MaterialExpressiveTheme`,
 * `MaterialShapes`, `ButtonGroup`, `WideNavigationRail`). Bumping would drag
 * Kotlin, AGP, KSP and both Android modules with it, against the version
 * catalog's explicit pin. So the Expressive language is implemented directly on
 * the pinned stack. The tokens and primitives here are the substitute for those
 * library components, and no screen needs anything the current stack can't draw.
 */

// ── Color roles ────────────────────────────────────────────────────────────────

/**
 * A complete Material 3 tonal scheme. Names follow the M3 role vocabulary so a
 * mockup token (`--pri-c`, `--sc-high`, `--on-surf-var`) maps to exactly one
 * property here.
 */
data class PureTvDesktopColors(
    // Primary: the accent. One accent moment per region, never a wash.
    val primary: Color,
    val onPrimary: Color,
    val primaryContainer: Color,
    val onPrimaryContainer: Color,
    // Secondary: selected nav, segmented toggles, quiet emphasis.
    val secondary: Color,
    val onSecondary: Color,
    val secondaryContainer: Color,
    val onSecondaryContainer: Color,
    // Tertiary: the ad-block/shield voice, deliberately off-accent.
    val tertiary: Color,
    val onTertiary: Color,
    val tertiaryContainer: Color,
    val onTertiaryContainer: Color,
    // Surfaces: the app body plus a five-step container ladder.
    val surface: Color,
    val onSurface: Color,
    val onSurfaceVariant: Color,
    val surfaceLowest: Color,
    val surfaceLow: Color,
    val surfaceContainer: Color,
    val surfaceHigh: Color,
    val surfaceHighest: Color,
    // Lines.
    val outline: Color,
    val outlineVariant: Color,
    // Error / LIVE. The mockup uses the error container as the LIVE badge fill.
    val error: Color,
    val errorContainer: Color,
    val onErrorContainer: Color,
) {
    /** LIVE badge fill + ink. Aliased so call sites read by intent, not by role. */
    val live: Color get() = errorContainer
    val onLive: Color get() = onErrorContainer

    // ---- Legacy aliases -----------------------------------------------------
    // The pre-Expressive palette named colours by appearance ("twitchPurple",
    // "textMuted"); the roles above name them by job. These aliases keep the
    // not-yet-migrated call sites compiling and correctly toned. They are a
    // migration aid, not part of the design system. Prefer the roles.
    val background: Color get() = surfaceLowest
    val surfaceVariant: Color get() = surfaceContainer
    val surfaceRaised: Color get() = surfaceHigh
    val surfaceHover: Color get() = surfaceHighest
    val hairline: Color get() = outlineVariant.copy(alpha = 0.45f)
    val hairlineStrong: Color get() = outlineVariant
    val innerHighlight: Color get() = Color(0x0FFFFFFF)
    val scrim: Color get() = Color(0xDD000000)
    val scrimSoft: Color get() = Color(0x88000000)
    val twitchPurple: Color get() = primary
    val twitchPurpleLight: Color get() = onPrimaryContainer
    val adBlockGreen: Color get() = tertiary
    val textPrimary: Color get() = onSurface
    val textSecondary: Color get() = onSurfaceVariant
    val textTertiary: Color get() = outline
    val textMuted: Color get() = outlineVariant
    val online: Color get() = tertiary
    val warning: Color get() = tertiary
    val accentGradient: Brush get() = Brush.linearGradient(listOf(primary, onPrimaryContainer))
    val topScrim: Brush get() = Brush.verticalGradient(listOf(Color(0xCC000000), Color.Transparent))

    /** The scrim that lets white text sit on arbitrary thumbnail artwork. */
    val cardScrim: Brush
        get() = Brush.verticalGradient(0f to Color(0xB3000000), 0.55f to Color.Transparent)
    val heroScrim: Brush
        get() = Brush.horizontalGradient(0f to Color(0xD1000000), 0.46f to Color(0x80000000), 0.82f to Color.Transparent)
    val bottomScrim: Brush
        get() = Brush.verticalGradient(0f to Color.Transparent, 1f to Color(0xE0000000))
}

// ── Palettes ───────────────────────────────────────────────────────────────────

/**
 * A palette is a seed colour expanded into the full role set, the way Material
 * dynamic colour derives a scheme from a wallpaper. [seed] is shown in Settings
 * so the derivation is visible rather than magic.
 */
enum class ThemeVariant(val key: String, val displayName: String, val seed: String) {
    VIOLET_DUSK("dark", "Violet Dusk", "#7C5CDB"),
    EMBER("ember", "Ember", "#B4501E"),
    TEAL_DEEP("teal", "Teal Deep", "#0E6E63"),
    AMOLED("amoled", "Pure Black", "#7C5CDB · true black"),
    MIDNIGHT_FOREST("forest", "Midnight Forest", "#2E7D5B");

    companion object {
        /**
         * Legacy keys from the pre-Expressive theme set still live in users'
         * `settings.json`, so they are mapped onto their nearest new palette
         * instead of silently resetting to the default.
         */
        fun fromKey(key: String): ThemeVariant = when (key) {
            "indigo" -> VIOLET_DUSK
            "charcoal" -> AMOLED
            else -> entries.firstOrNull { it.key == key } ?: VIOLET_DUSK
        }
    }
}

val themeColors: Map<ThemeVariant, PureTvDesktopColors> = mapOf(
    ThemeVariant.VIOLET_DUSK to PureTvDesktopColors(
        primary = Color(0xFFCFBCFF), onPrimary = Color(0xFF371E73),
        primaryContainer = Color(0xFF4F378B), onPrimaryContainer = Color(0xFFE9DDFF),
        secondary = Color(0xFFCBC2DB), onSecondary = Color(0xFF332D41),
        secondaryContainer = Color(0xFF4A4458), onSecondaryContainer = Color(0xFFE8DEF8),
        tertiary = Color(0xFFEFB8C8), onTertiary = Color(0xFF4A2532),
        tertiaryContainer = Color(0xFF633B48), onTertiaryContainer = Color(0xFFFFD8E4),
        surface = Color(0xFF141218), onSurface = Color(0xFFE6E0E9), onSurfaceVariant = Color(0xFFCAC4D0),
        surfaceLowest = Color(0xFF0F0D13), surfaceLow = Color(0xFF1D1B20),
        surfaceContainer = Color(0xFF211F26), surfaceHigh = Color(0xFF2B2930), surfaceHighest = Color(0xFF36343B),
        outline = Color(0xFF938F99), outlineVariant = Color(0xFF49454F),
        error = Color(0xFFFFB4AB), errorContainer = Color(0xFF93000A), onErrorContainer = Color(0xFFFFDAD6),
    ),
    ThemeVariant.EMBER to PureTvDesktopColors(
        primary = Color(0xFFFFB68F), onPrimary = Color(0xFF522300),
        primaryContainer = Color(0xFF743500), onPrimaryContainer = Color(0xFFFFDBC9),
        secondary = Color(0xFFE7BDAB), onSecondary = Color(0xFF442A1C),
        secondaryContainer = Color(0xFF5D3F30), onSecondaryContainer = Color(0xFFFFDBC9),
        tertiary = Color(0xFFCFC890), onTertiary = Color(0xFF343100),
        tertiaryContainer = Color(0xFF4B4800), onTertiaryContainer = Color(0xFFECE4A9),
        surface = Color(0xFF1A1110), onSurface = Color(0xFFF1DFDA), onSurfaceVariant = Color(0xFFD8C2BA),
        surfaceLowest = Color(0xFF140C0B), surfaceLow = Color(0xFF231A17),
        surfaceContainer = Color(0xFF271E1C), surfaceHigh = Color(0xFF322826), surfaceHighest = Color(0xFF3E3331),
        outline = Color(0xFFA08C85), outlineVariant = Color(0xFF53433F),
        error = Color(0xFFFFB4AB), errorContainer = Color(0xFF93000A), onErrorContainer = Color(0xFFFFDAD6),
    ),
    ThemeVariant.TEAL_DEEP to PureTvDesktopColors(
        primary = Color(0xFF80D5CA), onPrimary = Color(0xFF003731),
        primaryContainer = Color(0xFF00504A), onPrimaryContainer = Color(0xFF9CF2E6),
        secondary = Color(0xFFB1CCC7), onSecondary = Color(0xFF1C3532),
        secondaryContainer = Color(0xFF334B48), onSecondaryContainer = Color(0xFFCCE8E3),
        tertiary = Color(0xFFADCAE6), onTertiary = Color(0xFF133349),
        tertiaryContainer = Color(0xFF2B4A60), onTertiaryContainer = Color(0xFFCBE6FF),
        surface = Color(0xFF0E1513), onSurface = Color(0xFFDDE4E1), onSurfaceVariant = Color(0xFFBEC9C6),
        surfaceLowest = Color(0xFF090F0E), surfaceLow = Color(0xFF171D1C),
        surfaceContainer = Color(0xFF1B2120), surfaceHigh = Color(0xFF252B2A), surfaceHighest = Color(0xFF303635),
        outline = Color(0xFF889390), outlineVariant = Color(0xFF3F4947),
        error = Color(0xFFFFB4AB), errorContainer = Color(0xFF93000A), onErrorContainer = Color(0xFFFFDAD6),
    ),
    // True-black variant of Violet Dusk for OLED panels: the surface ladder starts
    // at #000 so unlit pixels really are off, while the accent roles are unchanged.
    ThemeVariant.AMOLED to PureTvDesktopColors(
        primary = Color(0xFFCFBCFF), onPrimary = Color(0xFF371E73),
        primaryContainer = Color(0xFF4F378B), onPrimaryContainer = Color(0xFFE9DDFF),
        secondary = Color(0xFFCBC2DB), onSecondary = Color(0xFF332D41),
        secondaryContainer = Color(0xFF3D3849), onSecondaryContainer = Color(0xFFE8DEF8),
        tertiary = Color(0xFFEFB8C8), onTertiary = Color(0xFF4A2532),
        tertiaryContainer = Color(0xFF57323D), onTertiaryContainer = Color(0xFFFFD8E4),
        surface = Color(0xFF000000), onSurface = Color(0xFFE6E0E9), onSurfaceVariant = Color(0xFFBDB7C4),
        surfaceLowest = Color(0xFF000000), surfaceLow = Color(0xFF0C0B0F),
        surfaceContainer = Color(0xFF121116), surfaceHigh = Color(0xFF1B1A20), surfaceHighest = Color(0xFF26242C),
        outline = Color(0xFF8A8692), outlineVariant = Color(0xFF3B3842),
        error = Color(0xFFFFB4AB), errorContainer = Color(0xFF93000A), onErrorContainer = Color(0xFFFFDAD6),
    ),
    ThemeVariant.MIDNIGHT_FOREST to PureTvDesktopColors(
        primary = Color(0xFF8FD8AE), onPrimary = Color(0xFF00391D),
        primaryContainer = Color(0xFF00522C), onPrimaryContainer = Color(0xFFABF5C9),
        secondary = Color(0xFFB8CCBD), onSecondary = Color(0xFF24342A),
        secondaryContainer = Color(0xFF3A4B40), onSecondaryContainer = Color(0xFFD4E8D9),
        tertiary = Color(0xFFA3CDDA), onTertiary = Color(0xFF033542),
        tertiaryContainer = Color(0xFF224C59), onTertiaryContainer = Color(0xFFBFE9F7),
        surface = Color(0xFF0F1512), onSurface = Color(0xFFDFE4DF), onSurfaceVariant = Color(0xFFC0C9C1),
        surfaceLowest = Color(0xFF090F0B), surfaceLow = Color(0xFF181D19),
        surfaceContainer = Color(0xFF1C221D), surfaceHigh = Color(0xFF262C28), surfaceHighest = Color(0xFF313732),
        outline = Color(0xFF8A938C), outlineVariant = Color(0xFF404943),
        error = Color(0xFFFFB4AB), errorContainer = Color(0xFF93000A), onErrorContainer = Color(0xFFFFDAD6),
    ),
)

// ── Shape ──────────────────────────────────────────────────────────────────────

/**
 * How far corners round and how far they morph. The user picks this in Settings;
 * it is the "expressiveness" dial the M3 Expressive spec talks about, exposed
 * rather than baked in.
 */
enum class ShapeIntensity(val key: String, val displayName: String) {
    CALM("calm", "Calm"),
    EXPRESSIVE("expressive", "Expressive"),
    MAXIMAL("maximal", "Maximal");

    companion object {
        fun fromKey(key: String) = entries.firstOrNull { it.key == key } ?: EXPRESSIVE
    }
}

/**
 * The corner vocabulary. [card] and [hero] scale with [ShapeIntensity]; the rest
 * are fixed because they are structural (a pill is a pill at every intensity).
 *
 * [cardMorph] / [heroMorph] / [pillMorph] are the radii those surfaces animate TO
 * on hover. The M3 Expressive move is that a pill squares off and a square rounds
 * out. The delta is the feedback, so both directions are represented.
 */
data class PureTvShapes(
    val card: Dp,
    val cardMorph: Dp,
    val hero: Dp,
    val heroMorph: Dp,
) {
    val xs: Dp = 8.dp
    val sm: Dp = 12.dp
    val md: Dp = 16.dp
    val thumb: Dp = 14.dp

    /** Full-round. Used for nav items, chips, badges, avatars, CTA buttons. */
    val pill: Dp = 999.dp

    /** What a pill morphs INTO on hover: the signature "pill squares off" cue. */
    val pillMorph: Dp = 14.dp

    /** The outermost panes: nav rail and the content surface. */
    val pane: Dp = 28.dp

    val cardShape get() = RoundedCornerShape(card)
    val heroShape get() = RoundedCornerShape(hero)
    val paneShape get() = RoundedCornerShape(pane)
    val pillShape get() = RoundedCornerShape(pill)
    val thumbShape get() = RoundedCornerShape(thumb)
    val smShape get() = RoundedCornerShape(sm)
    val mdShape get() = RoundedCornerShape(md)
}

val shapeScale: Map<ShapeIntensity, PureTvShapes> = mapOf(
    ShapeIntensity.CALM to PureTvShapes(card = 12.dp, cardMorph = 20.dp, hero = 20.dp, heroMorph = 26.dp),
    ShapeIntensity.EXPRESSIVE to PureTvShapes(card = 20.dp, cardMorph = 32.dp, hero = 28.dp, heroMorph = 40.dp),
    ShapeIntensity.MAXIMAL to PureTvShapes(card = 28.dp, cardMorph = 48.dp, hero = 48.dp, heroMorph = 64.dp),
)

/**
 * Fixed-radius shapes for call sites that predate [PureTvShapes]. Kept so the
 * migration can proceed file by file; new code should use `PureTvTheme.shapes`,
 * which respects the user's chosen [ShapeIntensity].
 */
object PureTvShape {
    val xs = RoundedCornerShape(8.dp)
    val sm = RoundedCornerShape(12.dp)
    val md = RoundedCornerShape(16.dp)
    val lg = RoundedCornerShape(20.dp)
    val xl = RoundedCornerShape(28.dp)
    val pill = RoundedCornerShape(999.dp)
}

// ── Motion ─────────────────────────────────────────────────────────────────────

object PureTvMotion {
    const val Fast = 150
    const val Medium = 250
    const val Slow = 400
    const val ControlsAutoHideMs = 2800L

    /**
     * The morph curve. Overshoots its target and settles back, which is what makes
     * a corner change read as elastic rather than as a plain tween. The CSS
     * mockup expresses the same thing as `cubic-bezier(.2, 1.4, .3, 1)`. Note the
     * 1.4 control point above 1.
     */
    val MorphSpring: SpringSpec<Dp> = spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow)

    /** Same feel, for float-valued properties (scale, translation, alpha ramps). */
    val MorphSpringFloat: SpringSpec<Float> = spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow)

    /** Non-springy easing for colour and opacity, which should not bounce. */
    val Standard: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
}

// ── Typography ─────────────────────────────────────────────────────────────────

/**
 * Bricolage Grotesque carries display + section voice with tight negative
 * tracking, Archivo carries every functional string, IBM Plex Mono (see
 * [PureTvType]) carries data. Sizes follow the Material 3 Expressive type scale,
 * which runs noticeably larger than baseline M3, because expressive layouts lean on a
 * big display step to anchor a page.
 */
private val PureTvDesktopTypography = Typography(
    // Display: hero titles only. One per screen at most.
    displayLarge = TextStyle(fontFamily = BricolageGrotesque, fontSize = 57.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-1.8).sp, lineHeight = 60.sp),
    displayMedium = TextStyle(fontFamily = BricolageGrotesque, fontSize = 45.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-1.2).sp, lineHeight = 52.sp),
    displaySmall = TextStyle(fontFamily = BricolageGrotesque, fontSize = 36.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.9).sp, lineHeight = 44.sp),
    // Headline: section headings.
    headlineLarge = TextStyle(fontFamily = BricolageGrotesque, fontSize = 32.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.6).sp, lineHeight = 40.sp),
    headlineMedium = TextStyle(fontFamily = BricolageGrotesque, fontSize = 28.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp, lineHeight = 36.sp),
    headlineSmall = TextStyle(fontFamily = BricolageGrotesque, fontSize = 24.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp, lineHeight = 32.sp),
    // Title: panel headings and card titles.
    titleLarge = TextStyle(fontFamily = BricolageGrotesque, fontSize = 22.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.2).sp, lineHeight = 28.sp),
    titleMedium = TextStyle(fontFamily = Archivo, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.15.sp, lineHeight = 22.sp),
    titleSmall = TextStyle(fontFamily = Archivo, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.1.sp, lineHeight = 20.sp),
    // Body.
    bodyLarge = TextStyle(fontFamily = Archivo, fontSize = 16.sp, fontWeight = FontWeight.Normal, letterSpacing = 0.5.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = Archivo, fontSize = 14.sp, fontWeight = FontWeight.Normal, letterSpacing = 0.25.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontFamily = Archivo, fontSize = 12.sp, fontWeight = FontWeight.Normal, letterSpacing = 0.4.sp, lineHeight = 16.sp),
    // Label: buttons, chips, nav.
    labelLarge = TextStyle(fontFamily = Archivo, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.1.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontFamily = Archivo, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.5.sp, lineHeight = 16.sp),
    labelSmall = TextStyle(fontFamily = Archivo, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.5.sp, lineHeight = 16.sp),
)

/**
 * Material's [Typography] has no monospace slot, so the IBM Plex Mono styles that
 * give data its instrument-panel precision live here, alongside direct family
 * handles for the rare component that must set a family explicitly.
 */
object PureTvType {
    val display: FontFamily = BricolageGrotesque
    val ui: FontFamily = Archivo
    val mono: FontFamily = IBMPlexMono

    /** Eyebrow / section kicker. Apply `.uppercase()` at the call site. */
    val kicker = TextStyle(fontFamily = Archivo, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.5.sp, lineHeight = 16.sp)

    /** Viewer counts, datelines, durations. */
    val data = TextStyle(fontFamily = IBMPlexMono, fontSize = 14.sp, fontWeight = FontWeight.Normal, letterSpacing = 0.3.sp, lineHeight = 20.sp)

    /** Badge text: LIVE pills, quality labels, timestamps. */
    val dataSmall = TextStyle(fontFamily = IBMPlexMono, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.8.sp, lineHeight = 14.sp)

    /** The all-caps status voice on hero overlays. */
    val badge = TextStyle(fontFamily = IBMPlexMono, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp, lineHeight = 14.sp)
}

// ── Composition locals ─────────────────────────────────────────────────────────

val LocalPureTvColors = staticCompositionLocalOf { themeColors[ThemeVariant.VIOLET_DUSK]!! }
val LocalPureTvShapes = staticCompositionLocalOf { shapeScale[ShapeIntensity.EXPRESSIVE]!! }

/** `PureTvTheme.colors.X` / `PureTvTheme.shapes.X` in any composable. */
object PureTvTheme {
    val colors: PureTvDesktopColors
        @Composable @ReadOnlyComposable get() = LocalPureTvColors.current

    val shapes: PureTvShapes
        @Composable @ReadOnlyComposable get() = LocalPureTvShapes.current
}

// ── Theme wrapper ──────────────────────────────────────────────────────────────

@Composable
fun PureTvDesktopTheme(
    variant: ThemeVariant = ThemeVariant.VIOLET_DUSK,
    shapeIntensity: ShapeIntensity = ShapeIntensity.EXPRESSIVE,
    content: @Composable () -> Unit,
) {
    val colors = themeColors[variant]!!
    val shapes = shapeScale[shapeIntensity]!!
    // Mirror the roles into Material's own scheme so the handful of stock M3
    // components still in use (progress indicators, text selection) tone with the
    // rest of the app instead of falling back to baseline purple.
    val colorScheme = darkColorScheme(
        primary = colors.primary,
        onPrimary = colors.onPrimary,
        primaryContainer = colors.primaryContainer,
        onPrimaryContainer = colors.onPrimaryContainer,
        secondary = colors.secondary,
        onSecondary = colors.onSecondary,
        secondaryContainer = colors.secondaryContainer,
        onSecondaryContainer = colors.onSecondaryContainer,
        tertiary = colors.tertiary,
        onTertiary = colors.onTertiary,
        tertiaryContainer = colors.tertiaryContainer,
        onTertiaryContainer = colors.onTertiaryContainer,
        background = colors.surfaceLowest,
        onBackground = colors.onSurface,
        surface = colors.surface,
        onSurface = colors.onSurface,
        surfaceVariant = colors.surfaceHigh,
        onSurfaceVariant = colors.onSurfaceVariant,
        outline = colors.outline,
        outlineVariant = colors.outlineVariant,
        error = colors.error,
        errorContainer = colors.errorContainer,
        onErrorContainer = colors.onErrorContainer,
    )
    CompositionLocalProvider(
        LocalPureTvColors provides colors,
        LocalPureTvShapes provides shapes,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = PureTvDesktopTypography,
            content = content,
        )
    }
}
