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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Switch
import androidx.tv.material3.Text
import com.puretv.twitch.core.adblock.AdBlockStrategy
import com.puretv.twitch.core.audio.AudioLevelling
import com.puretv.twitch.core.model.ChatTextSize
import com.puretv.twitch.core.model.StreamQuality
import com.puretv.twitch.core.notify.GoLiveAlerts
import com.puretv.twitch.core.notify.GoLiveText
import com.puretv.twitch.core.model.TV_CHAT_BESIDE_VIDEO
import com.puretv.twitch.core.model.TV_CHAT_OVER_VIDEO
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
import kotlin.math.abs
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
                lowLatency = state.settings.lowLatencyMode,
                onLowLatencyChange = viewModel::setLowLatency,
                audioNormalizer = state.settings.audioNormalizer,
                onAudioNormalizerChange = viewModel::setAudioNormalizer,
                audioLevelling = state.settings.audioLevelling,
                onAudioLevellingChange = viewModel::setAudioLevelling,
            )
        }
        TvDivider()

        TvPanel {
            RemoteControlSection(
                upDownFlips = state.settings.tvRemoteFlip,
                onUpDownFlipsChange = viewModel::setTvRemoteFlip,
            )
        }
        TvDivider()

        TvPanel {
            ChatTextSizeSection(
                selected = ChatTextSize.from(state.settings.chatFontSize),
                onSelect = viewModel::setChatTextSize,
            )
        }
        TvDivider()

        TvPanel {
            ChatBadgesSection(
                enabled = state.settings.showBadges,
                onEnabledChange = viewModel::setShowBadges,
            )
        }
        TvDivider()

        TvPanel {
            ChatPlacementSection(
                mode = state.settings.tvChatMode,
                onModeSelect = viewModel::setTvChatMode,
                opacity = state.settings.tvChatOpacity,
                onOpacitySelect = viewModel::setTvChatOpacity,
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
            GoLiveSection(
                enabled = state.settings.liveNotifications,
                onEnabledChange = viewModel::setLiveNotifications,
                showNote = GoLiveAlerts.showPerChannelNote(state.settings),
                onDismissNote = viewModel::dismissPerChannelNote,
                alertChannels = state.settings.notifyChannels,
                onRemoveChannel = viewModel::removeAlertChannel,
                onTurnOffAll = viewModel::turnOffAllAlertChannels,
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
                onGrantConsent = { updateManager.openInstallSettings() },
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
private fun StreamQualitySection(
    selected: String,
    onSelect: (StreamQuality) -> Unit,
    lowLatency: Boolean,
    onLowLatencyChange: (Boolean) -> Unit,
    audioNormalizer: Boolean,
    onAudioNormalizerChange: (Boolean) -> Unit,
    audioLevelling: AudioLevelling,
    onAudioLevellingChange: (AudioLevelling) -> Unit,
) {
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
        Spacer(Modifier.height(20.dp))
        TvDivider()
        Spacer(Modifier.height(20.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Low latency", style = MaterialTheme.typography.bodyLarge, color = c.onSurface)
                Text(
                    "Play a few seconds closer to live, so the stream keeps up with chat. " +
                        "If streams pause to buffer on your Wi-Fi, turn this off. Applies to the next stream you open.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = c.onSurfaceVariant,
                )
            }
            Switch(checked = lowLatency, onCheckedChange = onLowLatencyChange)
        }
        Spacer(Modifier.height(20.dp))
        TvDivider()
        Spacer(Modifier.height(20.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Even out loud and quiet audio", style = MaterialTheme.typography.bodyLarge, color = c.onSurface)
                Text(
                    "Turns down sudden loud moments and lifts quiet voices a little, so you can " +
                        "leave the volume alone. Music can sound flatter with it on. You can also " +
                        "switch it from the player controls.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = c.onSurfaceVariant,
                )
            }
            Switch(checked = audioNormalizer, onCheckedChange = onAudioNormalizerChange)
        }
        // How strongly, only while it is on. Applied live like the switch.
        if (audioNormalizer) {
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                AudioLevelling.entries.forEach { level ->
                    TvFilterChip(
                        label = level.label,
                        selected = level == audioLevelling,
                        onClick = { onAudioLevellingChange(level) },
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(audioLevelling.description, style = MaterialTheme.typography.bodyMedium, color = c.onSurfaceVariant)
        }
    }
}

// ── Remote control ────────────────────────────────────────────────────────

@Composable
private fun RemoteControlSection(
    upDownFlips: Boolean,
    onUpDownFlipsChange: (Boolean) -> Unit,
) {
    val c = PureTvTvTheme.colors
    Column {
        Text("Remote control", style = MaterialTheme.typography.titleLarge, color = c.onSurface)
        Spacer(Modifier.height(20.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Up and Down change channel", style = MaterialTheme.typography.bodyLarge, color = c.onSurface)
                Text(
                    "While watching, press Up or Down to switch to the next or previous channel. " +
                        "Turn this off to keep Up and Down for the controls. The Channel keys on your remote always switch channel.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = c.onSurfaceVariant,
                )
            }
            Switch(checked = upDownFlips, onCheckedChange = onUpDownFlipsChange)
        }
    }
}

// ── Chat ──────────────────────────────────────────────────────────────────

@Composable
private fun ChatBadgesSection(enabled: Boolean, onEnabledChange: (Boolean) -> Unit) {
    val c = PureTvTvTheme.colors
    Column {
        Text("Chat badges", style = MaterialTheme.typography.titleLarge, color = c.onSurface)
        Spacer(Modifier.height(20.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Show badges", style = MaterialTheme.typography.bodyLarge, color = c.onSurface)
                Text(
                    "Show subscriber, moderator and other badges next to names. Off keeps chat to names and messages.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = c.onSurfaceVariant,
                )
            }
            Switch(checked = enabled, onCheckedChange = onEnabledChange)
        }
    }
}

@Composable
private fun ChatTextSizeSection(selected: ChatTextSize, onSelect: (ChatTextSize) -> Unit) {
    val c = PureTvTvTheme.colors
    Column {
        Text("Chat text size", style = MaterialTheme.typography.titleLarge, color = c.onSurface)
        Spacer(Modifier.height(8.dp))
        Text(
            "Bigger chat is easier to read from the sofa. Emotes grow with the text.",
            style = MaterialTheme.typography.bodyLarge,
            color = c.onSurfaceVariant,
        )
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            ChatTextSize.entries.forEach { size ->
                TvFilterChip(label = size.label, selected = size == selected, onClick = { onSelect(size) })
            }
        }
    }
}

/** The chat background choices, as (label, opacity). */
private val ChatOpacityChoices = listOf("40%" to 0.4f, "60%" to 0.6f, "85%" to 0.85f, "100%" to 1f)

@Composable
private fun ChatPlacementSection(
    mode: String,
    onModeSelect: (String) -> Unit,
    opacity: Float,
    onOpacitySelect: (Float) -> Unit,
) {
    val c = PureTvTvTheme.colors
    Column {
        Text("Chat position", style = MaterialTheme.typography.titleLarge, color = c.onSurface)
        Spacer(Modifier.height(8.dp))
        Text(
            "Over the video keeps the picture full size. Beside the video makes the picture a little " +
                "smaller so chat never covers it. Applies to the next stream you open.",
            style = MaterialTheme.typography.bodyLarge,
            color = c.onSurfaceVariant,
        )
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            TvFilterChip(
                label = "Over the video",
                selected = mode != TV_CHAT_BESIDE_VIDEO,
                onClick = { onModeSelect(TV_CHAT_OVER_VIDEO) },
            )
            TvFilterChip(
                label = "Beside the video",
                selected = mode == TV_CHAT_BESIDE_VIDEO,
                onClick = { onModeSelect(TV_CHAT_BESIDE_VIDEO) },
            )
        }
        Spacer(Modifier.height(20.dp))
        TvDivider()
        Spacer(Modifier.height(20.dp))
        Text("Chat background", style = MaterialTheme.typography.bodyLarge, color = c.onSurface)
        Text(
            "Lower lets more of the stream show through chat.",
            style = MaterialTheme.typography.bodyMedium,
            color = c.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        // The stored value is matched to the nearest choice, so any saved number
        // still shows one chip as selected.
        val nearest = ChatOpacityChoices.minBy { abs(it.second - opacity) }
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            ChatOpacityChoices.forEach { choice ->
                TvFilterChip(
                    label = choice.first,
                    selected = choice == nearest,
                    onClick = { onOpacitySelect(choice.second) },
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

// ── Go-live banner ──────────────────────────────────────────────────────

@Composable
private fun GoLiveSection(
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    showNote: Boolean,
    onDismissNote: () -> Unit,
    alertChannels: Set<String>,
    onRemoveChannel: (String) -> Unit,
    onTurnOffAll: () -> Unit,
) {
    val c = PureTvTvTheme.colors
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Go-live banner", style = MaterialTheme.typography.titleLarge, color = c.onSurface)
                Spacer(Modifier.height(8.dp))
                Text(
                    "While PureTV is open, a banner in the corner says when a channel you picked starts " +
                        "streaming. It never moves your selection, so the remote keeps doing what it was doing. " +
                        "Alerts are per channel: open a channel and choose the bell, or press Menu on it in Following.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = c.onSurfaceVariant,
                )
            }
            Switch(checked = enabled, onCheckedChange = onEnabledChange)
        }
        if (showNote) {
            Spacer(Modifier.height(20.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                Text(
                    GoLiveText.PER_CHANNEL_NOTE,
                    style = MaterialTheme.typography.bodyLarge,
                    color = c.onSurface,
                    modifier = Modifier.weight(1f),
                )
                TvExpressiveButton(text = "Got it", onClick = onDismissNote, style = TvButtonStyle.Outlined)
            }
        }
        if (alertChannels.isEmpty()) {
            Spacer(Modifier.height(20.dp))
            Text(
                "No channels yet. Open a channel and choose the bell to get a banner when it goes live.",
                style = MaterialTheme.typography.bodyMedium,
                color = c.onSurfaceVariant,
            )
        } else {
            Spacer(Modifier.height(20.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        if (alertChannels.size == 1) "Banners for 1 channel" else "Banners for ${alertChannels.size} channels",
                        style = MaterialTheme.typography.titleMedium,
                        color = c.onSurface,
                    )
                }
                TvExpressiveButton(text = "Turn off all", onClick = onTurnOffAll, style = TvButtonStyle.Outlined)
            }
            alertChannels.sorted().forEach { login ->
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                ) {
                    Text(login, style = MaterialTheme.typography.bodyLarge, color = c.onSurface, modifier = Modifier.weight(1f))
                    TvExpressiveButton(text = "Remove", onClick = { onRemoveChannel(login) }, style = TvButtonStyle.Outlined)
                }
            }
        }
    }
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
    onGrantConsent: () -> Unit,
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
            is TvUpdateState.NeedsInstallConsent -> {
                Text(
                    "Update ${state.info.versionName} is ready to install.",
                    style = PureTvTvType.data,
                    color = c.primary,
                )
                Spacer(Modifier.height(8.dp))
                if (state.settingsResolvable) {
                    // Nothing has been downloaded yet, so leaving for Settings
                    // here costs nothing even if this app is stopped on the way.
                    // Coming back re-checks consent and continues on its own.
                    Text(
                        "Android needs your permission before PureTV can install it. " +
                            "Choose Allow on the next screen, then come back and the update will continue.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = c.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(16.dp))
                    TvExpressiveButton(text = "Allow and continue", onClick = onGrantConsent, icon = ExpressiveIcons.Download)
                } else {
                    // Fire OS publishes no per-app consent screen, so a button
                    // would go nowhere. Directions are the only honest option.
                    Text(
                        "This device has no in-app permission screen. Open Settings, " +
                            "then My Fire TV, then Developer options, and turn on " +
                            "Install unknown apps for PureTV. Then come back and press Check for updates.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = c.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(16.dp))
                    TvExpressiveButton(text = "Check for updates", onClick = onCheck, style = TvButtonStyle.Outlined, icon = ExpressiveIcons.Refresh)
                }
            }
            TvUpdateState.Idle ->
                TvExpressiveButton(text = "Check for updates", onClick = onCheck, style = TvButtonStyle.Outlined, icon = ExpressiveIcons.Refresh)
        }
    }
}
