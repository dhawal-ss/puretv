package com.puretv.twitch.tv.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.puretv.twitch.tv.ui.LoginViewModel
import com.puretv.twitch.tv.ui.QrCode
import com.puretv.twitch.tv.ui.theme.PureTvTvColors
import org.koin.androidx.compose.koinViewModel

/**
 * SECTION 03.2 / 07: TV login entry point (Twitch Device Code Grant flow).
 *
 * Typing a Twitch username/password with a D-pad is painful and Twitch rejects
 * custom-scheme redirects, so this screen shows a scannable QR of the
 * [LoginUiState.verificationUri] (twitch.tv/activate) plus a short
 * [LoginUiState.userCode] to enter on a phone or computer. [LoginViewModel]
 * polls Twitch in the background; once the code is approved the session persists
 * on THIS device and the screen auto-advances via [onLoggedIn].
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
            val verificationUri = state.verificationUri ?: "https://www.twitch.tv/activate"
            // Regenerate the QR only when the verification URL actually changes.
            val qr = remember(state.verificationUri) {
                state.verificationUri?.let { QrCode.generate(it) }
            }

            Text(
                text = "Scan this code with your phone, or open the address below:",
                style = MaterialTheme.typography.bodyLarge,
                color = PureTvTvColors.TextPrimary,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(32.dp), verticalAlignment = Alignment.CenterVertically) {
                // QR on a white plate so phone cameras read it reliably.
                Box(
                    modifier = Modifier
                        .background(Color.White, RoundedCornerShape(12.dp))
                        .padding(12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    if (qr != null) {
                        Image(bitmap = qr, contentDescription = "Sign-in QR code", modifier = Modifier.size(200.dp))
                    } else {
                        Box(modifier = Modifier.size(200.dp), contentAlignment = Alignment.Center) {
                            Text(text = "Loading…", color = Color(0xFF555555))
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = verificationUri.removePrefix("https://").removePrefix("http://").removePrefix("www."),
                        style = MaterialTheme.typography.headlineMedium,
                        color = PureTvTvColors.TwitchPurpleLight,
                    )
                    Text(
                        text = "then enter this code:",
                        style = MaterialTheme.typography.bodyLarge,
                        color = PureTvTvColors.TextPrimary,
                    )
                    // The user code, large so it's readable from the couch. Falls
                    // back to a waiting message until Twitch returns it.
                    Box(
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
                }
            }

            Text(
                text = "Keep this screen open. You'll be signed in automatically once you approve access on Twitch.",
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
