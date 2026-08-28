package com.puretv.twitch.android.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.puretv.twitch.android.BuildConfig
import com.puretv.twitch.android.data.AppSettingsStore
import com.puretv.twitch.android.ui.SettingsViewModel
import com.puretv.twitch.android.ui.components.ExpressiveButton
import com.puretv.twitch.android.ui.components.ExpressiveButtonStyle
import com.puretv.twitch.android.ui.components.ExpressiveDivider
import com.puretv.twitch.android.ui.components.ExpressiveFilterChip
import com.puretv.twitch.android.ui.components.ExpressiveIconButton
import com.puretv.twitch.android.ui.components.ExpressiveIcons
import com.puretv.twitch.android.ui.components.ExpressivePanel
import com.puretv.twitch.android.ui.components.ExpressiveSwitch
import com.puretv.twitch.android.ui.components.PageTitle
import com.puretv.twitch.android.ui.components.SegmentedToggle
import com.puretv.twitch.android.ui.components.expressiveClickable
import com.puretv.twitch.android.ui.theme.PureTvTheme
import com.puretv.twitch.android.ui.theme.PureTvType
import com.puretv.twitch.android.ui.theme.ShapeIntensity
import com.puretv.twitch.android.ui.theme.ThemeVariant
import com.puretv.twitch.android.ui.theme.themeColors
import com.puretv.twitch.android.update.AndroidUpdateInfo
import com.puretv.twitch.android.update.AndroidUpdateManager
import com.puretv.twitch.android.update.AndroidUpdateState
import com.puretv.twitch.core.adblock.AdSimulator
import com.puretv.twitch.core.model.AppSettings
import com.puretv.twitch.core.model.StreamQuality
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

/**
 * SECTION 06.4 / 09.2 — a stack of [ExpressivePanel]s: colour and shape are the
 * new presentation controls, the rest (playback, ad blocking, account, about)
 * carry forward every setting [SettingsViewModel] already exposed. Backed by
 * [com.puretv.twitch.android.data.AppSettingsStore] (DataStore + EncryptedSharedPreferences).
 */
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val viewModel: SettingsViewModel = koinViewModel()
    val state by viewModel.state.collectAsState()
    val updateManager = koinInject<AndroidUpdateManager>()
    val updateState by updateManager.state.collectAsState()
    // The colour and shape pickers write straight to the store: they are pure
    // presentation settings with no ViewModel-side validation, same class of
    // write as SettingsViewModel's own setters underneath, just not yet
    // exposed as a dedicated setter there.
    val settingsStore = koinInject<AppSettingsStore>()
    val scope = rememberCoroutineScope()
    val c = PureTvTheme.colors

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(c.surface)
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ExpressiveIconButton(
                icon = ExpressiveIcons.Back,
                contentDescription = "Back",
                onClick = onBack,
                style = ExpressiveButtonStyle.Tonal,
            )
            Spacer(Modifier.width(16.dp))
            PageTitle("Settings")
        }
        Spacer(Modifier.height(28.dp))

        ColourPanel(
            currentVariant = ThemeVariant.fromKey(state.settings.theme),
            onSelect = { variant -> scope.launch { settingsStore.update { it.copy(theme = variant.key) } } },
        )
        Spacer(Modifier.height(16.dp))

        ShapeAndMotionPanel(
            currentIntensity = ShapeIntensity.fromKey(state.settings.shapeIntensity),
            onSelect = { intensity -> scope.launch { settingsStore.update { it.copy(shapeIntensity = intensity.key) } } },
        )
        Spacer(Modifier.height(16.dp))

        PlaybackPanel(
            settings = state.settings,
            onSelectQuality = viewModel::setPreferredQuality,
            onAnimateEmotesChange = viewModel::setAnimateEmotes,
        )
        Spacer(Modifier.height(16.dp))

        AdBlockPanel(
            adBlockEnabled = state.settings.adBlockEnabled,
            onAdBlockEnabledChange = viewModel::setAdBlockEnabled,
        )
        Spacer(Modifier.height(16.dp))

        AccountPanel(
            isLoggedIn = state.isLoggedIn,
            username = state.loginUsername,
            onLogOut = viewModel::logOut,
        )
        Spacer(Modifier.height(16.dp))

        AboutPanel(
            currentVersion = updateManager.currentVersionName,
            state = updateState,
            onCheck = { updateManager.checkForUpdates(force = true) },
            onInstall = { info -> updateManager.downloadAndInstall(info) },
        )
        Spacer(Modifier.height(24.dp))
    }
}

// ── Colour ───────────────────────────────────────────────────────────────────

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
            // Two per row: a phone is never wide enough for the desktop's
            // wrapping FlowRow to read as a grid, so the rows are built by hand.
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                ThemeVariant.entries.chunked(2).forEach { rowVariants ->
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        rowVariants.forEach { variant ->
                            ColourSwatch(
                                variant = variant,
                                selected = variant == currentVariant,
                                onClick = { onSelect(variant) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                        // An odd final row: keep the lone tile at half width
                        // instead of letting it stretch to fill the row.
                        if (rowVariants.size == 1) Spacer(Modifier.weight(1f))
                    }
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
private fun ColourSwatch(variant: ThemeVariant, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = PureTvTheme.colors
    val vc = themeColors[variant]!!
    val interaction = remember { MutableInteractionSource() }

    Column(
        modifier = modifier
            .expressiveClickable(
                interaction = interaction,
                onClick = onClick,
                restRadius = 20.dp,
                pressRadius = 28.dp,
                color = Color.Transparent,
                selected = selected,
                selectedColor = c.primary,
            )
            .padding(4.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(vc.surfaceLow)
                .padding(14.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.weight(2f).height(44.dp).clip(RoundedCornerShape(12.dp)).background(vc.primary))
                Box(Modifier.weight(1f).height(44.dp).clip(RoundedCornerShape(12.dp)).background(vc.secondaryContainer))
                Box(Modifier.weight(1f).height(44.dp).clip(RoundedCornerShape(12.dp)).background(vc.tertiaryContainer))
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (selected) {
                    Icon(
                        ExpressiveIcons.CheckCircle,
                        contentDescription = null,
                        tint = vc.primary,
                        modifier = Modifier.size(18.dp),
                    )
                }
                Text(
                    variant.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    color = vc.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(2.dp))
            Text(variant.seed, style = PureTvType.data, color = vc.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

// ── Shape & motion ──────────────────────────────────────────────────────────

@Composable
private fun ShapeAndMotionPanel(currentIntensity: ShapeIntensity, onSelect: (ShapeIntensity) -> Unit) {
    val c = PureTvTheme.colors
    ExpressivePanel {
        Column {
            Text("Shape and motion", style = MaterialTheme.typography.titleLarge, color = c.onSurface)
            Spacer(Modifier.height(20.dp))
            Text("Expressiveness", style = MaterialTheme.typography.titleMedium, color = c.onSurface)
            Spacer(Modifier.height(4.dp))
            Text(
                "How far corners round, morph on press, and how springy the motion feels.",
                style = MaterialTheme.typography.bodyMedium,
                color = c.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            SegmentedToggle(
                options = ShapeIntensity.entries,
                selected = currentIntensity,
                label = { it.displayName },
                onSelect = onSelect,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

// ── Playback ─────────────────────────────────────────────────────────────────

@Composable
private fun PlaybackPanel(
    settings: AppSettings,
    onSelectQuality: (StreamQuality) -> Unit,
    onAnimateEmotesChange: (Boolean) -> Unit,
) {
    val c = PureTvTheme.colors
    ExpressivePanel {
        Column {
            Text("Playback", style = MaterialTheme.typography.titleLarge, color = c.onSurface)
            Spacer(Modifier.height(20.dp))

            Text("Preferred quality", style = MaterialTheme.typography.titleMedium, color = c.onSurface)
            Spacer(Modifier.height(4.dp))
            Text(
                "The variant PureTV reaches for first when a stream opens.",
                style = MaterialTheme.typography.bodyMedium,
                color = c.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            // A horizontally scrolling chip row rather than SegmentedToggle: seven
            // quality tiers do not fit an unscrolled pill track on a phone width.
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(StreamQuality.entries) { quality ->
                    val selected = settings.preferredQuality.equals(quality.name, ignoreCase = true)
                    ExpressiveFilterChip(label = quality.label, selected = selected, onClick = { onSelectQuality(quality) })
                }
            }

            ExpressiveDivider(Modifier.padding(vertical = 24.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Animate emotes", style = MaterialTheme.typography.titleMedium, color = c.onSurface)
                    Text(
                        "Play animated 7TV and BTTV emotes. Off shows a still frame, which is lighter on the battery.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = c.onSurfaceVariant,
                    )
                }
                ExpressiveSwitch(checked = settings.animateEmotes, onCheckedChange = onAnimateEmotesChange)
            }
        }
    }
}

// ── Ad blocking ──────────────────────────────────────────────────────────────

/**
 * Android's ad blocking is in-process (the OkHttp playlist interceptor strips
 * pods locally), so unlike desktop's proxy-based blocker there is a real
 * on/off switch here rather than a static fact panel. The custom-proxy /
 * strategy controls were removed before this pass: they had no effect on
 * Android and are not restored here.
 */
@Composable
private fun AdBlockPanel(adBlockEnabled: Boolean, onAdBlockEnabledChange: (Boolean) -> Unit) {
    val c = PureTvTheme.colors
    ExpressivePanel {
        Column {
            Text("Ad blocking", style = MaterialTheme.typography.titleLarge, color = c.onSurface)
            Spacer(Modifier.height(20.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Enable ad block", style = MaterialTheme.typography.titleMedium, color = c.onSurface)
                    Text(
                        "Ads are filtered on-device. No proxy setup required.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = c.onSurfaceVariant,
                    )
                }
                ExpressiveSwitch(checked = adBlockEnabled, onCheckedChange = onAdBlockEnabledChange)
            }

            if (BuildConfig.DEBUG) {
                ExpressiveDivider(Modifier.padding(vertical = 24.dp))
                var simulateAds by remember { mutableStateOf(AdSimulator.enabled) }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    Text(
                        "Simulate ads (debug)",
                        style = MaterialTheme.typography.titleMedium,
                        color = c.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    ExpressiveSwitch(
                        checked = simulateAds,
                        onCheckedChange = {
                            simulateAds = it
                            AdSimulator.enabled = it
                        },
                    )
                }
            }
        }
    }
}

// ── Account ──────────────────────────────────────────────────────────────────

@Composable
private fun AccountPanel(isLoggedIn: Boolean, username: String?, onLogOut: () -> Unit) {
    val c = PureTvTheme.colors
    ExpressivePanel {
        Column {
            Text("Account", style = MaterialTheme.typography.titleLarge, color = c.onSurface)
            Spacer(Modifier.height(20.dp))
            if (isLoggedIn) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    Text(
                        username ?: "Logged in",
                        style = MaterialTheme.typography.titleMedium,
                        color = c.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    ExpressiveButton(text = "Log out", onClick = onLogOut, style = ExpressiveButtonStyle.Outlined)
                }
            } else {
                Text(
                    "Not logged in. Log in to follow channels and chat.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = c.onSurfaceVariant,
                )
            }
        }
    }
}

// ── About ────────────────────────────────────────────────────────────────────

@Composable
private fun AboutPanel(
    currentVersion: String,
    state: AndroidUpdateState,
    onCheck: () -> Unit,
    onInstall: (AndroidUpdateInfo) -> Unit,
) {
    val c = PureTvTheme.colors
    ExpressivePanel {
        Column {
            Text("PureTV for Twitch", style = MaterialTheme.typography.titleLarge, color = c.onSurface)
            Text("v$currentVersion", style = PureTvType.data, color = c.onSurfaceVariant)

            when (state) {
                is AndroidUpdateState.Available -> {
                    Spacer(Modifier.height(20.dp))
                    ExpressiveDivider()
                    Spacer(Modifier.height(20.dp))
                    Text(
                        "Update available: ${state.info.versionName}",
                        style = PureTvType.data,
                        color = c.primary,
                    )
                    if (state.info.notes.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            state.info.notes.lineSequence().firstOrNull().orEmpty(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = c.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    ExpressiveButton(text = "Download and install", onClick = { onInstall(state.info) })
                }
                is AndroidUpdateState.Downloading -> {
                    Spacer(Modifier.height(20.dp))
                    ExpressiveDivider()
                    Spacer(Modifier.height(20.dp))
                    Text(
                        "Downloading, ${(state.progress * 100).toInt()}%",
                        style = PureTvType.data,
                        color = c.onSurfaceVariant,
                    )
                }
                AndroidUpdateState.Installing -> {
                    Spacer(Modifier.height(20.dp))
                    ExpressiveDivider()
                    Spacer(Modifier.height(20.dp))
                    Text("Starting installer...", style = MaterialTheme.typography.bodyLarge, color = c.onSurface)
                }
                AndroidUpdateState.Checking -> {
                    Spacer(Modifier.height(20.dp))
                    ExpressiveDivider()
                    Spacer(Modifier.height(20.dp))
                    Text("Checking for updates...", style = MaterialTheme.typography.bodyMedium, color = c.onSurfaceVariant)
                }
                AndroidUpdateState.UpToDate -> {
                    Spacer(Modifier.height(20.dp))
                    ExpressiveDivider()
                    Spacer(Modifier.height(20.dp))
                    Text("You're on the latest version.", style = MaterialTheme.typography.bodyMedium, color = c.onSurfaceVariant)
                    Spacer(Modifier.height(12.dp))
                    ExpressiveButton(text = "Check again", onClick = onCheck, style = ExpressiveButtonStyle.Outlined)
                }
                is AndroidUpdateState.Error -> {
                    Spacer(Modifier.height(20.dp))
                    ExpressiveDivider()
                    Spacer(Modifier.height(20.dp))
                    Text(state.message, style = MaterialTheme.typography.bodyMedium, color = c.error)
                    Spacer(Modifier.height(12.dp))
                    ExpressiveButton(text = "Try again", onClick = onCheck, style = ExpressiveButtonStyle.Outlined)
                }
                AndroidUpdateState.Idle -> {
                    Spacer(Modifier.height(20.dp))
                    ExpressiveDivider()
                    Spacer(Modifier.height(20.dp))
                    ExpressiveButton(text = "Check for updates", onClick = onCheck, style = ExpressiveButtonStyle.Outlined)
                }
            }

            Spacer(Modifier.height(20.dp))
            ExpressiveDivider()
            Spacer(Modifier.height(20.dp))
            Text(
                "PureTV for Twitch: sideloaded build. Not affiliated with Twitch Interactive, Inc.",
                style = MaterialTheme.typography.bodyMedium,
                color = c.onSurfaceVariant,
            )
        }
    }
}
