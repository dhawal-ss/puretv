package com.puretv.twitch.core.chat

import com.puretv.twitch.core.emotes.EmoteSource
import com.puretv.twitch.core.emotes.PickableEmote
import com.puretv.twitch.core.model.ChatMessage
import com.puretv.twitch.core.model.MessagePart
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ChatComposerTest {

    private fun emote(code: String) =
        PickableEmote(code = code, imageUrl = "", animated = false, source = EmoteSource.BTTV)

    private val all = listOf(
        emote("Kappa"),
        emote("KappaPride"),
        emote("PogChamp"),
        emote("pogU"),
        emote("LUL"),
    )

    private fun msg(login: String, display: String = login, system: Boolean = false, id: String = login) = ChatMessage(
        id = id,
        channel = "c",
        username = login,
        displayName = display,
        color = "#fff",
        message = "hi",
        parsedParts = listOf(MessagePart.Text("hi")),
        badges = emptyList(),
        timestamp = 0L,
        isSubscriber = false,
        isModerator = false,
        isBroadcaster = false,
        isSystem = system,
    )

    // ── wordAtCursor ────────────────────────────────────────────────────────────

    @Test fun wordAtCursorAtStart() {
        assertEquals("" to 0, wordAtCursor("", 0))
    }

    @Test fun wordAtCursorMidWord() {
        // "Kap|pa": cursor after "Kap"
        assertEquals("Kap" to 0, wordAtCursor("Kappa", 3))
    }

    @Test fun wordAtCursorAfterSpaceIsEmpty() {
        // "hi |": cursor right after the space starts a fresh (empty) word.
        assertEquals("" to 3, wordAtCursor("hi ", 3))
    }

    @Test fun wordAtCursorSecondWord() {
        // "hi Kap|pa" cursor after "Kap" of the second word (index 6).
        assertEquals("Kap" to 3, wordAtCursor("hi Kappa", 6))
    }

    @Test fun wordAtCursorClampsOutOfRange() {
        assertEquals("Kappa" to 0, wordAtCursor("Kappa", 999))
    }

    @Test fun wordAtCursorKeepsTheAtSign() {
        assertEquals("@bo" to 3, wordAtCursor("hi @bo", 6))
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
        // "Kap|" becomes "Kappa " with the cursor after the trailing space.
        val (text, cursor) = completeWord("Kap", 3, "Kappa")
        assertEquals("Kappa ", text)
        assertEquals(6, cursor)
    }

    @Test fun completeWordReplacesSecondWord() {
        // "hi Kap|pa": replace the "Kap" partial, keep trailing "pa".
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

    @Test fun completeWordReplacesAtMentionWholesale() {
        // "hi @bo|" completes to "hi @Bob " without doubling the @.
        val (text, cursor) = completeWord("hi @bo", 6, "@Bob")
        assertEquals("hi @Bob ", text)
        assertEquals(8, cursor)
    }

    // ── insertAtCursor ──────────────────────────────────────────────────────────

    @Test fun insertAtCursorEmpty() {
        val (text, cursor) = insertAtCursor("", 0, "Kappa")
        assertEquals("Kappa ", text)
        assertEquals(6, cursor)
    }

    @Test fun insertAtCursorAddsLeadingSpaceAfterWord() {
        // "hi|" needs a leading space before the emote.
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

    // ── recentChatters ──────────────────────────────────────────────────────────

    @Test fun recentChattersNewestFirstAndDistinct() {
        val msgs = listOf(msg("alice", id = "1"), msg("bob", id = "2"), msg("alice", id = "3"), msg("carol", id = "4"))
        assertEquals(listOf("carol", "alice", "bob"), recentChatters(msgs).map { it.login })
    }

    @Test fun recentChattersDedupesCaseInsensitively() {
        val msgs = listOf(msg("Alice", id = "1"), msg("alice", id = "2"))
        assertEquals(1, recentChatters(msgs).size)
    }

    @Test fun recentChattersSkipsSystemRowsBlankNamesAndSelf() {
        val msgs = listOf(
            msg("bob", id = "1"),
            msg("", id = "2", system = true),
            msg("me", id = "3"),
        )
        assertEquals(listOf("bob"), recentChatters(msgs, excludeLogin = "ME").map { it.login })
    }

    @Test fun recentChattersRespectsLimit() {
        val msgs = (1..10).map { msg("user$it", id = "$it") }
        assertEquals(listOf("user10", "user9", "user8"), recentChatters(msgs, limit = 3).map { it.login })
    }

    // ── matchChatters / mentionName ─────────────────────────────────────────────

    private val chatters = listOf(
        Chatter("bobby", "Bobby", ""),
        Chatter("bob", "Bob", ""),
        Chatter("carol", "Carol", ""),
        Chatter("xiaoming", "小明", ""),
    )

    @Test fun matchChattersPrefixCaseInsensitiveInRecencyOrder() {
        assertEquals(listOf("bobby", "bob"), matchChatters("BO", chatters).map { it.login })
    }

    @Test fun matchChattersEmptyPrefixMatchesEveryone() {
        assertEquals(4, matchChatters("", chatters).size)
    }

    @Test fun matchChattersMatchesDisplayNameToo() {
        assertEquals(listOf("xiaoming"), matchChatters("小", chatters).map { it.login })
    }

    @Test fun mentionNameUsesDisplayNameOnlyWhenItIsTheLogin() {
        assertEquals("Bob", Chatter("bob", "Bob", "").mentionName)
        assertEquals("xiaoming", Chatter("xiaoming", "小明", "").mentionName)
    }

    // ── composerSuggestions ─────────────────────────────────────────────────────

    @Test fun atPrefixSuggestsOnlyChatters() {
        val s = composerSuggestions("@ca", all, chatters)
        assertEquals(listOf("@Carol"), s.map { it.completion })
        assertTrue(s.all { it is ComposerSuggestion.User })
    }

    @Test fun bareAtListsRecentChatters() {
        val s = composerSuggestions("@", all, chatters, limit = 2)
        assertEquals(listOf("@Bobby", "@Bob"), s.map { it.completion })
    }

    @Test fun plainPrefixRanksEmotesBeforeChatters() {
        val emotes = all + emote("Bobble")
        val s = composerSuggestions("bob", emotes, chatters)
        assertEquals(listOf("Bobble", "@Bobby", "@Bob"), s.map { it.completion })
    }

    @Test fun plainPrefixReservesSlotsForChatters() {
        val many = (1..20).map { emote("bob$it") }
        val s = composerSuggestions("bob", many, chatters, limit = 8, userSlots = 2)
        assertEquals(8, s.size)
        assertEquals(listOf("@Bobby", "@Bob"), s.takeLast(2).map { it.completion })
    }

    @Test fun shortPlainWordSuggestsNothing() {
        assertEquals(emptyList(), composerSuggestions("b", all, chatters))
        assertEquals(emptyList(), composerSuggestions("", all, chatters))
    }

    @Test fun localizedNameCompletesToLogin() {
        val s = composerSuggestions("@小", all, chatters)
        assertEquals(listOf("@xiaoming"), s.map { it.completion })
    }

    // ── ":" emote search (issue #35) ────────────────────────────────────────────

    @Test fun colonPrefixSuggestsOnlyEmotes() {
        val people = listOf(Chatter("pogfan", "pogfan", "#fff"))
        val s = composerSuggestions(":pog", all, people)
        assertEquals(listOf("PogChamp", "pogU"), s.map { it.completion })
        assertTrue(s.all { it is ComposerSuggestion.Emote })
    }

    @Test fun colonSearchPutsStartsBeforeContains() {
        val emotes = listOf(emote("OMEGAKEK"), emote("KEKW"), emote("kekLaugh"))
        assertEquals(listOf("KEKW", "kekLaugh", "OMEGAKEK"), composerSuggestions(":kek", emotes, emptyList()).map { it.completion })
    }

    @Test fun colonNeedsTwoLetters() {
        assertEquals(emptyList(), composerSuggestions(":", all, emptyList()))
        assertEquals(emptyList(), composerSuggestions(":k", all, emptyList()))
    }

    @Test fun colonCompletionReplacesTheColonToo() {
        assertEquals("hi PogChamp " to 12, completeWord("hi :pog", 7, "PogChamp"))
    }

    // ── Tab cycling (issue #35) ─────────────────────────────────────────────────

    @Test fun firstTabFillsTheFirstOption() {
        val r = tabComplete("hi pog", 6, listOf("PogChamp", "pogU"), previous = null)!!
        assertEquals("hi PogChamp ", r.text)
        assertEquals(12, r.cursor)
        assertEquals(0, r.state.index)
    }

    @Test fun nextTabSwapsInTheNextOptionAndWraps() {
        val opts = listOf("PogChamp", "pogU")
        val first = tabComplete("hi pog there", 6, opts, previous = null)!!
        assertEquals("hi PogChamp  there", first.text)
        // The strip's own options are gone once the word is complete: the cycle carries them.
        val second = tabComplete(first.text, first.cursor, emptyList(), first.state)!!
        assertEquals("hi pogU  there", second.text)
        assertEquals(8, second.cursor)
        val third = tabComplete(second.text, second.cursor, emptyList(), second.state)!!
        assertEquals("hi PogChamp  there", third.text)
    }

    @Test fun shiftTabGoesBackwards() {
        val opts = listOf("a1", "a2", "a3")
        val first = tabComplete("aa", 2, opts, previous = null, backwards = true)!!
        assertEquals("a3 ", first.text)
        val second = tabComplete(first.text, first.cursor, emptyList(), first.state, backwards = true)!!
        assertEquals("a2 ", second.text)
    }

    @Test fun typingEndsTheCycle() {
        val first = tabComplete("pog", 3, listOf("PogChamp", "pogU"), previous = null)!!
        assertTrue(first.state.isCurrent(first.text, first.cursor))
        val typed = first.text + "lu"
        assertTrue(!first.state.isCurrent(typed, typed.length))
        // A fresh word starts a fresh cycle from its own options.
        val r = tabComplete(typed, typed.length, listOf("LUL"), first.state)!!
        assertEquals("PogChamp LUL ", r.text)
    }

    @Test fun nothingToCompleteIsNull() {
        assertEquals(null, tabComplete("hello", 5, emptyList(), previous = null))
    }
}
