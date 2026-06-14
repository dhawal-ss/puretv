package com.puretv.twitch.desktop.ui.theme

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Font

/**
 * The Cinémathèque type system — bundled OFL fonts loaded from the classpath
 * (`src/main/resources/fonts/`, the same place as `icon.ico`).
 *
 *  • Bricolage Grotesque — display / mastheads / channel names (the editorial voice)
 *  • Archivo             — every functional UI string (body, labels, buttons)
 *  • IBM Plex Mono       — data, viewer counts, timestamps, kickers (the "instrument" layer)
 *
 * Archivo and Bricolage ship upstream as VARIABLE fonts; Compose 1.7's desktop
 * `Font(resource, …)` has no variation-axis support (it would render every weight at
 * the default instance), so static weights were baked from the variable masters with
 * `fontTools.varLib.instancer` (pinning wght/wdth, plus opsz for Bricolage). Each
 * weight is therefore a real static TTF, loaded plainly by [Font].
 */

val BricolageGrotesque: FontFamily = FontFamily(
    Font("fonts/BricolageGrotesque-SemiBold.ttf", FontWeight.SemiBold),
    Font("fonts/BricolageGrotesque-Bold.ttf", FontWeight.Bold),
    Font("fonts/BricolageGrotesque-ExtraBold.ttf", FontWeight.ExtraBold),
)

val Archivo: FontFamily = FontFamily(
    Font("fonts/Archivo-Light.ttf", FontWeight.Light),
    Font("fonts/Archivo-Regular.ttf", FontWeight.Normal),
    Font("fonts/Archivo-Medium.ttf", FontWeight.Medium),
    Font("fonts/Archivo-SemiBold.ttf", FontWeight.SemiBold),
    Font("fonts/Archivo-Bold.ttf", FontWeight.Bold),
)

val IBMPlexMono: FontFamily = FontFamily(
    Font("fonts/IBMPlexMono-Regular.ttf", FontWeight.Normal),
    Font("fonts/IBMPlexMono-Medium.ttf", FontWeight.Medium),
    Font("fonts/IBMPlexMono-SemiBold.ttf", FontWeight.SemiBold),
)
