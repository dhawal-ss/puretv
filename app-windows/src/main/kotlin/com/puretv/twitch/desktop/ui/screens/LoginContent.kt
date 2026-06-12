package com.puretv.twitch.desktop.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.puretv.twitch.desktop.ui.LoginViewModel
import com.puretv.twitch.desktop.ui.rememberDesktopViewModel
import com.puretv.twitch.desktop.ui.theme.PureTvTheme
import org.koin.core.Koin

@Composable
fun LoginContent(koin: Koin) {
    val viewModel = rememberDesktopViewModel { koin.get<LoginViewModel>() }
    val state by viewModel.state.collectAsState()
    val c = PureTvTheme.colors

    Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(420.dp)) {
            Text("Sign in to PureTV for Twitch", style = MaterialTheme.typography.headlineMedium, color = c.textPrimary)
            Text(
                "Signing in lets PureTV show your followed channels and lets you chat. " +
                    "We'll open your browser to Twitch's official login page — your password " +
                    "never touches this app.",
                style = MaterialTheme.typography.bodyMedium,
                color = c.textSecondary,
                modifier = Modifier.padding(top = 12.dp),
            )

            when {
                state.isLoggedIn -> Text(
                    "You're signed in.",
                    style = MaterialTheme.typography.titleMedium,
                    color = c.adBlockGreen,
                    modifier = Modifier.padding(top = 24.dp),
                )
                state.isAuthenticating -> Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(top = 24.dp),
                ) {
                    CircularProgressIndicator(color = c.twitchPurple)
                    Text(
                        "Waiting for you to finish signing in in your browser…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = c.textSecondary,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
                else -> Button(
                    onClick = viewModel::beginLogin,
                    modifier = Modifier.padding(top = 24.dp),
                ) { Text("Sign in with Twitch") }
            }

            state.error?.let { error ->
                Text(error, style = MaterialTheme.typography.bodyMedium, color = c.live, modifier = Modifier.padding(top = 16.dp))
            }
        }
    }
}
