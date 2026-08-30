package com.puretv.twitch.tv.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.puretv.twitch.core.api.TwitchConfig
import com.puretv.twitch.tv.ui.LoginViewModel
import com.puretv.twitch.tv.ui.QrCode
import com.puretv.twitch.tv.ui.components.ExpressiveIcons
import com.puretv.twitch.tv.ui.components.TvButtonStyle
import com.puretv.twitch.tv.ui.components.TvExpressiveButton
import com.puretv.twitch.tv.ui.components.TvExpressiveIconButton
import com.puretv.twitch.tv.ui.components.TvShieldPill
import com.puretv.twitch.tv.ui.components.shrinkToFitHeight
import com.puretv.twitch.tv.ui.theme.PureTvTvTheme
import com.puretv.twitch.tv.ui.theme.PureTvTvType
import org.koin.androidx.compose.koinViewModel

/**
 * SECTION 03.2 / 07: TV login entry point (Twitch Device Code Grant flow).
 *
 * Typing a Twitch username/password with a D-pad is painful and Twitch rejects
 * custom-scheme redirects, so this screen shows a scannable QR of the plain
 * twitch.tv/activate page plus a short [LoginUiState.userCode] to type there.
 * The QR points at the bare page on purpose: Twitch's own code-bearing
 * verification URL often fails to load, which is what made this flow flaky.
 *
 * [LoginViewModel] polls Twitch in the background; once the code is approved the
 * session persists on THIS device and the screen auto-advances via [onLoggedIn].
 * That flow, its polling and its error handling are untouched here.
 *
 * ## Why this screen is laid out sideways (issue #20)
 *
 * A television hands out roughly 960x540dp, and the stack this screen used to
 * be, hero mark over headline over paragraph over QR over code over footnote
 * over button over pill, wanted about 800dp of it. Everything from the QR down
 * fell off the bottom, and a 10-foot UI cannot scroll its way out of that:
 * scroll position only moves when focus moves, and neither the QR nor the
 * activation code is focusable. Sign-in was impossible, not merely awkward.
 *
 * So the composition is deliberately wide rather than tall. Identity, the
 * standing ad-block fact and the way back share one header row; the words sit
 * beside the QR instead of above it; and the whole block is centred inside a
 * five-percent overscan margin. That lands it near 470dp on the canonical TV
 * surface. [shrinkToFitHeight] then covers every set that is not canonical,
 * scaling the block down by whatever a smaller display size or a larger system
 * font demands, so the code is on screen whatever the panel reports. Height is
 * all it covers: the one row that could still run out of width at an extreme
 * text size is the header, so nothing that matters for signing in lives there.
 */
@Composable
fun TvLoginScreen(onLoggedIn: () -> Unit, onBack: () -> Unit, viewModel: LoginViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsState()
    val c = PureTvTvTheme.colors

    LaunchedEffect(Unit) { viewModel.beginLogin() }
    LaunchedEffect(state.isLoggedIn) { if (state.isLoggedIn) onLoggedIn() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(c.surface)
            // The 5% overscan margin every 10-foot layout is drawn inside, so a
            // set that trims its edges cannot take a corner of the card with it.
            .padding(horizontal = 48.dp, vertical = 27.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .widthIn(max = 1000.dp)
                .fillMaxWidth()
                .shrinkToFitHeight(),
        ) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TvExpressiveIconButton(
                    icon = ExpressiveIcons.Back,
                    contentDescription = "Back",
                    onClick = onBack,
                    boxSize = 56.dp,
                    iconSize = 26.dp,
                )
                Spacer(Modifier.width(20.dp))
                AppMark(size = 48.dp)
                Spacer(Modifier.width(14.dp))
                Text("PureTV", style = MaterialTheme.typography.titleLarge, color = c.onSurface)
                Spacer(Modifier.weight(1f))
                // Short on purpose. This row is the one part of the screen the
                // shrink-to-fit cannot rescue, since a pill is a fixed-height
                // single line: keep it narrow enough to survive a doubled text
                // size, and keep anything sign-in depends on out of here.
                TvShieldPill(text = "Ad blocking always on")
            }

            Spacer(Modifier.height(18.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(40.dp))
                    .background(c.surfaceContainer)
                    .padding(horizontal = 40.dp, vertical = 28.dp),
                horizontalArrangement = Arrangement.spacedBy(44.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Sign in once, then just watch.",
                        style = MaterialTheme.typography.headlineLarge,
                        color = c.onSurface,
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Scan the code with your phone, or open the address beside it on any " +
                            "device and type the code in. Your password never reaches this app: " +
                            "sign-in happens on Twitch's own page.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = c.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(14.dp))
                    Text(
                        "Keep this screen open. You'll be signed in automatically once you approve access.",
                        style = MaterialTheme.typography.bodySmall,
                        color = c.onSurfaceVariant,
                    )

                    state.error?.let { error ->
                        Spacer(Modifier.height(14.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(c.errorContainer)
                                .padding(horizontal = 18.dp, vertical = 12.dp),
                        ) {
                            Text(text = error, style = MaterialTheme.typography.bodySmall, color = c.onErrorContainer)
                        }
                    }

                    Spacer(Modifier.height(20.dp))
                    TvExpressiveButton(
                        text = "Get a new code",
                        onClick = viewModel::beginLogin,
                        style = TvButtonStyle.Outlined,
                        icon = ExpressiveIcons.Refresh,
                    )
                }

                // The payload half. Nothing in this column is focusable, which is
                // exactly why it must never be the part that runs off the edge.
                Column(
                    modifier = Modifier.width(264.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    // The destination never varies, so the QR is built once and is on
                    // screen before Twitch has even answered. It deliberately does NOT
                    // encode the code-bearing URL Twitch hands back: that pre-filled
                    // activate page often fails to load, which is what made scanning
                    // unreliable. Scan takes you to a plain page, then you type the code.
                    val qr = remember { QrCode.generate(TwitchConfig.ACTIVATE_URL) }

                    // QR on a white plate so phone cameras read it reliably.
                    Box(
                        modifier = Modifier
                            .background(Color.White, RoundedCornerShape(14.dp))
                            .padding(12.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (qr != null) {
                            Image(
                                bitmap = qr,
                                contentDescription = "Sign-in QR code",
                                modifier = Modifier.size(180.dp),
                                // Nearest-neighbour, because this bitmap is resampled
                                // twice over: once for the panel's density and again by
                                // any shrink-to-fit. Bilinear softens the module edges a
                                // phone camera has to resolve, and a soft QR at couch
                                // distance is a QR that does not scan.
                                filterQuality = FilterQuality.None,
                            )
                        } else {
                            // Only reachable if the encoder itself fails. The address is
                            // then the whole instruction, so say it rather than leaving
                            // a blank plate.
                            Box(modifier = Modifier.size(180.dp), contentAlignment = Alignment.Center) {
                                Text(
                                    text = "Use the address below",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color(0xFF555555),
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }
                    }

                    // The typed route out, stated as loudly as the scanned one.
                    // Whoever is reading this either has no phone camera to hand
                    // or has just watched the QR fail, so an address buried in
                    // body copy at 18sp is no use to them from the sofa.
                    Spacer(Modifier.height(14.dp))
                    Text(
                        text = "twitch.tv/activate",
                        style = MaterialTheme.typography.titleMedium,
                        color = c.primary,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "then enter this code",
                        style = MaterialTheme.typography.bodySmall,
                        color = c.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(8.dp))
                    // The user code, large so it reads from the couch. The minimum
                    // height is the height of the code itself, so the plate does not
                    // jump when the waiting message is replaced by the real thing.
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 72.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(c.surfaceHigh)
                            .padding(horizontal = 12.dp, vertical = 14.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        val code = state.userCode
                        if (code != null) {
                            // No line cap. Twitch's device codes are eight characters
                            // and fit on one line, but a clipped code is a code nobody
                            // can type, so a longer one wraps and the block shrinks
                            // to suit rather than losing its tail.
                            Text(
                                text = code,
                                style = MaterialTheme.typography.headlineLarge.copy(
                                    fontFamily = PureTvTvType.mono,
                                    letterSpacing = 4.sp,
                                ),
                                color = c.onSurface,
                                textAlign = TextAlign.Center,
                            )
                        } else {
                            Text(
                                text = "Requesting code…",
                                style = MaterialTheme.typography.bodyMedium,
                                color = c.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** The app mark: a flat square carrying the "P", the same identity every screen opens on. */
@Composable
private fun AppMark(size: Dp = 104.dp) {
    val c = PureTvTvTheme.colors
    // Converted through the current density rather than written as a literal
    // `sp`, so the glyph and its line box stay a fixed fraction of the mark. A
    // logo is not body copy: at a doubled system text size a plain `sp` would
    // push the "P" straight out of its own square.
    val density = LocalDensity.current
    val glyph = with(density) { (size * 0.54f).toSp() }
    val glyphLine = with(density) { (size * 0.62f).toSp() }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(size).clip(RoundedCornerShape(size * 0.35f)).background(c.primary),
    ) {
        Text(
            "P",
            style = MaterialTheme.typography.displaySmall.copy(fontSize = glyph, lineHeight = glyphLine),
            color = c.onPrimary,
            fontWeight = FontWeight.ExtraBold,
        )
    }
}
