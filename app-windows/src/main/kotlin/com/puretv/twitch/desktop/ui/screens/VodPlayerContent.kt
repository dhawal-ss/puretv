package com.puretv.twitch.desktop.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.puretv.twitch.core.model.ChatMessage
import com.puretv.twitch.desktop.data.DesktopSettingsStore
import com.puretv.twitch.desktop.player.VlcPlayerView
import com.puretv.twitch.desktop.player.formatTimecode
import com.puretv.twitch.desktop.ui.LocalAppShell
import com.puretv.twitch.desktop.ui.PlayerMode
import com.puretv.twitch.desktop.ui.VodChatViewModel
import com.puretv.twitch.desktop.ui.VodLaunch
import com.puretv.twitch.desktop.ui.VodPlayerViewModel
import com.puretv.twitch.desktop.ui.chat.nextFollowing
import com.puretv.twitch.desktop.ui.chat.scrollAnchor
import com.puretv.twitch.desktop.ui.components.ChatMessageRow
import com.puretv.twitch.desktop.ui.components.ExpressiveButton
import com.puretv.twitch.desktop.ui.components.ExpressiveButtonSize
import com.puretv.twitch.desktop.ui.components.ExpressiveButtonStyle
import com.puretv.twitch.desktop.ui.components.ExpressiveIconButton
import com.puretv.twitch.desktop.ui.components.ExpressiveIcons
import com.puretv.twitch.desktop.ui.components.PlayerSettingsMenu
import com.puretv.twitch.desktop.ui.components.SeekPreview
import com.puretv.twitch.desktop.ui.components.expressiveClickable
import com.puretv.twitch.desktop.ui.rememberDesktopViewModel
import com.puretv.twitch.desktop.ui.theme.PureTvMotion
import com.puretv.twitch.desktop.ui.theme.PureTvTheme
import com.puretv.twitch.desktop.ui.theme.PureTvType
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.koin.core.Koin
import org.koin.core.parameter.parametersOf
import java.awt.KeyEventDispatcher
import java.awt.KeyboardFocusManager
import java.awt.event.KeyEvent

/** The player-chrome card radius. Fixed rather than [PureTvTheme.shapes] because the
 *  live and VOD players share this exact silhouette regardless of the user's shape
 *  intensity setting -- it is a player-surface convention, not a content-card one. */
private val PlayerCardShape = RoundedCornerShape(24.dp)

/**
 * VOD player with the same immersive modes as the live screen: Default / Theater /
 * Fullscreen via the shared [LocalAppShell], auto-hiding chrome in immersive modes,
 * and F/T/C/Space/Esc shortcuts. Preserves resume prompt, scrub preview, transport,
 * quality, and the synced chat-replay panel.
 */
@Composable
fun VodPlayerContent(koin: Koin, launch: VodLaunch, onBack: () -> Unit) {
    val viewModel = rememberDesktopViewModel(launch.vodId) { koin.get<VodPlayerViewModel> { parametersOf(launch) } }
    val state by viewModel.state.collectAsState()
    // Only the error branch below reads `status` at this level; the position slider
    // lives in VodControls, which collects the full status itself. Project away the
    // volatile position fields + de-dup so the per-second time ticks don't recompose
    // this whole screen (including the chat-replay LazyColumn), the dominant VOD
    // stutter. VodControls remains the one place that sees the live position.
    val status by remember(viewModel) {
        viewModel.status
            .map { it.copy(positionMs = 0L, durationMs = 0L) }
            .distinctUntilChanged()
    }.collectAsState(initial = viewModel.status.value.copy(positionMs = 0L, durationMs = 0L))
    val chatViewModel = rememberDesktopViewModel(launch.vodId) { koin.get<VodChatViewModel> { parametersOf(launch.vodId, launch.channelLogin) } }
    val chatMessages by chatViewModel.messages.collectAsState()
    val settingsStore = remember { koin.get<DesktopSettingsStore>() }
    val appSettings by settingsStore.settings.collectAsState()
    var settingsMenuOpen by remember { mutableStateOf(false) }
    val shell = LocalAppShell.current
    val mode = shell.playerMode
    val isChatOpen = shell.isChatOpen
    val c = PureTvTheme.colors

    var controlsVisible by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()
    var hideJob by remember { mutableStateOf<Job?>(null) }
    val currentMode by rememberUpdatedState(mode)
    fun resetControls() {
        controlsVisible = true
        hideJob?.cancel()
        if (currentMode != PlayerMode.DEFAULT) {
            hideJob = scope.launch {
                delay(PureTvMotion.ControlsAutoHideMs)
                controlsVisible = false
            }
        }
    }
    LaunchedEffect(mode) { resetControls() }

    val latestMode = rememberUpdatedState(mode)
    DisposableEffect(Unit) {
        val dispatcher = KeyEventDispatcher { e ->
            if (e.id != KeyEvent.KEY_PRESSED) return@KeyEventDispatcher false
            val m = latestMode.value
            when (e.keyCode) {
                KeyEvent.VK_F -> { shell.setPlayerMode(if (m == PlayerMode.FULLSCREEN) PlayerMode.DEFAULT else PlayerMode.FULLSCREEN); true }
                KeyEvent.VK_T -> { shell.setPlayerMode(if (m == PlayerMode.THEATER) PlayerMode.DEFAULT else PlayerMode.THEATER); true }
                KeyEvent.VK_C -> { shell.toggleChat(); true }
                KeyEvent.VK_SPACE -> { viewModel.togglePlayPause(); true }
                KeyEvent.VK_ESCAPE -> if (m != PlayerMode.DEFAULT) { shell.exitImmersive(); true } else false
                else -> false
            }
        }
        val kfm = KeyboardFocusManager.getCurrentKeyboardFocusManager()
        kfm.addKeyEventDispatcher(dispatcher)
        onDispose { kfm.removeKeyEventDispatcher(dispatcher) }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(c.surfaceLowest)
            .padding(8.dp)
            .pointerInput(mode) {
                var lastPos: Offset? = null
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.type == PointerEventType.Move) {
                            val pos = event.changes.firstOrNull()?.position
                            if (pos != null && pos != lastPos) {
                                lastPos = pos
                                resetControls()
                            }
                        }
                    }
                }
            },
    ) {
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(modifier = Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AnimatedVisibility(
                    visible = controlsVisible || mode == PlayerMode.DEFAULT,
                    enter = slideInVertically { -it } + fadeIn(),
                    exit = slideOutVertically { -it } + fadeOut(),
                ) {
                    VodTopBar(
                        mode = mode,
                        title = launch.title.ifBlank { "Past broadcast" },
                        channelLogin = launch.channelLogin,
                        isChatOpen = isChatOpen,
                        onBack = onBack,
                        onToggleChat = { shell.toggleChat() },
                        onToggleTheater = { shell.setPlayerMode(if (mode == PlayerMode.THEATER) PlayerMode.DEFAULT else PlayerMode.THEATER) },
                        onToggleFullscreen = { shell.setPlayerMode(if (mode == PlayerMode.FULLSCREEN) PlayerMode.DEFAULT else PlayerMode.FULLSCREEN) },
                    )
                }

                // The AWT Canvas is a heavyweight surface: it paints above every Compose
                // layer and ignores clipping, so this slot stays an unrounded rectangle:
                // no card radius here, unlike its siblings above and below.
                Box(
                    modifier = Modifier.fillMaxWidth().weight(1f).background(Color.Black),
                    contentAlignment = Alignment.Center,
                ) {
                    when {
                        // Show a player error whenever playback isn't active, which covers an
                        // unavailable engine, an mpv init failure, and a failed start.
                        // Self-clears on recovery (file-loaded/playing resets error).
                        status.error != null && !status.isPlaying && !status.isBuffering ->
                            Text(status.error!!, color = c.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(24.dp))
                        state.error != null ->
                            Text(state.error!!, color = c.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(24.dp))
                        else -> VlcPlayerView(
                            vlcPlayer = viewModel.player,
                            modifier = Modifier.fillMaxSize(),
                            onUserActivity = { resetControls() },
                        )
                    }
                    if (state.loading && state.error == null) {
                        Text("Loading…", color = c.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                    }
                }

                // Playback menu in the Column (not over the video Canvas), above controls.
                AnimatedVisibility(
                    visible = settingsMenuOpen,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut(),
                ) {
                    PlayerSettingsMenu(
                        currentQuality = state.quality,
                        onQualitySelected = viewModel::setQuality,
                        upscalingMode = appSettings.upscalingMode,
                        onUpscalingSelected = viewModel::setUpscaling,
                        scalingEnabled = viewModel.player.supportsUpscaling,
                        backend = appSettings.playbackBackend,
                        onBackendSelected = viewModel::setPlaybackBackend,
                    )
                }

                AnimatedVisibility(
                    visible = controlsVisible || mode == PlayerMode.DEFAULT,
                    enter = slideInVertically { it } + fadeIn(),
                    exit = slideOutVertically { it } + fadeOut(),
                ) {
                    VodControls(
                        koin = koin,
                        viewModel = viewModel,
                        settingsOpen = settingsMenuOpen,
                        onToggleSettings = { settingsMenuOpen = !settingsMenuOpen },
                    )
                }
            }

            if (isChatOpen) {
                Column(Modifier.width(392.dp).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val listState = rememberLazyListState()
                    // Geometry only DETECTS the bottom; it does NOT gate auto-scroll.
                    var following by remember { mutableStateOf(true) }
                    val atBottom by remember {
                        derivedStateOf {
                            val info = listState.layoutInfo
                            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: -1
                            info.totalItemsCount == 0 || lastVisible >= info.totalItemsCount - 1
                        }
                    }
                    // Auto-scroll on every new message while FOLLOWING: instant and intent-gated
                    // so it keeps up with VOD's bursty per-second batch appends (the bug: a batch
                    // makes the geometry read "not at bottom", which used to skip the scroll).
                    LaunchedEffect(scrollAnchor(chatMessages)) {
                        if (chatMessages.isNotEmpty() && following) listState.scrollToItem(chatMessages.lastIndex)
                    }
                    // A user scroll away from the bottom pauses; reaching the bottom resumes.
                    LaunchedEffect(listState) {
                        snapshotFlow { atBottom to listState.isScrollInProgress }.collect { (bottom, scrolling) ->
                            following = nextFollowing(following, atBottom = bottom, userScrolling = scrolling)
                        }
                    }

                    Box(Modifier.weight(1f).fillMaxWidth().clip(PlayerCardShape).background(c.surfaceContainer)) {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp),
                            contentPadding = PaddingValues(vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            items(chatMessages, key = { it.id }) { msg: ChatMessage ->
                                ChatMessageRow(message = msg, showTimestamps = false)
                            }
                        }
                        // Twitch parity: paused while scrolled up. Clicking snaps to the
                        // bottom and resumes following so replay chat keeps going.
                        if (!following) {
                            val resumeInteraction = remember { MutableInteractionSource() }
                            val shapes = PureTvTheme.shapes
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(bottom = 12.dp)
                                    .expressiveClickable(
                                        interaction = resumeInteraction,
                                        onClick = {
                                            // Guard scrollToItem(-1): a VOD backward seek can empty the
                                            // replay buffer while this pill is still shown (audit U3).
                                            scope.launch { following = true; if (chatMessages.isNotEmpty()) listState.scrollToItem(chatMessages.lastIndex) }
                                        },
                                        restRadius = shapes.pill,
                                        hoverRadius = shapes.pillMorph,
                                        color = c.primary,
                                    )
                                    .padding(horizontal = 14.dp, vertical = 8.dp),
                            ) {
                                Text(
                                    "Chat paused due to scroll",
                                    color = c.onPrimary,
                                    style = MaterialTheme.typography.labelMedium,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VodTopBar(
    mode: PlayerMode,
    title: String,
    channelLogin: String,
    isChatOpen: Boolean,
    onBack: () -> Unit,
    onToggleChat: () -> Unit,
    onToggleTheater: () -> Unit,
    onToggleFullscreen: () -> Unit,
) {
    val c = PureTvTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
            .clip(PlayerCardShape)
            .background(c.surfaceContainer)
            .padding(start = if (mode == PlayerMode.DEFAULT) 8.dp else 20.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (mode == PlayerMode.DEFAULT) {
            ExpressiveIconButton(
                icon = ExpressiveIcons.Back,
                contentDescription = "Back",
                onClick = onBack,
                boxSize = 48.dp,
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                color = c.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (channelLogin.isNotBlank()) {
                Text(
                    channelLogin,
                    style = MaterialTheme.typography.bodyMedium,
                    color = c.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        ExpressiveIconButton(
            icon = ExpressiveIcons.Chat,
            contentDescription = "Toggle chat",
            onClick = onToggleChat,
            style = if (isChatOpen) ExpressiveButtonStyle.Tonal else ExpressiveButtonStyle.Text,
            boxSize = 48.dp,
        )
        ExpressiveIconButton(
            icon = ExpressiveIcons.AspectRatio,
            contentDescription = "Theater mode",
            onClick = onToggleTheater,
            style = if (mode == PlayerMode.THEATER) ExpressiveButtonStyle.Tonal else ExpressiveButtonStyle.Text,
            boxSize = 48.dp,
        )
        ExpressiveIconButton(
            icon = if (mode == PlayerMode.FULLSCREEN) ExpressiveIcons.FullscreenExit else ExpressiveIcons.Fullscreen,
            contentDescription = "Fullscreen",
            onClick = onToggleFullscreen,
            style = if (mode == PlayerMode.FULLSCREEN) ExpressiveButtonStyle.Tonal else ExpressiveButtonStyle.Text,
            boxSize = 48.dp,
        )
    }
}

// The custom track/thumb Slider overload is still @ExperimentalMaterial3Api in
// material3 1.7; it is the only way to draw the Expressive seek bar's own geometry
// (16dp track, 6x28 thumb) instead of the stock Slider's fixed-height groove.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VodControls(
    koin: Koin,
    viewModel: VodPlayerViewModel,
    settingsOpen: Boolean,
    onToggleSettings: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    val status by viewModel.status.collectAsState()
    val c = PureTvTheme.colors
    val shapes = PureTvTheme.shapes

    Column(
        Modifier
            .fillMaxWidth()
            .clip(PlayerCardShape)
            .background(c.surfaceContainer)
            .padding(horizontal = 16.dp),
    ) {
        state.resumeOfferMs?.let { at ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Resume from ${formatTimecode(at)}?", style = PureTvType.data, color = c.onSurface, modifier = Modifier.weight(1f))
                ExpressiveButton(text = "Resume", onClick = viewModel::resume, style = ExpressiveButtonStyle.Filled, size = ExpressiveButtonSize.Small)
                Spacer(Modifier.width(8.dp))
                ExpressiveButton(text = "Start over", onClick = viewModel::startOver, style = ExpressiveButtonStyle.Outlined, size = ExpressiveButtonSize.Small)
            }
        }

        var dragMs by remember { mutableStateOf<Long?>(null) }
        val duration = status.durationMs.coerceAtLeast(1)
        val shown = dragMs ?: status.positionMs
        if (dragMs != null) {
            BoxWithConstraints(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                val frac = (dragMs!!.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
                val x = (maxWidth - 160.dp) * frac
                SeekPreview(
                    koin = koin,
                    storyboard = state.storyboard,
                    positionSeconds = dragMs!! / 1000,
                    modifier = Modifier.offset(x = x),
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().height(80.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            val playInteraction = remember { MutableInteractionSource() }
            Box(
                modifier = Modifier
                    .size(width = 64.dp, height = 56.dp)
                    .expressiveClickable(
                        interaction = playInteraction,
                        onClick = viewModel::togglePlayPause,
                        restRadius = 28.dp,
                        hoverRadius = shapes.thumb,
                        color = c.primary,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (status.isPlaying) ExpressiveIcons.Pause else ExpressiveIcons.Play,
                    contentDescription = if (status.isPlaying) "Pause" else "Play",
                    tint = c.onPrimary,
                    modifier = Modifier.size(26.dp),
                )
            }

            ExpressiveIconButton(
                icon = if (status.isMuted || status.volume == 0) ExpressiveIcons.VolumeOff else ExpressiveIcons.VolumeUp,
                contentDescription = if (status.isMuted) "Unmute" else "Mute",
                onClick = viewModel::toggleMute,
                style = ExpressiveButtonStyle.Tonal,
                boxSize = 52.dp,
                iconSize = 22.dp,
            )
            Slider(
                value = status.volume.toFloat(),
                onValueChange = { viewModel.setVolume(it.toInt()) },
                valueRange = 0f..100f,
                modifier = Modifier.width(90.dp),
                colors = SliderDefaults.colors(thumbColor = c.primary, activeTrackColor = c.primary, inactiveTrackColor = c.surfaceHighest),
            )

            Text(formatTimecode(shown), style = PureTvType.data, color = c.onSurfaceVariant)
            Slider(
                value = (shown.toFloat() / duration.toFloat()).coerceIn(0f, 1f),
                onValueChange = { f -> dragMs = (f * duration).toLong() },
                onValueChangeFinished = { dragMs?.let { viewModel.seekTo(it) }; dragMs = null },
                enabled = status.isSeekable,
                modifier = Modifier.weight(1f),
                track = { sliderState ->
                    val trackFrac = ((sliderState.value - sliderState.valueRange.start) / (sliderState.valueRange.endInclusive - sliderState.valueRange.start)).coerceIn(0f, 1f)
                    Box(Modifier.fillMaxWidth().height(16.dp).clip(shapes.pillShape).background(c.surfaceHighest)) {
                        Box(Modifier.fillMaxWidth(trackFrac).fillMaxHeight().clip(shapes.pillShape).background(c.primary))
                    }
                },
                thumb = {
                    Box(Modifier.size(width = 6.dp, height = 28.dp).clip(RoundedCornerShape(3.dp)).background(c.primary))
                },
            )
            Text(formatTimecode(status.durationMs), style = PureTvType.data, color = c.onSurfaceVariant)

            Box(Modifier.size(56.dp).clip(shapes.pillShape).background(c.surfaceHigh)) {
                val settingsInteraction = remember { MutableInteractionSource() }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .expressiveClickable(
                            interaction = settingsInteraction,
                            onClick = onToggleSettings,
                            restRadius = 0.dp,
                            hoverRadius = 0.dp,
                            color = Color.Transparent,
                            hoverColor = c.surfaceHighest,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        ExpressiveIcons.Settings,
                        contentDescription = "Playback settings",
                        tint = if (settingsOpen) c.primary else c.onSurfaceVariant,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
        }
    }
}
