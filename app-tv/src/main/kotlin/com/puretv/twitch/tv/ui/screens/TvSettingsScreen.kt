package com.puretv.twitch.tv.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Switch
import androidx.tv.material3.Text
import com.puretv.twitch.core.adblock.AdBlockStrategy
import com.puretv.twitch.core.model.StreamQuality
import com.puretv.twitch.tv.ui.SettingsViewModel
import com.puretv.twitch.tv.ui.theme.PureTvTvColors
import com.puretv.twitch.tv.update.TvUpdateManager
import com.puretv.twitch.tv.update.TvUpdateState
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

/**
 * SECTION 07.2 / 10.4 — focusable settings rows mirroring the phone app's
 * `SettingsScreen` content (preferred quality, ad-block toggle/strategy,
 * proxy URL, account/sign-out) but laid out as a single D-pad-navigable
 * column rather than scrollable Material3 list items, per the 10-foot
 * pattern used throughout `app-tv`.
 */
@Composable
fun TvSettingsScreen(onBack: () -> Unit, viewModel: SettingsViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsState()
    val updateManager = koinInject<TvUpdateManager>()
    val updateState by updateManager.state.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PureTvTvColors.Background)
            .verticalScroll(rememberScrollState())
            .padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Button(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back")
            }
            Text(text = "Settings", style = MaterialTheme.typography.headlineLarge, color = PureTvTvColors.TextPrimary)
        }

        SettingsSection(title = "Stream quality") {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StreamQuality.entries.forEach { quality ->
                    val selected = state.settings.preferredQuality.equals(quality.name, ignoreCase = true)
                    Button(onClick = { viewModel.setPreferredQuality(quality) }) {
                        Text(
                            text = quality.label,
                            color = if (selected) PureTvTvColors.TwitchPurple else Color.White,
                        )
                    }
                }
            }
        }

        SettingsSection(title = "Ad blocking") {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(text = "Enabled", style = MaterialTheme.typography.bodyLarge, color = PureTvTvColors.TextPrimary)
                Switch(checked = state.settings.adBlockEnabled, onCheckedChange = viewModel::setAdBlockEnabled)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AdBlockStrategy.entries.forEach { strategy ->
                    val selected = state.settings.adBlockStrategy.equals(strategy.name, ignoreCase = true)
                    Button(onClick = { viewModel.setAdBlockStrategy(strategy) }) {
                        Text(
                            text = strategy.name.lowercase().replaceFirstChar { it.uppercase() },
                            color = if (selected) PureTvTvColors.TwitchPurple else Color.White,
                        )
                    }
                }
            }
        }

        SettingsSection(title = "Software update") {
            UpdateSettingsContent(
                currentVersion = updateManager.currentVersionName,
                state = updateState,
                onCheck = { updateManager.checkForUpdates(force = true) },
                onInstall = { info -> updateManager.downloadAndInstall(info) },
            )
        }

        SettingsSection(title = "Account") {
            if (state.isLoggedIn) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Signed in as ${state.loginUsername ?: "Twitch user"}",
                        style = MaterialTheme.typography.bodyLarge,
                        color = PureTvTvColors.TextPrimary,
                    )
                    Button(onClick = viewModel::logOut) { Text("Sign out") }
                }
            } else {
                Text(
                    text = "Not signed in — sign in from the nav rail to follow channels and chat with your account.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = PureTvTvColors.TextSecondary,
                )
            }
        }
    }
}

@Composable
private fun UpdateSettingsContent(
    currentVersion: String,
    state: TvUpdateState,
    onCheck: () -> Unit,
    onInstall: (com.puretv.twitch.tv.update.TvUpdateInfo) -> Unit,
) {
    Text(
        text = "Installed version $currentVersion",
        style = MaterialTheme.typography.bodyLarge,
        color = PureTvTvColors.TextSecondary,
    )
    when (state) {
        is TvUpdateState.Available -> {
            Text(
                text = "Update available: ${state.info.versionName}",
                style = MaterialTheme.typography.bodyLarge,
                color = PureTvTvColors.TwitchPurple,
            )
            if (state.info.notes.isNotBlank()) {
                Text(
                    text = state.info.notes.lineSequence().firstOrNull().orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = PureTvTvColors.TextSecondary,
                )
            }
            Button(onClick = { onInstall(state.info) }) { Text("Download & install") }
        }
        is TvUpdateState.Downloading ->
            Text(
                text = "Downloading… ${(state.progress * 100).toInt()}%",
                style = MaterialTheme.typography.bodyLarge,
                color = PureTvTvColors.TextPrimary,
            )
        TvUpdateState.Installing ->
            Text("Starting installer…", style = MaterialTheme.typography.bodyLarge, color = PureTvTvColors.TextPrimary)
        TvUpdateState.Checking ->
            Text("Checking for updates…", style = MaterialTheme.typography.bodyLarge, color = PureTvTvColors.TextSecondary)
        TvUpdateState.UpToDate -> {
            Text("You're on the latest version.", style = MaterialTheme.typography.bodyLarge, color = PureTvTvColors.TextSecondary)
            Button(onClick = onCheck) { Text("Check again") }
        }
        is TvUpdateState.Error -> {
            Text(state.message, style = MaterialTheme.typography.bodyLarge, color = PureTvTvColors.Live)
            Button(onClick = onCheck) { Text("Try again") }
        }
        TvUpdateState.Idle ->
            Button(onClick = onCheck) { Text("Check for updates") }
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(PureTvTvColors.Surface, RoundedCornerShape(12.dp))
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(text = title, style = MaterialTheme.typography.titleLarge, color = PureTvTvColors.TextPrimary)
        content()
    }
}
