package com.puretv.twitch.desktop.ui.screens

import com.puretv.twitch.desktop.platform.ShortcutKey
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.puretv.twitch.core.adblock.AdBlockStatus
import com.puretv.twitch.core.model.AppSettings
import com.puretv.twitch.core.model.ChatTextSize
import com.puretv.twitch.desktop.data.DesktopSettingsStore
import com.puretv.twitch.desktop.ui.LocalAppShell
import com.puretv.twitch.desktop.ui.chat.ChatAppearance
import com.puretv.twitch.desktop.ui.chat.ChatScale
import com.puretv.twitch.desktop.ui.chat.LocalChatAppearance
import com.puretv.twitch.desktop.ui.components.LocalBadgeIndex
import com.puretv.twitch.desktop.ui.theme.PureTvMotion
import com.puretv.twitch.desktop.player.VlcPlayerView
import com.puretv.twitch.desktop.ui.FollowedRailViewModel
import com.puretv.twitch.desktop.ui.AddOutcome
import com.puretv.twitch.desktop.ui.HeldKey
import com.puretv.twitch.desktop.ui.MultiSession
import com.puretv.twitch.desktop.ui.MultiShortcutAction
import com.puretv.twitch.desktop.ui.AppWindows
import com.puretv.twitch.desktop.ui.LocalAwtWindow
import com.puretv.twitch.desktop.ui.MultiShortcuts
import com.puretv.twitch.desktop.ui.MultiTile
import com.puretv.twitch.desktop.ui.MultiViewLayout
import com.puretv.twitch.desktop.ui.MultiViewState
import com.puretv.twitch.desktop.ui.MultiViewUiState
import com.puretv.twitch.desktop.ui.MultiViewViewModel
import com.puretv.twitch.desktop.ui.SleepSession
import com.puretv.twitch.desktop.ui.TileInfo
import com.puretv.twitch.desktop.ui.TileSource
import com.puretv.twitch.desktop.ui.components.AdBlockPill
import com.puretv.twitch.desktop.ui.components.Avatar
import com.puretv.twitch.desktop.ui.components.PlayerNoticeStrip
import com.puretv.twitch.desktop.ui.components.ExpressiveButton
import com.puretv.twitch.desktop.ui.components.ExpressiveButtonSize
import com.puretv.twitch.desktop.ui.components.ExpressiveButtonStyle
import com.puretv.twitch.desktop.ui.components.ExpressiveIconButton
import com.puretv.twitch.desktop.ui.components.ExpressiveIcons
import com.puretv.twitch.desktop.ui.components.ExpressiveSlider
import com.puretv.twitch.desktop.ui.components.VodTransportRow
import com.puretv.twitch.desktop.ui.seekDeltaMs
import com.puretv.twitch.desktop.ui.components.formatViewerCount
import com.puretv.twitch.desktop.ui.rememberDesktopViewModel
import com.puretv.twitch.desktop.ui.theme.PureTvTheme
import com.puretv.twitch.desktop.ui.theme.PureTvType
import com.puretv.twitch.desktop.ui.theme.sidePanelReveal
import kotlinx.coroutines.delay
import org.koin.core.Koin
import java.awt.KeyEventDispatcher
import java.awt.KeyboardFocusManager
import java.awt.event.KeyEvent
import kotlin.math.roundToInt

private val TILE_GAP = 8.dp
private val TILE_FRAME = 3.dp
private val TILE_STRIP_HEIGHT = 48.dp

/**
 * Watch two to four live channels at once. All of them play, each with its own volume; by default only the selected one is
 * audible and outlined, and clicking another tile moves the sound to it.
 *
 * AWT heavyweight components (each tile's VLC canvas) always paint above Compose, so
 * nothing is drawn over a video: the active outline is a padded frame around it, and
 * the channel name, ad-block pill and close button sit in a strip below it. For the
 * same reason the add-channel panel pushes the grid down instead of floating over it.
 *
 * @param onWatchSingle called with the one channel left when a close leaves a single
 *   tile, so multi-stream hands back to the normal stream screen.
 */
@Composable
fun MultiViewContent(
    koin: Koin,
    initialLogin: String,
    onBack: () -> Unit,
    onWatchSingle: (String) -> Unit,
    onRequestSignIn: () -> Unit = {},
    // Owned by App, so the timer started on the stream screen carries on here.
    sleep: SleepSession = remember { SleepSession() },
) {
    // The session belongs to the app, not to this screen: tiles popped out into their own
    // windows keep playing when this screen is left, and only the tiles shown here stop.
    val session = remember { koin.get<MultiSession>() }
    val viewModel = remember(initialLogin) { session.open(initialLogin) }
    DisposableEffect(session) { onDispose { session.leaveGrid() } }
    // Ended while this screen is up (the sleep timer ran out in a popped-out window): leave.
    val running by session.current.collectAsState()
    if (running == null) {
        val leave by rememberUpdatedState(onBack)
        LaunchedEffect(Unit) { leave() }
        return
    }
    val state by viewModel.state.collectAsState()
    val layout = state.layout
    val c = PureTvTheme.colors

    // A short note under the top bar, for an add that did nothing.
    var notice by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(notice) {
        if (notice != null) {
            delay(3_000)
            notice = null
        }
    }

    // With a single tile there is nothing to compare yet: start with the picker open.
    var pickerOpen by remember { mutableStateOf(layout.count < 2) }

    fun closeTile(id: Int) {
        val left = session.closeTile(id)
        val sole = left.singleOrNull()
        when {
            left.isEmpty() -> onBack()
            // One tile still in the grid is a stream like any other; one in its own window stays there.
            // (A single past video stays here: there is no stream screen for it to hand over to.)
            sole?.liveLogin != null && !viewModel.state.value.layout.isPopped(sole.id) -> onWatchSingle(sole.liveLogin!!)
        }
    }

    val appSettings by remember { koin.get<DesktopSettingsStore>() }.settings.collectAsState()
    val shell = LocalAppShell.current
    val chatOpen = shell.isChatOpen
    // One step for the width, so up to four native video surfaces resize once per toggle
    // instead of on every frame; the chat card slides and fades inside it.
    val chatWidth by animateDpAsState(
        targetValue = if (chatOpen) CHAT_WIDTH else 0.dp,
        animationSpec = PureTvMotion.sidePanelSize(chatOpen),
        label = "multiChatWidth",
    )
    val chatReveal by animateFloatAsState(
        targetValue = if (chatOpen) 1f else 0f,
        animationSpec = PureTvMotion.sidePanelReveal(chatOpen),
        label = "multiChatReveal",
    )
    val chatGap by animateDpAsState(
        targetValue = if (chatOpen) TILE_GAP else 0.dp,
        animationSpec = PureTvMotion.sidePanelSize(chatOpen),
        label = "multiChatGap",
    )

    // Shortcuts are read at the AWT level like the stream screen (the video surfaces are native
    // windows that can take focus from Compose), and stand down while a text box has focus.
    var chatFocused by remember { mutableStateOf(false) }
    var pickerFocused by remember { mutableStateOf(false) }
    val pickerShown = pickerOpen && layout.canAdd
    LaunchedEffect(pickerShown) { if (!pickerShown) pickerFocused = false }
    LaunchedEffect(chatOpen) { if (!chatOpen) chatFocused = false }
    val latestTyping = rememberUpdatedState(chatFocused || pickerFocused)
    val latestState = rememberUpdatedState(state)
    val myWindow = LocalAwtWindow.current
    DisposableEffect(Unit) {
        val held = HeldKey()
        val dispatcher = KeyEventDispatcher { e ->
            // A key pressed in another PureTV window (a popped-out tile, a second window) is that window's.
            if (!AppWindows.handlesKeys(myWindow)) {
                held.clear()
                return@KeyEventDispatcher false
            }
            // Any key press is the viewer being there (sleep timer), typing in chat included.
            if (e.id == KeyEvent.KEY_PRESSED) sleep.onActivity()
            if (e.id == KeyEvent.KEY_RELEASED) held.released(e.keyCode)
            if (e.id != KeyEvent.KEY_PRESSED) return@KeyEventDispatcher false
            val isRepeat = held.pressed(e.keyCode)
            val action = MultiShortcuts.actionFor(
                keyCode = e.keyCode,
                ctrl = ShortcutKey.isDown(e),
                alt = e.isAltDown,
                textInputFocused = latestTyping.value,
                isRepeat = isRepeat,
                shift = e.isShiftDown,
                vodSelected = latestState.value.layout.active?.isVod == true,
            ) ?: return@KeyEventDispatcher false
            val activeId = latestState.value.layout.activeId
            action.seekDeltaMs()?.let { delta ->
                // Unconsumed while the video cannot seek yet, so the key is not swallowed into nothing.
                return@KeyEventDispatcher viewModel.seekActiveBy(delta)
            }
            when (action) {
                MultiShortcutAction.TogglePlayPause -> viewModel.toggleActivePlay()
                MultiShortcutAction.SeekBack, MultiShortcutAction.SeekForward,
                MultiShortcutAction.SeekBackLarge, MultiShortcutAction.SeekForwardLarge,
                MultiShortcutAction.JumpBack, MultiShortcutAction.JumpForward,
                -> {}
                MultiShortcutAction.VolumeUp -> viewModel.nudgeVolume(up = true)
                MultiShortcutAction.VolumeDown -> viewModel.nudgeVolume(up = false)
                MultiShortcutAction.ToggleMute -> viewModel.toggleActiveMute()
                MultiShortcutAction.CycleSleepTimer -> sleep.cycle()
                MultiShortcutAction.MoveEarlier -> activeId?.let { viewModel.move(it, -1) }
                MultiShortcutAction.MoveLater -> activeId?.let { viewModel.move(it, 1) }
            }
            true
        }
        val kfm = KeyboardFocusManager.getCurrentKeyboardFocusManager()
        kfm.addKeyEventDispatcher(dispatcher)
        onDispose { kfm.removeKeyEventDispatcher(dispatcher) }
    }

    // Sleep timer: at the end every tile is stopped (leaving the screen frees them all) and
    // PureTV leaves multi-stream, out of fullscreen too.
    val latestOnBack by rememberUpdatedState(onBack)
    LaunchedEffect(sleep.ui.running) {
        while (sleep.ui.running) {
            delay(1_000)
            if (sleep.tick().expired) {
                shell.exitImmersive()
                // Every tile stops, the ones in their own windows too.
                session.end()
                latestOnBack()
            }
        }
    }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(c.surfaceLowest)
            .padding(8.dp)
            .pointerInput(Unit) {
                // Only genuine cursor movement counts as "still here" for the sleep timer.
                var lastPos: Offset? = null
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.type == PointerEventType.Move) {
                            val pos = event.changes.firstOrNull()?.position
                            if (pos != null && pos != lastPos) {
                                lastPos = pos
                                sleep.onActivity()
                            }
                        }
                    }
                }
            },
    ) {
        Column(
            modifier = Modifier.weight(1f).fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            MultiTopBar(
                state = state,
                pickerOpen = pickerOpen,
                chatOpen = chatOpen,
                onToggleChat = { shell.toggleChat() },
                onBack = onBack,
                onTogglePicker = { pickerOpen = !pickerOpen },
                onListenAll = viewModel::setListenAll,
                sleepLabel = sleep.ui.label,
                sleepRunning = sleep.ui.running,
                onCycleSleep = sleep::cycle,
                onPopOutAll = {
                    // The streams move to their own windows and the main window is free to browse.
                    session.popOutAll()
                    onBack()
                },
                onDockAll = session::dockAll,
            )

            // The timer's last-minute warning sits in the column like the stream screen's
            // notice strip: a native video surface would paint over anything floating.
            PlayerNoticeStrip(text = notice ?: sleep.ui.warning)

            // Above the video tiles: the panel takes its height in one step (see panelEnter).
            AnimatedVisibility(
                visible = pickerOpen && layout.canAdd,
                enter = PureTvMotion.panelEnter(),
                exit = PureTvMotion.panelExit(),
            ) {
                AddChannelPanel(
                    koin = koin,
                    shown = layout,
                    onFocusChanged = { pickerFocused = it },
                    onAdd = { login ->
                        when (val outcome = viewModel.state.value.layout.addOutcome(login)) {
                            // Already on screen: show which one it is instead of doing nothing.
                            is AddOutcome.AlreadyShown -> {
                                viewModel.setActive(outcome.tileId)
                                notice = "Already watching ${login.trim().removePrefix("@")}"
                            }
                            AddOutcome.Full -> notice = "Four streams is the limit"
                            AddOutcome.Blank -> notice = "Type a channel name first"
                            AddOutcome.Added -> if (viewModel.addChannel(login) && viewModel.state.value.layout.count >= 2) pickerOpen = false
                        }
                    },
                    onAddVod = { vod ->
                        when (val outcome = viewModel.state.value.layout.addVodOutcome(vod.videoId)) {
                            is AddOutcome.AlreadyShown -> {
                                viewModel.setActive(outcome.tileId)
                                notice = "Already watching that video"
                            }
                            AddOutcome.Full -> notice = "Four streams is the limit"
                            AddOutcome.Blank -> notice = "That video can't be added"
                            AddOutcome.Added -> if (viewModel.addVod(vod) && viewModel.state.value.layout.count >= 2) pickerOpen = false
                        }
                    },
                )
            }

            if (state.fatalError != null) {
                Box(Modifier.weight(1f).fillMaxWidth().background(Color.Black), contentAlignment = Alignment.Center) {
                    Text(
                        state.fatalError!!,
                        color = c.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(24.dp),
                    )
                }
            } else if (layout.docked.isEmpty()) {
                Box(Modifier.weight(1f).fillMaxWidth().background(Color.Black), contentAlignment = Alignment.Center) {
                    Text(
                        if (layout.popped.isEmpty()) "Add a channel to start." else "Your streams are in their own windows. Dock them back to see them here.",
                        color = c.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(24.dp),
                    )
                }
            } else {
                TileGrid(count = layout.docked.size, modifier = Modifier.weight(1f).fillMaxWidth()) {
                    // Keyed by tile id so a tile keeps its video surface when the grid is
                    // rearranged: moving a surface to a new parent would destroy its window.
                    layout.docked.forEach { tile ->
                        key(tile.id) {
                            val info = state.info[tile.id]
                            if (info != null) {
                                Tile(
                                    tile = tile,
                                    info = info,
                                    isActive = tile.id == layout.activeId,
                                    audible = layout.isAudible(tile.id),
                                    onActivate = { viewModel.setActive(tile.id) },
                                    onVolume = { viewModel.setVolume(tile.id, it) },
                                    onToggleMute = { viewModel.toggleMute(tile.id) },
                                    onClose = { closeTile(tile.id) },
                                    canMoveEarlier = tile.id != layout.docked.first().id,
                                    canMoveLater = tile.id != layout.docked.last().id,
                                    onPopOut = { session.popOut(tile.id) },
                                    onTogglePlay = { viewModel.togglePlay(tile.id) },
                                    onSeek = { viewModel.seekTo(tile.id, it) },
                                    onMoveEarlier = { viewModel.move(tile.id, -1) },
                                    onMoveLater = { viewModel.move(tile.id, 1) },
                                )
                            } else {
                                Box(Modifier)
                            }
                        }
                    }
                }
                Text(
                    if (layout.active?.isVod == true) MultiShortcuts.VOD_HINT else MultiShortcuts.HINT,
                    style = MaterialTheme.typography.labelSmall,
                    color = c.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
            }
        }

        Spacer(Modifier.width(chatGap))

        // Chat for the active tile only; it follows the sound. Width goes to 0 when
        // closed, and the connection stays so reopening shows the room as it is.
        Box(Modifier.width(chatWidth).fillMaxHeight().clipToBounds().sidePanelReveal { chatReveal }) {
            // Composed only while it can be seen (see StreamContent's chat panel): hidden,
            // it still laid out every new row and animated its emotes.
            if (chatOpen || chatReveal > 0f) MultiChatColumn(
                viewModel = viewModel,
                appSettings = appSettings,
                channelName = layout.active?.let { state.info[it.id]?.displayName ?: it.login },
                replayOf = layout.active?.vod,
                onClose = { shell.toggleChat() },
                onRequestSignIn = onRequestSignIn,
                onInputFocusChanged = { chatFocused = it },
            )
        }
    }
}

private val CHAT_WIDTH = 392.dp
private val CHAT_RADIUS = 24.dp

@Composable
private fun MultiChatColumn(
    viewModel: MultiViewViewModel,
    appSettings: AppSettings,
    channelName: String?,
    // Set when the selected tile is a past video: the column shows that video's chat replay.
    replayOf: TileSource.Vod?,
    onClose: () -> Unit,
    onRequestSignIn: () -> Unit,
    onInputFocusChanged: (Boolean) -> Unit,
) {
    val c = PureTvTheme.colors
    val chat by viewModel.chatState.collectAsState()
    val replay by viewModel.vodChatState.collectAsState()
    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .clip(RoundedCornerShape(CHAT_RADIUS))
                .background(c.surfaceContainer)
                .padding(start = 20.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(if (replayOf != null) "Chat replay" else "Chat", style = MaterialTheme.typography.titleMedium, color = c.onSurface, maxLines = 1)
                Text(
                    channelName ?: "No channel",
                    style = MaterialTheme.typography.bodySmall,
                    color = c.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            ExpressiveIconButton(
                icon = ExpressiveIcons.Close,
                contentDescription = "Close chat",
                onClick = onClose,
                boxSize = 48.dp,
                iconSize = 22.dp,
            )
        }
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(CHAT_RADIUS))
                .background(c.surfaceContainer),
        ) {
            CompositionLocalProvider(
                LocalBadgeIndex provides chat.badges,
                LocalChatAppearance provides ChatAppearance(
                    showTimestamps = appSettings.chatTimestamps,
                    showBadges = appSettings.showBadges,
                    compact = appSettings.compactMode,
                ),
            ) {
                ChatScale(ChatTextSize.from(appSettings.chatFontSize).scale) {
                    // The room changes with the active tile: a fresh list state per channel.
                    if (replayOf != null) {
                        // The replay follows the video's own clock, one list per video.
                        key(replayOf.videoId) {
                            ChatMessageList(
                                messages = if (replay.videoId == replayOf.videoId) replay.messages else emptyList(),
                                onReply = {},
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    } else {
                        key(chat.channel) {
                            ChatMessageList(
                                messages = chat.messages,
                                onReply = viewModel::startReply,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                }
            }
        }
        if (replayOf != null) {
            Text(
                "Replay of this video's chat. Chatting happens on the live stream.",
                style = MaterialTheme.typography.labelSmall,
                color = c.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            )
            return@Column
        }
        RoomModeChips(chat.modes)
        ChatInputBar(
            canChat = chat.canChat,
            emotes = chat.emotes,
            recentMessages = chat.messages,
            selfLogin = chat.selfLogin,
            replyingTo = chat.replyingTo,
            onCancelReply = viewModel::cancelReply,
            onSend = viewModel::sendChat,
            onFocusChanged = onInputFocusChanged,
            onRequestSignIn = onRequestSignIn,
        )
    }
}

/** Places the tiles in the grid for their count. One flat layout, so nothing is reparented. */
@Composable
private fun TileGrid(count: Int, modifier: Modifier, content: @Composable () -> Unit) {
    Layout(content = content, modifier = modifier) { measurables, constraints ->
        val shape = MultiViewLayout.shape(count)
        val gap = TILE_GAP.roundToPx()
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        val cellWidth = ((width - gap * (shape.columns - 1)) / shape.columns).coerceAtLeast(0)
        val cellHeight = ((height - gap * (shape.rows - 1)) / shape.rows).coerceAtLeast(0)
        val placed = measurables.mapIndexed { index, measurable ->
            val cell = MultiViewLayout.cell(index, measurables.size)
            val w = cellWidth * cell.columnSpan + gap * (cell.columnSpan - 1)
            Triple(cell, w, measurable.measure(Constraints.fixed(w, cellHeight)))
        }
        layout(width, height) {
            placed.forEach { (cell, _, placeable) ->
                placeable.place(cell.column * (cellWidth + gap), cell.row * (cellHeight + gap))
            }
        }
    }
}

@Composable
private fun Tile(
    tile: MultiTile,
    info: TileInfo,
    isActive: Boolean,
    audible: Boolean,
    onActivate: () -> Unit,
    onVolume: (Int) -> Unit,
    onToggleMute: () -> Unit,
    onClose: () -> Unit,
    canMoveEarlier: Boolean,
    canMoveLater: Boolean,
    onMoveEarlier: () -> Unit,
    onMoveLater: () -> Unit,
    onPopOut: () -> Unit,
    onTogglePlay: () -> Unit,
    onSeek: (Long) -> Unit,
) {
    val c = PureTvTheme.colors
    val vod = tile.vod
    Column(modifier = Modifier.fillMaxSize()) {
        // The outline: a frame of padding around the video, in the accent colour while active.
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(if (isActive) c.primary else c.surfaceHigh)
                .padding(TILE_FRAME)
                .background(Color.Black),
            contentAlignment = Alignment.Center,
        ) {
            val player = info.player
            // The video surface stays mounted even when the tile has something to say
            // (the message is in the strip below): removing it would leave the player
            // holding a dead window, and a later reload would play into nothing.
            when {
                player == null && info.message != null -> Text(
                    info.message,
                    color = c.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(16.dp),
                )
                player == null -> Text("Starting...", color = c.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                else -> VlcPlayerView(
                    vlcPlayer = player,
                    modifier = Modifier.fillMaxSize(),
                    onClick = onActivate,
                )
            }
        }
        // A past video's seek bar sits between the picture and the strip, outside the video.
        if (vod != null) VodTransportRow(info.player, onTogglePlay = onTogglePlay, onSeek = onSeek)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(TILE_STRIP_HEIGHT)
                .background(c.surfaceContainer)
                .clickable(onClick = onActivate)
                .padding(start = 8.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Avatar(displayName = info.displayName, imageUrl = info.avatarUrl, size = 28)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    vod?.title?.ifBlank { null } ?: info.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    color = c.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val sub = when {
                    info.message != null -> info.message
                    audible -> "You are listening"
                    tile.silent -> if (isActive) "Selected, sound off" else "Sound off"
                    info.isBuffering -> "Loading..."
                    isActive -> "Selected"
                    else -> "Click to select"
                }
                Text(
                    if (vod != null && info.message == null) "${vod.kind.label} from ${info.displayName}. $sub" else sub,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (info.message == null && (audible || isActive)) c.primary else c.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            // Past videos have no ads to block.
            if (vod == null && (info.adBlockStatus != AdBlockStatus.UNKNOWN || info.player != null)) AdBlockPill(info.adBlockStatus)
            // This tile's own sound. It sits in the strip, below the video, because a click on
            // the native video surface never reaches Compose.
            ExpressiveIconButton(
                icon = if (tile.silent) ExpressiveIcons.VolumeOff else ExpressiveIcons.VolumeUp,
                contentDescription = if (tile.silent) "Unmute ${info.displayName}" else "Mute ${info.displayName}",
                onClick = onToggleMute,
                boxSize = 36.dp,
                iconSize = 18.dp,
            )
            ExpressiveSlider(
                value = if (tile.silent) 0f else tile.volume / 100f,
                onValueChange = { onVolume((it * 100f).roundToInt()) },
                modifier = Modifier.width(72.dp),
            )
            if (canMoveEarlier || canMoveLater) {
                ExpressiveIconButton(
                    icon = ExpressiveIcons.ArrowLeft,
                    contentDescription = "Move ${info.displayName} earlier",
                    onClick = onMoveEarlier,
                    boxSize = 36.dp,
                    iconSize = 18.dp,
                    enabled = canMoveEarlier,
                )
                ExpressiveIconButton(
                    icon = ExpressiveIcons.ArrowRight,
                    contentDescription = "Move ${info.displayName} later",
                    onClick = onMoveLater,
                    boxSize = 36.dp,
                    iconSize = 18.dp,
                    enabled = canMoveLater,
                )
            }
            ExpressiveIconButton(
                icon = ExpressiveIcons.OpenInNew,
                contentDescription = "Pop out ${info.displayName} into its own window",
                onClick = onPopOut,
                boxSize = 36.dp,
                iconSize = 18.dp,
            )
            ExpressiveIconButton(
                icon = ExpressiveIcons.Close,
                contentDescription = "Close ${info.displayName}",
                onClick = onClose,
                boxSize = 36.dp,
                iconSize = 18.dp,
            )
        }
    }
}

@Composable
private fun MultiTopBar(
    state: MultiViewUiState,
    pickerOpen: Boolean,
    chatOpen: Boolean,
    onToggleChat: () -> Unit,
    onBack: () -> Unit,
    onTogglePicker: () -> Unit,
    onListenAll: (Boolean) -> Unit,
    sleepLabel: String,
    sleepRunning: Boolean,
    onCycleSleep: () -> Unit,
    onPopOutAll: () -> Unit,
    onDockAll: () -> Unit,
) {
    val c = PureTvTheme.colors
    val layout = state.layout
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(c.surfaceContainer)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ExpressiveIconButton(
            icon = ExpressiveIcons.Back,
            contentDescription = "Back",
            onClick = onBack,
            boxSize = 48.dp,
            iconSize = 24.dp,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text("Multi-stream", style = MaterialTheme.typography.titleLarge, color = c.onSurface, maxLines = 1)
            Text(
                "${layout.count} of ${MultiViewState.MAX_TILES} streams",
                style = MaterialTheme.typography.bodySmall,
                color = c.onSurfaceVariant,
                maxLines = 1,
            )
        }
        ExpressiveButton(
            text = if (layout.listenAll) "Listen to: all streams" else "Listen to: selected stream",
            onClick = { onListenAll(!layout.listenAll) },
            style = if (layout.listenAll) ExpressiveButtonStyle.Tonal else ExpressiveButtonStyle.Text,
            size = ExpressiveButtonSize.Medium,
            icon = ExpressiveIcons.VolumeUp,
        )
        ExpressiveButton(
            text = sleepLabel,
            onClick = onCycleSleep,
            style = if (sleepRunning) ExpressiveButtonStyle.Tonal else ExpressiveButtonStyle.Text,
            size = ExpressiveButtonSize.Medium,
        )
        if (layout.docked.isNotEmpty()) {
            ExpressiveButton(
                text = "Pop out all",
                onClick = onPopOutAll,
                style = ExpressiveButtonStyle.Text,
                size = ExpressiveButtonSize.Medium,
                icon = ExpressiveIcons.OpenInNew,
            )
        }
        if (layout.popped.isNotEmpty()) {
            ExpressiveButton(
                text = "Dock all",
                onClick = onDockAll,
                style = ExpressiveButtonStyle.Text,
                size = ExpressiveButtonSize.Medium,
                icon = ExpressiveIcons.Grid,
            )
        }
        ExpressiveButton(
            text = if (pickerOpen) "Hide" else "Add channel",
            onClick = onTogglePicker,
            style = ExpressiveButtonStyle.Tonal,
            size = ExpressiveButtonSize.Medium,
            icon = ExpressiveIcons.Add,
            enabled = layout.canAdd,
        )
        ExpressiveIconButton(
            icon = ExpressiveIcons.Chat,
            contentDescription = if (chatOpen) "Hide chat" else "Show chat",
            onClick = onToggleChat,
            boxSize = 48.dp,
            iconSize = 24.dp,
            tint = if (chatOpen) c.primary else c.onSurfaceVariant,
        )
    }
}

/**
 * Pick a channel to add: the followed channels that are live right now, or any
 * channel by name.
 */
@Composable
private fun AddChannelPanel(
    koin: Koin,
    shown: MultiViewState,
    onFocusChanged: (Boolean) -> Unit,
    onAdd: (String) -> Unit,
    onAddVod: (TileSource.Vod) -> Unit,
) {
    val c = PureTvTheme.colors
    var pastOpen by remember { mutableStateOf(false) }
    val rail = rememberDesktopViewModel { koin.get<FollowedRailViewModel>() }
    val railState by rail.state.collectAsState()
    androidx.compose.runtime.LaunchedEffect(rail) { rail.refresh() }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(c.surfaceContainer)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            var typed by remember { mutableStateOf("") }
            fun submit() {
                val login = typed.trim()
                if (login.isNotEmpty()) {
                    onAdd(login)
                    typed = ""
                }
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(c.surfaceHigh)
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (typed.isEmpty()) {
                    Text("Type a channel name", color = c.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                }
                BasicTextField(
                    value = typed,
                    onValueChange = { typed = it },
                    singleLine = true,
                    textStyle = TextStyle(color = c.onSurface, fontSize = MaterialTheme.typography.bodyMedium.fontSize),
                    cursorBrush = SolidColor(c.primary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                    modifier = Modifier.fillMaxWidth().onFocusChanged { onFocusChanged(it.isFocused) },
                )
            }
            ExpressiveButton(text = "Add", onClick = ::submit, size = ExpressiveButtonSize.Medium, icon = ExpressiveIcons.Add)
        }

        ExpressiveButton(
            text = if (pastOpen) "Hide past broadcasts" else "Past broadcasts and highlights",
            onClick = { pastOpen = !pastOpen },
            style = if (pastOpen) ExpressiveButtonStyle.Tonal else ExpressiveButtonStyle.Text,
            size = ExpressiveButtonSize.Medium,
            icon = ExpressiveIcons.Play,
        )
        if (pastOpen) VodPickerPanel(koin = koin, onFocusChanged = onFocusChanged, onPick = onAddVod)

        val candidates = railState.live.filterNot { shown.contains(it.login) }
        when {
            !railState.isLoggedIn -> Text(
                "Sign in to pick from the channels you follow, or type any channel name above.",
                color = c.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
            candidates.isEmpty() -> Text(
                if (railState.isLoading) "Loading the channels you follow..." else "None of the channels you follow are live right now.",
                color = c.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
            else -> {
                Text("Live now from your follows", color = c.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    candidates.forEach { row ->
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(24.dp))
                                .background(c.surfaceHigh)
                                .clickable { onAdd(row.login) }
                                .padding(start = 8.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Avatar(displayName = row.displayName, imageUrl = row.avatarUrl, size = 32)
                            Column {
                                Text(row.displayName, style = MaterialTheme.typography.labelLarge, color = c.onSurface, maxLines = 1)
                                Text(
                                    "${formatViewerCount(row.viewerCount)} viewers",
                                    style = PureTvType.dataSmall,
                                    color = c.onSurfaceVariant,
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
