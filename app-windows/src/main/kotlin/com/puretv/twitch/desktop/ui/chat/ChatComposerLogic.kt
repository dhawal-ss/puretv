package com.puretv.twitch.desktop.ui.chat

import com.puretv.twitch.core.emotes.PickableEmote

/** The whitespace-delimited word ending at the cursor, plus its start index. */
fun wordAtCursor(text: String, cursor: Int): Pair<String, Int> {
    val end = cursor.coerceIn(0, text.length)
    var start = end
    while (start > 0 && !text[start - 1].isWhitespace()) start--
    return text.substring(start, end) to start
}

/** Up to [limit] emotes whose code starts with [word] (case-insensitive). Empty if word < 2 chars. */
fun matchEmotes(word: String, all: List<PickableEmote>, limit: Int = 8): List<PickableEmote> {
    if (word.length < 2) return emptyList()
    val w = word.lowercase()
    return all.asSequence().filter { it.code.lowercase().startsWith(w) }.take(limit).toList()
}

/** Replace the word ending at [cursor] with [code] + trailing space. Returns new text + new cursor index. */
fun completeWord(text: String, cursor: Int, code: String): Pair<String, Int> {
    val end = cursor.coerceIn(0, text.length)
    var start = end
    while (start > 0 && !text[start - 1].isWhitespace()) start--
    val newText = text.substring(0, start) + code + " " + text.substring(end)
    return newText to (start + code.length + 1)
}

/** Insert [code] at [cursor] (picker click), space-padded as needed. Returns new text + new cursor index. */
fun insertAtCursor(text: String, cursor: Int, code: String): Pair<String, Int> {
    val at = cursor.coerceIn(0, text.length)
    val needsLead = at > 0 && !text[at - 1].isWhitespace()
    val ins = (if (needsLead) " " else "") + code + " "
    return (text.substring(0, at) + ins + text.substring(at)) to (at + ins.length)
}

/** What a key press in the chat composer should do. */
enum class ComposerKeyAction { SEND, COMPLETE, NONE }

/**
 * Maps a composer key press to an action. Enter sends (matching Twitch's web
 * client, and what users expect instead of clicking the arrow); Tab accepts the
 * first emote suggestion only when one is offered; anything else falls through
 * to normal text editing. Enter wins over Tab even while the autocomplete strip
 * is open — Tab is the completion key, Enter is always "send".
 */
fun composerKeyAction(isEnter: Boolean, isTab: Boolean, hasSuggestions: Boolean): ComposerKeyAction = when {
    isEnter -> ComposerKeyAction.SEND
    isTab && hasSuggestions -> ComposerKeyAction.COMPLETE
    else -> ComposerKeyAction.NONE
}
