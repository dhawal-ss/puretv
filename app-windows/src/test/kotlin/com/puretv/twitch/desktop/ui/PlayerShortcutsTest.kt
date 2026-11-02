package com.puretv.twitch.desktop.ui

import java.awt.event.KeyEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlayerShortcutsTest {
    private fun action(code: Int, char: Char = KeyEvent.CHAR_UNDEFINED, ctrlOrAlt: Boolean = false, immersive: Boolean = false, help: Boolean = false) =
        PlayerShortcuts.actionFor(code, char, ctrlOrAlt, immersive, help)

    @Test fun existingShortcutsStillWork() {
        assertEquals(ShortcutAction.ToggleFullscreen, action(KeyEvent.VK_F))
        assertEquals(ShortcutAction.ToggleTheater, action(KeyEvent.VK_T))
        assertEquals(ShortcutAction.ToggleChat, action(KeyEvent.VK_C))
        assertEquals(ShortcutAction.TogglePlayPause, action(KeyEvent.VK_SPACE))
        assertEquals(ShortcutAction.ToggleStats, action(KeyEvent.VK_F3))
    }

    @Test fun escapeOnlyActsWhenThereIsSomethingToLeave() {
        assertNull(action(KeyEvent.VK_ESCAPE))
        assertEquals(ShortcutAction.ExitImmersive, action(KeyEvent.VK_ESCAPE, immersive = true))
    }

    @Test fun escapeClosesTheShortcutsSheetBeforeLeavingFullscreen() {
        assertEquals(ShortcutAction.ToggleHelp, action(KeyEvent.VK_ESCAPE, immersive = true, help = true))
        assertEquals(ShortcutAction.ToggleHelp, action(KeyEvent.VK_ESCAPE, help = true))
    }

    @Test fun pageKeysFlipUpDownAreVolumeMAndZ() {
        assertEquals(ShortcutAction.FlipNext, action(KeyEvent.VK_PAGE_UP))
        assertEquals(ShortcutAction.FlipPrevious, action(KeyEvent.VK_PAGE_DOWN))
        assertEquals(ShortcutAction.VolumeUp, action(KeyEvent.VK_UP))
        assertEquals(ShortcutAction.VolumeDown, action(KeyEvent.VK_DOWN))
        assertEquals(ShortcutAction.ToggleMute, action(KeyEvent.VK_M))
        assertEquals(ShortcutAction.CycleSleepTimer, action(KeyEvent.VK_Z))
    }

    @Test fun aEvensOutLoudAndQuietAudio() {
        assertEquals(ShortcutAction.ToggleAudioNormalizer, action(KeyEvent.VK_A, 'a'))
        assertEquals(ShortcutAction.ToggleAudioNormalizer, action(KeyEvent.VK_A, 'a', immersive = true))
        // Ctrl+A (select all) and an "a" typed in chat are not ours.
        assertNull(action(KeyEvent.VK_A, ctrlOrAlt = true))
        assertNull(PlayerShortcuts.actionFor(KeyEvent.VK_A, 'a', false, false, textInputFocused = true))
        // A toggle: holding the key flips it once, not on every repeat.
        assertNull(PlayerShortcuts.actionFor(KeyEvent.VK_A, 'a', false, false, isRepeat = true))
        // It is about sound, so the mini player keeps it, and the sheet lists it.
        assertTrue(PlayerShortcuts.worksInMini(ShortcutAction.ToggleAudioNormalizer))
        assertTrue(PlayerShortcuts.help.any { it.keys == "A" && "audio" in it.what })
    }

    @Test fun theQuestionMarkOpensTheSheetOnAnyLayout() {
        // Shift+/ on a US layout, and a layout where ? sits on another key.
        assertEquals(ShortcutAction.ToggleHelp, action(KeyEvent.VK_SLASH, '?'))
        assertEquals(ShortcutAction.ToggleHelp, action(KeyEvent.VK_UNDEFINED, '?'))
        assertEquals(ShortcutAction.ToggleHelp, action(KeyEvent.VK_F1))
        // A plain slash is not ours.
        assertNull(action(KeyEvent.VK_SLASH, '/'))
    }

    @Test fun controlAndAltCombinationsAreLeftAlone() {
        assertNull(action(KeyEvent.VK_C, ctrlOrAlt = true))
        assertNull(action(KeyEvent.VK_F, ctrlOrAlt = true))
        assertNull(action(KeyEvent.VK_F, '?', ctrlOrAlt = true))
    }

    @Test fun unknownKeysPassThrough() {
        assertNull(action(KeyEvent.VK_Q))
        assertNull(action(KeyEvent.VK_ENTER))
    }

    @Test fun nothingFiresWhileATextBoxHasFocus() {
        for (code in listOf(KeyEvent.VK_F, KeyEvent.VK_SPACE, KeyEvent.VK_Z, KeyEvent.VK_UP, KeyEvent.VK_PAGE_UP, KeyEvent.VK_ESCAPE)) {
            assertNull(PlayerShortcuts.actionFor(code, KeyEvent.CHAR_UNDEFINED, false, true, textInputFocused = true))
        }
        assertNull(PlayerShortcuts.actionFor(KeyEvent.VK_SLASH, '?', false, false, textInputFocused = true))
    }

    @Test fun upAndDownBelongToThePlaybackMenuWhileItIsOpen() {
        assertNull(PlayerShortcuts.actionFor(KeyEvent.VK_UP, KeyEvent.CHAR_UNDEFINED, false, false, menuOpen = true))
        assertNull(PlayerShortcuts.actionFor(KeyEvent.VK_DOWN, KeyEvent.CHAR_UNDEFINED, false, false, menuOpen = true))
        assertEquals(
            ShortcutAction.ToggleMute,
            PlayerShortcuts.actionFor(KeyEvent.VK_M, 'm', false, false, menuOpen = true),
        )
    }

    @Test fun onlyVolumeRepeatsWhenAKeyIsHeld() {
        assertEquals(
            ShortcutAction.VolumeUp,
            PlayerShortcuts.actionFor(KeyEvent.VK_UP, KeyEvent.CHAR_UNDEFINED, false, false, isRepeat = true),
        )
        assertNull(PlayerShortcuts.actionFor(KeyEvent.VK_Z, 'z', false, false, isRepeat = true))
        assertNull(PlayerShortcuts.actionFor(KeyEvent.VK_PAGE_UP, KeyEvent.CHAR_UNDEFINED, false, false, isRepeat = true))
    }

    @Test fun volumeStepsByFiveAndStaysInRange() {
        assertEquals(55, PlayerShortcuts.steppedVolume(50, up = true))
        assertEquals(45, PlayerShortcuts.steppedVolume(50, up = false))
        assertEquals(100, PlayerShortcuts.steppedVolume(98, up = true))
        assertEquals(0, PlayerShortcuts.steppedVolume(3, up = false))
    }

    @Test fun iTogglesTheMiniPlayerAndEscLeavesIt() {
        assertEquals(ShortcutAction.ToggleMini, action(KeyEvent.VK_I))
        assertEquals(ShortcutAction.ToggleMini, action(KeyEvent.VK_I, immersive = true))
        assertEquals(ShortcutAction.ExitImmersive, action(KeyEvent.VK_ESCAPE, immersive = true))
        assertNull(action(KeyEvent.VK_I, ctrlOrAlt = true))
        // Typing an "i" in chat is not a shortcut.
        assertNull(PlayerShortcuts.actionFor(KeyEvent.VK_I, 'i', false, false, textInputFocused = true))
    }

    @Test fun theMiniPlayerKeepsOnlyTheKeysThatMeanSomethingThere() {
        for (swallowed in listOf(
            ShortcutAction.ToggleFullscreen, ShortcutAction.ToggleTheater, ShortcutAction.ToggleChat,
            ShortcutAction.ToggleStats, ShortcutAction.ToggleHelp,
        )) assertTrue(!PlayerShortcuts.worksInMini(swallowed), "$swallowed")
        for (kept in listOf(
            ShortcutAction.TogglePlayPause, ShortcutAction.ExitImmersive, ShortcutAction.ToggleMini,
            ShortcutAction.FlipNext, ShortcutAction.FlipPrevious, ShortcutAction.VolumeUp,
            ShortcutAction.VolumeDown, ShortcutAction.ToggleMute, ShortcutAction.CycleSleepTimer,
        )) assertTrue(PlayerShortcuts.worksInMini(kept), "$kept")
    }

    @Test fun theSheetListsTheMiniPlayerKey() {
        assertTrue(PlayerShortcuts.help.any { it.keys == "I" })
    }

    @Test fun everyActionHasARowInTheSheet() {
        val text = PlayerShortcuts.help.joinToString("|") { it.keys + " " + it.what }
        assertTrue("Page Up" in text && "Volume" in text && "Mute" in text && "Sleep" in text && "?" in text)
        // No en or em dashes in anything the viewer reads (code points 0x2013 and 0x2014).
        assertTrue(PlayerShortcuts.help.none { row -> (row.keys + row.what).any { it.code == 0x2013 || it.code == 0x2014 } })
    }

    private fun multi(code: Int, ctrl: Boolean = false, alt: Boolean = false, text: Boolean = false, repeat: Boolean = false) =
        MultiShortcuts.actionFor(code, ctrl, alt, text, repeat)

    @Test fun multiStreamKeysAreVolumeMuteSleepAndMove() {
        assertEquals(MultiShortcutAction.VolumeUp, multi(KeyEvent.VK_UP))
        assertEquals(MultiShortcutAction.VolumeDown, multi(KeyEvent.VK_DOWN))
        assertEquals(MultiShortcutAction.ToggleMute, multi(KeyEvent.VK_M))
        assertEquals(MultiShortcutAction.CycleSleepTimer, multi(KeyEvent.VK_Z))
        assertEquals(MultiShortcutAction.MoveEarlier, multi(KeyEvent.VK_LEFT, ctrl = true))
        assertEquals(MultiShortcutAction.MoveLater, multi(KeyEvent.VK_RIGHT, ctrl = true))
    }

    @Test fun multiStreamLeavesOtherKeysAndModifiersAlone() {
        assertNull(multi(KeyEvent.VK_LEFT))
        assertNull(multi(KeyEvent.VK_UP, ctrl = true))
        assertNull(multi(KeyEvent.VK_M, alt = true))
        assertNull(multi(KeyEvent.VK_F))
    }

    @Test fun multiStreamKeysDoNothingWhileTyping() {
        assertNull(multi(KeyEvent.VK_M, text = true))
        assertNull(multi(KeyEvent.VK_UP, text = true))
        assertNull(multi(KeyEvent.VK_LEFT, ctrl = true, text = true))
    }

    @Test fun onlyVolumeRepeatsInMultiStream() {
        assertEquals(MultiShortcutAction.VolumeUp, multi(KeyEvent.VK_UP, repeat = true))
        assertNull(multi(KeyEvent.VK_M, repeat = true))
        assertNull(multi(KeyEvent.VK_Z, repeat = true))
        assertNull(multi(KeyEvent.VK_RIGHT, ctrl = true, repeat = true))
    }

    @Test fun theMultiStreamHintHasNoLongDashes() {
        assertTrue(MultiShortcuts.HINT.none { it.code == 0x2013 || it.code == 0x2014 })
    }

    @Test fun upFromMutedBringsBackSoundEvenWhenTheStoredVolumeIsZero() {
        assertEquals(40 to false, MultiShortcuts.nudged(40, soundOff = true, up = true))
        assertEquals(5 to false, MultiShortcuts.nudged(0, soundOff = true, up = true))
        assertEquals(40 to true, MultiShortcuts.nudged(40, soundOff = true, up = false))
        assertEquals(45 to false, MultiShortcuts.nudged(40, soundOff = false, up = true))
        assertEquals(0 to false, MultiShortcuts.nudged(0, soundOff = false, up = false))
    }
}
