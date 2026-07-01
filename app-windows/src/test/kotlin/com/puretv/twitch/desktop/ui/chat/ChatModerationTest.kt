package com.puretv.twitch.desktop.ui.chat

import com.puretv.twitch.core.model.ChatMessage
import com.puretv.twitch.core.model.MessagePart
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ChatModerationTest {

    private fun msg(
        id: String,
        login: String,
        body: String = "hello",
        deleted: Boolean = false,
        isSystem: Boolean = false,
    ) = ChatMessage(
        id = id,
        channel = "c",
        username = login,
        displayName = login,
        color = "#fff",
        message = body,
        parsedParts = listOf(MessagePart.Text(body)),
        badges = emptyList(),
        timestamp = 0L,
        isSubscriber = false,
        isModerator = false,
        isBroadcaster = false,
        deleted = deleted,
        isSystem = isSystem,
    )

    // ---- markUserDeleted (CLEARCHAT) ----

    @Test fun clearChatTombstonesAllMessagesFromUserCaseInsensitive() {
        val before = listOf(msg("1", "Bob"), msg("2", "alice"), msg("3", "bob"))
        val after = ChatModeration.markUserDeleted(before, "BOB")
        assertTrue(after[0].deleted)
        assertFalse(after[1].deleted)
        assertTrue(after[2].deleted)
    }

    @Test fun clearChatReturnsSameListWhenNobodyMatched() {
        val before = listOf(msg("1", "bob"), msg("2", "alice"))
        assertSame(before, ChatModeration.markUserDeleted(before, "carol"))
    }

    @Test fun clearChatSkipsSystemRowsAndBlankTarget() {
        val before = listOf(msg("sys", "", isSystem = true), msg("1", ""))
        assertSame(before, ChatModeration.markUserDeleted(before, ""))
        // A system row authored "by" no one must never be tombstoned.
        val after = ChatModeration.markUserDeleted(before, "anyone")
        assertSame(before, after)
    }

    @Test fun clearChatDoesNotReTombstoneAlreadyDeleted() {
        val before = listOf(msg("1", "bob", deleted = true))
        assertSame(before, ChatModeration.markUserDeleted(before, "bob"))
    }

    // ---- markMessageDeleted (CLEARMSG) ----

    @Test fun clearMessageTombstonesOnlyTheTargetId() {
        val before = listOf(msg("1", "bob"), msg("2", "bob"))
        val after = ChatModeration.markMessageDeleted(before, "2")
        assertFalse(after[0].deleted)
        assertTrue(after[1].deleted)
    }

    @Test fun clearMessageReturnsSameListWhenIdAbsent() {
        val before = listOf(msg("1", "bob"))
        assertSame(before, ChatModeration.markMessageDeleted(before, "nope"))
    }

    // ---- mentionsSelf ----

    @Test fun mentionMatchesOnWordBoundary() {
        assertTrue(ChatModeration.mentionsSelf("hey @bob how are you", "bob"))
        assertTrue(ChatModeration.mentionsSelf("ping @Bob!", "bob")) // case-insensitive + punctuation boundary
        assertTrue(ChatModeration.mentionsSelf("@bob", "bob")) // end of string
    }

    @Test fun mentionDoesNotMatchLongerLogin() {
        assertFalse(ChatModeration.mentionsSelf("hi @bobby", "bob"))
        assertFalse(ChatModeration.mentionsSelf("hi @bob_smith", "bob"))
    }

    @Test fun mentionBlankOrNullLoginNeverMatches() {
        assertFalse(ChatModeration.mentionsSelf("@bob", null))
        assertFalse(ChatModeration.mentionsSelf("@bob", ""))
        assertFalse(ChatModeration.mentionsSelf("no mention here", "bob"))
    }

    @Test fun mentionFindsLaterOccurrenceAfterNearMiss() {
        // First "@bobby" is a near miss; the later "@bob " is a real hit.
        assertTrue(ChatModeration.mentionsSelf("@bobby and also @bob ok", "bob"))
    }
}
