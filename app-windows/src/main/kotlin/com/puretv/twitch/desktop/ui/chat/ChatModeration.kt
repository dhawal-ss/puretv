package com.puretv.twitch.desktop.ui.chat

import com.puretv.twitch.core.model.ChatMessage

/**
 * Pure helpers for Twitch chat moderation + mention state, extracted from
 * `StreamViewModel` so the tombstoning/highlight rules are unit-testable in
 * isolation (same pattern as [SelfEcho]/[ChatScrollPolicy]).
 *
 * All functions are allocation-conservative: they return the SAME list instance
 * when nothing changed, so an unrelated CLEARCHAT/CLEARMSG doesn't churn the
 * Compose state or force a recomposition.
 */
object ChatModeration {

    /**
     * Tombstone every message authored by [targetLogin] (CLEARCHAT timeout/ban).
     * Matching is case-insensitive on the IRC login. Already-deleted rows and
     * system rows are left untouched. Returns [messages] unchanged if no row matched.
     */
    fun markUserDeleted(messages: List<ChatMessage>, targetLogin: String): List<ChatMessage> {
        if (targetLogin.isBlank()) return messages
        var changed = false
        val out = messages.map {
            if (!it.deleted && !it.isSystem && it.username.equals(targetLogin, ignoreCase = true)) {
                changed = true
                it.copy(deleted = true)
            } else it
        }
        return if (changed) out else messages
    }

    /** Tombstone the single message with id [targetMessageId] (CLEARMSG). */
    fun markMessageDeleted(messages: List<ChatMessage>, targetMessageId: String): List<ChatMessage> {
        var changed = false
        val out = messages.map {
            if (!it.deleted && it.id == targetMessageId) {
                changed = true
                it.copy(deleted = true)
            } else it
        }
        return if (changed) out else messages
    }

    /**
     * True when [body] @-mentions the viewer identified by [selfLogin]. Matches the
     * "@login" token case-insensitively and only on a word boundary, so "@bob" pings
     * bob but "@bobby" does not. Blank login (anonymous / pre-USERSTATE) never matches.
     */
    fun mentionsSelf(body: String, selfLogin: String?): Boolean {
        val login = selfLogin?.takeIf { it.isNotBlank() } ?: return false
        val needle = "@$login"
        var from = 0
        while (true) {
            val at = body.indexOf(needle, from, ignoreCase = true)
            if (at < 0) return false
            val after = at + needle.length
            // Word boundary: the char after the login must not continue an identifier
            // (Twitch logins are [a-z0-9_]), else "@bobby" would match login "bob".
            val boundary = after >= body.length || !body[after].isLoginChar()
            if (boundary) return true
            from = at + 1
        }
    }

    private fun Char.isLoginChar(): Boolean = isLetterOrDigit() || this == '_'
}
