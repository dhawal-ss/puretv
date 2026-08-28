package com.puretv.twitch.tv.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.puretv.twitch.tv.ui.LoginViewModel
import com.puretv.twitch.tv.ui.QrCode
import com.puretv.twitch.tv.ui.components.ExpressiveIcons
import com.puretv.twitch.tv.ui.components.TvButtonStyle
import com.puretv.twitch.tv.ui.components.TvExpressiveButton
import com.puretv.twitch.tv.ui.components.TvExpressiveIconButton
import com.puretv.twitch.tv.ui.components.TvShieldPill
import com.puretv.twitch.tv.ui.theme.PureTvTvTheme
import com.puretv.twitch.tv.ui.theme.PureTvTvType
import org.koin.androidx.compose.koinViewModel

/**
 * SECTION 03.2 / 07: TV login entry point (Twitch Device Code Grant flow).
 *
 * Typing a Twitch username/password with a D-pad is painful and Twitch rejects
 * custom-scheme redirects, so this screen shows a scannable QR of the
 * [LoginUiState.verificationUri] (twitch.tv/activate) plus a short
 * [LoginUiState.userCode] to enter on a phone or computer. [LoginViewModel]
 * polls Twitch in the background; once the code is approved the session persists
 * on THIS device and the screen auto-advances via [onLoggedIn]. That flow, its
 * polling and its error handling are untouched here, only the shell around it.
 */
@Composable
fun TvLoginScreen(onLoggedIn: () -> Unit, onBack: () -> Unit, viewModel: LoginViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsState()
    val c = PureTvTvTheme.colors

    LaunchedEffect(Unit) { viewModel.beginLogin() }
    LaunchedEffect(state.isLoggedIn) { if (state.isLoggedIn) onLoggedIn() }

    Box(modifier = Modifier.fillMaxSize().background(c.surface)) {
        TvExpressiveIconButton(
            icon = ExpressiveIcons.Back,
            contentDescription = "Back",
            onClick = onBack,
            modifier = Modifier.align(Alignment.TopStart).padding(32.dp),
        )

        Box(modifier = Modifier.fillMaxSize().padding(48.dp), contentAlignment = Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .widthIn(max = 760.dp)
                    .clip(RoundedCornerShape(48.dp))
                    .background(c.surfaceContainer)
                    .padding(56.dp),
            ) {
                AppMark()

                Spacer(Modifier.height(28.dp))
                Text(
                    "Sign in once, then just watch.",
                    style = MaterialTheme.typography.displayMedium,
                    color = c.onSurface,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    "Scan this code with your phone, or open the address below on any device. " +
                        "Sign-in happens on Twitch's own page, so your password never reaches this app.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = c.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )

                Spacer(Modifier.height(36.dp))

                val verificationUri = state.verificationUri ?: "https://www.twitch.tv/activate"
                // Regenerate the QR only when the verification URL actually changes.
                val qr = remember(state.verificationUri) {
                    state.verificationUri?.let { QrCode.generate(it) }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(36.dp), verticalAlignment = Alignment.CenterVertically) {
                    // QR on a white plate so phone cameras read it reliably.
                    Box(
                        modifier = Modifier
                            .background(Color.White, RoundedCornerShape(16.dp))
                            .padding(14.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (qr != null) {
                            Image(bitmap = qr, contentDescription = "Sign-in QR code", modifier = Modifier.size(220.dp))
                        } else {
                            Box(modifier = Modifier.size(220.dp), contentAlignment = Alignment.Center) {
                                Text(text = "Loading…", color = Color(0xFF555555))
                            }
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text(
                            text = verificationUri.removePrefix("https://").removePrefix("http://").removePrefix("www."),
                            style = MaterialTheme.typography.headlineMedium,
                            color = c.primary,
                        )
                        Text(
                            text = "then enter this code:",
                            style = MaterialTheme.typography.bodyLarge,
                            color = c.onSurface,
                        )
                        // The user code, large so it's readable from the couch. Falls
                        // back to a waiting message until Twitch returns it.
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(c.surfaceHigh)
                                .padding(horizontal = 32.dp, vertical = 20.dp),
                        ) {
                            Text(
                                text = state.userCode ?: "Requesting code…",
                                style = MaterialTheme.typography.displayMedium.copy(fontFamily = PureTvTvType.mono, letterSpacing = 6.sp),
                                color = c.onSurface,
                            )
                        }
                    }
                }

                Spacer(Modifier.height(28.dp))
                Text(
                    text = "Keep this screen open. You'll be signed in automatically once you approve access on Twitch.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = c.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )

                state.error?.let { error ->
                    Spacer(Modifier.height(20.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(c.errorContainer)
                            .padding(horizontal = 20.dp, vertical = 14.dp),
                    ) {
                        Text(text = error, style = MaterialTheme.typography.bodyMedium, color = c.onErrorContainer, textAlign = TextAlign.Center)
                    }
                }

                Spacer(Modifier.height(32.dp))
                TvExpressiveButton(
                    text = "Get a new code",
                    onClick = viewModel::beginLogin,
                    style = TvButtonStyle.Outlined,
                    icon = ExpressiveIcons.Refresh,
                )

                Spacer(Modifier.height(28.dp))
                TvShieldPill(text = "Ad blocking runs on-device, always")
            }
        }
    }
}

/** The app mark: a flat square carrying the "P", the same identity every screen opens on. */
@Composable
private fun AppMark() {
    val c = PureTvTvTheme.colors
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(104.dp).clip(RoundedCornerShape(36.dp)).background(c.primary),
    ) {
        Text(
            "P",
            style = MaterialTheme.typography.displaySmall.copy(fontSize = 56.sp),
            color = c.onPrimary,
            fontWeight = FontWeight.ExtraBold,
        )
    }
}
