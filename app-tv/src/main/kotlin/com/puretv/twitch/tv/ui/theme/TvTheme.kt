package com.puretv.twitch.tv.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Typography
import androidx.tv.material3.darkColorScheme

/**
 * PureTV's Material 3 Expressive design system, 10-foot edition.
 *
 * The role names and hex values are byte-identical to the phone app's
 * `Theme.kt` and the desktop app's `DesktopTheme.kt`. They are deliberately
 * duplicated: `core` is a KMP module without Compose, and this module themes
 * through `androidx.tv.material3`, whose `ColorScheme` and `Typography` are
 * different types from `androidx.compose.material3`'s. The wiring cannot be
 * shared; the numbers are kept in lockstep instead.
 *
 * The one real adaptation is what drives the morph. A remote has no pointer and
 * no press dwell, so shape and scale animate on **D-pad focus**. Focus is the
 * only affordance a remote has, which is why the focused element here both
 * morphs its corners AND grows, rather than only shifting colour.
 *
 * Type also steps up roughly a third from the phone scale for couch distance.
 */

// ---- Color roles -------------------------------------------------------------

data class PureTvTvColors(
    val primary: Color,
    val onPrimary: Color,
    val primaryContainer: Color,
    val onPrimaryContainer: Color,
    val secondary: Color,
    val onSecondary: Color,
    val secondaryContainer: Color,
    val onSecondaryContainer: Color,
    val tertiary: Color,
    val onTertiary: Color,
    val tertiaryContainer: Color,
    val onTertiaryContainer: Color,
    val surface: Color,
    val onSurface: Color,
    val onSurfaceVariant: Color,
    val surfaceLowest: Color,
    val surfaceLow: Color,
    val surfaceContainer: Color,
    val surfaceHigh: Color,
    val surfaceHighest: Color,
    val outline: Color,
    val outlineVariant: Color,
    val error: Color,
    val errorContainer: Color,
    val onErrorContainer: Color,
) {
    /** LIVE badge fill and ink. Aliased so call sites read by intent, not by role. */
    val live: Color get() = errorContainer
    val onLive: Color get() = onErrorContainer

    /** The ring drawn around the focused element. Focus must be unmistakable at 3 metres. */
    val focusRing: Color get() = primary

    val cardScrim: Brush
        get() = Brush.verticalGradient(0f to Color(0xB3000000), 0.55f to Color.Transparent)
    val heroScrim: Brush
        get() = Brush.horizontalGradient(0f to Color(0xD9000000), 0.5f to Color(0x8C000000), 0.9f to Color.Transparent)
    val bottomScrim: Brush
        get() = Brush.verticalGradient(0f to Color.Transparent, 1f to Color(0xE6000000))

    /**
     * The pre-Expressive palette, named by appearance rather than by job, held
     * at the Violet Dusk values. Screens not yet moved onto the tonal roles keep
     * compiling and stay roughly in tone through these. Being statics they
     * cannot follow the user's palette choice, which is exactly why they are
     * temporary: read `PureTvTvTheme.colors` instead, and this block goes away
     * once nothing references it.
     */
    companion object {
        val Background = Color(0xFF0F0D13)
        val Surface = Color(0xFF141218)
        val SurfaceVariant = Color(0xFF211F26)
        val TwitchPurple = Color(0xFFCFBCFF)
        val TwitchPurpleLight = Color(0xFFE9DDFF)
        val AdBlockGreen = Color(0xFFEFB8C8)
        val TextPrimary = Color(0xFFE6E0E9)
        val TextSecondary = Color(0xFFCAC4D0)
        val TextMuted = Color(0xFF938F99)
        val Live = Color(0xFF93000A)
        val Online = Color(0xFFEFB8C8)
        val Warning = Color(0xFFEFB8C8)
        val FocusBorder = TwitchPurple
    }
}

enum class ThemeVariant(val key: String, val displayName: String, val seed: String) {
    VIOLET_DUSK("dark", "Violet Dusk", "#7C5CDB"),
    EMBER("ember", "Ember", "#B4501E"),
    TEAL_DEEP("teal", "Teal Deep", "#0E6E63"),
    AMOLED("amoled", "Pure Black", "#7C5CDB, true black"),
    MIDNIGHT_FOREST("forest", "Midnight Forest", "#2E7D5B");

    companion object {
        fun fromKey(key: String): ThemeVariant = when (key) {
            "indigo", "purple" -> VIOLET_DUSK
            "charcoal", "darker" -> AMOLED
            else -> entries.firstOrNull { it.key == key } ?: VIOLET_DUSK
        }
    }
}

val themeColors: Map<ThemeVariant, PureTvTvColors> = mapOf(
    ThemeVariant.VIOLET_DUSK to PureTvTvColors(
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
    ThemeVariant.EMBER to PureTvTvColors(
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
    ThemeVariant.TEAL_DEEP to PureTvTvColors(
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
    // True black variant of Violet Dusk. On an OLED television this is the one
    // that actually saves panel wear on a mostly-dark browse screen.
    ThemeVariant.AMOLED to PureTvTvColors(
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
    ThemeVariant.MIDNIGHT_FOREST to PureTvTvColors(
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

// ---- Shape -------------------------------------------------------------------

/**
 * Corner vocabulary. Larger than the phone scale because a television is viewed
 * from three metres and small radii vanish at that distance.
 *
 * [cardFocus] and [pillFocus] are the radii those surfaces animate TO while
 * focused. Unlike the pointer platforms there is no hover here, so this is the
 * ONLY shape transition on TV, which is why it is generous.
 */
data class PureTvTvShapes(
    val card: Dp = 20.dp,
    val cardFocus: Dp = 32.dp,
    val hero: Dp = 32.dp,
) {
    val sm: Dp = 12.dp
    val md: Dp = 16.dp
    val thumb: Dp = 16.dp
    val pill: Dp = 999.dp
    val pillFocus: Dp = 16.dp
    val pane: Dp = 32.dp

    val cardShape get() = RoundedCornerShape(card)
    val heroShape get() = RoundedCornerShape(hero)
    val paneShape get() = RoundedCornerShape(pane)
    val pillShape get() = RoundedCornerShape(pill)
    val thumbShape get() = RoundedCornerShape(thumb)
    val mdShape get() = RoundedCornerShape(md)
}

// ---- Motion ------------------------------------------------------------------

object PureTvTvMotion {
    const val Fast = 150
    const val Medium = 250

    /** Overshoots and settles back, which is what makes focus feel physical. */
    val MorphSpring: SpringSpec<Dp> = spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow)
    val MorphSpringFloat: SpringSpec<Float> = spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow)

    val Standard: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    /** How far a focused card grows. Small enough not to overlap its neighbours. */
    const val FocusScale = 1.08f
}

// ---- Typography --------------------------------------------------------------

/**
 * The 10-foot type scale. Same three families as the phone and desktop apps,
 * sized up for couch viewing: nothing below 16sp, and body copy at 20sp.
 */
val PureTvTvTypography = Typography(
    displayLarge = TextStyle(fontFamily = BricolageGrotesque, fontSize = 57.sp, lineHeight = 62.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-1.6).sp),
    displayMedium = TextStyle(fontFamily = BricolageGrotesque, fontSize = 45.sp, lineHeight = 52.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-1.1).sp),
    displaySmall = TextStyle(fontFamily = BricolageGrotesque, fontSize = 36.sp, lineHeight = 44.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.8).sp),
    headlineLarge = TextStyle(fontFamily = BricolageGrotesque, fontSize = 34.sp, lineHeight = 42.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.6).sp),
    headlineMedium = TextStyle(fontFamily = BricolageGrotesque, fontSize = 30.sp, lineHeight = 38.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp),
    headlineSmall = TextStyle(fontFamily = BricolageGrotesque, fontSize = 26.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp),
    titleLarge = TextStyle(fontFamily = BricolageGrotesque, fontSize = 24.sp, lineHeight = 30.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.2).sp),
    titleMedium = TextStyle(fontFamily = Archivo, fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.15.sp),
    titleSmall = TextStyle(fontFamily = Archivo, fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.1.sp),
    bodyLarge = TextStyle(fontFamily = Archivo, fontSize = 20.sp, lineHeight = 28.sp, fontWeight = FontWeight.Normal, letterSpacing = 0.4.sp),
    bodyMedium = TextStyle(fontFamily = Archivo, fontSize = 18.sp, lineHeight = 25.sp, fontWeight = FontWeight.Normal, letterSpacing = 0.25.sp),
    bodySmall = TextStyle(fontFamily = Archivo, fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.Normal, letterSpacing = 0.4.sp),
    labelLarge = TextStyle(fontFamily = Archivo, fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.1.sp),
    labelMedium = TextStyle(fontFamily = Archivo, fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.5.sp),
    labelSmall = TextStyle(fontFamily = Archivo, fontSize = 14.sp, lineHeight = 19.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.5.sp),
)

/** The monospace layer Material's Typography has no slot for. */
object PureTvTvType {
    val display: FontFamily = BricolageGrotesque
    val ui: FontFamily = Archivo
    val mono: FontFamily = IBMPlexMono

    val kicker = TextStyle(fontFamily = Archivo, fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp)
    val data = TextStyle(fontFamily = IBMPlexMono, fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.Normal, letterSpacing = 0.3.sp)
    val dataSmall = TextStyle(fontFamily = IBMPlexMono, fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.8.sp)
    val badge = TextStyle(fontFamily = IBMPlexMono, fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp)
}

// ---- Composition locals ------------------------------------------------------

val LocalPureTvTvColors = staticCompositionLocalOf { themeColors[ThemeVariant.VIOLET_DUSK]!! }
val LocalPureTvTvShapes = staticCompositionLocalOf { PureTvTvShapes() }

/** `PureTvTvTheme.colors.X` and `PureTvTvTheme.shapes.X` in any composable. */
object PureTvTvTheme {
    val colors: PureTvTvColors
        @Composable @ReadOnlyComposable get() = LocalPureTvTvColors.current

    val shapes: PureTvTvShapes
        @Composable @ReadOnlyComposable get() = LocalPureTvTvShapes.current
}

// ---- Theme wrapper -----------------------------------------------------------

@Composable
fun PureTvTvTheme(
    variant: ThemeVariant = ThemeVariant.VIOLET_DUSK,
    content: @Composable () -> Unit,
) {
    val colors = themeColors[variant]!!
    // androidx.tv.material3's ColorScheme has no surfaceContainer ladder, so the
    // five-step ladder lives in the composition local above and only the roles
    // TV's own components actually read are mirrored here.
    val scheme = darkColorScheme(
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
        border = colors.outline,
        borderVariant = colors.outlineVariant,
        error = colors.error,
        errorContainer = colors.errorContainer,
        onErrorContainer = colors.onErrorContainer,
    )
    CompositionLocalProvider(
        LocalPureTvTvColors provides colors,
        LocalPureTvTvShapes provides PureTvTvShapes(),
    ) {
        MaterialTheme(colorScheme = scheme, typography = PureTvTvTypography, content = content)
    }
}
