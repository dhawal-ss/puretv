package com.puretv.twitch.tv.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Switch
import androidx.tv.material3.Text
import com.puretv.twitch.core.adblock.AdBlockStrategy
import com.puretv.twitch.core.model.StreamQuality
import com.puretv.twitch.tv.data.AppSettingsStore
import com.puretv.twitch.tv.ui.SettingsViewModel
import com.puretv.twitch.tv.ui.components.ExpressiveIcons
import com.puretv.twitch.tv.ui.components.TvButtonStyle
import com.puretv.twitch.tv.ui.components.TvDivider
import com.puretv.twitch.tv.ui.components.TvExpressiveButton
import com.puretv.twitch.tv.ui.components.TvExpressiveIconButton
import com.puretv.twitch.tv.ui.components.TvFilterChip
import com.puretv.twitch.tv.ui.components.TvPageTitle
import com.puretv.twitch.tv.ui.components.TvPanel
import com.puretv.twitch.tv.ui.components.tvFocusClickable
import com.puretv.twitch.tv.ui.theme.PureTvTvTheme
import com.puretv.twitch.tv.ui.theme.PureTvTvType
import com.puretv.twitch.tv.ui.theme.ThemeVariant
import com.puretv.twitch.tv.ui.theme.themeColors
import com.puretv.twitch.tv.update.TvUpdateManager
import com.puretv.twitch.tv.update.TvUpdateState
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

/**
 * SECTION 07.2 / 10.4: focusable settings rows mirroring the phone app's
 * `SettingsScreen` content (colour, preferred quality, ad-block
 * toggle/strategy, account/sign-out, software update) but laid out as a
 * single D-pad-navigable column of [TvPanel]s rather than scrollable
 * Material3 list items, per the 10-foot pattern used throughout `app-tv`.
 *
 * There is no shape-intensity control here: TV's theme exposes no shape
 * dial, and dialing corner roundness with a remote is not a meaningful
 * interaction, so that desktop-only panel has no TV counterpart.
 */
@Composable
fun TvSettingsScreen(onBack: () -> Unit, viewModel: SettingsViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsState()
    val updateManager = koinInject<TvUpdateManager>()
    val updateState by updateManager.state.collectAsState()
    val c = PureTvTvTheme.colors

    // The colour picker writes straight to the store: it's a pure presentation
    // setting with no ViewModel-side validation, same class of write as
    // SettingsViewModel's own setters underneath, just without a dedicated
    // method (mirrors desktop's SettingsContent.kt ColourPanel).
    val settingsStore = koinInject<AppSettingsStore>()
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(c.surface)
            .verticalScroll(rememberScrollState())
            .padding(48.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            TvExpressiveIconButton(icon = ExpressiveIcons.Back, contentDescription = "Back", onClick = onBack)
            TvPageTitle("Settings")
        }

        TvPanel {
            ColourSection(
                currentVariant = ThemeVariant.fromKey(state.settings.theme),
                onSelect = { variant -> scope.launch { settingsStore.update { it.copy(theme = variant.key) } } },
            )
        }
        TvDivider()

        TvPanel {
            StreamQualitySection(
                selected = state.settings.preferredQuality,
                onSelect = viewModel::setPreferredQuality,
            )
        }
        TvDivider()

        TvPanel {
            AdBlockSection(
                enabled = state.settings.adBlockEnabled,
                onEnabledChange = viewModel::setAdBlockEnabled,
                strategyKey = state.settings.adBlockStrategy,
                onStrategySelect = viewModel::setAdBlockStrategy,
            )
        }
        TvDivider()

        TvPanel {
            AccountSection(
                isLoggedIn = state.isLoggedIn,
                username = state.loginUsername,
                onLogOut = viewModel::logOut,
            )
        }
        TvDivider()

        TvPanel {
            SoftwareUpdateSection(
                currentVersion = updateManager.currentVersionName,
                state = updateState,
                onCheck = { updateManager.checkForUpdates(force = true) },
                onInstall = { info -> updateManager.downloadAndInstall(info) },
            )
        }
    }
}

// ── Colour ───────────────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColourSection(currentVariant: ThemeVariant, onSelect: (ThemeVariant) -> Unit) {
    val c = PureTvTvTheme.colors
    Column {
        Text("Colour", style = MaterialTheme.typography.titleLarge, color = c.onSurface)
        Spacer(Modifier.height(8.dp))
        Text(
            "The whole scheme comes from one source colour, the same way Material's dynamic colour " +
                "does. Pick a palette and every surface, container and accent retones together.",
            style = MaterialTheme.typography.bodyLarge,
            color = c.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            ThemeVariant.entries.forEach { variant ->
                ColourSwatch(variant = variant, selected = variant == currentVariant, onClick = { onSelect(variant) })
            }
        }
    }
}

/**
 * One palette tile. Always previews its OWN colours, read from [themeColors],
 * never the currently active theme, so every swatch shows what picking it
 * would actually look like.
 */
@Composable
private fun ColourSwatch(variant: ThemeVariant, selected: Boolean, onClick: () -> Unit) {
    val shapes = PureTvTvTheme.shapes
    val vc = themeColors[variant]!!
    val interaction = remember { MutableInteractionSource() }

    Column(
        modifier = Modifier
            .width(240.dp)
            .tvFocusClickable(
                interaction = interaction,
                onClick = onClick,
                restRadius = shapes.sm,
                focusRadius = shapes.cardFocus,
                color = if (selected) vc.primary else Color.Transparent,
                focusColor = vc.primary,
                scale = 1.04f,
            )
            .padding(6.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(shapes.sm))
                .background(vc.surfaceLow)
                .padding(20.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                androidx.compose.foundation.layout.Box(
                    Modifier.weight(2f).height(60.dp).clip(RoundedCornerShape(shapes.sm)).background(vc.primary),
                )
                androidx.compose.foundation.layout.Box(
                    Modifier.weight(1f).height(60.dp).clip(RoundedCornerShape(shapes.sm)).background(vc.secondaryContainer),
                )
                androidx.compose.foundation.layout.Box(
                    Modifier.weight(1f).height(60.dp).clip(RoundedCornerShape(shapes.sm)).background(vc.tertiaryContainer),
                )
            }
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (selected) {
                    Icon(ExpressiveIcons.Check, contentDescription = null, tint = vc.primary, modifier = Modifier.size(22.dp))
                }
                Text(variant.displayName, style = MaterialTheme.typography.titleMedium, color = vc.onSurface)
            }
            Spacer(Modifier.height(4.dp))
            Text(variant.seed, style = PureTvTvType.data, color = vc.onSurfaceVariant)
        }
    }
}

// ── Stream quality ──────────────────────────────────────────────────────

@Composable
private fun StreamQualitySection(selected: String, onSelect: (StreamQuality) -> Unit) {
    val c = PureTvTvTheme.colors
    Column {
        Text("Stream quality", style = MaterialTheme.typography.titleLarge, color = c.onSurface)
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            StreamQuality.entries.forEach { quality ->
                TvFilterChip(
                    label = quality.label,
                    selected = selected.equals(quality.name, ignoreCase = true),
                    onClick = { onSelect(quality) },
                )
            }
        }
    }
}

// ── Ad blocking ─────────────────────────────────────────────────────────

@Composable
private fun AdBlockSection(
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    strategyKey: String,
    onStrategySelect: (AdBlockStrategy) -> Unit,
) {
    val c = PureTvTvTheme.colors
    Column {
        Text("Ad blocking", style = MaterialTheme.typography.titleLarge, color = c.onSurface)
        Spacer(Modifier.height(20.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("Enabled", style = MaterialTheme.typography.bodyLarge, color = c.onSurface)
            Switch(checked = enabled, onCheckedChange = onEnabledChange)
        }
        Spacer(Modifier.height(20.dp))
        TvDivider()
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            AdBlockStrategy.entries.forEach { strategy ->
                TvFilterChip(
                    label = strategyLabel(strategy),
                    selected = strategyKey.equals(strategy.name, ignoreCase = true),
                    onClick = { onStrategySelect(strategy) },
                )
            }
        }
    }
}

private fun strategyLabel(strategy: AdBlockStrategy): String = when (strategy) {
    AdBlockStrategy.PROXY_PRIMARY -> "Proxy"
    AdBlockStrategy.MANIFEST_REWRITE_ONLY -> "Manifest rewrite"
    AdBlockStrategy.DISABLED -> "Disabled"
}

// ── Account ─────────────────────────────────────────────────────────────

@Composable
private fun AccountSection(isLoggedIn: Boolean, username: String?, onLogOut: () -> Unit) {
    val c = PureTvTvTheme.colors
    Column {
        Text("Account", style = MaterialTheme.typography.titleLarge, color = c.onSurface)
        Spacer(Modifier.height(20.dp))
        if (isLoggedIn) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Signed in as ${username ?: "your Twitch account"}", style = MaterialTheme.typography.bodyLarge, color = c.onSurface)
                TvExpressiveButton(text = "Sign out", onClick = onLogOut, style = TvButtonStyle.Outlined, icon = ExpressiveIcons.SignOut)
            }
        } else {
            Text(
                "Not signed in. Sign in from the nav rail to follow channels and chat with your account.",
                style = MaterialTheme.typography.bodyLarge,
                color = c.onSurfaceVariant,
            )
        }
    }
}

// ── Software update ─────────────────────────────────────────────────────

@Composable
private fun SoftwareUpdateSection(
    currentVersion: String,
    state: TvUpdateState,
    onCheck: () -> Unit,
    onInstall: (com.puretv.twitch.tv.update.TvUpdateInfo) -> Unit,
) {
    val c = PureTvTvTheme.colors
    Column {
        Text("Software update", style = MaterialTheme.typography.titleLarge, color = c.onSurface)
        Spacer(Modifier.height(20.dp))
        Text("Installed version $currentVersion", style = PureTvTvType.data, color = c.onSurfaceVariant)
        Spacer(Modifier.height(16.dp))
        when (state) {
            is TvUpdateState.Available -> {
                Text("Update available: ${state.info.versionName}", style = PureTvTvType.data, color = c.primary)
                if (state.info.notes.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        state.info.notes.lineSequence().firstOrNull().orEmpty(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = c.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(16.dp))
                TvExpressiveButton(text = "Download and install", onClick = { onInstall(state.info) }, icon = ExpressiveIcons.Download)
            }
            is TvUpdateState.Downloading -> {
                Text("Downloading… ${(state.progress * 100).toInt()}%", style = MaterialTheme.typography.bodyLarge, color = c.onSurface)
            }
            TvUpdateState.Installing ->
                Text("Starting installer…", style = MaterialTheme.typography.bodyLarge, color = c.onSurface)
            TvUpdateState.Checking ->
                Text("Checking for updates…", style = MaterialTheme.typography.bodyLarge, color = c.onSurfaceVariant)
            TvUpdateState.UpToDate -> {
                Text("You're on the latest version.", style = MaterialTheme.typography.bodyLarge, color = c.onSurfaceVariant)
                Spacer(Modifier.height(16.dp))
                TvExpressiveButton(text = "Check again", onClick = onCheck, style = TvButtonStyle.Outlined, icon = ExpressiveIcons.Refresh)
            }
            is TvUpdateState.Error -> {
                Text(state.message, style = MaterialTheme.typography.bodyLarge, color = c.error)
                Spacer(Modifier.height(16.dp))
                TvExpressiveButton(text = "Try again", onClick = onCheck, style = TvButtonStyle.Outlined, icon = ExpressiveIcons.Refresh)
            }
            TvUpdateState.Idle ->
                TvExpressiveButton(text = "Check for updates", onClick = onCheck, style = TvButtonStyle.Outlined, icon = ExpressiveIcons.Refresh)
        }
    }
}
