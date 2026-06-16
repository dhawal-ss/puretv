package com.puretv.twitch.desktop.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.puretv.twitch.core.model.ChatMessage
import com.puretv.twitch.core.model.StreamQuality
import com.puretv.twitch.desktop.player.VlcPlayerView
import com.puretv.twitch.desktop.player.formatTimecode
import com.puretv.twitch.desktop.ui.LocalAppShell
import com.puretv.twitch.desktop.ui.PlayerMode
import com.puretv.twitch.desktop.ui.VodChatViewModel
import com.puretv.twitch.desktop.ui.VodLaunch
import com.puretv.twitch.desktop.ui.VodPlayerViewModel
import com.puretv.twitch.desktop.ui.chat.nextFollowing
import com.puretv.twitch.desktop.ui.chat.scrollAnchor
import com.puretv.twitch.desktop.ui.components.ButtonVariant
import com.puretv.twitch.desktop.ui.components.ChatMessageRow
import com.puretv.twitch.desktop.ui.components.PureButton
import com.puretv.twitch.desktop.ui.components.SeekPreview
import com.puretv.twitch.desktop.ui.components.SegmentedControl
import com.puretv.twitch.desktop.ui.rememberDesktopViewModel
import com.puretv.twitch.desktop.ui.theme.PureTvMotion
import com.puretv.twitch.desktop.ui.theme.PureTvShape
import com.puretv.twitch.desktop.ui.theme.PureTvTheme
import com.puretv.twitch.desktop.ui.theme.PureTvType
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.core.Koin
import org.koin.core.parameter.parametersOf
import java.awt.KeyEventDispatcher
import java.awt.KeyboardFocusManager
import java.awt.event.KeyEvent

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
    val status by viewModel.status.collectAsState()
    val chatViewModel = rememberDesktopViewModel(launch.vodId) { koin.get<VodChatViewModel> { parametersOf(launch.vodId, launch.channelLogin) } }
    val chatMessages by chatViewModel.messages.collectAsState()
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
            .background(Color.Black)
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
        Row(Modifier.fillMaxSize()) {
            Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                AnimatedVisibility(
                    visible = controlsVisible || mode == PlayerMode.DEFAULT,
                    enter = slideInVertically { -it } + fadeIn(),
                    exit = slideOutVertically { -it } + fadeOut(),
                ) {
                    VodTopBar(
                        mode = mode,
                        title = launch.title.ifBlank { "Past broadcast" },
                        isChatOpen = isChatOpen,
                        onBack = onBack,
                        onToggleChat = { shell.toggleChat() },
                        onToggleTheater = { shell.setPlayerMode(if (mode == PlayerMode.THEATER) PlayerMode.DEFAULT else PlayerMode.THEATER) },
                        onToggleFullscreen = { shell.setPlayerMode(if (mode == PlayerMode.FULLSCREEN) PlayerMode.DEFAULT else PlayerMode.FULLSCREEN) },
                    )
                }

                Box(
                    modifier = Modifier.fillMaxWidth().weight(1f).background(Color.Black),
                    contentAlignment = Alignment.Center,
                ) {
                    when {
                        status.error != null && !viewModel.player.isAvailable ->
                            Text(status.error!!, color = c.textSecondary, modifier = Modifier.padding(24.dp))
                        state.error != null ->
                            Text(state.error!!, color = c.textSecondary, modifier = Modifier.padding(24.dp))
                        else -> VlcPlayerView(
                            vlcPlayer = viewModel.player,
                            modifier = Modifier.fillMaxSize(),
                            onUserActivity = { resetControls() },
                        )
                    }
                    if (state.loading && state.error == null) Text("Loading…", color = c.textSecondary)
                }

                AnimatedVisibility(
                    visible = controlsVisible || mode == PlayerMode.DEFAULT,
                    enter = slideInVertically { it } + fadeIn(),
                    exit = slideOutVertically { it } + fadeOut(),
                ) {
                    VodControls(koin = koin, viewModel = viewModel)
                }
            }

            if (isChatOpen) {
                Column(Modifier.width(340.dp).fillMaxHeight().background(c.surface)) {
                    Box(Modifier.fillMaxWidth().height(1.dp).background(c.hairline))
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
                    // Auto-scroll on every new message while FOLLOWING — instant + intent-gated
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

                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp),
                            contentPadding = PaddingValues(vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            items(chatMessages, key = { it.id }) { msg: ChatMessage ->
                                ChatMessageRow(message = msg, showTimestamps = false)
                            }
                        }
                        // Twitch parity: paused while scrolled up. Clicking snaps to the
                        // bottom and resumes following so replay chat keeps going.
                        if (!following) {
                            Surface(
                                onClick = {
                                    scope.launch { following = true; listState.scrollToItem(chatMessages.lastIndex) }
                                },
                                shape = PureTvShape.pill,
                                color = c.twitchPurple,
                                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp),
                            ) {
                                Text(
                                    "Chat paused due to scroll",
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
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
    isChatOpen: Boolean,
    onBack: () -> Unit,
    onToggleChat: () -> Unit,
    onToggleTheater: () -> Unit,
    onToggleFullscreen: () -> Unit,
) {
    val c = PureTvTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth().background(c.surface).padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (mode == PlayerMode.DEFAULT) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = c.textPrimary)
            }
        }
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            color = c.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(start = if (mode == PlayerMode.DEFAULT) 4.dp else 12.dp),
        )
        IconButton(onClick = onToggleChat) {
            Icon(Icons.AutoMirrored.Filled.Chat, "Toggle chat", tint = if (isChatOpen) c.twitchPurpleLight else c.textSecondary)
        }
        IconButton(onClick = onToggleTheater) {
            Icon(Icons.Filled.AspectRatio, "Theater mode", tint = if (mode == PlayerMode.THEATER) c.twitchPurpleLight else c.textSecondary)
        }
        IconButton(onClick = onToggleFullscreen) {
            Icon(
                if (mode == PlayerMode.FULLSCREEN) Icons.Filled.FullscreenExit else Icons.Filled.Fullscreen,
                "Fullscreen",
                tint = c.textSecondary,
            )
        }
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(c.hairline))
}

@Composable
private fun VodControls(koin: Koin, viewModel: VodPlayerViewModel) {
    val state by viewModel.state.collectAsState()
    val status by viewModel.status.collectAsState()
    val c = PureTvTheme.colors

    Box(Modifier.fillMaxWidth().height(1.dp).background(c.hairline))
    Column(Modifier.fillMaxWidth().background(c.surface).padding(horizontal = 12.dp, vertical = 8.dp)) {
        state.resumeOfferMs?.let { at ->
            Row(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Resume from ${formatTimecode(at)}?", style = PureTvType.data, color = c.textPrimary, modifier = Modifier.weight(1f))
                PureButton(text = "Resume", onClick = viewModel::resume, variant = ButtonVariant.Primary)
                Spacer(Modifier.width(8.dp))
                PureButton(text = "Start over", onClick = viewModel::startOver, variant = ButtonVariant.Secondary)
            }
        }

        var dragMs by remember { mutableStateOf<Long?>(null) }
        val duration = status.durationMs.coerceAtLeast(1)
        val shown = dragMs ?: status.positionMs
        if (dragMs != null) {
            BoxWithConstraints(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
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
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(formatTimecode(shown), style = PureTvType.data, color = c.textSecondary)
            Slider(
                value = (shown.toFloat() / duration.toFloat()).coerceIn(0f, 1f),
                onValueChange = { f -> dragMs = (f * duration).toLong() },
                onValueChangeFinished = { dragMs?.let { viewModel.seekTo(it) }; dragMs = null },
                enabled = status.isSeekable,
                modifier = Modifier.weight(1f).padding(horizontal = 10.dp),
                colors = SliderDefaults.colors(thumbColor = c.twitchPurple, activeTrackColor = c.twitchPurple, inactiveTrackColor = c.surfaceVariant),
            )
            Text(formatTimecode(status.durationMs), style = PureTvType.data, color = c.textSecondary)
        }
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = viewModel::togglePlayPause) {
                Icon(
                    if (status.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (status.isPlaying) "Pause" else "Play",
                    tint = c.textPrimary,
                )
            }
            IconButton(onClick = viewModel::toggleMute, modifier = Modifier.size(28.dp)) {
                Icon(
                    if (status.isMuted || status.volume == 0) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                    if (status.isMuted) "Unmute" else "Mute",
                    tint = c.textSecondary,
                    modifier = Modifier.size(18.dp),
                )
            }
            Slider(
                value = status.volume.toFloat(),
                onValueChange = { viewModel.setVolume(it.toInt()) },
                valueRange = 0f..100f,
                modifier = Modifier.width(110.dp).padding(horizontal = 6.dp),
                colors = SliderDefaults.colors(thumbColor = c.twitchPurple, activeTrackColor = c.twitchPurple, inactiveTrackColor = c.surfaceVariant),
            )
            Spacer(Modifier.weight(1f))
            SegmentedControl(
                options = StreamQuality.entries,
                selected = state.quality,
                label = { it.label },
                onSelect = viewModel::setQuality,
            )
        }
    }
}
