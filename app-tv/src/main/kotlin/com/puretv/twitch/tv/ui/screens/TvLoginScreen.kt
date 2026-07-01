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
 * SECTION 03.2 / 07 — TV login entry point (Twitch Device Code Grant flow).
 *
 * Typing a Twitch username/password with a D-pad is painful and Twitch rejects
 * custom-scheme redirects, so this screen shows a short [LoginUiState.userCode]
 * and points the viewer at [LoginUiState.verificationUri] (twitch.tv/activate)
 * to enter on a phone or computer. [LoginViewModel] polls Twitch in the
 * background; once the code is approved the session persists on THIS device and
 * the screen auto-advances via [onLoggedIn]. No redirect/QR round-trip needed.
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
            val verificationUri = state.verificationUri ?: "twitch.tv/activate"
            Text(
                text = "On your phone or computer, go to:",
                style = MaterialTheme.typography.bodyLarge,
                color = PureTvTvColors.TextPrimary,
            )
            Text(
                text = verificationUri.removePrefix("https://").removePrefix("http://"),
                style = MaterialTheme.typography.headlineMedium,
                color = PureTvTvColors.TwitchPurpleLight,
            )

            Text(
                text = "and enter this code:",
                style = MaterialTheme.typography.bodyLarge,
                color = PureTvTvColors.TextPrimary,
            )

            // The user code, rendered large and monospace-spaced so it's readable
            // from the couch. Falls back to a waiting message until Twitch returns it.
            Column(
                modifier = Modifier
                    .background(PureTvTvColors.SurfaceVariant, RoundedCornerShape(12.dp))
                    .padding(horizontal = 40.dp, vertical = 24.dp),
            ) {
                Text(
                    text = state.userCode ?: "Requesting code…",
                    style = MaterialTheme.typography.displayMedium,
                    color = PureTvTvColors.TextPrimary,
                )
            }

            Text(
                text = "Keep this screen open — you'll be signed in automatically once you approve access on Twitch.",
                style = MaterialTheme.typography.bodyMedium,
                color = PureTvTvColors.TextSecondary,
            )

            state.error?.let { error ->
                Text(text = error, style = MaterialTheme.typography.bodyMedium, color = PureTvTvColors.Live)
            }

            Button(onClick = viewModel::beginLogin) { Text("Get a new code") }
        }
    }
}
