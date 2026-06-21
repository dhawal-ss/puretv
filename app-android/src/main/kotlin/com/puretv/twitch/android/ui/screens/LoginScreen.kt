package com.puretv.twitch.android.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import android.content.Intent
import android.net.Uri
import com.puretv.twitch.android.ui.LoginViewModel
import com.puretv.twitch.android.ui.theme.PureTvColors
import org.koin.androidx.compose.koinViewModel

/**
 * SECTION 03.2 — Twitch login entry point. Opens the Authorization Code +
 * PKCE `authorize` URL in the system browser (no extra Custom Tabs
 * dependency needed — keeps the version catalog minimal); the OAuth
 * redirect lands back on `puretv-twitch://auth` (see AndroidManifest's
 * deep-link intent filter), which `MainActivity` forwards to
 * [LoginViewModel.completeWithCode].
 */
@Composable
fun LoginScreen(onLoggedIn: () -> Unit, onBack: () -> Unit) {
    val viewModel: LoginViewModel = koinViewModel()
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(state.isLoggedIn) {
        if (state.isLoggedIn) onLoggedIn()
    }

    LaunchedEffect(state.authorizeUrl) {
        val url = state.authorizeUrl ?: return@LaunchedEffect
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Log in to Twitch", color = PureTvColors.TextPrimary) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = PureTvColors.TextPrimary)
                    }
                },
            )
        },
        containerColor = PureTvColors.Background,
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "PureTV for Twitch needs your Twitch account to load your followed channels and let you chat.",
                style = MaterialTheme.typography.bodyLarge,
                color = PureTvColors.TextSecondary,
            )

            if (state.isAuthenticating) {
                CircularProgressIndicator(color = PureTvColors.TwitchPurple)
                Text("Waiting for Twitch authorization…", style = MaterialTheme.typography.bodyMedium, color = PureTvColors.TextMuted)
            } else {
                Button(onClick = viewModel::beginLogin) { Text("Continue with Twitch") }
            }

            state.error?.let { error ->
                Text(error, style = MaterialTheme.typography.bodyMedium, color = PureTvColors.Live)
            }
        }
    }
}
