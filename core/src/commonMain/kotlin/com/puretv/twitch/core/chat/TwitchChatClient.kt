package com.puretv.twitch.core.chat

import com.puretv.twitch.core.api.TwitchConfig
import com.puretv.twitch.core.model.Badge
import com.puretv.twitch.core.model.ChatEvent
import com.puretv.twitch.core.model.ChatMessage
import com.puretv.twitch.core.model.MessagePart
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ClosedSendChannelException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.random.Random

/**
 * SECTION 05.1 — Twitch IRC-over-WebSocket chat client.
 *
 * Connection sequence (must be sent in order):
 *   CAP REQ :twitch.tv/tags twitch.tv/commands twitch.tv/membership
 *   PASS oauth:{accessToken}     (omitted for anonymous viewers)
 *   NICK {username}              (or "justinfan{random}" when anonymous)
 *   JOIN #{channel}
 *
 * Parses PRIVMSG / USERNOTICE / CLEARCHAT / CLEARMSG / ROOMSTATE / GLOBALUSERSTATE
 * and emits everything as a single [Flow] of [ChatEvent] for the UI layer.
 */
class TwitchChatClient(
    private val httpClient: HttpClient,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default),
) {
    private val _events = MutableSharedFlow<ChatEvent>(replay = 0, extraBufferCapacity = 256)
    val events: Flow<ChatEvent> = _events.asSharedFlow()

    private val outbox = Channel<String>(capacity = Channel.UNLIMITED)
    private val rateLimiter = ChatRateLimiter()
    private var connectionJob: kotlinx.coroutines.Job? = null
    private var currentChannel: String? = null

    /**
     * [username] must be the Twitch login (lowercase) of the [token] owner —
     * Twitch IRC rejects PASS+NICK if they don't match. Resolve it via
     * `GET /users` (with the bearer token, no params) at login time and pass
     * it through here. When [token] is null, the value of [username] is
     * ignored and an anonymous `justinfanNNNN` identity is used instead.
     */
    suspend fun connect(channel: String, token: String?, username: String? = null) {
        val newChannel = channel.lowercase()
        val channelChanged = currentChannel != null && currentChannel != newChannel
        currentChannel = newChannel
        connectionJob?.cancel()
        // On a channel switch, drop any messages still queued for the previous
        // channel so a reconnect doesn't deliver them to the new one (audit F3).
        if (channelChanged) { while (outbox.tryReceive().isSuccess) { /* drain */ } }
        connectionJob = scope.launch {
            var backoffMs = INITIAL_BACKOFF_MS
            // Reconnect loop (audit P0-6): Twitch routinely drops idle/long-lived
            // connections and issues RECONNECT. Without this, a dropped socket
            // silently ended chat for the rest of the channel session.
            while (isActive) {
                try {
                    httpClient.webSocket(TwitchConfig.IRC_ENDPOINT) {
                        backoffMs = INITIAL_BACKOFF_MS // a healthy connect resets the backoff
                        _events.emit(ChatEvent.ConnectionState(connected = true))

                        send(Frame.Text("CAP REQ :twitch.tv/tags twitch.tv/commands twitch.tv/membership"))
                        if (!token.isNullOrBlank() && !username.isNullOrBlank()) {
                            send(Frame.Text("PASS oauth:$token"))
                            send(Frame.Text("NICK ${username.lowercase()}"))
                        } else {
                            // Anonymous read-only — works even when token is present
                            // but username hasn't been resolved yet.
                            send(Frame.Text("NICK justinfan${Random.nextInt(10_000, 99_999)}"))
                        }
                        send(Frame.Text("JOIN #$newChannel"))

                        // Outbound pump — respects the local token bucket (Gotcha #7).
                        val sender = launch {
                            for (raw in outbox) {
                                rateLimiter.acquire()
                                send(Frame.Text(raw))
                            }
                        }

                        try {
                            while (isActive) {
                                // Idle-timeout the read: Twitch PINGs ~every 5min, so no
                                // frame for READ_IDLE_MS means a half-open socket (Wi-Fi
                                // handoff, sleep/resume) — break to reconnect (audit F4).
                                val frame = withTimeoutOrNull(READ_IDLE_MS) {
                                    incoming.receiveCatching().getOrNull()
                                } ?: break
                                if (frame is Frame.Text) {
                                    val reconnectRequested = handleRawIrcChunk(frame.readText()) { send(Frame.Text(it)) }
                                    if (reconnectRequested) break
                                }
                            }
                        } finally {
                            sender.cancel()
                        }
                    }
                } catch (e: CancellationException) {
                    throw e // disconnect()/scope cancellation — do NOT reconnect
                } catch (e: Exception) {
                    _events.emit(ChatEvent.ConnectionState(connected = false, reason = e.message))
                }
                if (!isActive) break
                // Socket dropped (clean close, RECONNECT, idle, or error) — back off and redial.
                _events.emit(ChatEvent.ConnectionState(connected = false, reason = "reconnecting"))
                delay(backoffMs + Random.nextLong(0, BACKOFF_JITTER_MS))
                backoffMs = (backoffMs * 2).coerceAtMost(MAX_BACKOFF_MS)
            }
        }
    }

    /**
     * Non-suspending on purpose: ViewModels typically call this from
     * `onCleared()` (which is not a coroutine context), and any pattern that
     * wraps the call in `scope.launch { ... }` races scope cancellation —
     * the launched coroutine can be cancelled before its body runs, leaking
     * the WebSocket.
     *
     * The body is cheap: cancel the connection job (returns immediately;
     * Ktor's WebSocket honors structured cancellation), clear state, and
     * tryEmit one disconnect event. tryEmit can only fail when the flow's
     * 256-slot buffer is saturated, which we don't worry about at shutdown.
     */
    fun disconnect() {
        connectionJob?.cancel()
        connectionJob = null
        currentChannel = null
        // Drain any still-queued outbound messages (audit M2). Each queued line
        // has its target channel baked in by buildPrivmsgLine (`PRIVMSG #chanA`),
        // so leaving them queued is unsafe on two counts:
        //   1) Cross-channel leak: disconnect() clears currentChannel, so the
        //      connect()-time channel-switch drain (gated on currentChannel !=
        //      null) won't fire — a later connect(chanB) would flush chanA's
        //      lines into chanB.
        //   2) Unbounded growth: outbox is UNLIMITED and never closed, so the
        //      ClosedSendChannelException "drop when disconnected" path in
        //      sendMessage never triggers and offline sends accumulate forever.
        // Dropping them here matches the documented "drop when disconnected" intent.
        while (outbox.tryReceive().isSuccess) { /* drop queued lines */ }
        _events.tryEmit(ChatEvent.ConnectionState(connected = false, reason = "user disconnected"))
    }

    /** Queues a message; actual send is throttled by [ChatRateLimiter] (20 msgs / 30s). */
    suspend fun sendMessage(channel: String, message: String, replyParentMsgId: String? = null) {
        try {
            outbox.send(buildPrivmsgLine(channel, message, replyParentMsgId))
        } catch (e: ClosedSendChannelException) {
            // not connected — drop silently, UI should reflect connection state
        }
    }

    /**
     * IRC frames can arrive batched with `\r\n` separators — split, then
     * dispatch each line through [TwitchIrcParser]. Returns true if Twitch sent
     * a server-initiated `RECONNECT` (the caller should cycle the connection).
     */
    private suspend fun handleRawIrcChunk(chunk: String, rawSend: suspend (String) -> Unit): Boolean {
        var reconnectRequested = false
        chunk.split("\r\n").filter { it.isNotBlank() }.forEach { line ->
            when {
                line.startsWith("PING") -> rawSend(line.replaceFirst("PING", "PONG"))
                // Twitch tells us it's about to drop this socket — cycle proactively
                // instead of waiting for the read to fail (audit P0-6).
                line.startsWith("RECONNECT") -> reconnectRequested = true
                else -> TwitchIrcParser.parse(line, currentChannel.orEmpty())?.let { _events.emit(it) }
            }
        }
        return reconnectRequested
    }

    private companion object {
        const val INITIAL_BACKOFF_MS = 1_000L
        const val MAX_BACKOFF_MS = 30_000L
        const val BACKOFF_JITTER_MS = 500L
        // > Twitch's ~5min server PING cadence, so a quiet-but-healthy channel
        // isn't mistaken for a dead socket.
        const val READ_IDLE_MS = 360_000L
    }
}

/** Twitch rejects chat messages longer than 500 characters outright. */
internal const val MAX_CHAT_MESSAGE_LENGTH = 500

/**
 * Builds a single IRC PRIVMSG line, hardened against CRLF command injection.
 *
 * IRC delimits commands with `\r\n`, so an outgoing [message] body containing a
 * line break would otherwise be parsed by Twitch as multiple commands — letting
 * a crafted or pasted message forge JOIN/PRIVMSG/etc. under the authenticated
 * user's `chat:edit` session. Line breaks are neutralised to spaces and the body
 * is truncated to Twitch's [MAX_CHAT_MESSAGE_LENGTH] limit.
 */
internal fun buildPrivmsgLine(channel: String, message: String, replyParentMsgId: String? = null): String {
    val safeBody = message.replace('\r', ' ').replace('\n', ' ').take(MAX_CHAT_MESSAGE_LENGTH)
    val base = "PRIVMSG #" + channel.lowercase() + " :" + safeBody
    return if (replyParentMsgId.isNullOrBlank()) base else "@reply-parent-msg-id=" + replyParentMsgId + " " + base
}

/**
 * GOTCHA #7 — Twitch caps non-VIP chatters at 20 messages / 30 seconds.
 * Local token bucket so we never trip the server-side limit (which silently
 * drops messages and can lead to a temporary chat ban).
 */
class ChatRateLimiter(
    private val capacity: Int = 20,
    private val refillWindow: kotlin.time.Duration = kotlin.time.Duration.parse("30s"),
) {
    private val timeSource = kotlin.time.TimeSource.Monotonic
    private var tokens = capacity
    private var windowStart = timeSource.markNow()

    suspend fun acquire() {
        while (true) {
            val elapsed = windowStart.elapsedNow()
            if (elapsed >= refillWindow) {
                windowStart = timeSource.markNow()
                tokens = capacity
            }
            if (tokens > 0) {
                tokens--
                return
            }
            val remaining = refillWindow - windowStart.elapsedNow()
            kotlinx.coroutines.delay(remaining.inWholeMilliseconds.coerceAtLeast(50))
        }
    }
}

/**
 * SECTION 05.1 — parses raw Twitch-tagged IRC lines like:
 *   @badge-info=subscriber/12;badges=broadcaster/1;color=#FF0000;display-name=User;
 *   emotes=25:0-4,12-16;id=uuid;...;tmi-sent-ts=1234567 :user!user@user.tmi.twitch.tv
 *   PRIVMSG #channel :Hello Kappa
 *
 * Strategy: split off the leading @key=value tag block, then parse the
 * remaining standard-IRC line by command.
 */
object TwitchIrcParser {

    // Monotonic counter for the message-id fallback in [buildMessageEvent].
    // Lines are parsed sequentially on a connection's single read loop, so a
    // plain var suffices to keep the synthesized id unique.
    private var fallbackIdSeq = 0L

    fun parse(line: String, joinedChannel: String): ChatEvent? {
        var rest = line
        val tags = if (rest.startsWith("@")) {
            val (tagBlock, remainder) = rest.removePrefix("@").split(" ", limit = 2).let { it[0] to it.getOrElse(1) { "" } }
            rest = remainder
            parseTags(tagBlock)
        } else emptyMap()

        val prefixAndRest = if (rest.startsWith(":")) rest.removePrefix(":").split(" ", limit = 2) else listOf("", rest)
        val prefix = prefixAndRest[0]
        val commandLine = prefixAndRest.getOrElse(1) { "" }
        val parts = commandLine.split(" :", limit = 2)
        val commandAndParams = parts[0].trim().split(" ")
        val command = commandAndParams.firstOrNull().orEmpty()
        val trailing = parts.getOrNull(1)

        return when (command) {
            "PRIVMSG" -> buildMessageEvent(tags, prefix, trailing.orEmpty(), joinedChannel)?.let { ChatEvent.Message(it) }
            "USERNOTICE" -> ChatEvent.UserNotice(
                // system-msg is already IRCv3-unescaped by parseTags.
                systemMessage = tags["system-msg"].orEmpty(),
                raw = trailing?.let { buildMessageEvent(tags, prefix, it, joinedChannel) },
            )
            "CLEARCHAT" -> ChatEvent.ClearChat(
                targetUser = trailing,
                durationSeconds = tags["ban-duration"]?.toIntOrNull(),
            )
            "CLEARMSG" -> tags["target-msg-id"]?.let { ChatEvent.ClearMessage(it) }
            "ROOMSTATE" -> ChatEvent.RoomState(
                slowModeSeconds = tags["slow"]?.toIntOrNull(),
                emoteOnly = tags["emote-only"] == "1",
            )
            "GLOBALUSERSTATE" -> ChatEvent.SelfState(
                displayName = tags["display-name"].orEmpty(),
                color = tags["color"].orEmpty().ifBlank { "#9B5DE5" },
                badges = parseBadges(tags["badges"]),
            )
            // USERSTATE is sent on JOIN and after each of our own sends. It carries
            // our per-channel identity (channel sub/mod badges, color) — more
            // accurate than GLOBALUSERSTATE for echoing our own messages. Reuse the
            // same SelfState event; the later USERSTATE refines the global one.
            "USERSTATE" -> ChatEvent.SelfState(
                displayName = tags["display-name"].orEmpty(),
                color = tags["color"].orEmpty().ifBlank { "#9B5DE5" },
                badges = parseBadges(tags["badges"]),
            )
            else -> null
        }
    }

    private fun buildMessageEvent(tags: Map<String, String>, prefix: String, message: String, channel: String): ChatMessage? {
        val username = prefix.substringBefore("!").ifBlank { tags["display-name"].orEmpty() }
        if (username.isBlank()) return null
        val badges = parseBadges(tags["badges"])
        return ChatMessage(
            // Twitch sends a unique `id` on every PRIVMSG; the fallback only fires
            // on a malformed/tagless line. Append a monotonic seq so two messages
            // from the same user in the same millisecond can't collide into one
            // LazyColumn key (a duplicate-key crash).
            id = tags["id"] ?: "${tags["tmi-sent-ts"]}-$username-${fallbackIdSeq++}",
            channel = channel,
            username = username,
            displayName = tags["display-name"]?.ifBlank { username } ?: username,
            color = tags["color"].orEmpty().ifBlank { "#9B5DE5" },
            message = message,
            parsedParts = parseMessageParts(message, tags["emotes"]),
            badges = badges,
            timestamp = tags["tmi-sent-ts"]?.toLongOrNull() ?: 0L,
            isSubscriber = tags["subscriber"] == "1" || badges.any { it.setId == "subscriber" },
            isModerator = tags["mod"] == "1" || badges.any { it.setId == "moderator" },
            isBroadcaster = badges.any { it.setId == "broadcaster" },
            replyParentDisplayName = tags["reply-parent-display-name"]?.ifBlank { null },
            replyParentBody = tags["reply-parent-msg-body"]?.ifBlank { null },
        )
    }

    /** badges=broadcaster/1,subscriber/12 -> [Badge(broadcaster,1), Badge(subscriber,12)] */
    private fun parseBadges(raw: String?): List<Badge> =
        raw.orEmpty().split(',').filter { it.isNotBlank() }.mapNotNull {
            val (set, version) = it.split('/').let { p -> p.getOrNull(0) to p.getOrNull(1) }
            if (set != null && version != null) Badge(set, version) else null
        }

    /**
     * emotes=25:0-4,12-16/1902:6-10 -> ranges keyed by emote id, applied over
     * the raw [message] to interleave [MessagePart.Text] and [MessagePart.TwitchEmote].
     */
    private fun parseMessageParts(message: String, emoteTag: String?): List<MessagePart> {
        if (emoteTag.isNullOrBlank()) return listOf(MessagePart.Text(message))

        data class Range(val start: Int, val end: Int, val emoteId: String)

        val ranges = emoteTag.split('/').flatMap { spec ->
            val (id, rangesRaw) = spec.split(':', limit = 2).let { it.getOrNull(0) to it.getOrNull(1) }
            if (id == null || rangesRaw == null) emptyList()
            else rangesRaw.split(',').mapNotNull { r ->
                val (s, e) = r.split('-').let { it.getOrNull(0)?.toIntOrNull() to it.getOrNull(1)?.toIntOrNull() }
                if (s != null && e != null) Range(s, e, id) else null
            }
        }.sortedBy { it.start }

        if (ranges.isEmpty()) return listOf(MessagePart.Text(message))

        val parts = mutableListOf<MessagePart>()
        var cursor = 0
        // Twitch `emotes=` ranges are INCLUSIVE offsets counted in Unicode CODE
        // POINTS, not UTF-16 code units (audit M5). Slicing the raw char array
        // mis-aligns every index after an astral char (emoji), producing wrong
        // emote-name slices. Walk the message as code points instead.
        val codePoints = message.toCodePointArray()
        val size = codePoints.size
        for (range in ranges) {
            if (range.start > cursor) {
                parts += MessagePart.Text(codePoints.sliceToString(cursor, (range.start - cursor).coerceAtLeast(0)))
            }
            val end = (range.end + 1).coerceAtMost(size)
            val name = if (range.start in 0 until size && end <= size) {
                codePoints.sliceToString(range.start, (end - range.start).coerceAtLeast(0))
            } else ""
            parts += MessagePart.TwitchEmote(id = range.emoteId, name = name)
            cursor = end
        }
        if (cursor < size) parts += MessagePart.Text(codePoints.sliceToString(cursor, size - cursor))
        return parts
    }

    /**
     * KMP-common has no `String.codePointAt` (JVM-only), so we collapse the
     * string's UTF-16 units into an IntArray of Unicode code points by hand,
     * folding valid high+low surrogate pairs into a single astral code point.
     * A lone/unpaired surrogate is kept as-is so round-tripping never loses data.
     */
    private fun String.toCodePointArray(): IntArray {
        val out = ArrayList<Int>(length)
        var i = 0
        while (i < length) {
            val c = this[i]
            if (c.isHighSurrogate() && i + 1 < length && this[i + 1].isLowSurrogate()) {
                out += 0x10000 + ((c.code - 0xD800) shl 10) + (this[i + 1].code - 0xDC00)
                i += 2
            } else {
                out += c.code
                i++
            }
        }
        return out.toIntArray()
    }

    /** Rebuilds a String from [count] code points starting at [start], re-splitting astral ones into surrogate pairs. */
    private fun IntArray.sliceToString(start: Int, count: Int): String {
        if (count <= 0) return ""
        val sb = StringBuilder(count)
        var idx = start
        val endExclusive = start + count
        while (idx < endExclusive) {
            val cp = this[idx]
            if (cp >= 0x10000) {
                val v = cp - 0x10000
                sb.append(((v shr 10) + 0xD800).toChar())
                sb.append(((v and 0x3FF) + 0xDC00).toChar())
            } else {
                sb.append(cp.toChar())
            }
            idx++
        }
        return sb.toString()
    }

    private fun parseTags(tagBlock: String): Map<String, String> =
        // Split on the RAW ';' separator first (value semicolons arrive escaped as
        // '\:'), then IRCv3-unescape each value exactly once at the source.
        tagBlock.split(';').filter { it.isNotBlank() }.associate {
            val idx = it.indexOf('=')
            if (idx < 0) it to "" else it.substring(0, idx) to unescapeTagValue(it.substring(idx + 1))
        }

    /**
     * IRCv3 message-tag value unescaping (`\:`→`;`, `\s`→space, `\\`→`\`,
     * `\r`→CR, `\n`→LF). Processed left-to-right so `\\s` stays a literal
     * backslash + 's', not a space. Without this, reply quotes and USERNOTICE
     * system messages render visible `\s`/`\:` mojibake for any multi-word value.
     */
    internal fun unescapeTagValue(value: String): String {
        if (value.indexOf('\\') < 0) return value // fast path: nothing to unescape
        val sb = StringBuilder(value.length)
        var i = 0
        while (i < value.length) {
            val c = value[i]
            if (c != '\\') {
                sb.append(c)
                i++
                continue
            }
            if (i + 1 >= value.length) break // trailing lone backslash -> drop
            when (value[i + 1]) {
                ':' -> sb.append(';')
                's' -> sb.append(' ')
                '\\' -> sb.append('\\')
                'r' -> sb.append('\r')
                'n' -> sb.append('\n')
                else -> sb.append(value[i + 1])
            }
            i += 2
        }
        return sb.toString()
    }
}
