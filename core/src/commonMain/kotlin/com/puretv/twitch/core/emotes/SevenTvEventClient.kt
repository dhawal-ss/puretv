package com.puretv.twitch.core.emotes

import com.puretv.twitch.core.model.ChannelEmote
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.get
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.random.Random

/** A live change to a channel's active 7TV emote set, pushed over the EventAPI. */
data class SevenTvEmoteDelta(
    /** Emotes added to the set (or renamed-in). Index keyed by emote name downstream. */
    val added: List<ChannelEmote> = emptyList(),
    /** Emotes removed from the set (or renamed-out). */
    val removed: List<ChannelEmote> = emptyList(),
)

/**
 * SECTION 05.4 — 7TV EventAPI client (live emote updates).
 *
 * This is what makes chat "feel alive" like Chatterino7: when a broadcaster (or a
 * 7TV editor) adds/removes/renames an emote, 7TV pushes the change over a WebSocket
 * and it appears in chat WITHOUT a reconnect or app restart. We fetch the channel's
 * emotes once at join via [EmoteRepository]; this client keeps that set current.
 *
 * Protocol (https://github.com/SevenTV/EventAPI):
 *   - connect to [WS_ENDPOINT]; server opens with a HELLO (op 1)
 *   - send SUBSCRIBE (op 35) for `emote_set.update` keyed on the channel's set id
 *   - server streams DISPATCH (op 0) frames with `pushed` / `pulled` / `updated` lists
 *   - server sends periodic HEARTBEAT (op 2); a long read gap means a dead socket
 *
 * Mirrors [com.puretv.twitch.core.chat.TwitchChatClient]'s reconnect-with-backoff
 * shape so a dropped socket silently self-heals for the rest of the channel session.
 */
class SevenTvEventClient(
    private val httpClient: HttpClient,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default),
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    private val _updates = MutableSharedFlow<SevenTvEmoteDelta>(replay = 0, extraBufferCapacity = 64)
    val updates: Flow<SevenTvEmoteDelta> = _updates.asSharedFlow()

    private var connectionJob: Job? = null

    /**
     * Resolve [channelTwitchId]'s active 7TV emote-set id, then open the EventAPI
     * subscription and stream [updates]. Cancels any previous subscription first, so
     * calling this on a channel switch cleanly re-targets. No-op (just disconnects)
     * if the channel has no linked 7TV set.
     */
    fun connect(channelTwitchId: String) {
        connectionJob?.cancel()
        connectionJob = scope.launch {
            // Resolve the emote-set id once per channel (it's stable for the session;
            // a broadcaster swapping sets is signalled separately and is out of scope).
            val setId = runCatching { fetchEmoteSetId(channelTwitchId) }.getOrNull() ?: return@launch
            var backoffMs = INITIAL_BACKOFF_MS
            while (isActive) {
                try {
                    httpClient.webSocket(WS_ENDPOINT) {
                        backoffMs = INITIAL_BACKOFF_MS // a healthy connect resets the backoff
                        // Subscribe right away; the server tolerates SUBSCRIBE sent
                        // before we read its HELLO and replies with an ACK.
                        send(Frame.Text(subscribeFrame(setId)))
                        while (isActive) {
                            // Server HEARTBEATs ~every 25s; no frame for READ_IDLE_MS
                            // means a half-open socket — break to reconnect.
                            val frame = withTimeoutOrNull(READ_IDLE_MS) {
                                incoming.receiveCatching().getOrNull()
                            } ?: break
                            if (frame is Frame.Text) {
                                parseDispatch(frame.readText())?.let {
                                    if (it.added.isNotEmpty() || it.removed.isNotEmpty()) _updates.emit(it)
                                }
                            }
                        }
                    }
                } catch (e: CancellationException) {
                    throw e // disconnect()/scope cancellation — do NOT reconnect
                } catch (_: Exception) {
                    // fall through to backoff + redial
                }
                if (!isActive) break
                delay(backoffMs + Random.nextLong(0, BACKOFF_JITTER_MS))
                backoffMs = (backoffMs * 2).coerceAtMost(MAX_BACKOFF_MS)
            }
        }
    }

    /** Non-suspending (called from ViewModel onCleared, like TwitchChatClient.disconnect). */
    fun disconnect() {
        connectionJob?.cancel()
        connectionJob = null
    }

    private suspend fun fetchEmoteSetId(channelTwitchId: String): String? {
        val raw: JsonObject = httpClient.get("https://7tv.io/v3/users/twitch/$channelTwitchId").body()
        return raw["emote_set"]?.jsonObject?.get("id")?.jsonPrimitive?.contentOrNull
    }

    private fun subscribeFrame(setId: String): String =
        """{"op":$OP_SUBSCRIBE,"d":{"type":"$TYPE_EMOTE_SET_UPDATE","condition":{"object_id":"$setId"}}}"""

    /**
     * Parse an EventAPI text frame. Returns a delta only for an `emote_set.update`
     * DISPATCH (op 0); HELLO/ACK/HEARTBEAT and other dispatch types yield null.
     *
     * `pushed` carries the new active emote under `value`; `pulled` carries the
     * removed one under `old_value`; a rename arrives as `updated` (both present) —
     * we treat the rename as remove-old + add-new so the name→emote index stays correct.
     */
    internal fun parseDispatch(text: String): SevenTvEmoteDelta? {
        val root = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull() ?: return null
        if (root["op"]?.jsonPrimitive?.intOrNull != OP_DISPATCH) return null
        val d = root["d"]?.jsonObject ?: return null
        if (d["type"]?.jsonPrimitive?.contentOrNull != TYPE_EMOTE_SET_UPDATE) return null
        val body = d["body"]?.jsonObject ?: return null

        val added = mutableListOf<ChannelEmote>()
        val removed = mutableListOf<ChannelEmote>()
        emoteValues(body, "pushed", "value").forEach { added += it }
        emoteValues(body, "pulled", "old_value").forEach { removed += it }
        emoteValues(body, "updated", "old_value").forEach { removed += it }
        emoteValues(body, "updated", "value").forEach { added += it }
        return SevenTvEmoteDelta(added = added, removed = removed)
    }

    /** Pull the `emotes`-keyed active-emote objects out of a change list, mapping each
     *  via the shared [toSevenTvEmote] so live updates and the REST set parse identically. */
    private fun emoteValues(body: JsonObject, listKey: String, valueKey: String): List<ChannelEmote> =
        body[listKey]?.jsonArray.orEmpty().mapNotNull { entry ->
            runCatching {
                val obj = entry.jsonObject
                // Each change entry targets a field of the set; only "emotes" changes matter.
                if (obj["key"]?.jsonPrimitive?.contentOrNull != "emotes") return@mapNotNull null
                obj[valueKey]?.jsonObject?.toSevenTvEmote()
            }.getOrNull()
        }

    private companion object {
        const val WS_ENDPOINT = "wss://events.7tv.io/v3"
        const val TYPE_EMOTE_SET_UPDATE = "emote_set.update"
        const val OP_DISPATCH = 0
        const val OP_SUBSCRIBE = 35
        const val INITIAL_BACKOFF_MS = 1_000L
        const val MAX_BACKOFF_MS = 30_000L
        const val BACKOFF_JITTER_MS = 500L
        // > 7TV's ~25s heartbeat cadence with generous slack, so a quiet-but-healthy
        // subscription isn't mistaken for a dead socket.
        const val READ_IDLE_MS = 90_000L
    }
}
