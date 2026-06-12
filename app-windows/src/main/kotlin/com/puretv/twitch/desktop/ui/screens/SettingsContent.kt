package com.puretv.twitch.desktop.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.puretv.twitch.core.adblock.AdBlockStrategy
import com.puretv.twitch.core.model.StreamQuality
import com.puretv.twitch.desktop.ui.SettingsViewModel
import com.puretv.twitch.desktop.ui.rememberDesktopViewModel
import com.puretv.twitch.desktop.ui.theme.PureTvTheme
import com.puretv.twitch.desktop.ui.theme.ThemeVariant
import com.puretv.twitch.desktop.ui.theme.themeColors
import com.puretv.twitch.desktop.update.UpdateManager
import com.puretv.twitch.desktop.update.UpdateState
import org.koin.core.Koin

@Composable
fun SettingsContent(koin: Koin, onExit: () -> Unit) {
    val viewModel = rememberDesktopViewModel { koin.get<SettingsViewModel>() }
    val state by viewModel.state.collectAsState()
    val updateManager = remember { koin.get<UpdateManager>() }
    val updateState by updateManager.state.collectAsState()
    val c = PureTvTheme.colors

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium, color = c.textPrimary)

        SettingsSection(title = "Appearance") {
            Text("Theme", style = MaterialTheme.typography.titleMedium, color = c.textPrimary)
            val currentVariant = ThemeVariant.fromKey(state.settings.theme)
            Row(modifier = Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ThemeVariant.entries.forEach { variant ->
                    ThemeSwatch(
                        variant = variant,
                        selected = variant == currentVariant,
                        onClick = { viewModel.setTheme(variant.key) },
                    )
                }
            }
        }

        SettingsSection(title = "Playback") {
            Text("Preferred quality", style = MaterialTheme.typography.titleMedium, color = c.textPrimary)
            Row(modifier = Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StreamQuality.entries.forEach { quality ->
                    val selected = state.settings.preferredQuality.equals(quality.name, ignoreCase = true)
                    QualityChip(label = quality.label, selected = selected, onClick = { viewModel.setPreferredQuality(quality) })
                }
            }
        }

        SettingsSection(title = "Ad Block") {
            SettingsRow(
                label = "Block ads",
                description = "Routes playback through the local proxy so Twitch's mid-roll ads never reach the player.",
            ) {
                Switch(
                    checked = state.settings.adBlockEnabled,
                    onCheckedChange = viewModel::setAdBlockEnabled,
                    colors = SwitchDefaults.colors(checkedThumbColor = c.twitchPurple),
                )
            }
            Text("Strategy", style = MaterialTheme.typography.titleMedium, color = c.textPrimary, modifier = Modifier.padding(top = 12.dp))
            Row(modifier = Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AdBlockStrategy.entries.forEach { strategy ->
                    val selected = state.settings.adBlockStrategy.equals(strategy.name, ignoreCase = true)
                    QualityChip(
                        label = strategy.name.lowercase().replaceFirstChar { it.uppercase() },
                        selected = selected,
                        onClick = { viewModel.setAdBlockStrategy(strategy) },
                    )
                }
            }
            Text(
                "Local proxy on http://localhost:${state.proxyPort} — VLC streams through it, never directly from Twitch's CDN.",
                style = MaterialTheme.typography.bodyMedium,
                color = c.textMuted,
                modifier = Modifier.padding(top = 12.dp),
            )
        }

        SettingsSection(title = "Account") {
            if (state.isLoggedIn) {
                Text("Signed in as ${state.loginUsername ?: "(unknown)"}", style = MaterialTheme.typography.bodyLarge, color = c.textPrimary)
                Button(onClick = viewModel::logOut, modifier = Modifier.padding(top = 8.dp)) {
                    Text("Log out")
                }
            } else {
                Text(
                    "Not signed in — open the Account tab to sign in with Twitch.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = c.textSecondary,
                )
            }
        }

        SettingsSection(title = "About") {
            Text("PureTV for Twitch", style = MaterialTheme.typography.titleMedium, color = c.textPrimary)
            Text("Version ${updateManager.currentVersion}", style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
            Spacer(Modifier.height(12.dp))
            when (val s = updateState) {
                UpdateState.Idle -> Button(onClick = { updateManager.checkForUpdates(force = true) }) {
                    Text("Check for updates")
                }
                is UpdateState.Available -> {
                    Text("Update available: ${s.info.version}", style = MaterialTheme.typography.bodyMedium, color = c.twitchPurpleLight)
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { updateManager.downloadAndInstall(s.info, onExit) }) {
                        Text("Download & install ${s.info.version}")
                    }
                }
                is UpdateState.Downloading -> Text(
                    "Downloading update… ${(s.progress * 100).toInt()}%",
                    style = MaterialTheme.typography.bodyMedium,
                    color = c.textSecondary,
                )
                is UpdateState.Error -> {
                    Text("Update failed: ${s.message}", style = MaterialTheme.typography.bodyMedium, color = c.live)
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { updateManager.checkForUpdates(force = true) }) { Text("Retry") }
                }
            }
        }
    }
}

@Composable
private fun ThemeSwatch(variant: ThemeVariant, selected: Boolean, onClick: () -> Unit) {
    val c = PureTvTheme.colors
    val vc = themeColors[variant]!!
    Column(
        modifier = Modifier
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) c.twitchPurple else c.hairline,
                shape = RoundedCornerShape(10.dp),
            )
            .clip(RoundedCornerShape(10.dp))
            .background(vc.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Box(Modifier.size(12.dp).background(vc.background, CircleShape))
            Box(Modifier.size(12.dp).background(vc.twitchPurple, CircleShape))
        }
        Text(variant.displayName, color = vc.textSecondary, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    val c = PureTvTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 20.dp)
            .background(c.surface, RoundedCornerShape(12.dp))
            .padding(16.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge, color = c.twitchPurpleLight)
        Column(modifier = Modifier.padding(top = 12.dp)) { content() }
    }
}

@Composable
private fun SettingsRow(label: String, description: String, trailing: @Composable () -> Unit) {
    val c = PureTvTheme.colors
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.titleMedium, color = c.textPrimary)
            Text(description, style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
        }
        trailing()
    }
}

@Composable
private fun QualityChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val c = PureTvTheme.colors
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) c.twitchPurple else c.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(
            label,
            color = if (selected) Color.White else c.textSecondary,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}
