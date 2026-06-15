package com.puretv.twitch.core.emotes

import com.puretv.twitch.core.model.ChannelEmote
import com.puretv.twitch.core.model.EmoteLayer
import com.puretv.twitch.core.model.EmoteProvider
import com.puretv.twitch.core.model.MessagePart

/** A third-party emote resolved for inline chat word-matching. */
data class ResolvedEmote(
    val code: String,
    val url: String,
    val animated: Boolean,
    val provider: EmoteProvider,
    val zeroWidth: Boolean,
)

/**
 * Builds a code -> emote lookup used to tokenize incoming chat. Only third-party
 * emotes are indexed — Twitch-native emotes already arrive tagged via IRC.
 * Channel sets win over globals (first occurrence wins); blank codes are skipped.
 * Matching is case-sensitive (catJAM != catjam).
 */
fun buildEmoteIndex(
    thirdPartyChannel: List<ChannelEmote>,
    thirdPartyGlobal: List<ChannelEmote>,
): Map<String, ResolvedEmote> {
    val out = LinkedHashMap<String, ResolvedEmote>()
    for (emote in thirdPartyChannel + thirdPartyGlobal) {
        if (emote.name.isBlank() || out.containsKey(emote.name)) continue
        out[emote.name] = ResolvedEmote(emote.name, emote.url, emote.animated, emote.provider, emote.zeroWidth)
    }
    return out
}

/**
 * Second pass over already-parsed [parts]: replaces words that exactly match a
 * third-party emote [index] with [MessagePart.ThirdPartyEmote]. A zero-width
 * emote is merged as an overlay onto the immediately-preceding emote (Twitch or
 * third-party), with any single run of intervening whitespace dropped; if there
 * is no preceding emote it renders standalone. Whitespace is otherwise preserved
 * so text reflows. Returns [parts] unchanged when [index] is empty.
 */
fun applyThirdPartyEmotes(
    parts: List<MessagePart>,
    index: Map<String, ResolvedEmote>,
): List<MessagePart> {
    if (index.isEmpty()) return parts
    val out = ArrayList<MessagePart>()
    val pending = StringBuilder()

    fun flushText() {
        if (pending.isNotEmpty()) {
            out += MessagePart.Text(pending.toString())
            pending.clear()
        }
    }

    fun attachOverlay(emote: ResolvedEmote): Boolean {
        val layer = EmoteLayer(emote.url, emote.code, emote.provider, emote.animated)
        return when (val last = out.lastOrNull()) {
            is MessagePart.ThirdPartyEmote -> { out[out.lastIndex] = last.copy(overlays = last.overlays + layer); true }
            is MessagePart.TwitchEmote -> { out[out.lastIndex] = last.copy(overlays = last.overlays + layer); true }
            else -> false
        }
    }

    for (part in parts) {
        if (part !is MessagePart.Text) {
            flushText()
            out += part
            continue
        }
        for (token in tokenizePreservingWhitespace(part.content)) {
            val emote = if (token.isBlank()) null else index[token]
            if (emote == null) {
                pending.append(token)
                continue
            }
            // Zero-width with only whitespace (or nothing) before it: stack on the
            // previous emote and drop the intervening space.
            if (emote.zeroWidth && pending.isBlank() && attachOverlay(emote)) {
                pending.clear()
                continue
            }
            flushText()
            out += MessagePart.ThirdPartyEmote(emote.url, emote.code, emote.provider, emote.animated)
        }
    }
    flushText()
    return out
}

/** Splits [s] into alternating word / whitespace tokens, keeping whitespace runs. */
internal fun tokenizePreservingWhitespace(s: String): List<String> {
    if (s.isEmpty()) return emptyList()
    val out = ArrayList<String>()
    val sb = StringBuilder()
    var inWhitespace = s[0].isWhitespace()
    for (ch in s) {
        val isWs = ch.isWhitespace()
        if (isWs != inWhitespace) {
            out += sb.toString()
            sb.clear()
            inWhitespace = isWs
        }
        sb.append(ch)
    }
    if (sb.isNotEmpty()) out += sb.toString()
    return out
}
