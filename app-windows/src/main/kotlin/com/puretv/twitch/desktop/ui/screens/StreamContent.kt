package com.puretv.twitch.desktop.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.puretv.twitch.core.adblock.AdBlockStatus
import com.puretv.twitch.core.model.ChatMessage
import com.puretv.twitch.core.model.StreamInfo
import com.puretv.twitch.core.model.StreamQuality
import com.puretv.twitch.core.model.UpscalingMode
import com.puretv.twitch.desktop.data.DesktopSettingsStore
import com.puretv.twitch.desktop.player.DesktopPlayer
import com.puretv.twitch.desktop.player.VlcPlayerView
import com.puretv.twitch.desktop.ui.LocalAppShell
import com.puretv.twitch.desktop.ui.PlayerMode
import com.puretv.twitch.desktop.ui.StreamViewModel
import com.puretv.twitch.desktop.ui.chat.ComposerKeyAction
import com.puretv.twitch.desktop.ui.chat.completeWord
import com.puretv.twitch.desktop.ui.chat.composerKeyAction
import com.puretv.twitch.desktop.ui.chat.insertAtCursor
import com.puretv.twitch.desktop.ui.chat.matchEmotes
import com.puretv.twitch.desktop.ui.chat.nextFollowing
import com.puretv.twitch.desktop.ui.chat.scrollAnchor
import com.puretv.twitch.desktop.ui.chat.wordAtCursor
import com.puretv.twitch.desktop.ui.components.AdBlockPill
import com.puretv.twitch.desktop.ui.components.Avatar
import com.puretv.twitch.desktop.ui.components.ChatMessageRow
import com.puretv.twitch.desktop.ui.components.CountBadge
import com.puretv.twitch.desktop.ui.components.EmoteImage
import com.puretv.twitch.desktop.ui.components.ExpressiveButton
import com.puretv.twitch.desktop.ui.components.ExpressiveButtonSize
import com.puretv.twitch.desktop.ui.components.ExpressiveButtonStyle
import com.puretv.twitch.desktop.ui.components.ExpressiveIconButton
import com.puretv.twitch.desktop.ui.components.ExpressiveIcons
import com.puretv.twitch.desktop.ui.components.ExpressiveSlider
import com.puretv.twitch.desktop.ui.components.LivePill
import com.puretv.twitch.desktop.ui.components.LocalBadgeIndex
import com.puretv.twitch.desktop.ui.components.PlayerSettingsMenu
import com.puretv.twitch.desktop.ui.components.expressiveClickable
import com.puretv.twitch.desktop.ui.components.expressiveSurface
import com.puretv.twitch.desktop.ui.components.formatViewerCount
import com.puretv.twitch.core.emotes.PickableEmote
import com.puretv.twitch.desktop.ui.rememberDesktopViewModel
import com.puretv.twitch.desktop.ui.theme.PureTvMotion
import com.puretv.twitch.desktop.ui.theme.PureTvTheme
import com.puretv.twitch.desktop.ui.theme.PureTvType
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import org.koin.core.Koin
import org.koin.core.parameter.parametersOf
import java.awt.KeyEventDispatcher
import java.awt.KeyboardFocusManager
import java.awt.event.KeyEvent

private val CHAT_WIDTH = 392.dp

/** Chat's own three panels (header, message list, composer) stay rounded even
 *  when the player chrome goes edge-to-edge in theater/fullscreen: chat reads
 *  as a floating card regardless of how immersive the video gets. */
private val CHAT_PANEL_RADIUS = 24.dp

/**
 * Watch screen with stable VLC surface.
 *
 * AWT heavyweight components (VLC's Canvas/SwingPanel) always render above
 * Compose-drawn content, so controls CANNOT be overlaid on top of the player.
 * The layout uses a Column: top-bar → player Box → controls-bar, with
 * AnimatedVisibility sliding them in/out in THEATER/FULLSCREEN mode.
 * Chat lives in a Row column to the right, never overlapping the AWT surface.
 *
 *   F      toggle fullscreen      T  toggle theater
 *   C      toggle chat            Space  play/pause
 *   F3     upscaling stats (mpv)  Esc    exit immersive
 */
@Composable
fun StreamContent(koin: Koin, channelLogin: String, onBack: () -> Unit, onRequestSignIn: () -> Unit = {}) {
    val viewModel = rememberDesktopViewModel(channelLogin) {
        koin.get<StreamViewModel> { parametersOf(channelLogin) }
    }
    val state by viewModel.state.collectAsState()
    val isFollowed by viewModel.isFollowed.collectAsState()
    val vlcPlayer = remember { koin.get<DesktopPlayer>() }
    // The live screen never reads positionMs/durationMs, yet the backend emits a new
    // PlayerStatus on every time tick (VLC ~4Hz, mpv several Hz). Collecting the raw
    // flow here would recompose the ENTIRE screen, including the 200-row chat
    // LazyColumn, on every tick (the dominant playback-stutter cause). Project away
    // the volatile position fields and de-dup, so this screen recomposes only on a
    // change it actually shows (play/buffer/volume/mute/error).
    val playerStatus by remember(vlcPlayer) {
        vlcPlayer.status
            .map { it.copy(positionMs = 0L, durationMs = 0L) }
            .distinctUntilChanged()
    }.collectAsState(initial = vlcPlayer.status.value.copy(positionMs = 0L, durationMs = 0L))
    val settingsStore = remember { koin.get<DesktopSettingsStore>() }
    val appSettings by settingsStore.settings.collectAsState()
    val shell = LocalAppShell.current
    val mode = shell.playerMode
    val isChatOpen = shell.isChatOpen
    val c = PureTvTheme.colors

    // The App shell itself collapses its own outer inset and pane rounding to 0 in
    // THEATER/FULLSCREEN (see App.kt's paneCorner). Mirror that here so the player
    // chrome goes fully edge-to-edge when immersive, and springs back into the
    // "separate rounded cards" look the moment the user returns to DEFAULT.
    val immersive = mode != PlayerMode.DEFAULT
    val groundPad by animateDpAsState(if (immersive) 0.dp else 8.dp, tween(PureTvMotion.Medium), label = "groundPad")
    val panelRadius by animateDpAsState(if (immersive) 0.dp else 24.dp, tween(PureTvMotion.Medium), label = "panelRadius")

    val chatWidth by animateDpAsState(
        targetValue = if (isChatOpen) CHAT_WIDTH else 0.dp,
        animationSpec = tween(PureTvMotion.Medium),
        label = "chatWidth",
    )
    // The breathing room between the player column and the chat card. Tied to
    // isChatOpen (not the immersive ground padding) so chat keeps its own gap even
    // when the player chrome has gone edge-to-edge.
    val chatGap by animateDpAsState(
        targetValue = if (isChatOpen) 8.dp else 0.dp,
        animationSpec = tween(PureTvMotion.Medium),
        label = "chatGap",
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
    // never die. We skip it while the chat input is focused so typing, including
    // spaces and the letters f/t/c, still reaches the chat box.
    var chatInputFocused by remember { mutableStateOf(false) }
    val latestMode = rememberUpdatedState(mode)
    val latestChatFocused = rememberUpdatedState(chatInputFocused)
    val latestUpscaling = rememberUpdatedState(appSettings.upscalingMode)
    // F3 toggles the mpv upscaling stats overlay. It's drawn by mpv's own OSD (the
    // heavyweight video Canvas paints above Compose, so a Compose overlay can't sit
    // on the video). No-op on the VLC backend.
    var showStats by remember { mutableStateOf(false) }
    // In-player Playback menu (gear): resolution / scaling / engine.
    var settingsMenuOpen by remember { mutableStateOf(false) }
    DisposableEffect(Unit) {
        val dispatcher = KeyEventDispatcher { e ->
            if (latestChatFocused.value) return@KeyEventDispatcher false
            // Hold-to-compare (X): preview Off while held, restore the saved mode on
            // release: instant live A/B of the upscaler. Handled before the
            // KEY_PRESSED gate so we also see KEY_RELEASED. (Windows AWT doesn't
            // interleave KEY_RELEASED with key auto-repeat, so a held X stays Off
            // without flicker; preview does NOT persist, so the saved mode is intact.)
            if (e.keyCode == KeyEvent.VK_X) {
                when (e.id) {
                    KeyEvent.KEY_PRESSED -> viewModel.previewUpscaling(UpscalingMode.OFF)
                    KeyEvent.KEY_RELEASED -> viewModel.previewUpscaling(latestUpscaling.value)
                    else -> return@KeyEventDispatcher false
                }
                return@KeyEventDispatcher true
            }
            if (e.id != KeyEvent.KEY_PRESSED) return@KeyEventDispatcher false
            val m = latestMode.value
            when (e.keyCode) {
                KeyEvent.VK_F -> { shell.setPlayerMode(if (m == PlayerMode.FULLSCREEN) PlayerMode.DEFAULT else PlayerMode.FULLSCREEN); true }
                KeyEvent.VK_T -> { shell.setPlayerMode(if (m == PlayerMode.THEATER) PlayerMode.DEFAULT else PlayerMode.THEATER); true }
                KeyEvent.VK_C -> { shell.toggleChat(); true }
                KeyEvent.VK_SPACE -> { viewModel.togglePlayPause(); true }
                // Esc only acts when immersive, so it doesn't swallow a stray Esc
                // elsewhere; in DEFAULT mode it passes through untouched.
                KeyEvent.VK_ESCAPE -> if (m != PlayerMode.DEFAULT) { shell.exitImmersive(); true } else false
                KeyEvent.VK_F3 -> { showStats = !showStats; true }
                else -> false
            }
        }
        val kfm = KeyboardFocusManager.getCurrentKeyboardFocusManager()
        kfm.addKeyEventDispatcher(dispatcher)
        onDispose { kfm.removeKeyEventDispatcher(dispatcher) }
    }

    // Drive the mpv stats overlay: while toggled on, re-push every second so mpv's
    // OSD text (2s duration) never lapses; on toggle-off, clear it once. Runs on the
    // Compose/EDT dispatcher, the same thread as the player's surface lifecycle.
    LaunchedEffect(showStats) {
        if (!showStats) {
            vlcPlayer.renderStatsOverlay(false)
            return@LaunchedEffect
        }
        while (true) {
            // Guard each tick: a thrown renderStatsOverlay would otherwise
            // silently kill the loop and freeze the overlay (mirrors the VOD
            // progress loop's per-iteration guard).
            runCatching { vlcPlayer.renderStatsOverlay(true) }
            delay(1000)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(c.surfaceLowest)
            .padding(groundPad)
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
            Column(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(groundPad),
            ) {
                // Top bar: always in DEFAULT, slides up when idle in THEATER/FULLSCREEN
                AnimatedVisibility(
                    visible = controlsVisible || mode == PlayerMode.DEFAULT,
                    enter = slideInVertically { -it } + fadeIn(),
                    exit = slideOutVertically { -it } + fadeOut(),
                ) {
                    TopBar(
                        mode = mode,
                        channelName = state.channel?.displayName ?: channelLogin,
                        avatarUrl = state.channel?.profileImageUrl,
                        streamInfo = state.streamInfo,
                        adBlockStatus = state.adBlockStatus,
                        isFollowed = isFollowed,
                        canFollow = state.channel != null,
                        onToggleFollow = viewModel::toggleFollow,
                        onBack = onBack,
                        radius = panelRadius,
                    )
                }

                // Video panel + playback settings menu, grouped as ONE child of the
                // outer spacedBy so the gap above/below this pair stays a single
                // groundPad regardless of whether the settings menu is mounted.
                // Nesting it here (rather than as a sibling of top/controls bars)
                // avoids a phantom extra gap around its zero-height collapsed state.
                Column(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    // Player surface: never unmounted (see VlcPlayerView docs). The
                    // heavyweight AWT Canvas paints above Compose and ignores any
                    // clip Compose applies, so this box stays square-cornered: a
                    // rounded clip here would be cosmetic on the Compose layer only
                    // and invisible once real video frames cover it.
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .background(Color.Black),
                        contentAlignment = Alignment.Center,
                    ) {
                        when {
                            // A stream-level fatal error (e.g. the local proxy port is in use)
                            // takes priority: the player never even got a URL, so show the
                            // explanation instead of an endless "Loading…".
                            state.fatalError != null -> Text(
                                state.fatalError!!,
                                color = c.onSurfaceVariant,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.padding(24.dp),
                            )
                            // Surface a player error whenever nothing is actively playing.
                            // Covers an unavailable engine (e.g. "switch back to VLC"), an
                            // mpv init failure, and a failed stream start (bad URL). The
                            // error self-clears on recovery (playing/file-loaded sets error=null).
                            playerStatus.error != null && !playerStatus.isPlaying && !playerStatus.isBuffering -> Text(
                                playerStatus.error!!,
                                color = c.onSurfaceVariant,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.padding(24.dp),
                            )
                            state.playableUrl != null -> VlcPlayerView(
                                vlcPlayer = vlcPlayer,
                                modifier = Modifier.fillMaxSize(),
                                // The heavyweight video surface eats mouse events; bridge
                                // them back so moving the mouse reveals the controls,
                                // including in fullscreen, where the surface covers all.
                                onUserActivity = { resetControls() },
                            )
                            else -> Text(
                                if (state.isLoading) "Loading stream…" else "This channel is offline.",
                                color = c.onSurfaceVariant,
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                    }

                    // Playback menu: lives in the Column (NOT over the video; the
                    // heavyweight Canvas paints above Compose), between video and controls.
                    // Opening it pushes the video up, the same way the controls bar does.
                    AnimatedVisibility(
                        visible = settingsMenuOpen,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut(),
                    ) {
                        PlayerSettingsMenu(
                            currentQuality = state.currentQuality,
                            onQualitySelected = viewModel::setQuality,
                            upscalingMode = appSettings.upscalingMode,
                            onUpscalingSelected = viewModel::setUpscaling,
                            scalingEnabled = vlcPlayer.supportsUpscaling,
                            backend = appSettings.playbackBackend,
                            onBackendSelected = viewModel::setPlaybackBackend,
                        )
                    }
                }

                // Controls bar: always in DEFAULT, slides down when idle in THEATER/FULLSCREEN
                AnimatedVisibility(
                    visible = controlsVisible || mode == PlayerMode.DEFAULT,
                    enter = slideInVertically { it } + fadeIn(),
                    exit = slideOutVertically { it } + fadeOut(),
                ) {
                    PlaybackControls(
                        isPlaying = playerStatus.isPlaying,
                        volume = playerStatus.volume,
                        isMuted = playerStatus.isMuted,
                        settingsOpen = settingsMenuOpen,
                        mode = mode,
                        isChatOpen = isChatOpen,
                        onTogglePlayPause = viewModel::togglePlayPause,
                        onVolumeChange = viewModel::setVolume,
                        onToggleMute = viewModel::toggleMute,
                        onToggleSettings = { settingsMenuOpen = !settingsMenuOpen },
                        onToggleChat = { shell.toggleChat() },
                        onToggleTheater = { shell.setPlayerMode(if (mode == PlayerMode.THEATER) PlayerMode.DEFAULT else PlayerMode.THEATER) },
                        onToggleFullscreen = { shell.setPlayerMode(if (mode == PlayerMode.FULLSCREEN) PlayerMode.DEFAULT else PlayerMode.FULLSCREEN) },
                        radius = panelRadius,
                    )
                }
            }

            Spacer(Modifier.width(chatGap))

            // ── Chat panel ─────────────────────────────────────────────────────
            // Width animates 0↔392dp. clipToBounds() ensures content clips clean.
            Box(
                modifier = Modifier
                    .width(chatWidth)
                    .fillMaxHeight()
                    .clipToBounds(),
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    var chatTab by remember { mutableStateOf(ChatTab.Chat) }

                    ChatHeader(
                        selected = chatTab,
                        mentionCount = state.mentionMessages.size,
                        onSelectTab = { chatTab = it },
                        onClose = { shell.toggleChat() },
                    )

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(CHAT_PANEL_RADIUS))
                            .background(c.surfaceContainer),
                    ) {
                        CompositionLocalProvider(LocalBadgeIndex provides state.badges) {
                            when (chatTab) {
                                ChatTab.Chat -> ChatMessageList(
                                    messages = state.chatMessages,
                                    onReply = viewModel::startReply,
                                    modifier = Modifier.fillMaxSize(),
                                )
                                ChatTab.Mentions ->
                                    if (state.mentionMessages.isEmpty()) {
                                        MentionsEmptyState(Modifier.fillMaxSize())
                                    } else {
                                        ChatMessageList(
                                            messages = state.mentionMessages,
                                            onReply = viewModel::startReply,
                                            modifier = Modifier.fillMaxSize(),
                                        )
                                    }
                            }
                        }
                    }

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

// ── Top bar ────────────────────────────────────────────────────────────────────

@Composable
private fun TopBar(
    mode: PlayerMode,
    channelName: String,
    avatarUrl: String?,
    streamInfo: StreamInfo?,
    adBlockStatus: AdBlockStatus,
    isFollowed: Boolean,
    canFollow: Boolean,
    onToggleFollow: () -> Unit,
    onBack: () -> Unit,
    radius: Dp,
) {
    val c = PureTvTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
            .clip(RoundedCornerShape(radius))
            .background(c.surfaceContainer)
            .padding(start = 8.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (mode == PlayerMode.DEFAULT) {
            ExpressiveIconButton(
                icon = ExpressiveIcons.Back,
                contentDescription = "Back",
                onClick = onBack,
                boxSize = 48.dp,
                iconSize = 24.dp,
            )
        }
        Avatar(displayName = channelName, imageUrl = avatarUrl, size = 44)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                channelName,
                style = MaterialTheme.typography.titleLarge,
                color = c.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (streamInfo != null) {
                Row {
                    val game = streamInfo.gameName.takeIf { it.isNotBlank() } ?: "No category"
                    Text("$game · ", style = MaterialTheme.typography.bodyMedium, color = c.onSurfaceVariant, maxLines = 1)
                    Text("${formatViewerCount(streamInfo.viewerCount)} viewers", style = PureTvType.data, color = c.onSurfaceVariant, maxLines = 1)
                }
            }
        }
        AdBlockPill(adBlockStatus)
        ExpressiveButton(
            text = if (isFollowed) "Following" else "Follow",
            onClick = onToggleFollow,
            style = ExpressiveButtonStyle.Tonal,
            size = ExpressiveButtonSize.Medium,
            icon = if (isFollowed) ExpressiveIcons.Check else ExpressiveIcons.Add,
            enabled = canFollow,
        )
    }
}

// ── Playback controls ─────────────────────────────────────────────────────────

@Composable
private fun PlaybackControls(
    isPlaying: Boolean,
    volume: Int,
    isMuted: Boolean,
    settingsOpen: Boolean,
    mode: PlayerMode,
    isChatOpen: Boolean,
    onTogglePlayPause: () -> Unit,
    onVolumeChange: (Int) -> Unit,
    onToggleMute: () -> Unit,
    onToggleSettings: () -> Unit,
    onToggleChat: () -> Unit,
    onToggleTheater: () -> Unit,
    onToggleFullscreen: () -> Unit,
    radius: Dp,
) {
    val c = PureTvTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(80.dp)
            .clip(RoundedCornerShape(radius))
            .background(c.surfaceContainer)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        PlayPauseButton(isPlaying = isPlaying, onClick = onTogglePlayPause)
        VolumeButton(isMuted = isMuted || volume == 0, onClick = onToggleMute)
        ExpressiveSlider(
            value = volume / 100f,
            onValueChange = { onVolumeChange((it * 100f).roundToInt()) },
            modifier = Modifier.width(150.dp).padding(horizontal = 10.dp),
        )
        Spacer(Modifier.weight(1f))
        LivePill()
        Spacer(Modifier.width(8.dp))
        ConnectedControlsGroup(
            settingsOpen = settingsOpen,
            isChatOpen = isChatOpen,
            mode = mode,
            onToggleSettings = onToggleSettings,
            onToggleChat = onToggleChat,
            onToggleTheater = onToggleTheater,
            onToggleFullscreen = onToggleFullscreen,
        )
    }
}

/** Filled primary pill; morphs 28→14dp radius on hover and narrows 64→56dp while pressed. */
@Composable
private fun PlayPauseButton(isPlaying: Boolean, onClick: () -> Unit) {
    val c = PureTvTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val width by animateDpAsState(if (pressed) 56.dp else 64.dp, PureTvMotion.MorphSpring, label = "playPauseWidth")
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .width(width)
            .height(56.dp)
            .expressiveClickable(
                interaction = interaction,
                onClick = onClick,
                restRadius = 28.dp,
                hoverRadius = 14.dp,
                color = c.primary,
            ),
    ) {
        Icon(
            if (isPlaying) ExpressiveIcons.Pause else ExpressiveIcons.Play,
            contentDescription = if (isPlaying) "Pause" else "Play",
            tint = c.onPrimary,
            modifier = Modifier.size(26.dp),
        )
    }
}

@Composable
private fun VolumeButton(isMuted: Boolean, onClick: () -> Unit) {
    val c = PureTvTheme.colors
    val interaction = remember { MutableInteractionSource() }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(width = 52.dp, height = 56.dp)
            .expressiveClickable(
                interaction = interaction,
                onClick = onClick,
                restRadius = 28.dp,
                hoverRadius = 14.dp,
                color = c.surfaceHigh,
                hoverColor = c.surfaceHighest,
            ),
    ) {
        Icon(
            if (isMuted) ExpressiveIcons.VolumeOff else ExpressiveIcons.VolumeUp,
            contentDescription = if (isMuted) "Unmute" else "Mute",
            tint = c.onSurface,
            modifier = Modifier.size(24.dp),
        )
    }
}

/** The welded quality/settings/chat/theater/fullscreen cluster: one 28dp-radius bar,
 *  1dp outlineVariant dividers between square segments. The segments themselves
 *  never round; only the group's outer silhouette does. */
@Composable
private fun ConnectedControlsGroup(
    settingsOpen: Boolean,
    isChatOpen: Boolean,
    mode: PlayerMode,
    onToggleSettings: () -> Unit,
    onToggleChat: () -> Unit,
    onToggleTheater: () -> Unit,
    onToggleFullscreen: () -> Unit,
) {
    val c = PureTvTheme.colors
    Row(
        modifier = Modifier
            .height(56.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(c.surfaceHigh),
    ) {
        // No dedicated quick-quality flow exists in the ViewModel: resolution lives
        // inside the same combined playback menu the gear opens, so this is a second
        // entry point into that one menu rather than a distinct feature.
        ControlsGroupButton(ExpressiveIcons.Quality, "Quality", onToggleSettings)
        GroupDivider()
        ControlsGroupButton(ExpressiveIcons.Settings, "Playback settings", onToggleSettings, tint = if (settingsOpen) c.primary else null)
        GroupDivider()
        ControlsGroupButton(ExpressiveIcons.Chat, "Toggle chat", onToggleChat, tint = if (isChatOpen) c.primary else null)
        GroupDivider()
        ControlsGroupButton(ExpressiveIcons.AspectRatio, "Theater mode", onToggleTheater, tint = if (mode == PlayerMode.THEATER) c.primary else null)
        GroupDivider()
        ControlsGroupButton(
            icon = if (mode == PlayerMode.FULLSCREEN) ExpressiveIcons.FullscreenExit else ExpressiveIcons.Fullscreen,
            contentDescription = "Fullscreen",
            onClick = onToggleFullscreen,
        )
    }
}

@Composable
private fun GroupDivider() {
    Box(Modifier.width(1.dp).fillMaxHeight().background(PureTvTheme.colors.outlineVariant))
}

@Composable
private fun ControlsGroupButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    tint: Color? = null,
) {
    val c = PureTvTheme.colors
    val interaction = remember { MutableInteractionSource() }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(56.dp)
            .expressiveClickable(
                interaction = interaction,
                onClick = onClick,
                restRadius = 0.dp,
                hoverRadius = 0.dp,
                color = Color.Transparent,
                hoverColor = c.surfaceHighest,
            ),
    ) {
        Icon(icon, contentDescription = contentDescription, tint = tint ?: c.onSurface, modifier = Modifier.size(24.dp))
    }
}

// ── Chat UI ───────────────────────────────────────────────────────────────────

/** The two views the chat panel can show: the live feed, or just messages that @-mention you. */
private enum class ChatTab(val label: String) { Chat("Chat"), Mentions("Mentions") }

/** 72dp header: the Chat/Mentions toggle plus a circular close button. */
@Composable
private fun ChatHeader(
    selected: ChatTab,
    mentionCount: Int,
    onSelectTab: (ChatTab) -> Unit,
    onClose: () -> Unit,
) {
    val c = PureTvTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
            .clip(RoundedCornerShape(CHAT_PANEL_RADIUS))
            .background(c.surfaceContainer)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ChatTabToggle(selected = selected, mentionCount = mentionCount, onSelect = onSelectTab, modifier = Modifier.weight(1f))
        ExpressiveIconButton(
            icon = ExpressiveIcons.Close,
            contentDescription = "Close chat",
            onClick = onClose,
            boxSize = 48.dp,
            iconSize = 22.dp,
        )
    }
}

/**
 * A pill-track segmented toggle, hand-built rather than the shared [com.puretv.twitch.desktop.ui.components.SegmentedToggle]
 * because the Mentions segment needs a trailing [CountBadge] and that component's
 * `label: (T) -> String` slot has no room for a second composable.
 */
@Composable
private fun ChatTabToggle(
    selected: ChatTab,
    mentionCount: Int,
    onSelect: (ChatTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = PureTvTheme.colors
    val shapes = PureTvTheme.shapes
    Row(
        modifier
            .height(48.dp)
            .clip(shapes.pillShape)
            .background(c.surfaceHigh)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        ChatTab.entries.forEach { tab ->
            val isSelected = tab == selected
            val interaction = remember(tab) { MutableInteractionSource() }
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .expressiveClickable(
                        interaction = interaction,
                        onClick = { onSelect(tab) },
                        restRadius = shapes.pill,
                        hoverRadius = shapes.pillMorph,
                        color = if (isSelected) c.secondaryContainer else Color.Transparent,
                        hoverColor = if (isSelected) c.secondaryContainer else c.surfaceHighest,
                    ),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        tab.label,
                        color = if (isSelected) c.onSecondaryContainer else c.onSurfaceVariant,
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                    )
                    if (tab == ChatTab.Mentions && mentionCount > 0) CountBadge(mentionCount.toString())
                }
            }
        }
    }
}

/** Shown in the Mentions tab when nobody has pinged you yet. */
@Composable
private fun MentionsEmptyState(modifier: Modifier = Modifier) {
    val c = PureTvTheme.colors
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(24.dp),
        ) {
            Text(
                "No mentions yet",
                style = MaterialTheme.typography.titleSmall,
                color = c.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Messages that @ your name will show up here.",
                style = MaterialTheme.typography.bodySmall,
                color = c.outlineVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun ChatMessageList(
    messages: List<ChatMessage>,
    onReply: (ChatMessage) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = PureTvTheme.colors
    val shapes = PureTvTheme.shapes
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
            modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            contentPadding = PaddingValues(vertical = 14.dp),
        ) {
            items(messages, key = { it.id }) { ChatMessageRow(message = it, onReply = onReply) }
        }
        // Twitch parity: paused while scrolled up. Clicking snaps to the bottom and resumes
        // following so the feed keeps going seamlessly.
        if (!following) {
            val pillInteraction = remember { MutableInteractionSource() }
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 12.dp)
                    .expressiveClickable(
                        interaction = pillInteraction,
                        onClick = {
                            // Guard scrollToItem(-1) when the message list is momentarily empty (audit U3).
                            scope.launch { following = true; if (messages.isNotEmpty()) listState.scrollToItem(messages.lastIndex) }
                        },
                        restRadius = shapes.pill,
                        hoverRadius = shapes.pillMorph,
                        color = c.primary,
                    ),
            ) {
                Text(
                    "Chat paused due to scroll",
                    color = c.onPrimary,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
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

    // Anonymous (read-only) viewers get a tappable prompt instead of a composer.
    // Sending requires a token + the token-owner's login (see StreamViewModel).
    if (!canChat) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(CHAT_PANEL_RADIUS))
                .background(c.surfaceContainer)
                .padding(12.dp),
        ) {
            val interaction = remember { MutableInteractionSource() }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .expressiveClickable(
                        interaction = interaction,
                        onClick = onRequestSignIn,
                        restRadius = 28.dp,
                        hoverRadius = 16.dp,
                        color = c.surfaceHigh,
                        hoverColor = c.surfaceHighest,
                    )
                    .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Sign in to chat", color = c.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
            }
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
        // Reply context bar, shows who we're replying to, with a dismiss button.
        if (replyingTo != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 14.dp, end = 14.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "replying to @" + replyingTo.displayName,
                    color = c.onSurfaceVariant,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                ExpressiveIconButton(
                    icon = ExpressiveIcons.Close,
                    contentDescription = "Cancel reply",
                    onClick = onCancelReply,
                    boxSize = 28.dp,
                    iconSize = 16.dp,
                )
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

        // Autocomplete chip strip, only while typing a recognised partial.
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
                    val chipInteraction = remember(s.code) { MutableInteractionSource() }
                    Row(
                        modifier = Modifier
                            .expressiveClickable(
                                interaction = chipInteraction,
                                onClick = { applyCompletion(s.code) },
                                restRadius = PureTvTheme.shapes.pill,
                                hoverRadius = PureTvTheme.shapes.pillMorph,
                                color = c.surfaceHigh,
                                hoverColor = c.surfaceHighest,
                            )
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        EmoteImage(s.imageUrl, s.code, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(5.dp))
                        Text(s.code, color = c.onSurfaceVariant, style = PureTvType.dataSmall)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        // Composer panel, the mockup's surfaceContainer card holding the one input row.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(CHAT_PANEL_RADIUS))
                .background(c.surfaceContainer)
                .padding(12.dp),
        ) {
            val fieldInteraction = remember { MutableInteractionSource() }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .expressiveSurface(
                        interaction = fieldInteraction,
                        restRadius = 28.dp,
                        hoverRadius = 16.dp,
                        color = c.surfaceHigh,
                    )
                    .hoverable(fieldInteraction)
                    .padding(start = 20.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    if (value.text.isEmpty()) {
                        Text("Send a message…", color = c.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                    }
                    BasicTextField(
                        value = value,
                        onValueChange = { value = it },
                        singleLine = true,
                        textStyle = TextStyle(color = c.onSurface, fontSize = MaterialTheme.typography.bodyMedium.fontSize),
                        cursorBrush = SolidColor(c.primary),
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
                ExpressiveIconButton(
                    icon = ExpressiveIcons.Emote,
                    contentDescription = "Emotes",
                    onClick = { pickerOpen = !pickerOpen },
                    boxSize = 44.dp,
                    iconSize = 22.dp,
                    tint = if (pickerOpen) c.primary else c.onSurfaceVariant,
                )
                ExpressiveIconButton(
                    icon = ExpressiveIcons.Send,
                    contentDescription = "Send",
                    onClick = { submit() },
                    style = ExpressiveButtonStyle.Filled,
                    boxSize = 44.dp,
                    iconSize = 22.dp,
                )
            }
        }
    }
}
