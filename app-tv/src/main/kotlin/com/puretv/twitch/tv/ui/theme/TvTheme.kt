package com.puretv.twitch.tv.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Typography
import androidx.tv.material3.darkColorScheme

/**
 * SECTION 10 — same PureTV design-system palette as the phone app
 * (`PureTvColors` in app-android), re-declared here because Compose for TV's
 * `androidx.tv.material3` ColorScheme/Typography types are distinct from
 * Material3's — the values must match exactly across platforms even though
 * the wiring can't be shared (Section 10 notes this is per-app by necessity).
 */
object PureTvTvColors {
    val Background = Color(0xFF0A0A0F)
    val Surface = Color(0xFF141420)
    val SurfaceVariant = Color(0xFF1E1E2E)

    val TwitchPurple = Color(0xFF9B5DE5)
    val TwitchPurpleLight = Color(0xFFC77DFF)
    val AdBlockGreen = Color(0xFF06D6A0)

    val TextPrimary = Color(0xFFE8E8F0)
    val TextSecondary = Color(0xFF888899)
    val TextMuted = Color(0xFF555566)

    val Live = Color(0xFFE53935)
    val Online = Color(0xFF43A047)
    val Warning = Color(0xFFFFB703)

    /** Scale-on-focus border color — the canonical TV "this has the D-pad" cue (Section 7.3). */
    val FocusBorder = TwitchPurple
}

private val TvDarkScheme = darkColorScheme(
    background = PureTvTvColors.Background,
    surface = PureTvTvColors.Surface,
    surfaceVariant = PureTvTvColors.SurfaceVariant,
    primary = PureTvTvColors.TwitchPurple,
    secondary = PureTvTvColors.TwitchPurpleLight,
    onBackground = PureTvTvColors.TextPrimary,
    onSurface = PureTvTvColors.TextPrimary,
    error = PureTvTvColors.Live,
)

/** 10-foot type scale — sized up from the phone app's [PureTvTypography] for couch viewing distance. */
val PureTvTvTypography = Typography(
    displayLarge = TextStyle(fontSize = 36.sp, fontWeight = FontWeight.Bold),
    headlineLarge = TextStyle(fontSize = 30.sp, fontWeight = FontWeight.Bold),
    headlineMedium = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 18.sp),
    bodyMedium = TextStyle(fontSize = 16.sp),
    labelSmall = TextStyle(fontSize = 13.sp, letterSpacing = 1.sp),
)

@Composable
fun PureTvTvTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = TvDarkScheme, typography = PureTvTvTypography, content = content)
}
