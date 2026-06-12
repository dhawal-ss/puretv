package com.puretv.twitch.desktop.ui.theme

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
import androidx.compose.ui.text.font.FontWeight
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
    val scrim: Color,
    val scrimSoft: Color,
    val twitchPurple: Color,
    val twitchPurpleLight: Color,
    val adBlockGreen: Color,
    val textPrimary: Color,
    val textSecondary: Color,
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
    accent: Color = Color(0xFF9147FF),
    accentLight: Color = Color(0xFFBF94FF),
) = PureTvDesktopColors(
    background = bg,
    surface = surface,
    surfaceVariant = surfaceVariant,
    surfaceRaised = surfaceRaised,
    surfaceHover = surfaceHover,
    hairline = Color(0x12FFFFFF),
    hairlineStrong = Color(0x2AFFFFFF),
    scrim = Color(0xDD000000),
    scrimSoft = Color(0x88000000),
    twitchPurple = accent,
    twitchPurpleLight = accentLight,
    adBlockGreen = Color(0xFF00C896),
    textPrimary = Color(0xFFEFEFF5),
    textSecondary = Color(0xFF7B7B90),
    textMuted = Color(0xFF484858),
    live = Color(0xFFEB0400),
    online = Color(0xFF3DAA43),
    warning = Color(0xFFFFAD00),
)

val themeColors: Map<ThemeVariant, PureTvDesktopColors> = mapOf(
    ThemeVariant.PURE_DARK to buildColors(
        bg = Color(0xFF080810),
        surface = Color(0xFF0F0F1A),
        surfaceVariant = Color(0xFF171726),
        surfaceRaised = Color(0xFF1C1C2E),
        surfaceHover = Color(0xFF22223A),
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

// ── Typography ─────────────────────────────────────────────────────────────────

private val PureTvDesktopTypography = Typography(
    headlineLarge = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Bold),
    headlineMedium = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontSize = 15.sp),
    bodyMedium = TextStyle(fontSize = 13.sp),
    labelSmall = TextStyle(fontSize = 11.sp, letterSpacing = 0.5.sp),
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
