package com.puretv.twitch.core.chat

import com.puretv.twitch.core.emotes.PickableEmote
import com.puretv.twitch.core.model.ChatMessage

/*
 * Pure text helpers behind the chat composer, shared by the Windows and Android
 * apps: find the word being typed, suggest emotes / @usernames for it, and splice
 * a completion or picked emote back into the draft. No UI types here, so both
 * composers (and the unit tests) drive the exact same logic.
 */

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

/**
 * Emotes for a Chatterino-style `:` search ([query] is what follows the colon). Codes
 * that start with it come first, then codes that merely contain it, so ":kek" finds
 * KEKW before OMEGAKEK. Empty below 2 letters.
 */
fun matchEmotesByColon(query: String, all: List<PickableEmote>, limit: Int = 8): List<PickableEmote> {
    if (query.length < 2) return emptyList()
    val q = query.lowercase()
    val starts = all.filter { it.code.lowercase().startsWith(q) }
    if (starts.size >= limit) return starts.take(limit)
    val contains = all.filter { val c = it.code.lowercase(); !c.startsWith(q) && c.contains(q) }
    return (starts + contains).take(limit)
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

// ---- @username completion ----------------------------------------------------

/** Someone who has spoken in the current chat, offered for @-completion. */
data class Chatter(val login: String, val displayName: String, val color: String) {
    /**
     * The name a completion writes after the `@`. The display name when it is just
     * a re-cased login (keeps "@DhawaL" looking the way the user styles it), else
     * the login: a localized display name (CJK, for example) is not what Twitch
     * matches mentions on, so "@表示名" would ping nobody.
     */
    val mentionName: String
        get() = if (displayName.equals(login, ignoreCase = true)) displayName else login
}

/**
 * Distinct chatters in [messages], most recently active first. System rows and
 * blank usernames are skipped, and so is [excludeLogin] (the local viewer: you
 * never want to @-complete yourself). De-duplicated by login, case-insensitive.
 */
fun recentChatters(messages: List<ChatMessage>, excludeLogin: String? = null, limit: Int = 100): List<Chatter> {
    val seen = HashSet<String>()
    val out = ArrayList<Chatter>()
    excludeLogin?.lowercase()?.takeIf { it.isNotBlank() }?.let { seen.add(it) }
    for (i in messages.indices.reversed()) {
        val m = messages[i]
        if (m.isSystem || m.username.isBlank()) continue
        if (!seen.add(m.username.lowercase())) continue
        out += Chatter(m.username, m.displayName.ifBlank { m.username }, m.color)
        if (out.size >= limit) break
    }
    return out
}

/**
 * Up to [limit] of [chatters] whose login or display name starts with [prefix]
 * (case-insensitive), keeping their recency order. An empty prefix matches
 * everyone, so a bare "@" lists the most recent chatters.
 */
fun matchChatters(prefix: String, chatters: List<Chatter>, limit: Int = 8): List<Chatter> {
    val p = prefix.lowercase()
    return chatters.asSequence()
        .filter { it.login.lowercase().startsWith(p) || it.displayName.lowercase().startsWith(p) }
        .take(limit)
        .toList()
}

/** One entry in the composer's autocomplete strip. */
sealed class ComposerSuggestion {
    /** The text [completeWord] should put in place of the word being typed. */
    abstract val completion: String

    data class Emote(val emote: PickableEmote) : ComposerSuggestion() {
        override val completion: String get() = emote.code
    }

    data class User(val chatter: Chatter) : ComposerSuggestion() {
        override val completion: String get() = "@" + chatter.mentionName
    }
}

/**
 * Autocomplete suggestions for the [word] at the cursor.
 *
 * A word starting with `@` is unambiguously a mention, so it gets chatters only
 * (an empty "@" lists recent chatters). A word starting with `:` asks for emotes only,
 * matched anywhere in the code (see [matchEmotesByColon]). A plain word gets emote matches first,
 * then up to [userSlots] chatters whose name also matches, so typing a username
 * without the `@` still finds them without a busy emote set crowding them out.
 * Everything completes via [completeWord], which replaces the whole word, `@`
 * included, with [ComposerSuggestion.completion].
 */
fun composerSuggestions(
    word: String,
    emotes: List<PickableEmote>,
    chatters: List<Chatter>,
    limit: Int = 8,
    userSlots: Int = 2,
): List<ComposerSuggestion> {
    if (word.startsWith("@")) {
        return matchChatters(word.drop(1), chatters, limit).map { ComposerSuggestion.User(it) }
    }
    // ":kek" asks for emotes only (Chatterino style); the colon goes away on completion.
    if (word.startsWith(":")) {
        return matchEmotesByColon(word.drop(1), emotes, limit).map { ComposerSuggestion.Emote(it) }
    }
    if (word.length < 2) return emptyList()
    val users = matchChatters(word, chatters, minOf(userSlots, limit))
    val emoteMatches = matchEmotes(word, emotes, limit - users.size)
    return emoteMatches.map { ComposerSuggestion.Emote(it) } + users.map { ComposerSuggestion.User(it) }
}

// ---- Tab completion ----------------------------------------------------------

/**
 * Where Tab completion stands: the word started at [start], [options] are the
 * completions offered when Tab was first pressed, and [index] is the one now in the
 * text (followed by a space). Pressing Tab again swaps it for the next option.
 */
data class TabCompletion(val start: Int, val options: List<String>, val index: Int) {
    val inserted: String get() = options[index] + " "
    val end: Int get() = start + inserted.length
}

/** The result of a Tab press: the new draft, where the cursor goes, and the cycle to keep. */
data class TabCompletionResult(val text: String, val cursor: Int, val state: TabCompletion)

/**
 * True when [state] still describes the draft: the cursor sits right after the
 * completion it put in, and that text is still there. Typing or moving the cursor
 * ends the cycle.
 */
fun TabCompletion.isCurrent(text: String, cursor: Int): Boolean =
    options.isNotEmpty() && index in options.indices && cursor == end && end <= text.length && text.startsWith(inserted, start)

/**
 * One Tab press (Shift+Tab when [backwards]). If [previous] is still current, the
 * completion in the text moves to the next (or previous) option, wrapping around.
 * Otherwise a new cycle starts from [options] (the suggestions for the word at the
 * cursor) with the first one, or the last one going backwards. Null when there is
 * nothing to complete.
 */
fun tabComplete(
    text: String,
    cursor: Int,
    options: List<String>,
    previous: TabCompletion?,
    backwards: Boolean = false,
): TabCompletionResult? {
    if (previous != null && previous.isCurrent(text, cursor)) {
        val n = previous.options.size
        val next = previous.copy(index = (previous.index + if (backwards) n - 1 else 1) % n)
        val newText = text.substring(0, previous.start) + next.inserted + text.substring(previous.end)
        return TabCompletionResult(newText, next.end, next)
    }
    if (options.isEmpty()) return null
    val at = cursor.coerceIn(0, text.length)
    val (_, start) = wordAtCursor(text, at)
    val state = TabCompletion(start, options, if (backwards) options.size - 1 else 0)
    val newText = text.substring(0, start) + state.inserted + text.substring(at)
    return TabCompletionResult(newText, state.end, state)
}
