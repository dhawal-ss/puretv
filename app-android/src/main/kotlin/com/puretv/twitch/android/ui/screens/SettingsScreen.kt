package com.puretv.twitch.android.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.puretv.twitch.android.ui.theme.PureTvColors
import com.puretv.twitch.core.model.StreamQuality
import org.koin.androidx.compose.koinViewModel

/**
 * SECTION 06.4 / 09.2 — quality, ad-block mode, proxy URL, and account.
 * Backed by [AppSettingsStore] (DataStore + EncryptedSharedPreferences).
 */
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val viewModel: SettingsViewModel = koinViewModel()
    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", color = PureTvColors.TextPrimary) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = PureTvColors.TextPrimary)
                    }
                },
            )
        },
        containerColor = PureTvColors.Background,
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item { SectionHeader("Account") }
            item {
                if (state.isLoggedIn) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            state.loginUsername ?: "Logged in",
                            style = MaterialTheme.typography.bodyLarge,
                            color = PureTvColors.TextPrimary,
                        )
                        Button(onClick = viewModel::logOut) { Text("Log out") }
                    }
                } else {
                    Text(
                        "Not logged in — log in to follow channels and chat.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = PureTvColors.TextSecondary,
                    )
                }
            }

            item { Divider(color = PureTvColors.SurfaceVariant) }
            item { SectionHeader("Playback") }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Preferred quality", style = MaterialTheme.typography.bodyLarge, color = PureTvColors.TextPrimary)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(StreamQuality.AUTO, StreamQuality.SOURCE, StreamQuality.P720P60).forEach { quality ->
                            val selected = state.settings.preferredQuality.equals(quality.name, ignoreCase = true)
                            Button(
                                onClick = { viewModel.setPreferredQuality(quality) },
                                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                    containerColor = if (selected) PureTvColors.TwitchPurple else PureTvColors.Surface,
                                ),
                            ) { Text(quality.label) }
                        }
                    }
                }
            }

            item { Divider(color = PureTvColors.SurfaceVariant) }
            item { SectionHeader("Ad blocking") }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Enable ad block", style = MaterialTheme.typography.bodyLarge, color = PureTvColors.TextPrimary)
                    Switch(
                        checked = state.settings.adBlockEnabled,
                        onCheckedChange = viewModel::setAdBlockEnabled,
                        colors = SwitchDefaults.colors(checkedTrackColor = PureTvColors.AdBlockGreen),
                    )
                }
            }
            item {
                var proxyDraft by remember(state.settings.customProxyUrl) { mutableStateOf(state.settings.customProxyUrl) }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Custom proxy URL (TTV LOL PRO compatible)", style = MaterialTheme.typography.bodyMedium, color = PureTvColors.TextSecondary)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = proxyDraft,
                            onValueChange = { proxyDraft = it },
                            placeholder = { Text("https://api.ttv.lol", color = PureTvColors.TextMuted) },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        Button(onClick = { viewModel.setProxyUrl(proxyDraft) }) { Text("Save") }
                    }
                }
            }
            if (com.puretv.twitch.android.BuildConfig.DEBUG) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Simulate ads (debug)", style = MaterialTheme.typography.bodyLarge, color = PureTvColors.TextPrimary)
                        var simulateAds by remember { mutableStateOf(com.puretv.twitch.core.adblock.AdSimulator.enabled) }
                        Switch(
                            checked = simulateAds,
                            onCheckedChange = {
                                simulateAds = it
                                com.puretv.twitch.core.adblock.AdSimulator.enabled = it
                            },
                        )
                    }
                }
            }

            item { Divider(color = PureTvColors.SurfaceVariant) }
            item {
                Text(
                    "PureTV for Twitch — sideloaded build. Not affiliated with Twitch Interactive, Inc.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = PureTvColors.TextMuted,
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(title, style = MaterialTheme.typography.titleLarge, color = PureTvColors.TwitchPurpleLight)
}
