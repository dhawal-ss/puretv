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
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Mood
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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.onFocusChanged
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
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
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
import com.puretv.twitch.desktop.ui.components.AdBlockPill
import com.puretv.twitch.desktop.ui.components.ChatMessageRow
import com.puretv.twitch.desktop.ui.components.LiveDot
import com.puretv.twitch.desktop.ui.components.SegmentedControl
import com.puretv.twitch.desktop.ui.chat.ComposerKeyAction
import com.puretv.twitch.desktop.ui.chat.completeWord
import com.puretv.twitch.desktop.ui.chat.composerKeyAction
import com.puretv.twitch.desktop.ui.chat.insertAtCursor
import com.puretv.twitch.desktop.ui.chat.matchEmotes
import com.puretv.twitch.desktop.ui.chat.nextFollowing
import com.puretv.twitch.desktop.ui.chat.scrollAnchor
import com.puretv.twitch.desktop.ui.chat.wordAtCursor
import com.puretv.twitch.desktop.ui.components.EmoteImage
import com.puretv.twitch.core.emotes.PickableEmote
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
fun StreamContent(koin: Koin, channelLogin: String, onBack: () -> Unit, onRequestSignIn: () -> Unit = {}) {
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
    LaunchedEffect(mode) { resetControls() }

    // Player hotkeys (F/T/C/Space/Esc) are handled at the AWT KeyboardFocusManager
    // level, NOT via Compose's onKeyEvent. The heavyweight VLC video Canvas is a
    // native window that can pull keyboard focus off Compose's Skia layer; once
    // that happened the Compose key handler stopped firing and the user was
    // trapped in fullscreen with F/Esc dead (the "stuck fullscreen" bug). A
    // KeyEventDispatcher sees every key the focused window receives regardless of
    // whether Compose or the Canvas currently holds focus, so the shortcuts can
    // never die. We skip it while the chat input is focused so typing — including
    // spaces and the letters f/t/c — still reaches the chat box.
    var chatInputFocused by remember { mutableStateOf(false) }
    val latestMode = rememberUpdatedState(mode)
    val latestChatFocused = rememberUpdatedState(chatInputFocused)
    DisposableEffect(Unit) {
        val dispatcher = KeyEventDispatcher { e ->
            if (e.id != KeyEvent.KEY_PRESSED) return@KeyEventDispatcher false
            if (latestChatFocused.value) return@KeyEventDispatcher false
            val m = latestMode.value
            when (e.keyCode) {
                KeyEvent.VK_F -> { shell.setPlayerMode(if (m == PlayerMode.FULLSCREEN) PlayerMode.DEFAULT else PlayerMode.FULLSCREEN); true }
                KeyEvent.VK_T -> { shell.setPlayerMode(if (m == PlayerMode.THEATER) PlayerMode.DEFAULT else PlayerMode.THEATER); true }
                KeyEvent.VK_C -> { shell.toggleChat(); true }
                KeyEvent.VK_SPACE -> { viewModel.togglePlayPause(); true }
                // Esc only acts when immersive, so it doesn't swallow a stray Esc
                // elsewhere; in DEFAULT mode it passes through untouched.
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
                        isMuted = playerStatus.isMuted,
                        currentQuality = state.currentQuality,
                        onTogglePlayPause = viewModel::togglePlayPause,
                        onVolumeChange = viewModel::setVolume,
                        onToggleMute = viewModel::toggleMute,
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
                        ChatMessageList(
                            messages = state.chatMessages,
                            onReply = viewModel::startReply,
                            modifier = Modifier.weight(1f),
                        )
                        ChatInputBar(
                            canChat = state.canChat,
                            emotes = state.emotes,
                            replyingTo = state.replyingTo,
                            onCancelReply = viewModel::cancelReply,
                            onSend = viewModel::sendChatMessage,
                            onFocusChanged = { chatInputFocused = it },
                            onRequestSignIn = onRequestSignIn,
                        )
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
                Text(viewerInfo, style = PureTvType.data, color = c.textSecondary)
            }
        }

        AdBlockPill(adBlockStatus)
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

// ── Playback controls ─────────────────────────────────────────────────────────

@Composable
private fun PlaybackControls(
    isPlaying: Boolean,
    volume: Int,
    isMuted: Boolean,
    currentQuality: StreamQuality,
    onTogglePlayPause: () -> Unit,
    onVolumeChange: (Int) -> Unit,
    onToggleMute: () -> Unit,
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
        IconButton(onClick = onToggleMute, modifier = Modifier.size(28.dp)) {
            Icon(
                if (isMuted || volume == 0) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                if (isMuted) "Unmute" else "Mute",
                tint = c.textSecondary,
                modifier = Modifier.size(18.dp),
            )
        }
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
        Spacer(Modifier.weight(1f))
        Row(verticalAlignment = Alignment.CenterVertically) {
            LiveDot(size = 6.dp)
            Spacer(Modifier.width(6.dp))
            Text("LIVE", style = PureTvType.data, color = c.textSecondary)
        }
        Spacer(Modifier.width(14.dp))
        SegmentedControl(
            options = StreamQuality.entries,
            selected = currentQuality,
            label = { it.label },
            onSelect = onQualitySelected,
        )
    }
}

// ── Chat UI ───────────────────────────────────────────────────────────────────

@Composable
private fun ChatMessageList(
    messages: List<ChatMessage>,
    onReply: (ChatMessage) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = PureTvTheme.colors
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // Geometry only DETECTS the bottom; it does NOT gate auto-scroll (that's `following`).
    var following by remember { mutableStateOf(true) }
    val atBottom by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            info.totalItemsCount == 0 || lastVisible >= info.totalItemsCount - 1
        }
    }
    // Auto-scroll on every new message while FOLLOWING (keyed on the newest id, since the
    // buffer caps at 200 so size is constant). Instant + gated on intent, so it keeps up
    // even when a burst/batch arrives faster than an animated scroll could.
    LaunchedEffect(scrollAnchor(messages)) {
        if (messages.isNotEmpty() && following) listState.scrollToItem(messages.lastIndex)
    }
    // A USER scroll away from the bottom pauses following; reaching the bottom resumes it.
    // Our scrolls are instant snaps to the bottom (serialized on the main thread), so they
    // only ever land at-bottom → resume, never falsely pause.
    LaunchedEffect(listState) {
        snapshotFlow { atBottom to listState.isScrollInProgress }.collect { (bottom, scrolling) ->
            following = nextFollowing(following, atBottom = bottom, userScrolling = scrolling)
        }
    }

    Box(modifier) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp),
        ) {
            items(messages, key = { it.id }) { ChatMessageRow(message = it, onReply = onReply) }
        }
        // Twitch parity: paused while scrolled up. Clicking snaps to the bottom and resumes
        // following so the feed keeps going seamlessly.
        if (!following) {
            Surface(
                onClick = {
                    scope.launch { following = true; listState.scrollToItem(messages.lastIndex) }
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

@Composable
private fun ChatInputBar(
    canChat: Boolean,
    emotes: List<PickableEmote>,
    onSend: (String) -> Unit,
    replyingTo: ChatMessage? = null,
    onCancelReply: () -> Unit = {},
    onFocusChanged: (Boolean) -> Unit = {},
    onRequestSignIn: () -> Unit = {},
) {
    val c = PureTvTheme.colors

    // Anonymous (read-only) viewers get a tappable prompt instead of a composer —
    // sending requires a token + the token-owner's login (see StreamViewModel).
    if (!canChat) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
                .border(1.dp, c.hairline, PureTvShape.lg)
                .background(c.surfaceVariant, PureTvShape.lg)
                .clickable { onRequestSignIn() }
                .padding(horizontal = 13.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Sign in to chat", color = c.textMuted, style = MaterialTheme.typography.bodyMedium)
        }
        return
    }

    var value by remember { mutableStateOf(TextFieldValue("")) }
    var pickerOpen by remember { mutableStateOf(false) }
    var fieldFocused by remember { mutableStateOf(false) }

    fun submit() {
        val trimmed = value.text.trim()
        if (trimmed.isNotEmpty()) {
            onSend(trimmed)
            value = TextFieldValue("")
        }
    }

    // Autocomplete suggestions for the word the cursor sits in.
    val (word, _) = wordAtCursor(value.text, value.selection.start)
    val suggestions = matchEmotes(word, emotes)

    fun applyCompletion(code: String) {
        val (t, cur) = completeWord(value.text, value.selection.start, code)
        value = TextFieldValue(t, TextRange(cur))
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        // Reply context bar — shows who we're replying to, with a dismiss button.
        if (replyingTo != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 14.dp, end = 14.dp, top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "replying to @" + replyingTo.displayName,
                    color = c.textMuted,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onCancelReply, modifier = Modifier.size(20.dp)) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = "Cancel reply",
                        tint = c.textSecondary,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }

        // Emote picker sits directly above the composer when open.
        if (pickerOpen) {
            EmotePickerPanel(
                emotes = emotes,
                onPick = { e ->
                    val (t, cur) = insertAtCursor(value.text, value.selection.start, e.code)
                    value = TextFieldValue(t, TextRange(cur))
                },
                onDismiss = { pickerOpen = false },
            )
            Spacer(Modifier.height(8.dp))
        }

        // Autocomplete chip strip — only while typing a recognised partial.
        if (suggestions.isNotEmpty() && fieldFocused) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp)
                    .horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                suggestions.forEach { s ->
                    Row(
                        modifier = Modifier
                            .border(1.dp, c.hairline, PureTvShape.pill)
                            .background(c.surfaceVariant, PureTvShape.pill)
                            .clickable { applyCompletion(s.code) }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        EmoteImage(s.imageUrl, s.code, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(5.dp))
                        Text(s.code, color = c.textSecondary, style = PureTvType.dataSmall)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
                .border(1.dp, c.hairline, PureTvShape.lg)
                .background(c.surfaceVariant, PureTvShape.lg)
                .padding(horizontal = 13.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(modifier = Modifier.weight(1f)) {
                if (value.text.isEmpty()) {
                    Text("Send a message…", color = c.textMuted, style = MaterialTheme.typography.bodyMedium)
                }
                BasicTextField(
                    value = value,
                    onValueChange = { value = it },
                    singleLine = true,
                    textStyle = TextStyle(color = c.textPrimary, fontSize = MaterialTheme.typography.bodyMedium.fontSize),
                    cursorBrush = SolidColor(c.twitchPurple),
                    // Report focus up so the player-hotkey dispatcher stands down while
                    // the user is typing (otherwise f/t/c/space would be eaten).
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged {
                            fieldFocused = it.isFocused
                            onFocusChanged(it.isFocused)
                        }
                        // Enter sends the message; Tab accepts the first emote
                        // suggestion when one is offered (see composerKeyAction).
                        .onPreviewKeyEvent { ev ->
                            if (ev.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                            val isEnter = ev.key == Key.Enter || ev.key == Key.NumPadEnter
                            when (composerKeyAction(isEnter, ev.key == Key.Tab, suggestions.isNotEmpty())) {
                                ComposerKeyAction.SEND -> { submit(); true }
                                ComposerKeyAction.COMPLETE -> { applyCompletion(suggestions.first().code); true }
                                ComposerKeyAction.NONE -> false
                            }
                        },
                )
            }
            IconButton(onClick = { pickerOpen = !pickerOpen }) {
                Icon(
                    Icons.Filled.Mood,
                    "Emotes",
                    tint = if (pickerOpen) c.twitchPurpleLight else c.textSecondary,
                )
            }
            IconButton(onClick = { submit() }) {
                Icon(Icons.AutoMirrored.Filled.Send, "Send", tint = c.twitchPurpleLight)
            }
        }
    }
}
