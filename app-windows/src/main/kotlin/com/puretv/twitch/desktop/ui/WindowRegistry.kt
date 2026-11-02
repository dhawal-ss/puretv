package com.puretv.twitch.desktop.ui

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class WindowKind { PRIMARY, SECONDARY }

/** What a window offers the rest of the app: being told to go somewhere, and coming forward. */
interface WindowHost {
    fun navigate(destination: WindowDestination)
    fun bringToFront()

    /** Shows the multi-stream grid (a popped-out tile asked for it). */
    fun openMultiGrid()
}

/**
 * One PureTV window as the registry knows it.
 *
 * [id] is never reused during a run, so a window that closes and one that opens straight
 * after can never be mistaken for each other. [slot] is the lowest free number (0 is the
 * main window), which is what a window's remembered place is filed under.
 */
data class WindowEntry(
    val id: Int,
    val slot: Int,
    val kind: WindowKind,
    /** Where a secondary window opens. */
    val start: WindowDestination = WindowDestination.Home,
    val title: String = "",
    /** A player screen (stream or video) is up: this window holds a player. */
    val playing: Boolean = false,
    /** A stream or multi-stream is up: the sleep timer has something to time. */
    val watching: Boolean = false,
)

sealed interface OpenResult {
    data class Opened(val windowId: Int) : OpenResult

    /** Nothing opened; [message] says why, in words for the viewer. */
    data class Refused(val message: String) : OpenResult
}

/** What closing a window means, see [WindowRegistry.closeAction]. */
enum class CloseAction {
    /** Just this window goes. */
    CLOSE_WINDOW,

    /** Every window hides in the tray and anything playing stops. */
    HIDE_ALL,

    /** PureTV exits. */
    QUIT,
}

/** What minimizing a window means, see [WindowRegistry.minimizeAction]. */
enum class MinimizeAction {
    /** The ordinary minimize to the taskbar. */
    MINIMIZE_ONLY,

    /** Every window hides in the tray and anything playing stops. */
    HIDE_ALL,
}

/**
 * Every PureTV window of this run and the rules between them: how many may be open, how
 * many players may run at once, which window was used last, what closing each one means,
 * and which one holds multi-stream. No AWT or Compose in here, so it is tested on its own.
 *
 * The main window (id 0) is always there. Secondary windows are added by [open], which is
 * all the rest of the app needs: main.kt shows a window for every secondary entry.
 *
 * Players: a window holds one while a stream or video screen is up. [tiles] counts the
 * multi-stream players on top (grid and popped-out windows alike).
 */
class WindowRegistry(
    val maxWindows: Int = MAX_WINDOWS,
    val maxPlayers: Int = MAX_PLAYERS,
    private val tiles: () -> Int = { 0 },
) {
    private val lock = Any()
    private var nextId = PRIMARY_ID + 1

    private val _windows = MutableStateFlow(listOf(WindowEntry(PRIMARY_ID, slot = 0, kind = WindowKind.PRIMARY)))

    /** Every open window, the main one first. */
    val windows: StateFlow<List<WindowEntry>> = _windows.asStateFlow()

    // Most recently focused last. Guarded by [lock].
    private val focusOrder = ArrayList<Int>()
    private val hosts = HashMap<Int, WindowHost>()
    private var multiHost: Int? = null

    private val _notice = MutableStateFlow<String?>(null)

    /** A short message for the viewer, for example why a window did not open. Cleared by [clearNotice]. */
    val notice: StateFlow<String?> = _notice.asStateFlow()

    fun postNotice(message: String) {
        _notice.value = message
    }

    fun clearNotice(message: String) {
        _notice.update { if (it == message) null else it }
    }

    // ---- Opening and closing ----------------------------------------------------

    /** Players running now: windows on a stream or video, plus the multi-stream tiles. */
    fun playersInUse(): Int = _windows.value.count { it.playing } + tiles()

    /** Whether one more player may start. The add-tile path of multi-stream asks this too. */
    fun canStartPlayer(): Boolean = playersInUse() < maxPlayers

    /** [canStartPlayer] for a caller that is about to start one: when not, the reason is posted as the [notice]. */
    fun admitPlayer(): Boolean {
        if (canStartPlayer()) return true
        postNotice(PLAYER_CAP_MESSAGE)
        return false
    }

    fun canOpenWindow(): Boolean = _windows.value.size < maxWindows

    /**
     * Opens a new window on [destination]. Refused, with a message already posted as the
     * [notice], when there are [maxWindows] windows, or when the destination starts a
     * player and [maxPlayers] are running.
     */
    fun open(destination: WindowDestination): OpenResult = synchronized(lock) {
        if (!canOpenWindow()) return refuse("PureTV already has $maxWindows windows open. Close one to open another.")
        if (destination.startsPlayer() && !canStartPlayer()) {
            return refuse(PLAYER_CAP_MESSAGE)
        }
        val entry = WindowEntry(
            id = nextId++,
            slot = lowestFreeSlot(),
            kind = WindowKind.SECONDARY,
            start = destination,
        )
        _windows.update { it + entry }
        OpenResult.Opened(entry.id)
    }

    private fun refuse(message: String): OpenResult.Refused {
        postNotice(message)
        return OpenResult.Refused(message)
    }

    private fun lowestFreeSlot(): Int {
        val used = _windows.value.map { it.slot }.toSet()
        return generateSequence(1) { it + 1 }.first { it !in used }
    }

    /** A secondary window closed. The main window cannot be removed; this returns false for it or an unknown id. */
    fun close(id: Int): Boolean = synchronized(lock) {
        val entry = _windows.value.firstOrNull { it.id == id } ?: return false
        if (entry.kind == WindowKind.PRIMARY) return false
        _windows.update { list -> list.filterNot { it.id == id } }
        focusOrder.remove(id)
        hosts.remove(id)
        if (multiHost == id) multiHost = null
        true
    }

    /**
     * What closing window [id] (its X button, Alt+F4) does. A secondary window just closes.
     * The main window always quits, with or without a tray, visible or hidden (a close that
     * arrives while hidden is Windows signing out or an installer). The tray is for minimizing,
     * see [minimizeAction].
     */
    fun closeAction(id: Int): CloseAction {
        val entry = _windows.value.firstOrNull { it.id == id } ?: return CloseAction.CLOSE_WINDOW
        return if (entry.kind == WindowKind.SECONDARY) CloseAction.CLOSE_WINDOW else CloseAction.QUIT
    }

    /**
     * What minimizing window [id] does. By default every window takes the ordinary taskbar
     * minimize and keeps playing. Only when the person turned on "Minimize to tray"
     * ([trayChosen]) does minimizing the main window send PureTV to the tray (everything
     * hides), and then only when the tray icon is really there ([trayAvailable]) and it is
     * [visible]; while an update is downloading ([updating]) a window must stay in the taskbar.
     */
    fun minimizeAction(
        id: Int,
        visible: Boolean,
        trayAvailable: Boolean,
        updating: Boolean = false,
        trayChosen: Boolean = false,
    ): MinimizeAction {
        val entry = _windows.value.firstOrNull { it.id == id } ?: return MinimizeAction.MINIMIZE_ONLY
        val toTray = entry.kind == WindowKind.PRIMARY && trayChosen && visible && trayAvailable && !updating
        return if (toTray) MinimizeAction.HIDE_ALL else MinimizeAction.MINIMIZE_ONLY
    }

    // ---- Focus ------------------------------------------------------------------

    /** Window [id] became the active one. */
    fun focused(id: Int) = synchronized(lock) {
        if (_windows.value.none { it.id == id }) return@synchronized
        focusOrder.remove(id)
        focusOrder.add(id)
    }

    /** The window used last, or the main window when none was focused yet. Always an open window. */
    fun mostRecentlyFocused(): Int = synchronized(lock) {
        focusOrder.lastOrNull { id -> _windows.value.any { it.id == id } } ?: PRIMARY_ID
    }

    // ---- State the windows report -------------------------------------------------

    fun setTitle(id: Int, title: String) = change(id) { if (it.title == title) it else it.copy(title = title) }

    /** [playing]: a stream or video screen is up. [watching]: a stream or multi-stream is up. */
    fun setActivity(id: Int, playing: Boolean, watching: Boolean) =
        change(id) { if (it.playing == playing && it.watching == watching) it else it.copy(playing = playing, watching = watching) }

    private fun change(id: Int, edit: (WindowEntry) -> WindowEntry) {
        _windows.update { list ->
            val i = list.indexOfFirst { it.id == id }
            if (i < 0) return@update list
            val next = edit(list[i])
            if (next === list[i]) list else list.toMutableList().also { it[i] = next }
        }
    }

    /** Some window has a stream up, so the sleep timer has something to time. */
    fun anyWatching(): Boolean = _windows.value.any { it.watching }

    // ---- Navigation ---------------------------------------------------------------

    fun attach(id: Int, host: WindowHost) = synchronized(lock) { hosts[id] = host }

    fun detach(id: Int, host: WindowHost) = synchronized(lock) {
        if (hosts[id] === host) hosts.remove(id)
    }

    /**
     * Sends [destination] to window [id] (default: the one used last), or to the main window when
     * that one is not ready yet. False when no window can take it yet.
     */
    fun navigate(destination: WindowDestination, id: Int = mostRecentlyFocused()): Boolean {
        val host = synchronized(lock) { hosts[id] ?: hosts[PRIMARY_ID] } ?: return false
        host.navigate(destination)
        return true
    }

    /** Brings every window forward: a second launch, the tray's Open PureTV. */
    fun bringAllToFront() {
        val all = synchronized(lock) { hosts.values.toList() }
        all.forEach { it.bringToFront() }
    }

    // ---- Multi-stream -------------------------------------------------------------

    /**
     * The multi-stream session is one for the whole app, and leaving its screen closes the
     * tiles in the grid, so only one window shows it at a time. True when [id] may (it
     * already does, or nobody does); false when another window has it.
     */
    fun claimMultiGrid(id: Int): Boolean = synchronized(lock) {
        val holder = multiHost
        if (holder != null && holder != id && _windows.value.any { it.id == holder }) return false
        multiHost = id
        true
    }

    fun releaseMultiGrid(id: Int) = synchronized(lock) {
        if (multiHost == id) multiHost = null
    }

    /** Brings the window that shows multi-stream forward, true when there is one other than [except]. */
    fun showMultiGridHolder(except: Int? = null): Boolean {
        val host = synchronized(lock) { multiHost?.takeIf { it != except }?.let { hosts[it] } } ?: return false
        host.bringToFront()
        return true
    }

    /** A popped-out tile asked for the grid: the window that has it, otherwise the main window. */
    fun openMultiGrid() {
        val host = synchronized(lock) { hosts[multiHost ?: PRIMARY_ID] } ?: return
        host.openMultiGrid()
    }

    companion object {
        const val PRIMARY_ID = 0
        const val MAX_WINDOWS = 6
        const val MAX_PLAYERS = 8
        private const val PLAYER_CAP_MESSAGE = "That is a lot of video at once. Stop a stream or close a window first."

        @Volatile private var installed: WindowRegistry? = null

        /** The registry of the running app, set once at startup (see the Koin module). */
        var instance: WindowRegistry
            get() = installed ?: WindowRegistry().also { installed = it }
            set(value) {
                installed = value
            }
    }
}

/**
 * Opens a new PureTV window on [destination], from anywhere (a click handler, a context
 * menu, the tray). On a refusal the reason is shown to the viewer in the window they are
 * using; the result says whether a window opened.
 */
fun openInNewWindow(destination: WindowDestination): OpenResult = WindowRegistry.instance.open(destination)
