package com.puretv.twitch.desktop.ui.screens

import com.puretv.twitch.desktop.platform.AppRestart
import com.puretv.twitch.desktop.player.DesktopPlayerProvider
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.ui.platform.LocalFocusManager
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
import com.puretv.twitch.core.chat.ChatRoomModes
import com.puretv.twitch.core.chat.ComposerSuggestion
import com.puretv.twitch.core.chat.completeWord
import com.puretv.twitch.core.chat.labels
import com.puretv.twitch.core.chat.composerSuggestions
import com.puretv.twitch.core.chat.insertAtCursor
import com.puretv.twitch.core.chat.recentChatters
import com.puretv.twitch.core.chat.wordAtCursor
import com.puretv.twitch.core.model.ChatMessage
import com.puretv.twitch.core.model.ChatTextSize
import com.puretv.twitch.desktop.ui.chat.ChatAppearance
import com.puretv.twitch.desktop.ui.chat.ChatScale
import com.puretv.twitch.desktop.ui.chat.LocalChatAppearance
import com.puretv.twitch.core.model.StreamInfo
import com.puretv.twitch.core.model.StreamQuality
import com.puretv.twitch.core.model.UpscalingMode
import com.puretv.twitch.core.notify.LiveFollowsMonitor
import com.puretv.twitch.core.playback.ChannelFlip
import com.puretv.twitch.core.playback.FlipDirection
import com.puretv.twitch.core.playback.FlipOrigin
import com.puretv.twitch.core.playback.PendingFlip
import com.puretv.twitch.core.playback.PrewarmGate
import com.puretv.twitch.core.stream.PlayableStreamCache
import com.puretv.twitch.desktop.data.DesktopSettingsStore
import com.puretv.twitch.desktop.player.DesktopPlayer
import com.puretv.twitch.desktop.player.VlcPlayerView
import com.puretv.twitch.desktop.ui.DoubleClickDetector
import com.puretv.twitch.desktop.ui.LocalAppShell
import com.puretv.twitch.desktop.ui.MiniControlStrip
import com.puretv.twitch.desktop.ui.PlayerMode
import com.puretv.twitch.desktop.ui.PlayerShortcuts
import com.puretv.twitch.desktop.ui.AppWindows
import com.puretv.twitch.desktop.ui.WindowDestination
import com.puretv.twitch.desktop.ui.openInNewWindow
import com.puretv.twitch.desktop.ui.LocalAwtWindow
import com.puretv.twitch.desktop.ui.NativePointerActivity
import com.puretv.twitch.desktop.ui.LocalWindowPlayback
import com.puretv.twitch.desktop.player.WindowPlayback
import com.puretv.twitch.desktop.ui.ShortcutAction
import com.puretv.twitch.desktop.ui.SleepSession
import com.puretv.twitch.desktop.ui.StreamViewModel
import com.puretv.twitch.desktop.ui.chat.ComposerKeyAction
import com.puretv.twitch.desktop.ui.chat.composerKeyAction
import com.puretv.twitch.desktop.ui.chat.nextFollowing
import com.puretv.twitch.desktop.ui.chat.scrollAnchor
import com.puretv.twitch.desktop.ui.components.LocalStreamPrewarm
import com.puretv.twitch.desktop.ui.components.PlayerGate
import com.puretv.twitch.desktop.ui.components.AdBlockPill
import com.puretv.twitch.desktop.ui.components.RtxMenuState
import com.puretv.twitch.desktop.ui.components.Avatar
import com.puretv.twitch.desktop.ui.components.ChatMessageRow
import com.puretv.twitch.desktop.ui.components.CountBadge
import com.puretv.twitch.desktop.ui.components.EmoteImage
import com.puretv.twitch.desktop.ui.components.ExpressiveButton
import com.puretv.twitch.desktop.ui.components.ExpressiveButtonSize
import com.puretv.twitch.desktop.ui.components.ExpressiveButtonStyle
import com.puretv.twitch.desktop.ui.components.ExpressiveIconButton
import com.puretv.twitch.desktop.ui.components.AlertBell
import com.puretv.twitch.desktop.ui.components.rememberAlertBell
import com.puretv.twitch.desktop.ui.components.ExpressiveIcons
import com.puretv.twitch.desktop.ui.components.ExpressiveSlider
import com.puretv.twitch.desktop.ui.components.LivePill
import com.puretv.twitch.desktop.ui.components.LocalBadgeIndex
import com.puretv.twitch.desktop.ui.components.PlayerNoticeStrip
import com.puretv.twitch.desktop.ui.components.PlayerSettingsMenu
import com.puretv.twitch.desktop.ui.components.ShortcutsSheet
import com.puretv.twitch.desktop.ui.components.expressiveClickable
import com.puretv.twitch.desktop.ui.components.expressiveSurface
import com.puretv.twitch.desktop.ui.components.formatViewerCount
import com.puretv.twitch.core.emotes.PickableEmote
import com.puretv.twitch.core.emotes.SevenTvPaint
import com.puretv.twitch.desktop.ui.rememberDesktopViewModel
import com.puretv.twitch.desktop.ui.theme.PureTvMotion
import com.puretv.twitch.desktop.ui.theme.PureTvTheme
import com.puretv.twitch.desktop.ui.theme.PureTvType
import com.puretv.twitch.desktop.ui.theme.sidePanelReveal
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
 *   PageUp/PageDown  next/previous channel (see [onFlip])
 *   Up/Down  volume                M  mute      Z  sleep timer      ?  all shortcuts
 *   I      mini player (the window shrinks into a corner; Esc or the expand button returns)
 * The keys themselves are decided by [PlayerShortcuts].
 *
 * [onFlip] asks the app to open another channel's stream. Flipping rebuilds this
 * screen's view model for the new channel while this composable (and the video
 * surface) stays in place.
 */
@Composable
fun StreamContent(
    koin: Koin,
    channelLogin: String,
    onBack: () -> Unit,
    onRequestSignIn: () -> Unit = {},
    onFlip: (String) -> Unit = {},
    onOpenMultiView: () -> Unit = {},
    // Owned by App, so the timer carries on into multi-stream.
    sleep: SleepSession = remember { SleepSession() },
) {
    val windowPlayback = LocalWindowPlayback.current ?: remember { koin.get<WindowPlayback>() }
    val prewarm = LocalStreamPrewarm.current
    // Never wait for the player on the UI thread (see PlayerGate). While it starts, the
    // stream itself is resolved (token and playlist), so the two do not run one after the other.
    PlayerGate(windowPlayback, onBack, whileStarting = { prewarm?.invoke(channelLogin) }) {
        StreamScreen(koin, channelLogin, windowPlayback, onBack, onRequestSignIn, onFlip, onOpenMultiView, sleep)
    }
}

@Composable
private fun StreamScreen(
    koin: Koin,
    channelLogin: String,
    windowPlayback: WindowPlayback,
    onBack: () -> Unit,
    onRequestSignIn: () -> Unit,
    onFlip: (String) -> Unit,
    onOpenMultiView: () -> Unit,
    sleep: SleepSession,
) {
    val viewModel = rememberDesktopViewModel(channelLogin) {
        koin.get<StreamViewModel> { parametersOf(channelLogin, windowPlayback) }
    }
    val state by viewModel.state.collectAsState()
    val isFollowed by viewModel.isFollowed.collectAsState()
    val vlcPlayer = remember(windowPlayback) { windowPlayback.player }
    // Once a stream has had a video surface, a flip to the next channel keeps it mounted.
    var keepSurface by remember { mutableStateOf(false) }
    if (state.playableUrl != null && !keepSurface) keepSurface = true
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
    val playerProvider = remember { koin.get<DesktopPlayerProvider>() }
    val appSettings by settingsStore.settings.collectAsState()
    val rtxStatus by vlcPlayer.rtxStatus.collectAsState()
    val bell = rememberAlertBell(settingsStore, channelLogin, state.channel?.displayName ?: channelLogin)
    val shell = LocalAppShell.current
    val mode = shell.playerMode
    val mini = mode == PlayerMode.MINI
    val isChatOpen = shell.isChatOpen && !mini
    val c = PureTvTheme.colors

    // The App shell itself collapses its own outer inset and pane rounding to 0 in
    // THEATER/FULLSCREEN (see App.kt's paneCorner). Mirror that here so the player
    // chrome goes fully edge-to-edge when immersive, and back into the "separate
    // rounded cards" look the moment the user returns to DEFAULT. In one step, like
    // the shell: easing the padding would resize the video surface on every frame.
    val immersive = mode != PlayerMode.DEFAULT
    val groundPad = if (immersive) 0.dp else 8.dp
    val panelRadius = if (immersive) 0.dp else 24.dp

    // Chat takes or gives back its width in one step (see PureTvMotion.sidePanelSize),
    // so the video beside it resizes once per toggle; the card itself slides and fades.
    val chatWidth by animateDpAsState(
        targetValue = if (isChatOpen) CHAT_WIDTH else 0.dp,
        animationSpec = PureTvMotion.sidePanelSize(isChatOpen, instant = mini),
        label = "chatWidth",
    )
    val chatReveal by animateFloatAsState(
        targetValue = if (isChatOpen) 1f else 0f,
        animationSpec = PureTvMotion.sidePanelReveal(isChatOpen, instant = mini),
        label = "chatReveal",
    )
    // The breathing room between the player column and the chat card. Tied to
    // isChatOpen (not the immersive ground padding) so chat keeps its own gap even
    // when the player chrome has gone edge-to-edge.
    val chatGap by animateDpAsState(
        targetValue = if (isChatOpen) 8.dp else 0.dp,
        animationSpec = PureTvMotion.sidePanelSize(isChatOpen, instant = mini),
        label = "chatGap",
    )

    // Controls visibility: always shown in DEFAULT, auto-hides in THEATER/FULLSCREEN
    var controlsVisible by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()
    var hideJob by remember { mutableStateOf<Job?>(null) }
    val currentMode by rememberUpdatedState(mode)

    // Sleep timer (Z, or the Playback menu). Mouse moves and key presses are the "still
    // here" signal: in the last minute they add 15 minutes. At the end the stream is left.
    val latestOnBack by rememberUpdatedState(onBack)

    fun resetControls() {
        controlsVisible = true
        hideJob?.cancel()
        if (currentMode != PlayerMode.DEFAULT) {
            hideJob = scope.launch {
                delay(PureTvMotion.ControlsAutoHideMs)
                controlsVisible = false
            }
        }
        sleep.onActivity()
    }
    LaunchedEffect(mode) { resetControls() }
    // The mini player's video is covered by a window of its own, so it reports the pointer here.
    DisposableEffect(shell) {
        shell.miniActivity = { resetControls() }
        onDispose { shell.miniActivity = {} }
    }

    LaunchedEffect(sleep.ui.running) {
        while (sleep.ui.running) {
            delay(1_000)
            val next = sleep.tick()
            if (next.expired) {
                shell.exitImmersive()
                latestOnBack()
            }
        }
    }

    // Channel flipping: PageUp/PageDown walk a ring of channels and only the settled
    // target is opened (see PendingFlip). The ring is the viewer's live follows when
    // they are signed in and watching one of them, otherwise the row the stream was
    // opened from. A channel is prewarmed only on intent (see PrewarmGate).
    val flipOrigin = remember { koin.get<FlipOrigin>() }
    val liveFollows = remember { koin.get<LiveFollowsMonitor>() }
    val streamCache = remember { koin.get<PlayableStreamCache>() }
    val pendingFlip = remember { PendingFlip() }
    val prewarmGate = remember { PrewarmGate(System::currentTimeMillis) }
    var flipTarget by remember { mutableStateOf<String?>(null) }
    var flipNote by remember { mutableStateOf<String?>(null) }
    var flipJob by remember { mutableStateOf<Job?>(null) }
    val latestChannel by rememberUpdatedState(channelLogin)
    val latestOnFlip by rememberUpdatedState(onFlip)
    // The new channel is on screen: the banner has done its job.
    LaunchedEffect(channelLogin) { flipTarget = null }

    // The alert timer polls only the channels with a bell, so the live follows come
    // from the followed feed (shared for 30 seconds with the rail) when the viewer
    // reaches for a flip; the ring uses the previous answer and the next press this one.
    fun refreshFollows() {
        val userId = settingsStore.sessionUserId
        if (!settingsStore.isLoggedIn || userId.isNullOrBlank()) return
        scope.launch { runCatching { liveFollows.followedLive(userId) } }
    }
    LaunchedEffect(channelLogin) { refreshFollows() }

    fun flip(direction: FlipDirection) {
        refreshFollows()
        val follows = if (settingsStore.isLoggedIn) liveFollows.liveNow.value.map { it.userLogin } else null
        val ring = ChannelFlip.ringFor(latestChannel, follows, flipOrigin.logins)
        val target = pendingFlip.press(ring, latestChannel, direction, System.currentTimeMillis())
        if (target == null) {
            // Nowhere to go: drop a target an earlier press left pending.
            pendingFlip.cancel()
            flipJob?.cancel()
            flipTarget = null
            flipNote = "No other channel to switch to"
            scope.launch {
                delay(2_500)
                flipNote = null
            }
            return
        }
        // Both neighbours are warm by the time the viewer settles on one.
        prewarmGate.take(ChannelFlip.neighbours(ring, latestChannel) + target).forEach(streamCache::prewarm)
        flipTarget = target
        flipJob?.cancel()
        flipJob = scope.launch {
            val next = pendingFlip.awaitSettled({ latestChannel }, System::currentTimeMillis)
            if (next == null) {
                flipTarget = null
                return@launch
            }
            // Heading the same way, the channel after this one is the likeliest next.
            ChannelFlip.step(ring, next, direction)?.let { prewarmGate.take(listOf(it)).forEach(streamCache::prewarm) }
            latestOnFlip(next)
        }
    }

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
    var helpOpen by remember { mutableStateOf(false) }
    // The mini player has no room for the menu or the shortcuts list.
    // Chat is hidden there too: drop its focus so the keys reach the player, not an invisible box.
    val focusManager = LocalFocusManager.current
    LaunchedEffect(mini) {
        if (mini) {
            settingsMenuOpen = false
            helpOpen = false
            chatInputFocused = false
            focusManager.clearFocus(force = true)
        }
    }
    // A closed chat panel leaves composition (see the chat panel below), and its text field
    // may not report losing focus as it goes: let the keys reach the player again.
    LaunchedEffect(isChatOpen) { if (!isChatOpen) chatInputFocused = false }
    val miniDoubleClick = remember { DoubleClickDetector() }
    val latestHelpOpen = rememberUpdatedState(helpOpen)
    val latestVolume = rememberUpdatedState(playerStatus.volume)
    val latestMuted = rememberUpdatedState(playerStatus.isMuted)
    val latestMenuOpen = rememberUpdatedState(settingsMenuOpen)
    var heldKey = 0
    val latestViewModel = rememberUpdatedState(viewModel)
    val myWindow = LocalAwtWindow.current
    DisposableEffect(Unit) {
        val dispatcher = KeyEventDispatcher { e ->
            // A key pressed in another PureTV window (a popped-out tile, a second window) is that window's.
            if (!AppWindows.handlesKeys(myWindow)) {
                heldKey = 0
                return@KeyEventDispatcher false
            }
            // Any key press is the viewer being there (sleep timer), typing in chat included,
            // and it brings hidden controls back (resetControls also counts as activity).
            if (e.id == KeyEvent.KEY_PRESSED) {
                if (latestMode.value != PlayerMode.DEFAULT) resetControls() else sleep.onActivity()
            }
            if (latestChatFocused.value) {
                heldKey = 0
                return@KeyEventDispatcher false
            }
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
            if (e.id == KeyEvent.KEY_RELEASED && e.keyCode == heldKey) heldKey = 0
            if (e.id != KeyEvent.KEY_PRESSED) return@KeyEventDispatcher false
            // A key that is still down repeats KEY_PRESSED without a release in between.
            val isRepeat = e.keyCode == heldKey
            heldKey = e.keyCode
            val m = latestMode.value
            val action = PlayerShortcuts.actionFor(
                keyCode = e.keyCode,
                keyChar = e.keyChar,
                ctrlOrAlt = e.isControlDown || e.isMetaDown || e.isAltDown,
                immersive = m != PlayerMode.DEFAULT,
                helpOpen = latestHelpOpen.value,
                textInputFocused = latestChatFocused.value,
                menuOpen = latestMenuOpen.value,
                isRepeat = isRepeat,
            ) ?: return@KeyEventDispatcher false
            if (m == PlayerMode.MINI && !PlayerShortcuts.worksInMini(action)) return@KeyEventDispatcher true
            when (action) {
                ShortcutAction.ToggleFullscreen -> shell.setPlayerMode(if (m == PlayerMode.FULLSCREEN) PlayerMode.DEFAULT else PlayerMode.FULLSCREEN)
                ShortcutAction.ToggleTheater -> shell.setPlayerMode(if (m == PlayerMode.THEATER) PlayerMode.DEFAULT else PlayerMode.THEATER)
                ShortcutAction.ToggleChat -> shell.toggleChat()
                ShortcutAction.TogglePlayPause -> latestViewModel.value.togglePlayPause()
                ShortcutAction.ExitImmersive -> shell.exitImmersive()
                ShortcutAction.ToggleStats -> showStats = !showStats
                ShortcutAction.ToggleHelp -> helpOpen = !helpOpen
                ShortcutAction.FlipNext -> flip(FlipDirection.NEXT)
                ShortcutAction.FlipPrevious -> flip(FlipDirection.PREVIOUS)
                ShortcutAction.VolumeUp, ShortcutAction.VolumeDown -> {
                    if (latestMuted.value) {
                        // Muted: Up brings back the volume from before the mute; Down stays quiet.
                        if (action == ShortcutAction.VolumeUp) latestViewModel.value.toggleMute()
                    } else {
                        latestViewModel.value.setVolume(
                            PlayerShortcuts.steppedVolume(latestVolume.value, up = action == ShortcutAction.VolumeUp),
                        )
                    }
                    resetControls() // the volume slider is the feedback
                }
                ShortcutAction.ToggleMute -> { latestViewModel.value.toggleMute(); resetControls() }
                ShortcutAction.CycleSleepTimer -> { sleep.cycle(); resetControls() }
                ShortcutAction.ToggleMini -> shell.setPlayerMode(if (m == PlayerMode.MINI) PlayerMode.DEFAULT else PlayerMode.MINI)
            }
            true
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

    // Compose never hears the mouse over the native video, which in fullscreen is everywhere.
    NativePointerActivity(enabled = mode != PlayerMode.DEFAULT && !mini) { resetControls() }

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
                    visible = (controlsVisible || mode == PlayerMode.DEFAULT) && !mini,
                    enter = PureTvMotion.barEnter(fromTop = true),
                    exit = PureTvMotion.barExit(fromTop = true, instant = mini),
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
                        bell = bell,
                        onBack = onBack,
                        onOpenMultiView = onOpenMultiView,
                        onOpenInNewWindow = { openInNewWindow(WindowDestination.Channel(channelLogin)) },
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
                            // keepSurface: a flip builds the next channel's state with no URL for a
                            // moment, and the surface must not be torn down and rebuilt for it.
                            state.playableUrl != null || keepSurface -> VlcPlayerView(
                                vlcPlayer = vlcPlayer,
                                modifier = Modifier.fillMaxSize(),
                                // The heavyweight video surface eats mouse events; bridge
                                // them back so moving the mouse reveals the controls,
                                // including in fullscreen, where the surface covers all.
                                onUserActivity = { resetControls() },
                                // Mini player: a double click on the video goes back to the full window.
                                onClick = if (mini) {
                                    { if (miniDoubleClick.click(System.currentTimeMillis())) shell.setPlayerMode(PlayerMode.DEFAULT) }
                                } else null,
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
                        enter = PureTvMotion.panelEnter(),
                        exit = PureTvMotion.panelExit(),
                    ) {
                        PlayerSettingsMenu(
                            currentQuality = state.currentQuality,
                            onQualitySelected = viewModel::setQuality,
                            upscalingMode = appSettings.upscalingMode,
                            onUpscalingSelected = viewModel::setUpscaling,
                            scalingEnabled = vlcPlayer.supportsUpscaling,
                            backend = appSettings.playbackBackend,
                            onBackendSelected = viewModel::setPlaybackBackend,
                            sleepMinutes = sleep.ui.minutes,
                            onSleepSelected = sleep::set,
                            rtx = if (vlcPlayer.supportsRtxVideo) RtxMenuState(
                                status = rtxStatus,
                                superResolution = appSettings.rtxSuperResolution,
                                onSuperResolutionChange = viewModel::setRtxSuperResolution,
                                hdr = appSettings.rtxHdr.takeIf { vlcPlayer.supportsRtxHdr },
                                onHdrChange = viewModel::setRtxHdr,
                            ) else null,
                            engineNeedsRestart = playerProvider.runningBackend(appSettings.playbackBackend) != appSettings.playbackBackend,
                            onRestart = if (AppRestart.isAvailable) AppRestart::restart else null,
                        )
                    }

                    // Flip banner and the sleep timer's last-minute warning, then the
                    // shortcuts list: between the video and the controls like the menu above.
                    PlayerNoticeStrip(
                        text = if (mini) null else flipTarget?.let { "Switching to $it" } ?: flipNote ?: sleep.ui.warning,
                    )
                    ShortcutsSheet(visible = helpOpen)
                }

                // Controls bar: always in DEFAULT, slides down when idle in THEATER/FULLSCREEN
                AnimatedVisibility(
                    visible = (controlsVisible || mode == PlayerMode.DEFAULT) && !mini,
                    enter = PureTvMotion.barEnter(fromTop = false),
                    exit = PureTvMotion.barExit(fromTop = false, instant = mini),
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
                        onToggleMini = { shell.setPlayerMode(PlayerMode.MINI) },
                        radius = panelRadius,
                    )
                }

                if (mini) {
                    MiniControlStrip(
                        shell = shell,
                        title = flipTarget?.let { "Switching to $it" } ?: state.channel?.displayName ?: channelLogin,
                        isPlaying = playerStatus.isPlaying,
                        volume = playerStatus.volume,
                        isMuted = playerStatus.isMuted,
                        onTogglePlayPause = viewModel::togglePlayPause,
                        onToggleMute = viewModel::toggleMute,
                        onVolumeChange = viewModel::setVolume,
                        onExpand = { shell.setPlayerMode(PlayerMode.DEFAULT) },
                        onClose = { shell.exitMini(); latestOnBack() },
                    )
                }
            }

            Spacer(Modifier.width(chatGap))

            // ── Chat panel ─────────────────────────────────────────────────────
            // Width is 0 or 392dp; the card slides and fades inside it. clipToBounds()
            // keeps the slide inside the panel.
            // Outside the panel, so closing chat keeps the tab it was on.
            var chatTab by remember { mutableStateOf(ChatTab.Chat) }
            Box(
                modifier = Modifier
                    .width(chatWidth)
                    .fillMaxHeight()
                    .clipToBounds(),
            ) {
                // Composed only while it can be seen. A closed chat (and the mini player)
                // kept laying out new rows at zero width ten times a second, loading their
                // images and animating their emotes. The view model keeps collecting, so
                // chat is current the moment it reopens.
                if (isChatOpen || chatReveal > 0f) Column(
                    modifier = Modifier.fillMaxSize().sidePanelReveal { chatReveal },
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
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
                        CompositionLocalProvider(
                            LocalBadgeIndex provides state.badges,
                            LocalChatAppearance provides ChatAppearance(
                                showTimestamps = appSettings.chatTimestamps,
                                showBadges = appSettings.showBadges,
                                compact = appSettings.compactMode,
                            ),
                        ) {
                            ChatScale(ChatTextSize.from(appSettings.chatFontSize).scale) {
                                // Settings > Chat > Show 7TV name paints; off draws plain names.
                                val namePaints = if (appSettings.showSevenTvPaints) state.namePaints else emptyMap()
                                when (chatTab) {
                                    ChatTab.Chat -> ChatMessageList(
                                        messages = state.chatMessages,
                                        onReply = viewModel::startReply,
                                        modifier = Modifier.fillMaxSize(),
                                        namePaints = namePaints,
                                    )
                                    ChatTab.Mentions ->
                                        if (state.mentionMessages.isEmpty()) {
                                            MentionsEmptyState(Modifier.fillMaxSize())
                                        } else {
                                            ChatMessageList(
                                                messages = state.mentionMessages,
                                                onReply = viewModel::startReply,
                                                modifier = Modifier.fillMaxSize(),
                                                namePaints = namePaints,
                                            )
                                        }
                                }
                            }
                        }
                    }

                    RoomModeChips(state.roomModes)

                    ChatInputBar(
                        canChat = state.canChat,
                        emotes = state.emotes,
                        recentMessages = state.chatMessages,
                        selfLogin = state.selfLogin,
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

/**
 * The room's chat modes (slow, emote-only, followers-only, sub-only) as a slim
 * row of muted pills above the composer, so a message that will not go through
 * is explained before it is typed. Takes no space when the room is open.
 */
@Composable
internal fun RoomModeChips(modes: ChatRoomModes) {
    val labels = remember(modes) { modes.labels() }
    if (labels.isEmpty()) return
    val c = PureTvTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        labels.forEach { label ->
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = c.onSurfaceVariant,
                maxLines = 1,
                modifier = Modifier
                    .clip(PureTvTheme.shapes.pillShape)
                    .background(c.surfaceHigh)
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            )
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
    bell: AlertBell,
    onBack: () -> Unit,
    onOpenMultiView: () -> Unit,
    onOpenInNewWindow: () -> Unit,
    radius: Dp,
) {
    val c = PureTvTheme.colors
    // The buttons are measured before the name takes what is left, so in a narrow bar (the
    // default 1280x720 window with chat open) the name was squeezed to one letter. A narrow
    // bar drops the labels of its two widest buttons instead; their names stay for screen
    // readers.
    BoxWithConstraints(Modifier.fillMaxWidth()) {
    val compactMulti = maxWidth < TOP_BAR_FULL_WIDTH
    val compactFollow = maxWidth < TOP_BAR_COMPACT_WIDTH
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
                // One line that ellipsizes as a whole: as two texts, a narrow bar cut the
                // category short and pushed the viewer count out of the bar.
                val game = streamInfo.gameName.takeIf { it.isNotBlank() } ?: "No category"
                val countStyle = PureTvType.data.toSpanStyle()
                Text(
                    buildAnnotatedString {
                        append("$game · ")
                        withStyle(countStyle) { append("${formatViewerCount(streamInfo.viewerCount)} viewers") }
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = c.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        AdBlockPill(adBlockStatus)
        bell.message?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = c.primary, maxLines = 1) }
        ExpressiveIconButton(
            icon = if (bell.on) ExpressiveIcons.NotifyOn else ExpressiveIcons.Notify,
            contentDescription = if (bell.on) "Turn off go-live alerts" else "Tell me when this channel goes live",
            onClick = bell.toggle,
            tint = if (bell.on) c.primary else null,
        )
        ExpressiveIconButton(
            icon = ExpressiveIcons.OpenInNew,
            contentDescription = "Open this channel's page in a new window",
            onClick = onOpenInNewWindow,
        )
        if (compactMulti) {
            ExpressiveIconButton(
                icon = ExpressiveIcons.Grid,
                contentDescription = "Watch with another channel",
                onClick = onOpenMultiView,
                style = ExpressiveButtonStyle.Tonal,
            )
        } else {
            ExpressiveButton(
                text = "Watch with another channel",
                onClick = onOpenMultiView,
                style = ExpressiveButtonStyle.Tonal,
                size = ExpressiveButtonSize.Medium,
                icon = ExpressiveIcons.Grid,
            )
        }
        if (compactFollow) {
            ExpressiveIconButton(
                icon = if (isFollowed) ExpressiveIcons.Check else ExpressiveIcons.Add,
                contentDescription = if (isFollowed) "Following" else "Follow",
                onClick = onToggleFollow,
                style = ExpressiveButtonStyle.Tonal,
                enabled = canFollow,
            )
        } else {
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
    }
}

/** At or above this width the stream's top bar has room for every button label and the name. */
private val TOP_BAR_FULL_WIDTH = 1000.dp

/** Below this width Follow also shows only its icon. */
private val TOP_BAR_COMPACT_WIDTH = 760.dp

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
    onToggleMini: () -> Unit,
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
            onToggleMini = onToggleMini,
        )
    }
}

/** Filled primary pill; morphs 28→14dp radius on hover and compresses on press. */
@Composable
private fun PlayPauseButton(isPlaying: Boolean, onClick: () -> Unit) {
    val c = PureTvTheme.colors
    val interaction = remember { MutableInteractionSource() }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .width(64.dp)
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
    onToggleMini: () -> Unit,
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
        ControlsGroupButton(ExpressiveIcons.MiniPlayer, "Mini player", onToggleMini)
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
internal fun ChatMessageList(
    messages: List<ChatMessage>,
    onReply: (ChatMessage) -> Unit,
    modifier: Modifier = Modifier,
    /** 7TV name paints by Twitch user id; a lookup per row, nothing per frame. */
    namePaints: Map<String, SevenTvPaint> = emptyMap(),
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
            items(messages, key = { it.id }) {
                ChatMessageRow(message = it, onReply = onReply, namePaint = namePaints[it.userId])
            }
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
internal fun ChatInputBar(
    canChat: Boolean,
    emotes: List<PickableEmote>,
    onSend: (String) -> Unit,
    recentMessages: List<ChatMessage> = emptyList(),
    selfLogin: String? = null,
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
    // The emote picker's search box is a second text input in the chat column.
    var searchFocused by remember { mutableStateOf(false) }

    fun submit() {
        val trimmed = value.text.trim()
        if (trimmed.isNotEmpty()) {
            onSend(trimmed)
            value = TextFieldValue("")
        }
    }

    // Autocomplete suggestions for the word the cursor sits in: emotes, plus
    // recent chatters for "@" (and, ranked after emotes, for a plain prefix).
    // Skipped for an empty word so new messages don't rescan chat while idle.
    val (word, _) = wordAtCursor(value.text, value.selection.start)
    val suggestions = if (word.isEmpty()) {
        emptyList()
    } else {
        composerSuggestions(word, emotes, recentChatters(recentMessages, selfLogin))
    }

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
                onSearchFocusChanged = {
                    searchFocused = it
                    onFocusChanged(fieldFocused || it)
                },
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
                    val chipInteraction = remember(s.completion) { MutableInteractionSource() }
                    Row(
                        modifier = Modifier
                            .expressiveClickable(
                                interaction = chipInteraction,
                                onClick = { applyCompletion(s.completion) },
                                restRadius = PureTvTheme.shapes.pill,
                                hoverRadius = PureTvTheme.shapes.pillMorph,
                                color = c.surfaceHigh,
                                hoverColor = c.surfaceHighest,
                            )
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (s is ComposerSuggestion.Emote) {
                            EmoteImage(s.emote.imageUrl, s.emote.code, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(5.dp))
                        }
                        Text(s.completion, color = c.onSurfaceVariant, style = PureTvType.dataSmall)
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
                                onFocusChanged(it.isFocused || searchFocused)
                            }
                            // Enter sends the message; Tab accepts the first emote or
                            // @username suggestion when one is offered (see composerKeyAction).
                            .onPreviewKeyEvent { ev ->
                                if (ev.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                                val isEnter = ev.key == Key.Enter || ev.key == Key.NumPadEnter
                                when (composerKeyAction(isEnter, ev.key == Key.Tab, suggestions.isNotEmpty())) {
                                    ComposerKeyAction.SEND -> { submit(); true }
                                    ComposerKeyAction.COMPLETE -> { applyCompletion(suggestions.first().completion); true }
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
