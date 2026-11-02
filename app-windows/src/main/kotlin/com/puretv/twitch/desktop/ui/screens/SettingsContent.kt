package com.puretv.twitch.desktop.ui.screens

import androidx.compose.runtime.produceState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.puretv.twitch.desktop.platform.RtxGpu
import com.puretv.twitch.desktop.player.RtxHdrState
import com.puretv.twitch.desktop.player.RtxVideoStatus
import com.puretv.twitch.desktop.player.SuperResolutionState
import com.puretv.twitch.desktop.ui.components.RtxVideoText
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.puretv.twitch.core.model.PlaybackBackend
import com.puretv.twitch.core.notify.GoLiveAlerts
import com.puretv.twitch.desktop.player.DesktopPlayerProvider
import com.puretv.twitch.core.model.StreamQuality
import com.puretv.twitch.core.model.ChatTextSize
import com.puretv.twitch.desktop.data.DesktopSettingsStore
import com.puretv.twitch.desktop.platform.Diagnostics
import com.puretv.twitch.desktop.platform.LoginStartup
import com.puretv.twitch.desktop.platform.WindowChrome
import com.puretv.twitch.desktop.platform.openInBrowser
import com.puretv.twitch.desktop.ui.components.ExpressiveButtonSize
import androidx.compose.ui.window.isTraySupported
import com.puretv.twitch.desktop.player.LocalStreamProxy
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
            lowLatency = state.settings.lowLatencyMode,
            onLowLatencyChange = viewModel::setLowLatency,
            audioNormalizer = state.settings.audioNormalizer,
            onAudioNormalizerChange = viewModel::setAudioNormalizer,
            // The engine running now, not the saved one: an engine switch itself waits for a restart.
            normalizerNeedsRestart = koin.get<DesktopPlayerProvider>()
                .runningBackend(state.settings.playbackBackend) == PlaybackBackend.VLC,
        )
        Spacer(Modifier.height(16.dp))

        // Only on a PC with an NVIDIA RTX card. Read off the UI thread: the first read runs `reg query`.
        val rtxGpuName by produceState<String?>(null) { value = withContext(Dispatchers.IO) { RtxGpu.name } }
        rtxGpuName?.let { gpu ->
            val rtxStatus by remember { koin.get<DesktopPlayerProvider>().rtxStatusIfCreated() }.collectAsState()
            RtxVideoPanel(
                gpuName = gpu,
                status = rtxStatus,
                superResolution = state.settings.rtxSuperResolution,
                onSuperResolutionChange = viewModel::setRtxSuperResolution,
                hdr = state.settings.rtxHdr.takeIf { koin.get<DesktopPlayerProvider>().offersRtxHdr() },
                onHdrChange = viewModel::setRtxHdr,
            )
            Spacer(Modifier.height(16.dp))
        }

        ChatPanel(
            textSize = ChatTextSize.from(state.settings.chatFontSize),
            onTextSize = { size -> settingsStore.updateSettings { it.copy(chatFontSize = size.sp) } },
            showTimestamps = state.settings.chatTimestamps,
            onShowTimestamps = { on -> settingsStore.updateSettings { it.copy(chatTimestamps = on) } },
            showBadges = state.settings.showBadges,
            onShowBadges = { on -> settingsStore.updateSettings { it.copy(showBadges = on) } },
            compact = state.settings.compactMode,
            onCompact = { on -> settingsStore.updateSettings { it.copy(compactMode = on) } },
            showNamePaints = state.settings.showSevenTvPaints,
            onShowNamePaints = { on -> settingsStore.updateSettings { it.copy(showSevenTvPaints = on) } },
        )
        Spacer(Modifier.height(16.dp))

        NotificationsPanel(
            liveAlerts = state.settings.liveNotifications,
            onLiveAlerts = { on -> settingsStore.updateSettings { it.copy(liveNotifications = on) } },
            minimizeToTray = state.settings.minimizeToTray,
            onMinimizeToTray = { on -> settingsStore.updateSettings { it.copy(minimizeToTray = on) } },
            startWithWindows = state.settings.startWithWindows,
            onStartWithWindows = { on ->
                if (LoginStartup.setEnabled(on)) settingsStore.updateSettings { it.copy(startWithWindows = on) }
            },
            showNote = GoLiveAlerts.showPerChannelNote(state.settings),
            onDismissNote = { settingsStore.updateSettings { it.copy(perChannelNoteDismissed = true) } },
            alertChannels = state.settings.notifyChannels,
            onRemoveChannel = { login ->
                settingsStore.updateSettings { it.copy(notifyChannels = GoLiveAlerts.toggled(it.notifyChannels, login)) }
            },
            onTurnOffAll = { settingsStore.updateSettings { it.copy(notifyChannels = emptySet()) } },
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

        val clipboard = LocalClipboardManager.current
        AboutPanel(
            version = updateManager.currentVersion,
            onCopyDiagnostics = {
                val report = Diagnostics.report(
                    listOf(
                        "App" to "PureTV ${updateManager.currentVersion}, player ${state.settings.playbackBackend}, " +
                            "signed in: ${if (state.isLoggedIn) "yes" else "no"}",
                        "Stream proxy" to runCatching { koin.get<LocalStreamProxy>().diagnosticsSummary() }
                            .getOrDefault("(not started)"),
                    ),
                )
                clipboard.setText(AnnotatedString(report))
            },
            updateState = updateState,
            onCheckForUpdates = { updateManager.checkForUpdates(force = true) },
            onDownloadAndInstall = { info -> updateManager.downloadAndInstall(info, onExit) },
            onOpenDownloadPage = { url -> openInBrowser(url) },
            canSelfInstall = updateManager.canSelfInstall,
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
                pressScale = 1f,
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
                description = "How far corners round, and how much they morph when you press them.",
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
    lowLatency: Boolean,
    onLowLatencyChange: (Boolean) -> Unit,
    audioNormalizer: Boolean,
    onAudioNormalizerChange: (Boolean) -> Unit,
    normalizerNeedsRestart: Boolean,
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
                label = "Low latency",
                description = "Play a few seconds closer to live, so the stream keeps up with chat. If streams " +
                    "pause to buffer on a weak connection, turn this off. Applies to the next stream you open.",
            ) {
                ExpressiveSwitch(checked = lowLatency, onCheckedChange = onLowLatencyChange)
            }
            ExpressiveDivider(Modifier.padding(vertical = 24.dp))
            SettingsRow(
                label = "Even out loud and quiet audio",
                description = "Turns down sudden loud moments and lifts quiet voices a little, so you can " +
                    "leave the volume alone. Music can sound flatter with it on." +
                    if (normalizerNeedsRestart) " Applies after restart." else "",
            ) {
                ExpressiveSwitch(checked = audioNormalizer, onCheckedChange = onAudioNormalizerChange)
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

// ── NVIDIA RTX Video ─────────────────────────────────────────────────────────────

@Composable
private fun RtxVideoPanel(
    gpuName: String,
    status: RtxVideoStatus,
    superResolution: Boolean,
    onSuperResolutionChange: (Boolean) -> Unit,
    // Null hides the HDR switch: the running player can't do RTX Video HDR (VLC can't).
    hdr: Boolean?,
    onHdrChange: (Boolean) -> Unit,
) {
    val c = PureTvTheme.colors
    // Say what it is doing only once a stream has told us; before that the descriptions do the work.
    fun now(line: String, waiting: Boolean) = if (waiting) "" else " Now: $line"
    ExpressivePanel {
        Column {
            Text("NVIDIA RTX Video", style = MaterialTheme.typography.titleLarge, color = c.onSurface)
            Spacer(Modifier.height(6.dp))
            Text(
                "Uses your $gpuName to make streams look better in the main player. Multi-stream tiles " +
                    "and extra windows stay as they are. You can also switch these from the player menu.",
                style = MaterialTheme.typography.bodyMedium,
                color = c.onSurfaceVariant,
            )
            Spacer(Modifier.height(20.dp))
            SettingsRow(
                label = "Super Resolution",
                description = "Sharpens streams shown bigger than they were sent, like 1080p on a 1440p or 4K " +
                    "screen, or 720p in a big window. Uses more GPU power while it works. " +
                    RtxVideoText.DRIVER_HINT +
                    now(RtxVideoText.superResolution(status), status.superResolution in setOf(SuperResolutionState.OFF, SuperResolutionState.WAITING)),
            ) {
                ExpressiveSwitch(checked = superResolution, onCheckedChange = onSuperResolutionChange)
            }
            if (hdr != null) ExpressiveDivider(Modifier.padding(vertical = 24.dp))
            if (hdr != null) SettingsRow(
                label = "HDR",
                description = "Turns normal streams into HDR. It only does something when HDR is on for your " +
                    "display in Windows." +
                    now(RtxVideoText.hdr(status), status.hdr in setOf(RtxHdrState.OFF, RtxHdrState.WAITING)),
            ) {
                ExpressiveSwitch(checked = hdr, onCheckedChange = onHdrChange)
            }
        }
    }
}

// ── Chat ─────────────────────────────────────────────────────────────────────────

@Composable
private fun ChatPanel(
    textSize: ChatTextSize,
    onTextSize: (ChatTextSize) -> Unit,
    showTimestamps: Boolean,
    onShowTimestamps: (Boolean) -> Unit,
    showBadges: Boolean,
    onShowBadges: (Boolean) -> Unit,
    compact: Boolean,
    onCompact: (Boolean) -> Unit,
    showNamePaints: Boolean,
    onShowNamePaints: (Boolean) -> Unit,
) {
    val c = PureTvTheme.colors
    ExpressivePanel {
        Column {
            Text("Chat", style = MaterialTheme.typography.titleLarge, color = c.onSurface)
            Spacer(Modifier.height(20.dp))
            SettingsRow(
                label = "Text size",
                description = "How big chat reads. Emotes and badges grow with the text.",
            ) {
                SegmentedToggle(
                    options = ChatTextSize.entries,
                    selected = textSize,
                    label = { it.label },
                    onSelect = onTextSize,
                )
            }
            ExpressiveDivider(Modifier.padding(vertical = 24.dp))
            SettingsRow(label = "Timestamps", description = "Show the time each message was sent.") {
                ExpressiveSwitch(checked = showTimestamps, onCheckedChange = onShowTimestamps)
            }
            ExpressiveDivider(Modifier.padding(vertical = 24.dp))
            SettingsRow(label = "Badges", description = "Show subscriber, moderator and other badges next to names.") {
                ExpressiveSwitch(checked = showBadges, onCheckedChange = onShowBadges)
            }
            ExpressiveDivider(Modifier.padding(vertical = 24.dp))
            SettingsRow(label = "Compact chat", description = "Tighter rows, so more messages fit on screen.") {
                ExpressiveSwitch(checked = compact, onCheckedChange = onCompact)
            }
            ExpressiveDivider(Modifier.padding(vertical = 24.dp))
            SettingsRow(
                label = "Show 7TV name paints",
                description = "Show the gradient names 7TV users pick for themselves.",
            ) {
                ExpressiveSwitch(checked = showNamePaints, onCheckedChange = onShowNamePaints)
            }
        }
    }
}

// ── Notifications ────────────────────────────────────────────────────────────────

/**
 * Go-live alerts and the tray. Alerts arrive as Windows notifications from the tray
 * icon while PureTV is running (closing the window quits). Minimizing goes to the taskbar
 * unless "Minimize to tray" is on. Without a system tray (rare on Windows) neither works, so
 * the panel says so instead of offering switches that would do nothing.
 */
@Composable
private fun NotificationsPanel(
    liveAlerts: Boolean,
    onLiveAlerts: (Boolean) -> Unit,
    minimizeToTray: Boolean,
    onMinimizeToTray: (Boolean) -> Unit,
    startWithWindows: Boolean,
    onStartWithWindows: (Boolean) -> Unit,
    showNote: Boolean,
    onDismissNote: () -> Unit,
    alertChannels: Set<String>,
    onRemoveChannel: (String) -> Unit,
    onTurnOffAll: () -> Unit,
) {
    val c = PureTvTheme.colors
    val trayAvailable = remember { isTraySupported }
    val startupAvailable = remember { LoginStartup.isAvailable }
    ExpressivePanel {
        Column {
            Text("Notifications", style = MaterialTheme.typography.titleLarge, color = c.onSurface)
            Spacer(Modifier.height(20.dp))
            if (showNote) {
                SettingsRow(label = "Alerts are now per channel", description = "Open a channel and tap the bell.") {
                    ExpressiveButton(
                        text = "Got it",
                        onClick = onDismissNote,
                        style = ExpressiveButtonStyle.Outlined,
                        size = ExpressiveButtonSize.Small,
                    )
                }
                ExpressiveDivider(Modifier.padding(vertical = 24.dp))
            }
            if (!trayAvailable) {
                Text(
                    "Go-live alerts need a system tray, which this desktop does not offer.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = c.onSurfaceVariant,
                )
                return@Column
            }
            SettingsRow(
                label = "Go-live alerts",
                description = "Tell me when the channels I picked go live. Open a channel and tap the bell to pick it. Alerts appear from the tray icon while PureTV is open or minimized. Checks about every 90 seconds while PureTV is running.",
            ) {
                ExpressiveSwitch(checked = liveAlerts, onCheckedChange = onLiveAlerts)
            }
            ExpressiveDivider(Modifier.padding(vertical = 24.dp))
            if (WindowChrome.minimizeToTray()) {
                SettingsRow(
                    label = "Minimize to tray",
                    description = "Minimizing hides PureTV in the tray instead of the taskbar. The stream stops while hidden and picks up again when you open PureTV. Off: minimizing keeps the stream playing.",
                ) {
                    ExpressiveSwitch(checked = minimizeToTray, onCheckedChange = onMinimizeToTray)
                }
                ExpressiveDivider(Modifier.padding(vertical = 24.dp))
            }
            SettingsRow(
                label = LoginStartup.label(),
                description = if (startupAvailable) {
                    "Open PureTV in the tray when you sign in."
                } else {
                    "Available in the installed app."
                },
            ) {
                if (startupAvailable) ExpressiveSwitch(checked = startWithWindows, onCheckedChange = onStartWithWindows)
            }
            ExpressiveDivider(Modifier.padding(vertical = 24.dp))
            if (alertChannels.isEmpty()) {
                SettingsRow(
                    label = "Channels with alerts",
                    description = "None yet. Alerts are per channel: open a channel and tap the bell.",
                ) {}
            } else {
                SettingsRow(
                    label = "Channels with alerts",
                    description = "You are told when these go live.",
                ) {
                    ExpressiveButton(
                        text = "Turn off all",
                        onClick = onTurnOffAll,
                        style = ExpressiveButtonStyle.Outlined,
                        size = ExpressiveButtonSize.Small,
                    )
                }
                alertChannels.sorted().forEach { login ->
                    Spacer(Modifier.height(12.dp))
                    SettingsRow(label = login, description = "Alerts on") {
                        ExpressiveButton(
                            text = "Remove",
                            onClick = { onRemoveChannel(login) },
                            style = ExpressiveButtonStyle.Outlined,
                            size = ExpressiveButtonSize.Small,
                        )
                    }
                }
            }
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
    onCopyDiagnostics: () -> Unit,
    updateState: UpdateState,
    onCheckForUpdates: () -> Unit,
    onDownloadAndInstall: (UpdateInfo) -> Unit,
    onOpenDownloadPage: (String) -> Unit,
    canSelfInstall: Boolean,
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
            Spacer(Modifier.height(20.dp))
            ExpressiveDivider()
            Spacer(Modifier.height(20.dp))
            var copied by remember { mutableStateOf(false) }
            SettingsRow(
                label = "Diagnostics",
                description = if (copied) {
                    "Copied. Paste it into your bug report. It stays on this PC until you do."
                } else {
                    "Copies versions, recent errors and logs for a bug report. Sign-in details are left out " +
                        "and nothing is sent anywhere."
                },
            ) {
                ExpressiveButton(
                    text = "Copy diagnostics",
                    onClick = {
                        onCopyDiagnostics()
                        copied = true
                    },
                    style = ExpressiveButtonStyle.Outlined,
                )
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
                        text = if (canSelfInstall) "Download and install ${updateState.info.version}" else "Open download page for ${updateState.info.version}",
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
