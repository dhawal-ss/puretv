package com.puretv.twitch.android.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.puretv.twitch.android.ui.LoginViewModel
import com.puretv.twitch.android.ui.WelcomeViewModel
import com.puretv.twitch.android.ui.components.AdFreeChip
import com.puretv.twitch.android.ui.components.streamThumbUrl
import com.puretv.twitch.android.ui.theme.PureTvColors
import org.koin.androidx.compose.koinViewModel

/**
 * SECTION 06.3: the Welcome gate (option C "hybrid peek"). A blurred grid of last
 * session's cached streams (or a branded gradient on first launch) sits behind a
 * glass connect card running the device-code flow. On sign-in, SessionState flips
 * and RootScreen crossfades this away into the populated tab shell.
 */
@Composable
fun WelcomeScreen(
    welcomeViewModel: WelcomeViewModel = koinViewModel(),
    loginViewModel: LoginViewModel = koinViewModel(),
) {
    val thumbnails by welcomeViewModel.thumbnails.collectAsState()
    val login by loginViewModel.state.collectAsState()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    Box(modifier = Modifier.fillMaxSize().background(PureTvColors.Background)) {
        if (thumbnails.isNotEmpty()) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxSize().blur(26.dp),
                userScrollEnabled = false,
                contentPadding = PaddingValues(6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(thumbnails) { url ->
                    AsyncImage(
                        model = streamThumbUrl(url),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(MaterialTheme.shapes.medium),
                    )
                }
            }
        } else {
            Box(
                modifier = Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        listOf(PureTvColors.TwitchPurple.copy(alpha = 0.28f), PureTvColors.Background),
                    ),
                ),
            )
        }
        Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)))

        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .padding(28.dp)
                .clip(MaterialTheme.shapes.large)
                .background(PureTvColors.Surface1.copy(alpha = 0.94f))
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("PureTV", style = MaterialTheme.typography.headlineMedium, color = PureTvColors.TextPrimary)
                AdFreeChip()
            }
            Text(
                "Watch Twitch, ad-free.",
                style = MaterialTheme.typography.bodyLarge,
                color = PureTvColors.TextSecondary,
                textAlign = TextAlign.Center,
            )

            val code = login.userCode
            if (code == null) {
                Text(
                    "Connect your Twitch account to load your follows and chat. No password, no tracking.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = PureTvColors.TextSecondary,
                    textAlign = TextAlign.Center,
                )
                Button(
                    onClick = loginViewModel::beginLogin,
                    enabled = !login.isAuthenticating,
                    colors = ButtonDefaults.buttonColors(containerColor = PureTvColors.TwitchPurple),
                ) { Text(if (login.isAuthenticating) "Starting..." else "Connect with Twitch") }
            } else {
                val verifyUrl = login.verificationUri ?: "https://www.twitch.tv/activate"
                Text(
                    "Open $verifyUrl and enter:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = PureTvColors.TextSecondary,
                    textAlign = TextAlign.Center,
                )
                Text(code, style = MaterialTheme.typography.displaySmall, color = PureTvColors.TwitchPurpleLight)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = { clipboard.setText(AnnotatedString(code)) },
                        colors = ButtonDefaults.buttonColors(containerColor = PureTvColors.Surface3),
                    ) { Text("Copy") }
                    Button(
                        onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(verifyUrl))) },
                        colors = ButtonDefaults.buttonColors(containerColor = PureTvColors.TwitchPurple),
                    ) { Text("Open Twitch") }
                }
                if (login.isAuthenticating) {
                    CircularProgressIndicator(color = PureTvColors.TwitchPurpleLight)
                    Text(
                        "Waiting for you to authorize...",
                        style = MaterialTheme.typography.bodySmall,
                        color = PureTvColors.TextMuted,
                    )
                }
            }

            login.error?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = PureTvColors.Live) }
        }
    }
}
