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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.puretv.twitch.core.model.PlaybackBackend
import com.puretv.twitch.core.model.StreamQuality
import com.puretv.twitch.core.model.UpscalingMode
import com.puretv.twitch.desktop.ui.SettingsViewModel
import com.puretv.twitch.desktop.ui.components.ButtonVariant
import com.puretv.twitch.desktop.ui.components.Kicker
import com.puretv.twitch.desktop.ui.components.PureButton
import com.puretv.twitch.desktop.ui.components.SegmentedControl
import com.puretv.twitch.desktop.ui.components.handCursor
import com.puretv.twitch.desktop.ui.rememberDesktopViewModel
import com.puretv.twitch.desktop.ui.theme.PureTvShape
import com.puretv.twitch.desktop.ui.theme.PureTvTheme
import com.puretv.twitch.desktop.ui.theme.PureTvType
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
            .padding(horizontal = 40.dp, vertical = 36.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        Kicker("Settings", accent = true)
        Spacer(Modifier.height(12.dp))
        Text("Preferences", style = MaterialTheme.typography.displayLarge, color = c.textPrimary)

        // ── Appearance ───────────────────────────────────────────────────────────
        SettingsSection(title = "Appearance") {
            Text("Theme", style = MaterialTheme.typography.titleMedium, color = c.textPrimary)
            Text(
                "Choose the cinema base this app paints against.",
                style = MaterialTheme.typography.bodyMedium,
                color = c.textMuted,
            )
            val currentVariant = ThemeVariant.fromKey(state.settings.theme)
            Row(
                modifier = Modifier.padding(top = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                ThemeVariant.entries.forEach { variant ->
                    ThemeSwatch(
                        variant = variant,
                        selected = variant == currentVariant,
                        onClick = { viewModel.setTheme(variant.key) },
                    )
                }
            }
        }

        // ── Playback ─────────────────────────────────────────────────────────────
        SettingsSection(title = "Playback") {
            SettingsRow(
                label = "Preferred quality",
                description = "The variant PureTV reaches for first when a stream opens.",
            ) {
                val selectedQuality = StreamQuality.entries.firstOrNull {
                    state.settings.preferredQuality.equals(it.name, ignoreCase = true)
                } ?: StreamQuality.AUTO
                SegmentedControl(
                    options = StreamQuality.entries,
                    selected = selectedQuality,
                    label = { it.label },
                    onSelect = { viewModel.setPreferredQuality(it) },
                )
            }
            SettingsRow(
                label = "Playback engine",
                description = "VLC is the default. mpv enables GPU upscaling (libplacebo / Anime4K). Takes effect after restart.",
            ) {
                SegmentedControl(
                    options = PlaybackBackend.entries,
                    selected = state.settings.playbackBackend,
                    label = { it.label },
                    onSelect = { viewModel.setPlaybackBackend(it) },
                )
            }
            SettingsRow(
                label = "GPU upscaling",
                description = "Sharpen sub-native streams with GPU scaling. Standard uses libplacebo; Anime uses Anime4K. Requires the mpv engine (above) and takes effect after restart.",
            ) {
                SegmentedControl(
                    options = UpscalingMode.entries,
                    selected = state.settings.upscalingMode,
                    label = { it.label },
                    onSelect = { viewModel.setUpscalingMode(it) },
                )
            }
        }

        // ── Chat ─────────────────────────────────────────────────────────────────
        SettingsSection(title = "Chat") {
            SettingsRow(
                label = "Animate emotes",
                description = "Play animated 7TV / BTTV emotes. Turn off to show a still frame (lighter on the CPU).",
            ) {
                SegmentedControl(
                    options = listOf(true, false),
                    selected = state.settings.animateEmotes,
                    label = { if (it) "On" else "Off" },
                    onSelect = { viewModel.setAnimateEmotes(it) },
                )
            }
        }

        // ── Ad blocking ──────────────────────────────────────────────────────────
        SettingsSection(title = "Ad blocking") {
            Text(
                "Always on. Twitch ads are blocked across live streams and past videos.",
                style = MaterialTheme.typography.bodyLarge,
                color = c.textSecondary,
            )
        }

        // ── Account ──────────────────────────────────────────────────────────────
        SettingsSection(title = "Account") {
            if (state.isLoggedIn) {
                SettingsRow(
                    label = "Signed in",
                    description = state.loginUsername ?: "(unknown)",
                ) {
                    PureButton(
                        text = "Log out",
                        onClick = viewModel::logOut,
                        variant = ButtonVariant.Secondary,
                    )
                }
            } else {
                Text(
                    "Not signed in — open the Account tab to sign in with Twitch.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = c.textSecondary,
                )
            }
        }

        // ── About ────────────────────────────────────────────────────────────────
        SettingsSection(title = "About") {
            Text("PureTV for Twitch", style = MaterialTheme.typography.titleMedium, color = c.textPrimary)
            Text(
                "v${updateManager.currentVersion}",
                style = PureTvType.data,
                color = c.textTertiary,
                modifier = Modifier.padding(top = 4.dp),
            )
            Spacer(Modifier.height(16.dp))
            when (val s = updateState) {
                UpdateState.Idle -> PureButton(
                    text = "Check for updates",
                    onClick = { updateManager.checkForUpdates(force = true) },
                    variant = ButtonVariant.Secondary,
                )
                is UpdateState.Available -> {
                    Text(
                        "Update available: ${s.info.version}",
                        style = PureTvType.data,
                        color = c.twitchPurpleLight,
                    )
                    Spacer(Modifier.height(12.dp))
                    PureButton(
                        text = "Download & install ${s.info.version}",
                        onClick = { updateManager.downloadAndInstall(s.info, onExit) },
                    )
                }
                is UpdateState.Downloading -> Text(
                    "Downloading update… ${(s.progress * 100).toInt()}%",
                    style = PureTvType.data,
                    color = c.textSecondary,
                )
                is UpdateState.Error -> {
                    Text("Update failed: ${s.message}", style = PureTvType.data, color = c.live)
                    Spacer(Modifier.height(12.dp))
                    PureButton(
                        text = "Retry",
                        onClick = { updateManager.checkForUpdates(force = true) },
                        variant = ButtonVariant.Secondary,
                    )
                }
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}

// ── Theme swatch ──────────────────────────────────────────────────────────────────

@Composable
private fun ThemeSwatch(variant: ThemeVariant, selected: Boolean, onClick: () -> Unit) {
    val c = PureTvTheme.colors
    val vc = themeColors[variant]!!
    Column(
        modifier = Modifier
            .handCursor()
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // 76x50 preview split between the variant's canvas and its accent.
        Box(
            modifier = Modifier
                .size(width = 76.dp, height = 50.dp)
                .clip(PureTvShape.sm)
                .background(vc.background)
                .border(
                    width = if (selected) 2.dp else 1.dp,
                    color = if (selected) c.twitchPurple else c.hairlineStrong,
                    shape = PureTvShape.sm,
                ),
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
                    .size(16.dp)
                    .clip(PureTvShape.xs)
                    .background(vc.twitchPurple),
            )
        }
        Text(
            variant.displayName,
            style = PureTvType.dataSmall,
            color = if (selected) c.textPrimary else c.textTertiary,
        )
    }
}

// ── Section + row scaffolding ──────────────────────────────────────────────────────

@Composable
private fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    val c = PureTvTheme.colors
    Column(modifier = Modifier.fillMaxWidth().padding(top = 40.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(title, style = MaterialTheme.typography.headlineMedium, color = c.textPrimary)
            Spacer(Modifier.width(16.dp))
            Box(Modifier.weight(1f).height(1.dp).background(c.hairline))
        }
        Column(modifier = Modifier.padding(top = 20.dp)) { content() }
    }
}

@Composable
private fun SettingsRow(label: String, description: String, trailing: @Composable () -> Unit) {
    val c = PureTvTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 24.dp)) {
            Text(label, style = MaterialTheme.typography.titleMedium, color = c.textPrimary)
            Text(description, style = MaterialTheme.typography.bodyMedium, color = c.textMuted)
        }
        trailing()
    }
}
