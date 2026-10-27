package com.puretv.twitch.desktop.ui

import com.puretv.twitch.core.api.TwitchApiClient
import com.puretv.twitch.core.chat.BadgeIndex
import com.puretv.twitch.core.chat.BadgeRepository
import com.puretv.twitch.core.chat.ChatRoomModes
import com.puretv.twitch.core.chat.ChatTimeline
import com.puretv.twitch.core.chat.ChatTimelineState
import com.puretv.twitch.core.chat.TwitchChatClient
import com.puretv.twitch.core.chat.applying
import com.puretv.twitch.core.chat.batchedEvery
import com.puretv.twitch.core.chat.buildSelfEcho
import com.puretv.twitch.core.chat.outgoingChatBody
import com.puretv.twitch.core.emotes.EmoteRepository
import com.puretv.twitch.core.emotes.PickableEmote
import com.puretv.twitch.core.emotes.ResolvedEmote
import com.puretv.twitch.core.emotes.applyThirdPartyEmotes
import com.puretv.twitch.core.emotes.buildEmoteIndex
import com.puretv.twitch.core.emotes.buildPickableEmotes
import com.puretv.twitch.core.emotes.prioritizeThirdParty
import com.puretv.twitch.core.model.Badge
import com.puretv.twitch.core.model.ChannelEmote
import com.puretv.twitch.core.model.ChatEvent
import com.puretv.twitch.core.model.ChatMessage
import com.puretv.twitch.core.model.EmoteProvider
import com.puretv.twitch.core.repository.ChannelRepository
import com.puretv.twitch.desktop.data.DesktopSettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What the multi-stream chat column shows: the active tile's chat and nothing else. */
data class MultiChatState(
    val channel: String? = null,
    val messages: List<ChatMessage> = emptyList(),
    val modes: ChatRoomModes = ChatRoomModes(),
    val emotes: List<PickableEmote> = emptyList(),
    val badges: BadgeIndex = BadgeIndex.EMPTY,
    val canChat: Boolean = false,
    val selfLogin: String? = null,
    val replyingTo: ChatMessage? = null,
)

/**
 * The one chat connection of the multi-stream screen. It follows the active tile:
 * [switchTo] drops the old room (socket, rows, emotes, reply) and joins the new one,
 * so there is never more than one IRC connection however many tiles are open.
 *
 * It uses the same shared chat rules as the single stream screen (core's
 * [ChatTimeline], the third-party emote index, the local echo of your own lines).
 * Left out on purpose, to keep one socket and one light column: live 7TV emote
 * updates and 7TV name paints, which need their own socket per channel.
 */
class MultiChatSession(
    private val chatClient: TwitchChatClient,
    private val emoteRepository: EmoteRepository,
    private val badgeRepository: BadgeRepository,
    private val channelRepository: ChannelRepository,
    private val apiClient: TwitchApiClient,
    private val settingsStore: DesktopSettingsStore,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow(MultiChatState())
    val state: StateFlow<MultiChatState> = _state.asStateFlow()

    private var job: Job? = null
    @Volatile private var room: Room? = null

    /** Joins [login]'s chat and leaves the previous room; null leaves chat altogether. */
    @Synchronized
    fun switchTo(login: String?) {
        val clean = login?.lowercase()
        if (clean == room?.login) return
        job?.cancel()
        job = null
        room = null
        _state.value = MultiChatState(channel = clean)
        if (clean == null) {
            chatClient.disconnect()
            return
        }
        val next = Room(clean)
        room = next
        job = scope.launch { next.run() }
    }

    fun startReply(message: ChatMessage) {
        _state.update { it.copy(replyingTo = message) }
    }

    fun cancelReply() {
        _state.update { it.copy(replyingTo = null) }
    }

    fun send(text: String) {
        val current = room ?: return
        scope.launch { current.send(text) }
    }

    /** Leaves the room and closes the socket. */
    @Synchronized
    fun close() {
        job?.cancel()
        job = null
        room = null
        chatClient.disconnect()
    }

    /** Everything that belongs to one joined channel, so a switch can never mix two rooms. */
    private inner class Room(val login: String) {
        private var selfLogin: String? = null
        private var selfDisplayName: String? = null
        private var selfColor: String? = null
        private var selfBadges: List<Badge> = emptyList()
        private var echoCounter = 0
        private val timeline = ChatTimeline(login, selfLogin = { selfLogin }, clock = System::currentTimeMillis)

        // Immutable once published, replaced whole (see StreamViewModel).
        @Volatile private var emoteIndex: Map<String, ResolvedEmote> = emptyMap()
        @Volatile private var selfEchoIndex: Map<String, ResolvedEmote> = emptyMap()
        private var tpGlobal: List<ChannelEmote> = emptyList()
        private var tpChannel: List<ChannelEmote> = emptyList()
        private var twitchGlobal: List<ChannelEmote> = emptyList()
        private var twitchChannel: List<ChannelEmote> = emptyList()

        /** Applies [change] only while this room is still the one on screen. */
        private fun update(change: (MultiChatState) -> MultiChatState) {
            _state.update { if (it.channel == login && room === this) change(it) else it }
        }

        suspend fun run() {
            val saved = runCatching { settingsStore.loadTokens() }.getOrNull()
            selfLogin = saved?.login
            update { it.copy(canChat = saved?.accessToken != null && saved.login != null, selfLogin = saved?.login) }

            // Everything below is a child of this room's job, so a switch cancels the
            // emote and badge loads along with the chat collector.
            coroutineScope {
                var joined = false
                launch { loadEmotes() }
                // Each redial reads the sign-in again, so a refreshed token is picked up.
                chatClient.connect(login, saved?.accessToken, saved?.login) {
                    settingsStore.loadTokens()?.let { it.accessToken to it.login } ?: (null to null)
                }
                chatClient.events.batchedEvery().collect { batch ->
                    // The socket is shared across switches; a straggler from the previous
                    // room must never land in this one.
                    val (mine, nowJoined) = eventsForRoom(batch, login, joined)
                    joined = nowJoined
                    val changes = timeline.changesFor(
                        mine,
                        onSelfState = { self ->
                            self.displayName.ifBlank { null }?.let { selfDisplayName = it }
                            selfColor = self.color
                            selfBadges = self.badges
                        },
                        decorate = { it.copy(parsedParts = applyThirdPartyEmotes(it.parsedParts, emoteIndex)) },
                    )
                    if (changes.isNotEmpty()) {
                        update {
                            val after = ChatTimelineState(it.messages, emptyList(), it.modes).applying(changes)
                            it.copy(messages = after.messages, modes = after.modes)
                        }
                    }
                }
            }
        }

        private suspend fun loadEmotes() = coroutineScope {
            val channelLookup = async { runCatching { channelRepository.getChannel(login) }.getOrNull() }
            val globalLookup = async { runCatching { apiClient.getGlobalEmotes() }.getOrDefault(emptyList()) }
            val channel = channelLookup.await()
            channel?.let { ch ->
                launch {
                    val badges = runCatching { badgeRepository.loadForChannel(ch.id) }.getOrDefault(BadgeIndex.EMPTY)
                    update { it.copy(badges = badges) }
                }
            }
            val channelEmotes = async {
                channel?.let { runCatching { apiClient.getChannelTwitchEmotes(it.id) }.getOrDefault(emptyList()) }
                    ?: emptyList()
            }
            twitchGlobal = globalLookup.await()
            emoteRepository.thirdPartyEmotes(channel?.id, channel?.login).collect { emotes ->
                tpGlobal = emotes.global
                tpChannel = emotes.channel
                rebuild()
            }
            twitchChannel = channelEmotes.await()
            rebuild()
        }

        /** Rebuilds both emote indices and the picker, and re-maps the rows already shown. */
        private fun rebuild() {
            val s = settingsStore.settings.value
            val enabled = buildSet {
                if (s.show7tvEmotes) add(EmoteProvider.SEVENTV)
                if (s.showBttvEmotes) add(EmoteProvider.BTTV)
                if (s.showFfzEmotes) add(EmoteProvider.FFZ)
            }
            val chan = prioritizeThirdParty(tpChannel, enabled)
            val glob = prioritizeThirdParty(tpGlobal, enabled)
            emoteIndex = buildEmoteIndex(chan, glob)
            selfEchoIndex = buildEmoteIndex(chan, glob, twitchChannel, twitchGlobal)
            val picks = buildPickableEmotes(twitchChannel, chan, twitchGlobal, glob)
            update { st ->
                st.copy(
                    emotes = picks,
                    messages = st.messages.map { it.copy(parsedParts = applyThirdPartyEmotes(it.parsedParts, emoteIndex)) },
                )
            }
        }

        suspend fun send(text: String) {
            val replyParent = _state.value.replyingTo
            chatClient.sendMessage(login, text, replyParent?.id)
            // Twitch never echoes your own line back, so draw it here, exactly as sent.
            val echo = buildSelfEcho(
                id = "self-" + (echoCounter++).toString(),
                login = selfLogin ?: "you",
                displayName = selfDisplayName,
                color = selfColor,
                badges = selfBadges,
                channel = login,
                text = outgoingChatBody(text),
                timestamp = System.currentTimeMillis(),
                replyParent = replyParent,
                emoteIndex = selfEchoIndex,
            )
            update {
                val after = ChatTimelineState(it.messages, emptyList(), it.modes)
                    .applying(listOf(com.puretv.twitch.core.chat.ChatChange.Add(echo)))
                it.copy(messages = after.messages, replyingTo = null)
            }
        }
    }
}

/**
 * The events of [batch] that belong to the room [login], and whether this room's own
 * connection has opened by the end of it ([joined] going in).
 *
 * Messages and notices that carry a row name their room, so those are matched by
 * name. Room modes, deletions and server notices carry none; each connection starts
 * with its own "connected" event, so anything of that kind seen before it comes from
 * the previous room's socket and is dropped.
 */
internal fun eventsForRoom(batch: List<ChatEvent>, login: String, joined: Boolean): Pair<List<ChatEvent>, Boolean> {
    var open = joined
    val mine = ArrayList<ChatEvent>(batch.size)
    for (event in batch) {
        when (event) {
            is ChatEvent.ConnectionState -> {
                if (event.connected) open = true
                mine += event
            }
            is ChatEvent.Message ->
                if (event.message.channel.equals(login, ignoreCase = true)) {
                    open = true // a row from this room proves its connection is up
                    mine += event
                }
            is ChatEvent.UserNotice -> {
                val room = event.raw?.channel
                if (room != null) {
                    if (room.equals(login, ignoreCase = true)) {
                        open = true
                        mine += event
                    }
                } else if (open) {
                    mine += event
                }
            }
            else -> if (open) mine += event
        }
    }
    return mine to open
}
