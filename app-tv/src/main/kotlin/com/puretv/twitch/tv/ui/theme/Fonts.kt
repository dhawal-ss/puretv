package com.puretv.twitch.tv.ui.theme

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.puretv.twitch.tv.R

/**
 * The PureTV type system, the same three OFL families the desktop and TV apps
 * use, bundled here under `res/font`.
 *
 *  Bricolage Grotesque: display, mastheads, section headings.
 *  Archivo:             every functional UI string.
 *  IBM Plex Mono:       data, viewer counts, timestamps, badges.
 *
 * Archivo and Bricolage ship upstream as variable fonts. Compose resolves a
 * `res/font` family by picking the nearest declared weight rather than by
 * setting a variation axis, so these are real static instances baked from the
 * variable masters, one file per weight.
 */

val BricolageGrotesque = FontFamily(
    Font(R.font.bricolage_semibold, FontWeight.SemiBold),
    Font(R.font.bricolage_bold, FontWeight.Bold),
    Font(R.font.bricolage_extrabold, FontWeight.ExtraBold),
)

val Archivo = FontFamily(
    Font(R.font.archivo_light, FontWeight.Light),
    Font(R.font.archivo_regular, FontWeight.Normal),
    Font(R.font.archivo_medium, FontWeight.Medium),
    Font(R.font.archivo_semibold, FontWeight.SemiBold),
    Font(R.font.archivo_bold, FontWeight.Bold),
)

val IBMPlexMono = FontFamily(
    Font(R.font.ibm_plex_mono_regular, FontWeight.Normal),
    Font(R.font.ibm_plex_mono_medium, FontWeight.Medium),
    Font(R.font.ibm_plex_mono_semibold, FontWeight.SemiBold),
)
