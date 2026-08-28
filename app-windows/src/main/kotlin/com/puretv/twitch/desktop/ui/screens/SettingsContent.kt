package com.puretv.twitch.desktop.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import com.puretv.twitch.core.model.StreamQuality
import com.puretv.twitch.desktop.data.DesktopSettingsStore
import com.puretv.twitch.desktop.platform.openInBrowser
import com.puretv.twitch.desktop.ui.SettingsViewModel
import com.puretv.twitch.desktop.ui.components.ExpressiveButton
import com.puretv.twitch.desktop.ui.components.ExpressiveButtonStyle
import com.puretv.twitch.desktop.ui.components.ExpressiveDivider
import com.puretv.twitch.desktop.ui.components.ExpressiveIcons
import com.puretv.twitch.desktop.ui.components.ExpressivePanel
import com.puretv.twitch.desktop.ui.components.ExpressiveSwitch
import com.puretv.twitch.desktop.ui.components.PageTitle
import com.puretv.twitch.desktop.ui.components.SegmentedToggle
import com.puretv.twitch.desktop.ui.components.expressiveClickable
import com.puretv.twitch.desktop.ui.rememberDesktopViewModel
import com.puretv.twitch.desktop.ui.theme.PureTvTheme
import com.puretv.twitch.desktop.ui.theme.PureTvType
import com.puretv.twitch.desktop.ui.theme.ShapeIntensity
import com.puretv.twitch.desktop.ui.theme.ThemeVariant
import com.puretv.twitch.desktop.ui.theme.themeColors
import com.puretv.twitch.desktop.update.UpdateInfo
import com.puretv.twitch.desktop.update.UpdateManager
import com.puretv.twitch.desktop.update.UpdateState
import com.puretv.twitch.desktop.update.resolveReleaseUrl
import org.koin.core.Koin

@Composable
fun SettingsContent(koin: Koin, onExit: () -> Unit) {
    val viewModel = rememberDesktopViewModel { koin.get<SettingsViewModel>() }
    val state by viewModel.state.collectAsState()
    val updateManager = remember { koin.get<UpdateManager>() }
    val updateState by updateManager.state.collectAsState()
    // The colour and shape pickers write straight to the store: they are pure
    // presentation settings with no ViewModel-side validation, same class of
    // write as SettingsViewModel's own setters underneath.
    val settingsStore = remember { koin.get<DesktopSettingsStore>() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(top = 36.dp, start = 32.dp, end = 32.dp, bottom = 40.dp),
    ) {
        PageTitle("Settings")
        Spacer(Modifier.height(28.dp))

        ColourPanel(
            currentVariant = ThemeVariant.fromKey(state.settings.theme),
            onSelect = { variant -> settingsStore.updateSettings { it.copy(theme = variant.key) } },
        )
        Spacer(Modifier.height(16.dp))

        ShapeAndMotionPanel(
            currentIntensity = ShapeIntensity.fromKey(state.settings.shapeIntensity),
            onSelect = { intensity -> settingsStore.updateSettings { it.copy(shapeIntensity = intensity.key) } },
        )
        Spacer(Modifier.height(16.dp))

        PlaybackPanel(
            selectedQuality = StreamQuality.entries.firstOrNull {
                state.settings.preferredQuality.equals(it.name, ignoreCase = true)
            } ?: StreamQuality.AUTO,
            onSelectQuality = viewModel::setPreferredQuality,
            animateEmotes = state.settings.animateEmotes,
            onAnimateEmotesChange = viewModel::setAnimateEmotes,
        )
        Spacer(Modifier.height(16.dp))

        AdBlockPanel()
        Spacer(Modifier.height(16.dp))

        AccountPanel(
            isLoggedIn = state.isLoggedIn,
            username = state.loginUsername,
            onLogOut = viewModel::logOut,
        )
        Spacer(Modifier.height(16.dp))

        AboutPanel(
            version = updateManager.currentVersion,
            updateState = updateState,
            onCheckForUpdates = { updateManager.checkForUpdates(force = true) },
            onDownloadAndInstall = { info -> updateManager.downloadAndInstall(info, onExit) },
            onOpenDownloadPage = { url -> openInBrowser(url) },
        )
    }
}

// ── Colour ───────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColourPanel(currentVariant: ThemeVariant, onSelect: (ThemeVariant) -> Unit) {
    val c = PureTvTheme.colors
    ExpressivePanel {
        Column {
            Text("Colour", style = MaterialTheme.typography.titleLarge, color = c.onSurface)
            Spacer(Modifier.height(4.dp))
            Text(
                "PureTV derives its whole scheme from one source colour, the way Material dynamic " +
                    "colour does. Pick a palette and every surface, container and accent re-tones together.",
                style = MaterialTheme.typography.bodyMedium,
                color = c.onSurfaceVariant,
            )
            Spacer(Modifier.height(20.dp))
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                ThemeVariant.entries.forEach { variant ->
                    ColourSwatch(
                        variant = variant,
                        selected = variant == currentVariant,
                        onClick = { onSelect(variant) },
                    )
                }
            }
        }
    }
}

/**
 * One palette tile. Always previews its OWN colours ([themeColors] keyed by
 * [variant]), never the currently active theme, so every swatch shows what
 * picking it would actually look like. Only the selection ring uses the active
 * theme's primary, because that ring is app chrome, not palette preview.
 */
@Composable
private fun ColourSwatch(variant: ThemeVariant, selected: Boolean, onClick: () -> Unit) {
    val c = PureTvTheme.colors
    val vc = themeColors[variant]!!
    val interaction = remember { MutableInteractionSource() }

    Column(
        modifier = Modifier
            .width(176.dp)
            .expressiveClickable(
                interaction = interaction,
                onClick = onClick,
                restRadius = 24.dp,
                hoverRadius = 32.dp,
                color = if (selected) c.primary else Color.Transparent,
            )
            .padding(4.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(vc.surfaceLow)
                .padding(16.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    Modifier.weight(2f).height(56.dp).clip(RoundedCornerShape(16.dp)).background(vc.primary),
                )
                Box(
                    Modifier.weight(1f).height(56.dp).clip(RoundedCornerShape(16.dp))
                        .background(vc.secondaryContainer),
                )
                Box(
                    Modifier.weight(1f).height(56.dp).clip(RoundedCornerShape(16.dp))
                        .background(vc.tertiaryContainer),
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (selected) {
                    Icon(
                        ExpressiveIcons.CheckCircle,
                        contentDescription = null,
                        tint = vc.primary,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Text(variant.displayName, style = MaterialTheme.typography.titleMedium, color = vc.onSurface)
            }
            Text(variant.seed, style = PureTvType.data, color = vc.onSurfaceVariant)
        }
    }
}

// ── Shape & motion ─────────────────────────────────────────────────────────────────

@Composable
private fun ShapeAndMotionPanel(currentIntensity: ShapeIntensity, onSelect: (ShapeIntensity) -> Unit) {
    val c = PureTvTheme.colors
    ExpressivePanel {
        Column {
            Text("Shape and motion", style = MaterialTheme.typography.titleLarge, color = c.onSurface)
            Spacer(Modifier.height(20.dp))
            SettingsRow(
                label = "Expressiveness",
                description = "How far corners round, morph on press, and how springy the motion feels.",
            ) {
                SegmentedToggle(
                    options = ShapeIntensity.entries,
                    selected = currentIntensity,
                    label = { it.displayName },
                    onSelect = onSelect,
                )
            }
        }
    }
}

// ── Playback ─────────────────────────────────────────────────────────────────────

@Composable
private fun PlaybackPanel(
    selectedQuality: StreamQuality,
    onSelectQuality: (StreamQuality) -> Unit,
    animateEmotes: Boolean,
    onAnimateEmotesChange: (Boolean) -> Unit,
) {
    val c = PureTvTheme.colors
    ExpressivePanel {
        Column {
            Text("Playback", style = MaterialTheme.typography.titleLarge, color = c.onSurface)
            Spacer(Modifier.height(20.dp))
            SettingsRow(
                label = "Preferred quality",
                description = "The variant PureTV reaches for first when a stream opens.",
            ) {
                SegmentedToggle(
                    options = StreamQuality.entries,
                    selected = selectedQuality,
                    label = { it.label },
                    onSelect = onSelectQuality,
                )
            }
            ExpressiveDivider(Modifier.padding(vertical = 24.dp))
            SettingsRow(
                label = "Animate emotes",
                description = "Play animated 7TV and BTTV emotes. Off shows a still frame, which is lighter on the CPU.",
            ) {
                ExpressiveSwitch(checked = animateEmotes, onCheckedChange = onAnimateEmotesChange)
            }
            // Playback backend + GPU upscaling live in the in-player gear menu
            // (per-stream, applied live) rather than here. See PlayerSettingsMenu.
        }
    }
}

// ── Ad blocking ──────────────────────────────────────────────────────────────────

/**
 * Desktop's ad blocking is unconditional: [com.puretv.twitch.desktop.player.LocalStreamProxy]
 * always runs the stream through [com.puretv.twitch.core.adblock.AdBlockEngine], and never
 * reads `adBlockEnabled` / `adBlockStrategy` / `customProxyUrl` from [com.puretv.twitch.core.model.AppSettings].
 * Those fields exist for the Android and TV clients, which do wire them up. So
 * this panel states a fact rather than offering controls that would not do
 * anything on this platform.
 */
@Composable
private fun AdBlockPanel() {
    val c = PureTvTheme.colors
    ExpressivePanel(color = c.tertiaryContainer) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            Box(
                modifier = Modifier.size(56.dp).clip(CircleShape).background(c.onTertiaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    ExpressiveIcons.Shield,
                    contentDescription = null,
                    tint = c.tertiaryContainer,
                    modifier = Modifier.size(28.dp),
                )
            }
            Column(Modifier.weight(1f)) {
                Text(
                    "Ad blocking is always on",
                    style = MaterialTheme.typography.titleLarge,
                    color = c.onTertiaryContainer,
                )
                Text(
                    "Filtered on-device across live streams and past videos. Nothing to configure.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = c.onTertiaryContainer,
                )
            }
        }
    }
}

// ── Account ──────────────────────────────────────────────────────────────────────

@Composable
private fun AccountPanel(isLoggedIn: Boolean, username: String?, onLogOut: () -> Unit) {
    val c = PureTvTheme.colors
    ExpressivePanel {
        Column {
            Text("Account", style = MaterialTheme.typography.titleLarge, color = c.onSurface)
            Spacer(Modifier.height(20.dp))
            if (isLoggedIn) {
                SettingsRow(label = "Signed in", description = username ?: "(unknown)") {
                    ExpressiveButton(text = "Log out", onClick = onLogOut, style = ExpressiveButtonStyle.Outlined)
                }
            } else {
                Text(
                    "Not signed in. Open the Account tab to sign in with Twitch.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = c.onSurfaceVariant,
                )
            }
        }
    }
}

// ── About ────────────────────────────────────────────────────────────────────────

@Composable
private fun AboutPanel(
    version: String,
    updateState: UpdateState,
    onCheckForUpdates: () -> Unit,
    onDownloadAndInstall: (UpdateInfo) -> Unit,
    onOpenDownloadPage: (String) -> Unit,
) {
    val c = PureTvTheme.colors
    ExpressivePanel {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text("PureTV for Twitch", style = MaterialTheme.typography.titleLarge, color = c.onSurface)
                    Text("v$version", style = PureTvType.data, color = c.onSurfaceVariant)
                }
                if (updateState is UpdateState.Idle) {
                    ExpressiveButton(
                        text = "Check for updates",
                        onClick = onCheckForUpdates,
                        style = ExpressiveButtonStyle.Outlined,
                    )
                }
            }
            when (updateState) {
                is UpdateState.Idle -> Unit
                is UpdateState.Available -> {
                    Spacer(Modifier.height(20.dp))
                    ExpressiveDivider()
                    Spacer(Modifier.height(20.dp))
                    Text(
                        "Update available: ${updateState.info.version}",
                        style = PureTvType.data,
                        color = c.primary,
                    )
                    Spacer(Modifier.height(12.dp))
                    ExpressiveButton(
                        text = "Download and install ${updateState.info.version}",
                        onClick = { onDownloadAndInstall(updateState.info) },
                    )
                }
                is UpdateState.Downloading -> {
                    Spacer(Modifier.height(20.dp))
                    ExpressiveDivider()
                    Spacer(Modifier.height(20.dp))
                    Text(
                        "Downloading update, ${(updateState.progress * 100).toInt()}%",
                        style = PureTvType.data,
                        color = c.onSurfaceVariant,
                    )
                }
                is UpdateState.Error -> {
                    Spacer(Modifier.height(20.dp))
                    ExpressiveDivider()
                    Spacer(Modifier.height(20.dp))
                    Text("Update failed: ${updateState.message}", style = PureTvType.data, color = c.error)
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        ExpressiveButton(
                            text = "Open download page",
                            onClick = { onOpenDownloadPage(updateState.releaseUrl ?: resolveReleaseUrl("")) },
                        )
                        ExpressiveButton(
                            text = "Retry",
                            onClick = onCheckForUpdates,
                            style = ExpressiveButtonStyle.Outlined,
                        )
                    }
                }
            }
        }
    }
}

// ── Row scaffolding ────────────────────────────────────────────────────────────────

/** Label + description on the left (weight 1f), a control on the right. */
@Composable
private fun SettingsRow(label: String, description: String, trailing: @Composable () -> Unit) {
    val c = PureTvTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(32.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.titleMedium, color = c.onSurface)
            Text(description, style = MaterialTheme.typography.bodyMedium, color = c.onSurfaceVariant)
        }
        trailing()
    }
}
