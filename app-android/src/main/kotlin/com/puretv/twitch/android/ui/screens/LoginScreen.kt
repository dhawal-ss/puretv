package com.puretv.twitch.android.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.puretv.twitch.android.ui.LoginViewModel
import com.puretv.twitch.android.ui.theme.PureTvColors
import org.koin.androidx.compose.koinViewModel

/**
 * SECTION 03.2: Twitch login via the Device Code Grant flow (the same flow the
 * desktop app uses). The user opens twitch.tv/activate, enters the shown code,
 * and [LoginViewModel] polls until Twitch authorizes it. No browser redirect is
 * involved (Twitch does not accept custom-scheme redirect URIs), so this works
 * on phone, tablet, and the TV app alike.
 */
@Composable
fun LoginScreen(onLoggedIn: () -> Unit, onBack: () -> Unit) {
    val viewModel: LoginViewModel = koinViewModel()
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    LaunchedEffect(state.isLoggedIn) {
        if (state.isLoggedIn) onLoggedIn()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Log in to Twitch", color = PureTvColors.TextPrimary) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = PureTvColors.TextPrimary)
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

            val code = state.userCode
            if (code == null) {
                Button(onClick = viewModel::beginLogin, enabled = !state.isAuthenticating) {
                    Text(if (state.isAuthenticating) "Starting..." else "Continue with Twitch")
                }
            } else {
                val verifyUrl = state.verificationUri ?: "https://www.twitch.tv/activate"
                Text(
                    "1. Open $verifyUrl on any device",
                    style = MaterialTheme.typography.bodyMedium,
                    color = PureTvColors.TextSecondary,
                )
                Text(
                    "2. Enter this code:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = PureTvColors.TextSecondary,
                )
                Text(
                    code,
                    style = MaterialTheme.typography.displaySmall,
                    color = PureTvColors.TwitchPurpleLight,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = { clipboard.setText(AnnotatedString(code)) }) { Text("Copy code") }
                    Button(onClick = {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(verifyUrl)))
                    }) { Text("Open Twitch") }
                }
                if (state.isAuthenticating) {
                    CircularProgressIndicator(color = PureTvColors.TwitchPurple)
                    Text(
                        "Waiting for you to authorize...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = PureTvColors.TextMuted,
                    )
                }
            }

            state.error?.let { error ->
                Text(error, style = MaterialTheme.typography.bodyMedium, color = PureTvColors.Live)
            }
        }
    }
}
