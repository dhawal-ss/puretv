package com.puretv.twitch.desktop.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.puretv.twitch.core.adblock.AdBlockStatus
import com.puretv.twitch.core.model.ChatMessage
import com.puretv.twitch.core.model.StreamQuality
import com.puretv.twitch.desktop.player.VlcPlayer
import com.puretv.twitch.desktop.player.VlcPlayerView
import com.puretv.twitch.desktop.ui.LocalAppShell
import com.puretv.twitch.desktop.ui.PlayerMode
import com.puretv.twitch.desktop.ui.StreamViewModel
import com.puretv.twitch.desktop.ui.rememberDesktopViewModel
import com.puretv.twitch.desktop.ui.theme.PureTvMotion
import com.puretv.twitch.desktop.ui.theme.PureTvTheme
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.core.Koin
import org.koin.core.parameter.parametersOf
import java.awt.Color as AwtColor

private val CHAT_WIDTH = 360.dp

/**
 * Watch screen with stable VLC surface.
 *
 * AWT heavyweight components (VLC's Canvas/SwingPanel) always render above
 * Compose-drawn content, so controls CANNOT be overlaid on top of the player.
 * The layout uses a Column: top-bar → player Box → controls-bar, with
 * AnimatedVisibility sliding them in/out in THEATER/FULLSCREEN mode.
 * Chat lives in a Row column to the right — never overlapping the AWT surface.
 *
 *   F      toggle fullscreen      T  toggle theater
 *   C      toggle chat            Space  play/pause
 *   Esc    exit immersive
 */
@Composable
fun StreamContent(koin: Koin, channelLogin: String, onBack: () -> Unit) {
    val viewModel = rememberDesktopViewModel(channelLogin) {
        koin.get<StreamViewModel> { parametersOf(channelLogin) }
    }
    val state by viewModel.state.collectAsState()
    val isFollowed by viewModel.isFollowed.collectAsState()
    val vlcPlayer = remember { koin.get<VlcPlayer>() }
    val playerStatus by vlcPlayer.status.collectAsState()
    val shell = LocalAppShell.current
    val mode = shell.playerMode
    val isChatOpen = shell.isChatOpen
    val c = PureTvTheme.colors

    val chatWidth by animateDpAsState(
        targetValue = if (isChatOpen) CHAT_WIDTH else 0.dp,
        animationSpec = tween(PureTvMotion.Medium),
        label = "chatWidth",
    )

    // Controls visibility: always shown in DEFAULT, auto-hides in THEATER/FULLSCREEN
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
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(mode) {
        // Re-assert keyboard focus on every mode change. Entering fullscreen
        // resizes the heavyweight VLC surface and can pull AWT focus toward it;
        // without re-requesting, the F/Esc shortcuts would die and the user
        // would be trapped in fullscreen with no way out but Task Manager.
        focusRequester.requestFocus()
        resetControls()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (e.key) {
                    Key.F -> { shell.setPlayerMode(if (mode == PlayerMode.FULLSCREEN) PlayerMode.DEFAULT else PlayerMode.FULLSCREEN); true }
                    Key.T -> { shell.setPlayerMode(if (mode == PlayerMode.THEATER) PlayerMode.DEFAULT else PlayerMode.THEATER); true }
                    Key.C -> { shell.toggleChat(); true }
                    Key.Spacebar -> { viewModel.togglePlayPause(); true }
                    Key.Escape -> { shell.exitImmersive(); true }
                    else -> false
                }
            }
            .pointerInput(mode) {
                // Only genuine cursor movement counts as "activity". When the
                // controls auto-hide in THEATER/FULLSCREEN, the player Box grows
                // and resizes the heavyweight VLC surface; that relayout emits a
                // SAME-position synthetic move event. Resetting on it would re-show
                // the controls → relayout → re-hide, an endless show/hide flicker
                // synced to ControlsAutoHideMs (the theatre-mode "epilepsy" bug).
                // Comparing positions filters those synthetic events out.
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
            // ── Player + controls column ───────────────────────────────────────
            Column(modifier = Modifier.weight(1f).fillMaxHeight()) {

                // Top bar — always in DEFAULT, slides up when idle in THEATER/FULLSCREEN
                AnimatedVisibility(
                    visible = controlsVisible || mode == PlayerMode.DEFAULT,
                    enter = slideInVertically { -it } + fadeIn(),
                    exit = slideOutVertically { -it } + fadeOut(),
                ) {
                    TopBar(
                        mode = mode,
                        channelName = state.channel?.displayName ?: channelLogin,
                        viewerInfo = state.streamInfo?.let { "${it.gameName.takeIf { g -> g.isNotBlank() } ?: "—"} · ${it.viewerCount} viewers" },
                        adBlockStatus = state.adBlockStatus,
                        isChatOpen = isChatOpen,
                        isFollowed = isFollowed,
                        canFollow = state.channel != null,
                        onToggleFollow = viewModel::toggleFollow,
                        onBack = onBack,
                        onToggleChat = { shell.toggleChat() },
                        onToggleTheater = { shell.setPlayerMode(if (mode == PlayerMode.THEATER) PlayerMode.DEFAULT else PlayerMode.THEATER) },
                        onToggleFullscreen = { shell.setPlayerMode(if (mode == PlayerMode.FULLSCREEN) PlayerMode.DEFAULT else PlayerMode.FULLSCREEN) },
                    )
                }

                // Player surface — never unmounted (see VlcPlayerView docs)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .background(Color.Black),
                    contentAlignment = Alignment.Center,
                ) {
                    when {
                        playerStatus.error != null && !vlcPlayer.isAvailable -> Text(
                            playerStatus.error!!,
                            color = c.textSecondary,
                            modifier = Modifier.padding(24.dp),
                        )
                        state.playableUrl != null -> VlcPlayerView(
                            vlcPlayer = vlcPlayer,
                            modifier = Modifier.fillMaxSize(),
                            // The heavyweight video surface eats mouse events; bridge
                            // them back so moving the mouse reveals the controls —
                            // including in fullscreen, where the surface covers all.
                            onUserActivity = { resetControls() },
                        )
                        else -> Text(
                            if (state.isLoading) "Loading stream…" else "This channel is offline.",
                            color = c.textSecondary,
                        )
                    }
                }

                // Controls bar — always in DEFAULT, slides down when idle in THEATER/FULLSCREEN
                AnimatedVisibility(
                    visible = controlsVisible || mode == PlayerMode.DEFAULT,
                    enter = slideInVertically { it } + fadeIn(),
                    exit = slideOutVertically { it } + fadeOut(),
                ) {
                    PlaybackControls(
                        isPlaying = playerStatus.isPlaying,
                        volume = playerStatus.volume,
                        currentQuality = state.currentQuality,
                        onTogglePlayPause = viewModel::togglePlayPause,
                        onVolumeChange = viewModel::setVolume,
                        onQualitySelected = viewModel::setQuality,
                    )
                }
            }

            // ── Chat panel ─────────────────────────────────────────────────────
            // Width animates 0↔360dp. clipToBounds() ensures content clips clean.
            Box(
                modifier = Modifier
                    .width(chatWidth)
                    .fillMaxHeight()
                    .clipToBounds(),
            ) {
                Row(Modifier.fillMaxSize()) {
                    // 1dp hairline left border
                    Box(Modifier.width(1.dp).fillMaxHeight().background(c.hairline))
                    // Chat content
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .background(c.surface),
                    ) {
                        // Header
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "Chat",
                                style = MaterialTheme.typography.titleMedium,
                                color = c.textPrimary,
                                modifier = Modifier.weight(1f),
                            )
                            IconButton(onClick = { shell.toggleChat() }) {
                                Icon(Icons.Filled.Close, "Close chat", tint = c.textSecondary, modifier = Modifier.size(16.dp))
                            }
                        }
                        Box(Modifier.fillMaxWidth().height(1.dp).background(c.hairline))
                        ChatMessageList(messages = state.chatMessages, modifier = Modifier.weight(1f))
                        ChatInputBar(onSend = viewModel::sendChatMessage)
                    }
                }
            }
        }
    }
}

// ── Top bar ────────────────────────────────────────────────────────────────────

@Composable
private fun TopBar(
    mode: PlayerMode,
    channelName: String,
    viewerInfo: String?,
    adBlockStatus: AdBlockStatus,
    isChatOpen: Boolean,
    isFollowed: Boolean,
    canFollow: Boolean,
    onToggleFollow: () -> Unit,
    onBack: () -> Unit,
    onToggleChat: () -> Unit,
    onToggleTheater: () -> Unit,
    onToggleFullscreen: () -> Unit,
) {
    val c = PureTvTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(c.surface)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (mode == PlayerMode.DEFAULT) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = c.textPrimary)
            }
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = if (mode == PlayerMode.DEFAULT) 4.dp else 12.dp),
        ) {
            Text(
                channelName,
                style = MaterialTheme.typography.titleLarge,
                color = c.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (viewerInfo != null) {
                Text(viewerInfo, style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
            }
        }

        AdBlockStatusPill(adBlockStatus)
        Spacer(Modifier.width(4.dp))

        IconButton(onClick = onToggleFollow, enabled = canFollow) {
            Icon(
                if (isFollowed) Icons.Filled.Check else Icons.Filled.Add,
                contentDescription = if (isFollowed) "Following" else "Follow",
                tint = if (isFollowed) c.twitchPurpleLight else c.textSecondary,
            )
        }
        IconButton(onClick = onToggleChat) {
            Icon(
                Icons.AutoMirrored.Filled.Chat,
                "Toggle chat",
                tint = if (isChatOpen) c.twitchPurpleLight else c.textSecondary,
            )
        }
        IconButton(onClick = onToggleTheater) {
            Icon(
                Icons.Filled.AspectRatio,
                "Theater mode",
                tint = if (mode == PlayerMode.THEATER) c.twitchPurpleLight else c.textSecondary,
            )
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

// ── Ad-block pill ──────────────────────────────────────────────────────────────

@Composable
private fun AdBlockStatusPill(status: AdBlockStatus) {
    val c = PureTvTheme.colors
    val (label, dotColor) = when (status) {
        AdBlockStatus.AD_BLOCKED -> "Ads blocked" to c.adBlockGreen
        AdBlockStatus.AD_FILTERED -> "Ads filtered" to c.adBlockGreen
        AdBlockStatus.AD_BLOCK_OFF -> "Ad block off" to c.textMuted
        AdBlockStatus.DISABLED -> "Disabled" to c.textMuted
        AdBlockStatus.UNKNOWN -> "Checking…" to c.textMuted
    }
    Row(
        modifier = Modifier
            .border(1.dp, c.hairline, RoundedCornerShape(20.dp))
            .background(c.surfaceVariant, RoundedCornerShape(20.dp))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(7.dp).background(dotColor, CircleShape))
        Text(label, color = c.textSecondary, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(start = 6.dp))
    }
}

// ── Playback controls ─────────────────────────────────────────────────────────

@Composable
private fun PlaybackControls(
    isPlaying: Boolean,
    volume: Int,
    currentQuality: StreamQuality,
    onTogglePlayPause: () -> Unit,
    onVolumeChange: (Int) -> Unit,
    onQualitySelected: (StreamQuality) -> Unit,
) {
    val c = PureTvTheme.colors
    Box(Modifier.fillMaxWidth().height(1.dp).background(c.hairline))
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(c.surface)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onTogglePlayPause) {
            Icon(
                if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = if (isPlaying) "Pause" else "Play",
                tint = c.textPrimary,
            )
        }
        Icon(
            Icons.AutoMirrored.Filled.VolumeUp,
            "Volume",
            tint = c.textSecondary,
            modifier = Modifier.size(18.dp),
        )
        Slider(
            value = volume.toFloat(),
            onValueChange = { onVolumeChange(it.toInt()) },
            valueRange = 0f..100f,
            modifier = Modifier.width(110.dp).padding(horizontal = 6.dp),
            colors = SliderDefaults.colors(
                thumbColor = c.twitchPurple,
                activeTrackColor = c.twitchPurple,
                inactiveTrackColor = c.surfaceVariant,
            ),
        )
        Spacer(Modifier.width(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            StreamQuality.entries.forEach { quality ->
                QualityPill(quality.label, quality == currentQuality) { onQualitySelected(quality) }
            }
        }
    }
}

@Composable
private fun QualityPill(label: String, selected: Boolean, onClick: () -> Unit) {
    val c = PureTvTheme.colors
    Box(
        modifier = Modifier
            .border(1.dp, if (selected) c.twitchPurple else c.hairline, RoundedCornerShape(6.dp))
            .background(if (selected) c.twitchPurple.copy(alpha = 0.2f) else Color.Transparent, RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 9.dp, vertical = 5.dp),
    ) {
        Text(
            label,
            color = if (selected) c.twitchPurpleLight else c.textSecondary,
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

// ── Chat UI ───────────────────────────────────────────────────────────────────

@Composable
private fun ChatMessageList(messages: List<ChatMessage>, modifier: Modifier = Modifier) {
    val listState = rememberLazyListState()
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }
    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxWidth().padding(horizontal = 10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp),
    ) {
        items(messages, key = { it.id }) { ChatMessageRow(it) }
    }
}

@Composable
private fun ChatMessageRow(message: ChatMessage) {
    val c = PureTvTheme.colors
    val nameColor = remember(message.color) {
        runCatching { Color(AwtColor.decode(message.color).rgb or (0xFF shl 24)) }
            .getOrDefault(c.twitchPurpleLight)
    }
    Row {
        Text(message.displayName, color = nameColor, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        Text(": ${message.message}", color = c.textPrimary, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ChatInputBar(onSend: (String) -> Unit) {
    val c = PureTvTheme.colors
    var text by remember { mutableStateOf("") }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(10.dp)
            .background(c.surfaceVariant, RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.weight(1f)) {
            if (text.isEmpty()) {
                Text("Send a message…", color = c.textMuted, style = MaterialTheme.typography.bodyMedium)
            }
            BasicTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                textStyle = TextStyle(color = c.textPrimary, fontSize = MaterialTheme.typography.bodyMedium.fontSize),
                cursorBrush = SolidColor(c.twitchPurple),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        IconButton(onClick = {
            val trimmed = text.trim()
            if (trimmed.isNotEmpty()) { onSend(trimmed); text = "" }
        }) {
            Icon(Icons.AutoMirrored.Filled.Send, "Send", tint = c.twitchPurpleLight)
        }
    }
}
