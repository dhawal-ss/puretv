package com.puretv.twitch.android.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * SECTION 10 — UI Design System, shared visually across all three apps
 * (Compose Multiplatform makes the actual color/type objects shareable from
 * `core`, but we keep the Material3 ColorScheme/Typography wiring per-app
 * since theming APIs differ between Compose Android, Compose for TV, and
 * Compose Desktop).
 */
object PureTvColors {
    // Backgrounds
    val Background = Color(0xFF0A0A0F)
    val Surface = Color(0xFF141420)
    val SurfaceVariant = Color(0xFF1E1E2E)

    // Brand
    val TwitchPurple = Color(0xFF9B5DE5)
    val TwitchPurpleLight = Color(0xFFC77DFF)
    val AdBlockGreen = Color(0xFF06D6A0)

    // Text
    val TextPrimary = Color(0xFFE8E8F0)
    val TextSecondary = Color(0xFF888899)
    val TextMuted = Color(0xFF555566)

    // Semantic
    val Live = Color(0xFFE53935)
    val Online = Color(0xFF43A047)
    val Warning = Color(0xFFFFB703)
}

private val DarkScheme = darkColorScheme(
    background = PureTvColors.Background,
    surface = PureTvColors.Surface,
    surfaceVariant = PureTvColors.SurfaceVariant,
    primary = PureTvColors.TwitchPurple,
    secondary = PureTvColors.TwitchPurpleLight,
    onBackground = PureTvColors.TextPrimary,
    onSurface = PureTvColors.TextPrimary,
    error = PureTvColors.Live,
)

/**
 * Display font: "Space Grotesk" (loaded as a downloadable Google Font on
 * Android, bundled TTF on Desktop). Chat font: "JetBrains Mono". Both are
 * referenced through [androidx.compose.ui.text.font.FontFamily.Default] here
 * as a safe fallback — wire the real families in res/font + a
 * GoogleFont.Provider once API keys are configured.
 */
val PureTvTypography = Typography(
    headlineLarge = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Bold),
    headlineMedium = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 15.sp),
    bodyMedium = TextStyle(fontSize = 13.sp),
    labelSmall = TextStyle(fontSize = 10.sp, letterSpacing = 1.sp),
)

@Composable
fun PureTvTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = DarkScheme, typography = PureTvTypography, content = content)
}
