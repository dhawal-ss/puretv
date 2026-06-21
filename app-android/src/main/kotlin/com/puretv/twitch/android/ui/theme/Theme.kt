package com.puretv.twitch.android.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * SECTION 10: UI Design System, shared visually across all three apps
 * (Compose Multiplatform makes the actual color/type objects shareable from
 * `core`, but we keep the Material3 ColorScheme/Typography wiring per-app
 * since theming APIs differ between Compose Android, Compose for TV, and
 * Compose Desktop).
 *
 * Design language: "premium Twitch dialect". Keep the purple, the dark, the
 * dense grid, but borrow editorial restraint. One accent (purple), a real type
 * scale, a true elevation ramp, and monospace numerals as the brand gesture.
 */
object PureTvColors {
    // Backgrounds (kept for back-compat: other files import Surface/SurfaceVariant)
    val Background = Color(0xFF0A0A0F)
    val Surface = Color(0xFF141420)
    val SurfaceVariant = Color(0xFF1E1E2E)

    // Elevation ramp. Higher number = visually closer to the viewer. Use these
    // instead of opaque overlays so cards, raised chrome, and inputs read as a
    // coherent stack rather than a flat field of one grey.
    val Surface1 = Color(0xFF15151F) // resting cards (StreamCard / GameTile body)
    val Surface2 = Color(0xFF1C1C29) // raised: thumbnail placeholders, sheets
    val Surface3 = Color(0xFF242434) // chips, inputs, pressed/hover affordances
    val Hairline = Color(0x14FFFFFF) // 8% white divider / 1dp outline

    // Brand
    val TwitchPurple = Color(0xFF9B5DE5)
    val TwitchPurpleLight = Color(0xFFC77DFF)
    val AdBlockGreen = Color(0xFF06D6A0)

    // Text
    val TextPrimary = Color(0xFFE8E8F0)
    val TextSecondary = Color(0xFF888899)
    val TextMuted = Color(0xFF555566)
    // WCAG-AA tertiary text for stream titles over Surface1 (the old TextMuted
    // failed contrast on card bodies). Use for the supporting title line.
    val TextTertiary = Color(0xFF9A9AA8)

    // Semantic
    val Live = Color(0xFFEB0400) // brightened so the LIVE pill punches on dark
    val Online = Color(0xFF43A047)
    val Warning = Color(0xFFFFB703)
}

private val DarkScheme = darkColorScheme(
    background = PureTvColors.Background,
    surface = PureTvColors.Surface,
    surfaceVariant = PureTvColors.SurfaceVariant,
    // Real surfaceContainer ramp so Material components (sheets, menus, cards)
    // tonally agree with our hand-rolled elevation tokens above.
    surfaceContainerLowest = PureTvColors.Background,
    surfaceContainerLow = PureTvColors.Surface1,
    surfaceContainer = PureTvColors.Surface1,
    surfaceContainerHigh = PureTvColors.Surface2,
    surfaceContainerHighest = PureTvColors.Surface3,
    primary = PureTvColors.TwitchPurple,
    secondary = PureTvColors.TwitchPurpleLight,
    onBackground = PureTvColors.TextPrimary,
    onSurface = PureTvColors.TextPrimary,
    outline = PureTvColors.Hairline,
    error = PureTvColors.Live,
)

/**
 * Display/title families resolve to [FontFamily.Default] and numerals/labels to
 * [FontFamily.Monospace] (the "monospace numerals" brand gesture: tabular,
 * non-jittering viewer counts and badge text).
 *
 * FOLLOW-UP: bundle real families. Display/title want "Space Grotesk", body
 * wants "Inter", and chat/numerals want a true mono ("JetBrains Mono"). Add
 * them under res/font + a downloadable GoogleFont.Provider, then swap the
 * fontFamily lines below. No binary font assets are bundled today, so we hold
 * to the platform defaults and complete the full SCALE instead.
 */
private val Display = FontFamily.Default
private val Body = FontFamily.Default
private val Mono = FontFamily.Monospace

val PureTvTypography = Typography(
    // Display: reserved for hero numbers / splash. Tight tracking at large size.
    displaySmall = TextStyle(
        fontFamily = Display,
        fontSize = 34.sp,
        lineHeight = 40.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.5).sp,
    ),
    // Headlines: screen and section titles.
    headlineLarge = TextStyle(
        fontFamily = Display,
        fontSize = 28.sp,
        lineHeight = 34.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.4).sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = Display,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.2).sp,
    ),
    // Titles: card headers, list-item leads.
    titleLarge = TextStyle(
        fontFamily = Display,
        fontSize = 18.sp,
        lineHeight = 24.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.1).sp,
    ),
    titleMedium = TextStyle(
        fontFamily = Display,
        fontSize = 15.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.sp,
    ),
    // Body: prose, descriptions, chat.
    bodyLarge = TextStyle(
        fontFamily = Body,
        fontSize = 15.sp,
        lineHeight = 22.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = 0.1.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = Body,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = 0.1.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = Body,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = 0.1.sp,
    ),
    // Labels: buttons, captions.
    labelMedium = TextStyle(
        fontFamily = Body,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.4.sp,
    ),
    // labelSmall is mono: it backs the LIVE pill, ad-block pill, and all-caps
    // micro-labels where tabular, non-shifting glyphs matter.
    labelSmall = TextStyle(
        fontFamily = Mono,
        fontSize = 10.sp,
        lineHeight = 14.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.sp,
    ),
)

/**
 * Monospace numeral style for live viewer counts. Tabular figures keep counts
 * from reflowing as they tick (1.2K -> 1.3K does not shift the layout).
 */
val ViewerCountStyle = TextStyle(
    fontFamily = Mono,
    fontSize = 12.sp,
    lineHeight = 16.sp,
    fontWeight = FontWeight.Medium,
    letterSpacing = 0.sp,
)

/** Corner ramp: tight inputs (small), cards (medium), sheets/hero (large). */
val PureTvShapes = Shapes(
    small = RoundedCornerShape(6.dp),
    medium = RoundedCornerShape(10.dp),
    large = RoundedCornerShape(16.dp),
)

@Composable
fun PureTvTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkScheme,
        typography = PureTvTypography,
        shapes = PureTvShapes,
        content = content,
    )
}
