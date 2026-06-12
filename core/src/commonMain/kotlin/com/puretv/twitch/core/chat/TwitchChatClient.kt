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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ClosedSendChannelException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
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
        currentChannel = channel.lowercase()
        connectionJob?.cancel()
        connectionJob = scope.launch {
            try {
                httpClient.webSocket(TwitchConfig.IRC_ENDPOINT) {
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
                    send(Frame.Text("JOIN #${currentChannel}"))

                    // Outbound pump — respects the local token bucket (Gotcha #7).
                    val sender = launch {
                        for (raw in outbox) {
                            rateLimiter.acquire()
                            send(Frame.Text(raw))
                        }
                    }

                    try {
                        for (frame in incoming) {
                            if (frame is Frame.Text) {
                                handleRawIrcChunk(frame.readText()) { send(Frame.Text(it)) }
                            }
                        }
                    } finally {
                        sender.cancel()
                    }
                }
            } catch (e: Exception) {
                _events.emit(ChatEvent.ConnectionState(connected = false, reason = e.message))
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
        _events.tryEmit(ChatEvent.ConnectionState(connected = false, reason = "user disconnected"))
    }

    /** Queues a message; actual send is throttled by [ChatRateLimiter] (20 msgs / 30s). */
    suspend fun sendMessage(channel: String, message: String) {
        try {
            outbox.send("PRIVMSG #${channel.lowercase()} :$message")
        } catch (e: ClosedSendChannelException) {
            // not connected — drop silently, UI should reflect connection state
        }
    }

    /**
     * IRC frames can arrive batched with `\r\n` separators — split, then
     * dispatch each line through [TwitchIrcParser].
     */
    private suspend fun handleRawIrcChunk(chunk: String, rawSend: suspend (String) -> Unit) {
        chunk.split("\r\n").filter { it.isNotBlank() }.forEach { line ->
            if (line.startsWith("PING")) {
                rawSend(line.replaceFirst("PING", "PONG"))
                return@forEach
            }
            TwitchIrcParser.parse(line, currentChannel.orEmpty())?.let { _events.emit(it) }
        }
    }
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
                systemMessage = tags["system-msg"]?.replace("\\s", " ").orEmpty(),
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
            else -> null
        }
    }

    private fun buildMessageEvent(tags: Map<String, String>, prefix: String, message: String, channel: String): ChatMessage? {
        val username = prefix.substringBefore("!").ifBlank { tags["display-name"].orEmpty() }
        if (username.isBlank()) return null
        val badges = parseBadges(tags["badges"])
        return ChatMessage(
            id = tags["id"] ?: "${tags["tmi-sent-ts"]}-$username",
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
        val codePoints = message.toCharArray() // Twitch ranges are UTF-16 code-unit offsets
        for (range in ranges) {
            if (range.start > cursor) {
                parts += MessagePart.Text(String(codePoints, cursor, (range.start - cursor).coerceAtLeast(0)))
            }
            val end = (range.end + 1).coerceAtMost(codePoints.size)
            val name = if (range.start in codePoints.indices && end <= codePoints.size) {
                String(codePoints, range.start, (end - range.start).coerceAtLeast(0))
            } else ""
            parts += MessagePart.TwitchEmote(id = range.emoteId, name = name)
            cursor = end
        }
        if (cursor < codePoints.size) parts += MessagePart.Text(String(codePoints, cursor, codePoints.size - cursor))
        return parts
    }

    private fun parseTags(tagBlock: String): Map<String, String> =
        tagBlock.split(';').filter { it.isNotBlank() }.associate {
            val idx = it.indexOf('=')
            if (idx < 0) it to "" else it.substring(0, idx) to it.substring(idx + 1)
        }
}
