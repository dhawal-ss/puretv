package com.puretv.twitch.desktop.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ── Color palette ──────────────────────────────────────────────────────────────

data class PureTvDesktopColors(
    val background: Color,
    val surface: Color,
    val surfaceVariant: Color,
    val surfaceRaised: Color,
    val surfaceHover: Color,
    val hairline: Color,
    val hairlineStrong: Color,
    /** 6%-white top-edge inner glow that gives flat cards a sense of depth. */
    val innerHighlight: Color,
    val scrim: Color,
    val scrimSoft: Color,
    val twitchPurple: Color,
    val twitchPurpleLight: Color,
    val adBlockGreen: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    /** Between secondary and muted — metadata, captions. */
    val textTertiary: Color,
    val textMuted: Color,
    val live: Color,
    val online: Color,
    val warning: Color,
) {
    val accentGradient: Brush
        get() = Brush.linearGradient(listOf(twitchPurple, twitchPurpleLight))
    val bottomScrim: Brush
        get() = Brush.verticalGradient(listOf(Color.Transparent, Color(0xDD000000)))
    val topScrim: Brush
        get() = Brush.verticalGradient(listOf(Color(0xCC000000), Color.Transparent))
}

// ── Theme variants ─────────────────────────────────────────────────────────────

enum class ThemeVariant(val key: String, val displayName: String) {
    PURE_DARK("dark", "Pure Dark"),
    AMOLED("amoled", "AMOLED Black"),
    DEEP_INDIGO("indigo", "Deep Indigo"),
    CHARCOAL("charcoal", "Charcoal"),
    MIDNIGHT_FOREST("forest", "Midnight Forest");

    companion object {
        fun fromKey(key: String) = entries.firstOrNull { it.key == key } ?: PURE_DARK
    }
}

private fun buildColors(
    bg: Color,
    surface: Color,
    surfaceVariant: Color,
    surfaceRaised: Color,
    surfaceHover: Color,
    // Brand accent: deliberately distinct from Twitch's own #9146FF (non-affiliation
    // — see DESIGN_SYSTEM.md). Reconciles the prior code/doc mismatch on #9B5DE5.
    accent: Color = Color(0xFF9B5DE5),
    accentLight: Color = Color(0xFFC77DFF),
) = PureTvDesktopColors(
    background = bg,
    surface = surface,
    surfaceVariant = surfaceVariant,
    surfaceRaised = surfaceRaised,
    surfaceHover = surfaceHover,
    hairline = Color(0x14FFFFFF),
    hairlineStrong = Color(0x24FFFFFF),
    innerHighlight = Color(0x0FFFFFFF),
    scrim = Color(0xDD000000),
    scrimSoft = Color(0x88000000),
    twitchPurple = accent,
    twitchPurpleLight = accentLight,
    adBlockGreen = Color(0xFF00C896),
    // Brighter, near-neutral ink ladder — the old secondary/muted were too dim.
    textPrimary = Color(0xFFF4F4F8),
    textSecondary = Color(0xFFC2C4CE),
    textTertiary = Color(0xFF8A8C99),
    textMuted = Color(0xFF5E6070),
    live = Color(0xFFEB0400),
    online = Color(0xFF3DAA43),
    warning = Color(0xFFFFAD00),
)

val themeColors: Map<ThemeVariant, PureTvDesktopColors> = mapOf(
    ThemeVariant.PURE_DARK to buildColors(
        // Deepened, faintly cooler surface ladder — each rung ~+8-10 lightness is
        // the elevation system (depth from the ladder + hairlines, not shadows).
        bg = Color(0xFF07070E),
        surface = Color(0xFF0E0E18),
        surfaceVariant = Color(0xFF16161F),
        surfaceRaised = Color(0xFF1D1D29),
        surfaceHover = Color(0xFF25253A),
    ),
    ThemeVariant.AMOLED to buildColors(
        bg = Color(0xFF000000),
        surface = Color(0xFF080808),
        surfaceVariant = Color(0xFF101010),
        surfaceRaised = Color(0xFF141414),
        surfaceHover = Color(0xFF1A1A1A),
    ),
    ThemeVariant.DEEP_INDIGO to buildColors(
        bg = Color(0xFF06061A),
        surface = Color(0xFF0C0C28),
        surfaceVariant = Color(0xFF121236),
        surfaceRaised = Color(0xFF16163E),
        surfaceHover = Color(0xFF1C1C4A),
        accent = Color(0xFF7B5EA7),
        accentLight = Color(0xFFAA80FF),
    ),
    ThemeVariant.CHARCOAL to buildColors(
        bg = Color(0xFF0C0C0C),
        surface = Color(0xFF141414),
        surfaceVariant = Color(0xFF1C1C1C),
        surfaceRaised = Color(0xFF222222),
        surfaceHover = Color(0xFF282828),
    ),
    ThemeVariant.MIDNIGHT_FOREST to buildColors(
        bg = Color(0xFF060E0C),
        surface = Color(0xFF0B1714),
        surfaceVariant = Color(0xFF111F1C),
        surfaceRaised = Color(0xFF152622),
        surfaceHover = Color(0xFF1A2E2A),
        accent = Color(0xFF3D9970),
        accentLight = Color(0xFF5DBFA0),
    ),
)

// ── Composition local + convenience accessor ───────────────────────────────────

val LocalPureTvColors = staticCompositionLocalOf { themeColors[ThemeVariant.PURE_DARK]!! }

/** Use `PureTvTheme.colors.X` in any composable instead of a static object. */
object PureTvTheme {
    val colors: PureTvDesktopColors
        @Composable
        @ReadOnlyComposable
        get() = LocalPureTvColors.current
}

// ── Motion tokens ──────────────────────────────────────────────────────────────

object PureTvMotion {
    const val Fast = 150
    const val Medium = 250
    const val Slow = 400
    const val ControlsAutoHideMs = 2800L
}

// ── Shape scale ─────────────────────────────────────────────────────────────────
// One radius vocabulary so corners are consistent instead of ad hoc per call site.

object PureTvShape {
    val xs = RoundedCornerShape(6.dp)    // chips, badges
    val sm = RoundedCornerShape(8.dp)    // buttons, inputs
    val md = RoundedCornerShape(10.dp)   // thumbnails, cards
    val lg = RoundedCornerShape(14.dp)   // panels, dialogs
    val pill = RoundedCornerShape(999.dp) // primary CTA, search, avatars
}

// ── Typography ─────────────────────────────────────────────────────────────────

// Type discipline: negative tracking on large sizes, generous line-height on body,
// positive tracking on tiny labels; weights clustered at 400/500/600/700. This is
// what reads as "designed, not defaulted" even before a custom font is bundled.
private val PureTvDesktopTypography = Typography(
    displayLarge = TextStyle(fontSize = 32.sp, fontWeight = FontWeight.Bold, letterSpacing = (-1.0).sp, lineHeight = 38.sp),
    headlineLarge = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.6).sp, lineHeight = 32.sp),
    headlineMedium = TextStyle(fontSize = 21.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.4).sp, lineHeight = 28.sp),
    titleLarge = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.3).sp, lineHeight = 24.sp),
    titleMedium = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Medium, letterSpacing = (-0.1).sp, lineHeight = 22.sp),
    bodyLarge = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Normal, lineHeight = 23.sp),
    bodyMedium = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Normal, lineHeight = 20.sp),
    labelLarge = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.1.sp, lineHeight = 16.sp),
    labelMedium = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.2.sp, lineHeight = 16.sp),
    labelSmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp, lineHeight = 14.sp),
)

// ── Theme wrapper ──────────────────────────────────────────────────────────────

@Composable
fun PureTvDesktopTheme(
    variant: ThemeVariant = ThemeVariant.PURE_DARK,
    content: @Composable () -> Unit,
) {
    val colors = themeColors[variant]!!
    val colorScheme = darkColorScheme(
        background = colors.background,
        surface = colors.surface,
        surfaceVariant = colors.surfaceVariant,
        primary = colors.twitchPurple,
        secondary = colors.twitchPurpleLight,
        onBackground = colors.textPrimary,
        onSurface = colors.textPrimary,
        onSurfaceVariant = colors.textSecondary,
        error = colors.live,
    )
    CompositionLocalProvider(LocalPureTvColors provides colors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = PureTvDesktopTypography,
            content = content,
        )
    }
}
