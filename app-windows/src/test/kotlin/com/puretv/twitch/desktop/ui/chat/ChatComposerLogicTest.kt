package com.puretv.twitch.desktop.ui.chat

import com.puretv.twitch.core.emotes.EmoteSource
import com.puretv.twitch.core.emotes.PickableEmote
import kotlin.test.Test
import kotlin.test.assertEquals

class ChatComposerLogicTest {

    private fun emote(code: String) =
        PickableEmote(code = code, imageUrl = "", animated = false, source = EmoteSource.BTTV)

    private val all = listOf(
        emote("Kappa"),
        emote("KappaPride"),
        emote("PogChamp"),
        emote("pogU"),
        emote("LUL"),
    )

    // ── wordAtCursor ────────────────────────────────────────────────────────────

    @Test fun wordAtCursorAtStart() {
        assertEquals("" to 0, wordAtCursor("", 0))
    }

    @Test fun wordAtCursorMidWord() {
        // "Kap|pa" — cursor after "Kap"
        assertEquals("Kap" to 0, wordAtCursor("Kappa", 3))
    }

    @Test fun wordAtCursorAfterSpaceIsEmpty() {
        // "hi |" — cursor right after the space starts a fresh (empty) word.
        assertEquals("" to 3, wordAtCursor("hi ", 3))
    }

    @Test fun wordAtCursorSecondWord() {
        // "hi Kap|pa" cursor after "Kap" of the second word (index 6).
        assertEquals("Kap" to 3, wordAtCursor("hi Kappa", 6))
    }

    @Test fun wordAtCursorClampsOutOfRange() {
        assertEquals("Kappa" to 0, wordAtCursor("Kappa", 999))
    }

    // ── matchEmotes ─────────────────────────────────────────────────────────────

    @Test fun matchEmotesIgnoresShortWords() {
        assertEquals(emptyList(), matchEmotes("K", all))
        assertEquals(emptyList(), matchEmotes("", all))
    }

    @Test fun matchEmotesPrefixCaseInsensitive() {
        val matches = matchEmotes("kap", all).map { it.code }
        assertEquals(listOf("Kappa", "KappaPride"), matches)
    }

    @Test fun matchEmotesPogMatchesBothCases() {
        val matches = matchEmotes("pog", all).map { it.code }
        assertEquals(listOf("PogChamp", "pogU"), matches)
    }

    @Test fun matchEmotesNoMatch() {
        assertEquals(emptyList(), matchEmotes("zzz", all))
    }

    @Test fun matchEmotesRespectsLimit() {
        val many = (1..20).map { emote("Emote$it") }
        assertEquals(3, matchEmotes("Emote", many, limit = 3).size)
    }

    // ── completeWord ────────────────────────────────────────────────────────────

    @Test fun completeWordReplacesPartial() {
        // "Kap|" → "Kappa " with cursor after the trailing space.
        val (text, cursor) = completeWord("Kap", 3, "Kappa")
        assertEquals("Kappa ", text)
        assertEquals(6, cursor)
    }

    @Test fun completeWordReplacesSecondWord() {
        // "hi Kap|pa" → replace the "Kap" partial, keep trailing "pa".
        val (text, cursor) = completeWord("hi Kappa", 6, "Kappa")
        assertEquals("hi Kappa pa", text)
        // start (3) + "Kappa".length (5) + space (1)
        assertEquals(9, cursor)
    }

    @Test fun completeWordAtEmptyStart() {
        val (text, cursor) = completeWord("", 0, "LUL")
        assertEquals("LUL ", text)
        assertEquals(4, cursor)
    }

    // ── insertAtCursor ──────────────────────────────────────────────────────────

    @Test fun insertAtCursorEmpty() {
        val (text, cursor) = insertAtCursor("", 0, "Kappa")
        assertEquals("Kappa ", text)
        assertEquals(6, cursor)
    }

    @Test fun insertAtCursorAddsLeadingSpaceAfterWord() {
        // "hi|" → needs a leading space before the emote.
        val (text, cursor) = insertAtCursor("hi", 2, "Kappa")
        assertEquals("hi Kappa ", text)
        assertEquals(9, cursor)
    }

    @Test fun insertAtCursorNoLeadingSpaceAfterSpace() {
        val (text, cursor) = insertAtCursor("hi ", 3, "Kappa")
        assertEquals("hi Kappa ", text)
        assertEquals(9, cursor)
    }

    @Test fun insertAtCursorInMiddle() {
        // "hi there", cursor after "hi" (index 2): inserts " Kappa " before " there".
        val (text, cursor) = insertAtCursor("hi there", 2, "Kappa")
        assertEquals("hi Kappa  there", text)
        assertEquals(9, cursor)
    }
}
