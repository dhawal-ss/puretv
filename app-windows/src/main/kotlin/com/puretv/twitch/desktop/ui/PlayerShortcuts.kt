package com.puretv.twitch.desktop.ui

import com.puretv.twitch.desktop.platform.ShortcutKey
import java.awt.event.KeyEvent

/** What a keyboard shortcut on the live player screen does. */
enum class ShortcutAction {
    ToggleFullscreen,
    ToggleTheater,
    ToggleChat,
    TogglePlayPause,
    ExitImmersive,
    ToggleStats,
    FlipNext,
    FlipPrevious,
    VolumeUp,
    VolumeDown,
    ToggleMute,
    CycleSleepTimer,
    ToggleHelp,
    ToggleMini,

    // "Even out loud and quiet audio" (the audio normalizer), on or off.
    ToggleAudioNormalizer,
}

/**
 * The live player's keyboard table, in one place so it is tested and the shortcuts
 * sheet cannot drift from what the keys do. The screen reads keys at the AWT level
 * (the video surface is a native window that can take focus from Compose) and asks
 * this table what to do.
 *
 * F fullscreen, T theater, C chat, Space play/pause, Esc leaves fullscreen or theater
 * (and closes the shortcuts sheet), F3 upscaling stats (mpv), PageUp/PageDown change
 * channel, Up/Down volume, M mute, A evens out loud and quiet audio, Z sleep timer,
 * I mini player (Esc leaves it too), ? (Shift+/) or F1 this list.
 * Hold X compares the upscaler off and is handled separately because it needs the
 * release event too.
 */
object PlayerShortcuts {
    /** Volume change for one Up or Down press. */
    const val VOLUME_STEP = 5

    /**
     * The action for a key press, or null when it is not ours. [keyChar] is the typed
     * character (layout aware, which is how `?` is found on any keyboard); Ctrl and Alt
     * combinations are left alone so system and menu shortcuts keep working.
     * [immersive] is true in theater or fullscreen, when Esc has something to leave.
     * [helpOpen] lets Esc close the shortcuts sheet first.
     */
    fun actionFor(
        keyCode: Int,
        keyChar: Char,
        ctrlOrAlt: Boolean,
        immersive: Boolean,
        helpOpen: Boolean = false,
        // A text box (chat, emote search) has focus: every key is the viewer typing.
        textInputFocused: Boolean = false,
        // The Playback menu is open: Up and Down belong to its sliders and segments.
        menuOpen: Boolean = false,
        // The key is being held (auto-repeat). Only volume repeats; toggles fire once.
        isRepeat: Boolean = false,
    ): ShortcutAction? {
        if (textInputFocused) return null
        val action = actionForKey(keyCode, keyChar, ctrlOrAlt, immersive, helpOpen) ?: return null
        val volume = action == ShortcutAction.VolumeUp || action == ShortcutAction.VolumeDown
        if (volume && menuOpen) return null
        if (isRepeat && !volume) return null
        return action
    }

    private fun actionForKey(
        keyCode: Int,
        keyChar: Char,
        ctrlOrAlt: Boolean,
        immersive: Boolean,
        helpOpen: Boolean,
    ): ShortcutAction? {
        if (ctrlOrAlt) return null
        if (keyChar == '?') return ShortcutAction.ToggleHelp
        return when (keyCode) {
            KeyEvent.VK_F -> ShortcutAction.ToggleFullscreen
            KeyEvent.VK_T -> ShortcutAction.ToggleTheater
            KeyEvent.VK_C -> ShortcutAction.ToggleChat
            KeyEvent.VK_SPACE -> ShortcutAction.TogglePlayPause
            // Esc only acts when there is something to leave, so a stray Esc elsewhere passes through.
            KeyEvent.VK_ESCAPE -> when {
                helpOpen -> ShortcutAction.ToggleHelp
                immersive -> ShortcutAction.ExitImmersive
                else -> null
            }
            KeyEvent.VK_F3 -> ShortcutAction.ToggleStats
            KeyEvent.VK_F1 -> ShortcutAction.ToggleHelp
            KeyEvent.VK_PAGE_UP -> ShortcutAction.FlipNext
            KeyEvent.VK_PAGE_DOWN -> ShortcutAction.FlipPrevious
            KeyEvent.VK_UP -> ShortcutAction.VolumeUp
            KeyEvent.VK_DOWN -> ShortcutAction.VolumeDown
            KeyEvent.VK_M -> ShortcutAction.ToggleMute
            KeyEvent.VK_A -> ShortcutAction.ToggleAudioNormalizer
            KeyEvent.VK_Z -> ShortcutAction.CycleSleepTimer
            KeyEvent.VK_I -> ShortcutAction.ToggleMini
            else -> null
        }
    }

    /**
     * Whether [action] means anything in the mini player, which has no chat, no menus and
     * no room for the shortcuts sheet. Those keys are swallowed there rather than left to
     * change state the viewer cannot see. Sound keys (volume, mute, A) keep working: the
     * viewer hears what they do.
     */
    fun worksInMini(action: ShortcutAction): Boolean = when (action) {
        ShortcutAction.ToggleFullscreen,
        ShortcutAction.ToggleTheater,
        ShortcutAction.ToggleChat,
        ShortcutAction.ToggleStats,
        ShortcutAction.ToggleHelp,
        -> false
        else -> true
    }

    /** The volume after one Up or Down press from [volume] (0 to 100). */
    fun steppedVolume(volume: Int, up: Boolean): Int =
        (volume + if (up) VOLUME_STEP else -VOLUME_STEP).coerceIn(0, 100)

    /** One row of the shortcuts sheet. */
    data class Help(val keys: String, val what: String)

    val help: List<Help> = listOf(
        Help("Space", "Play or pause"),
        Help("F", "Fullscreen"),
        Help("T", "Theater mode"),
        Help("C", "Show or hide chat"),
        Help("I", "Mini player"),
        Help("Esc", "Leave fullscreen, theater or the mini player"),
        Help("Page Up / Page Down", "Next or previous channel"),
        Help("Up / Down", "Volume"),
        Help("M", "Mute"),
        Help("A", "Even out loud and quiet audio"),
        Help("Z", "Sleep timer"),
        Help("Hold X", "Compare with upscaling off (mpv)"),
        Help("F3", "Upscaling stats (mpv)"),
        Help("?", "This list"),
    )
}

/** What a keyboard shortcut does on the multi-stream screen. */
enum class MultiShortcutAction {
    VolumeUp,
    VolumeDown,
    ToggleMute,
    CycleSleepTimer,
    MoveEarlier,
    MoveLater,

    // Only when the selected tile is a past video.
    TogglePlayPause,
    SeekBack,
    SeekForward,
    SeekBackLarge,
    SeekForwardLarge,
    JumpBack,
    JumpForward,
}

/** How far a seek action skips in milliseconds (negative is back), or null for actions that do not seek. */
fun MultiShortcutAction.seekDeltaMs(): Long? = when (this) {
    MultiShortcutAction.SeekBack -> -VodTileLogic.ARROW_STEP_MS
    MultiShortcutAction.SeekForward -> VodTileLogic.ARROW_STEP_MS
    MultiShortcutAction.SeekBackLarge -> -VodTileLogic.ARROW_STEP_LARGE_MS
    MultiShortcutAction.SeekForwardLarge -> VodTileLogic.ARROW_STEP_LARGE_MS
    MultiShortcutAction.JumpBack -> -VodTileLogic.JUMP_STEP_MS
    MultiShortcutAction.JumpForward -> VodTileLogic.JUMP_STEP_MS
    else -> null
}

fun PopoutShortcutAction.seekDeltaMs(): Long? = when (this) {
    PopoutShortcutAction.SeekBack -> -VodTileLogic.ARROW_STEP_MS
    PopoutShortcutAction.SeekForward -> VodTileLogic.ARROW_STEP_MS
    PopoutShortcutAction.SeekBackLarge -> -VodTileLogic.ARROW_STEP_LARGE_MS
    PopoutShortcutAction.SeekForwardLarge -> VodTileLogic.ARROW_STEP_LARGE_MS
    PopoutShortcutAction.JumpBack -> -VodTileLogic.JUMP_STEP_MS
    PopoutShortcutAction.JumpForward -> VodTileLogic.JUMP_STEP_MS
    else -> null
}

/**
 * The multi-stream keyboard table. Up/Down and M act on the active tile (the only one
 * that is audible), Z is the sleep timer, and Ctrl+Left / Ctrl+Right move the active
 * tile one place earlier or later in the grid. Like [PlayerShortcuts] it does nothing
 * while a text box has focus.
 */
object MultiShortcuts {
    fun actionFor(
        keyCode: Int,
        ctrl: Boolean,
        alt: Boolean,
        textInputFocused: Boolean = false,
        // The key is being held (auto-repeat). Only volume repeats; the rest fire once.
        isRepeat: Boolean = false,
        // Shift is held: the arrows skip the long step.
        shift: Boolean = false,
        // The selected tile is a past video: Space, the arrows and J / L are its transport.
        vodSelected: Boolean = false,
    ): MultiShortcutAction? {
        if (textInputFocused || alt) return null
        val action = if (ctrl) {
            when (keyCode) {
                KeyEvent.VK_LEFT -> MultiShortcutAction.MoveEarlier
                KeyEvent.VK_RIGHT -> MultiShortcutAction.MoveLater
                else -> null
            }
        } else {
            when (keyCode) {
                KeyEvent.VK_UP -> MultiShortcutAction.VolumeUp
                KeyEvent.VK_DOWN -> MultiShortcutAction.VolumeDown
                KeyEvent.VK_M -> MultiShortcutAction.ToggleMute
                KeyEvent.VK_Z -> MultiShortcutAction.CycleSleepTimer
                KeyEvent.VK_SPACE -> if (vodSelected) MultiShortcutAction.TogglePlayPause else null
                KeyEvent.VK_LEFT -> if (vodSelected) (if (shift) MultiShortcutAction.SeekBackLarge else MultiShortcutAction.SeekBack) else null
                KeyEvent.VK_RIGHT -> if (vodSelected) (if (shift) MultiShortcutAction.SeekForwardLarge else MultiShortcutAction.SeekForward) else null
                KeyEvent.VK_J -> if (vodSelected) MultiShortcutAction.JumpBack else null
                KeyEvent.VK_L -> if (vodSelected) MultiShortcutAction.JumpForward else null
                else -> null
            }
        } ?: return null
        // Volume and skipping repeat while the key is held; the rest fire once.
        val repeats = action == MultiShortcutAction.VolumeUp || action == MultiShortcutAction.VolumeDown || action.seekDeltaMs() != null
        if (isRepeat && !repeats) return null
        return action
    }

    /**
     * The volume and mute state after Up or Down. Muted: Up brings the sound back (at the
     * stepped volume when the stored one is 0, so it is never silent), Down stays quiet.
     */
    fun nudged(volume: Int, soundOff: Boolean, up: Boolean): Pair<Int, Boolean> = when {
        soundOff && !up -> volume to true
        soundOff && volume > 0 -> volume to false
        else -> PlayerShortcuts.steppedVolume(volume, up) to false
    }

    /** The one-line hint under the grid, from the same table the keys use. */
    val HINT = "Up / Down volume and M mute (selected channel)   Z sleep timer   ${ShortcutKey.label()} + Left / Right move the selected channel"

    /** The hint when the selected tile is a past video. */
    val VOD_HINT = "Space play or pause   Left / Right skip 5 s (Shift 30 s)   J / L skip 10 s   Up / Down volume   M mute   Z sleep timer   ${ShortcutKey.label()} + Left / Right move"
}

/** What a keyboard shortcut does in a popped-out stream window. */
enum class PopoutShortcutAction {
    VolumeUp,
    VolumeDown,
    ToggleMute,
    ToggleFullscreen,
    ExitFullscreen,
    CloseTile,
    Dock,

    // Only in the window of a past video.
    TogglePlayPause,
    SeekBack,
    SeekForward,
    SeekBackLarge,
    SeekForwardLarge,
    JumpBack,
    JumpForward,
}

/**
 * The keyboard table of a popped-out stream window. Up/Down and M act on that window's
 * stream, F toggles borderless fullscreen on the monitor the window is on, Esc only leaves
 * fullscreen (it never closes anything), Ctrl+W closes the stream and D puts it back in the
 * multi-stream grid. Like the other tables it does nothing while a text box has focus.
 */
object PopoutShortcuts {
    fun actionFor(
        keyCode: Int,
        ctrl: Boolean,
        alt: Boolean,
        fullscreen: Boolean,
        textInputFocused: Boolean = false,
        isRepeat: Boolean = false,
        shift: Boolean = false,
        // The window plays a past video: Space, the arrows and J / L are its transport.
        vod: Boolean = false,
    ): PopoutShortcutAction? {
        if (textInputFocused || alt) return null
        val action = if (ctrl) {
            if (keyCode == KeyEvent.VK_W) PopoutShortcutAction.CloseTile else null
        } else {
            when (keyCode) {
                KeyEvent.VK_UP -> PopoutShortcutAction.VolumeUp
                KeyEvent.VK_DOWN -> PopoutShortcutAction.VolumeDown
                KeyEvent.VK_M -> PopoutShortcutAction.ToggleMute
                KeyEvent.VK_F -> PopoutShortcutAction.ToggleFullscreen
                KeyEvent.VK_ESCAPE -> if (fullscreen) PopoutShortcutAction.ExitFullscreen else null
                KeyEvent.VK_D -> PopoutShortcutAction.Dock
                KeyEvent.VK_SPACE -> if (vod) PopoutShortcutAction.TogglePlayPause else null
                KeyEvent.VK_LEFT -> if (vod) (if (shift) PopoutShortcutAction.SeekBackLarge else PopoutShortcutAction.SeekBack) else null
                KeyEvent.VK_RIGHT -> if (vod) (if (shift) PopoutShortcutAction.SeekForwardLarge else PopoutShortcutAction.SeekForward) else null
                KeyEvent.VK_J -> if (vod) PopoutShortcutAction.JumpBack else null
                KeyEvent.VK_L -> if (vod) PopoutShortcutAction.JumpForward else null
                else -> null
            }
        } ?: return null
        val repeats = action == PopoutShortcutAction.VolumeUp || action == PopoutShortcutAction.VolumeDown || action.seekDeltaMs() != null
        if (isRepeat && !repeats) return null
        return action
    }

    /** The one-line hint in a popped-out window, from the same table the keys use. */
    val HINT = "Up / Down volume   M mute   F fullscreen   Esc leave fullscreen   D dock back   ${ShortcutKey.label()} + W close"

    /** The hint in the window of a past video. */
    val VOD_HINT = "Space play or pause   Left / Right skip 5 s (Shift 30 s)   J / L skip 10 s   Up / Down volume   M mute   F fullscreen   D dock back   ${ShortcutKey.label()} + W close"
}

/**
 * Tells an auto-repeat from a fresh press for one key handler. A handler that stands down
 * (another window is active, a text box has focus) misses the key release, so it must
 * [clear] what it remembers, or the next real press of that key would count as a repeat
 * and be swallowed.
 */
class HeldKey {
    private var code = 0

    fun released(keyCode: Int) {
        if (keyCode == code) code = 0
    }

    /** Records the press and returns true when [keyCode] was already held. */
    fun pressed(keyCode: Int): Boolean = (keyCode == code).also { code = keyCode }

    fun clear() {
        code = 0
    }
}
