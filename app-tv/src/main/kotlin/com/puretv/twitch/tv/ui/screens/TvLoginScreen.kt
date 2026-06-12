package com.puretv.twitch.tv.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.puretv.twitch.tv.ui.LoginViewModel
import com.puretv.twitch.tv.ui.theme.PureTvTvColors
import org.koin.androidx.compose.koinViewModel

/**
 * SECTION 03.2 / 07 — TV login entry point.
 *
 * As documented on [LoginViewModel]: typing a full Twitch username/password
 * with a D-pad is painful, so this screen renders the `authorizeUrl` (and a
 * QR-code placeholder — wiring an actual QR generator is a follow-up; the
 * spec's "companion-device handoff" note applies) for the viewer to open on
 * their phone. The OAuth redirect still completes *on this device* via the
 * shared `puretv-twitch://auth` deep link — [LoginViewModel] is already
 * collecting [com.puretv.twitch.tv.AuthRedirectBus] from `init{}`, so once
 * the phone browser redirects back, `TvMainActivity.onNewIntent` captures it
 * and this screen auto-advances via [onLoggedIn].
 */
@Composable
fun TvLoginScreen(onLoggedIn: () -> Unit, onBack: () -> Unit, viewModel: LoginViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(Unit) { viewModel.beginLogin() }
    LaunchedEffect(state.isLoggedIn) { if (state.isLoggedIn) onLoggedIn() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PureTvTvColors.Background)
            .padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Button(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back")
            }
            Text(text = "Sign in to Twitch", style = MaterialTheme.typography.headlineLarge, color = PureTvTvColors.TextPrimary)
        }

        Column(
            modifier = Modifier
                .background(PureTvTvColors.Surface, RoundedCornerShape(16.dp))
                .padding(32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "On your phone or computer, open this address to sign in:",
                style = MaterialTheme.typography.bodyLarge,
                color = PureTvTvColors.TextPrimary,
            )

            // QR placeholder — replace with a real QR bitmap generator (e.g. ZXing)
            // once a CLIENT_ID is registered; the authorizeUrl below is fully
            // functional and can be typed manually in the meantime.
            Column(
                modifier = Modifier
                    .width(220.dp)
                    .background(PureTvTvColors.SurfaceVariant, RoundedCornerShape(12.dp))
                    .padding(24.dp),
            ) {
                Text(
                    text = "[ QR code placeholder ]",
                    style = MaterialTheme.typography.bodyMedium,
                    color = PureTvTvColors.TextMuted,
                )
            }

            Text(
                text = state.authorizeUrl ?: "Generating sign-in link…",
                style = MaterialTheme.typography.titleLarge,
                color = PureTvTvColors.TwitchPurpleLight,
            )

            Text(
                text = "After approving access on Twitch, you'll be returned here automatically.",
                style = MaterialTheme.typography.bodyMedium,
                color = PureTvTvColors.TextSecondary,
            )

            state.error?.let { error ->
                Text(text = error, style = MaterialTheme.typography.bodyMedium, color = PureTvTvColors.Live)
            }

            Button(onClick = viewModel::beginLogin) { Text("Generate a new sign-in link") }
        }
    }
}
