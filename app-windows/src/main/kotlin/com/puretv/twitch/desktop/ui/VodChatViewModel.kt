package com.puretv.twitch.desktop.ui

import com.puretv.twitch.core.emotes.EmoteRepository
import com.puretv.twitch.core.emotes.ResolvedEmote
import com.puretv.twitch.core.emotes.buildEmoteIndex
import com.puretv.twitch.core.model.ChatMessage
import com.puretv.twitch.core.repository.ChannelRepository
import com.puretv.twitch.core.repository.VodRepository
import com.puretv.twitch.core.stream.ReplayBuffer
import com.puretv.twitch.core.stream.withThirdPartyEmotes
import com.puretv.twitch.desktop.player.VlcPlayer
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Drives VOD chat replay: buffers comments (deduped by id) and exposes those due
 * at the current playback position, prefetching forward and resetting on a
 * backward seek. Isolated from [VodPlayerViewModel] so chat is independently
 * cuttable.
 *
 * Third-party (7TV/BTTV/FFZ) emotes are tokenized against the broadcaster's emote
 * sets when the due window is published (only the visible comments, so it stays
 * cheap). The emote index loads in PARALLEL — chat and position tracking never
 * block on it; until it lands, comments simply render with Twitch-native emotes
 * only, then re-emit tokenized once it's ready (mirrors live chat).
 */
class VodChatViewModel(
    private val vodId: String,
    private val channelLogin: String,
    private val player: VlcPlayer,
    private val vodRepository: VodRepository,
    private val channelRepository: ChannelRepository,
    private val emoteRepository: EmoteRepository,
) : DesktopViewModel() {

    private val buffer = ReplayBuffer()
    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    // Touched from collector + load coroutines on a thread-pool dispatcher → @Volatile.
    @Volatile private var loading = false
    @Volatile private var lastSec = -1L
    @Volatile private var exhausted = false   // no more pages after the loaded window
    // Published once when the parallel load finishes; immutable map after publish.
    @Volatile private var emoteIndex: Map<String, ResolvedEmote> = emptyMap()

    init {
        // Load the broadcaster's emote sets in parallel — NEVER block chat/position
        // tracking on a slow emote provider. Re-emit once it lands so the comments
        // already on screen pick up their emotes immediately.
        scope.launch {
            emoteIndex = runCatching { loadEmoteIndex() }.getOrDefault(emptyMap())
            emitDue(lastSec.coerceAtLeast(0))
        }
        requestLoad(0)
        scope.launch {
            player.status.collect { st ->
                val sec = st.positionMs / 1000
                if (sec != lastSec) onSecond(sec)
            }
        }
    }

    /** Broadcaster channel + global third-party emote sets → a code→emote index. */
    private suspend fun loadEmoteIndex(): Map<String, ResolvedEmote> = coroutineScope {
        val channel = runCatching { channelRepository.getChannel(channelLogin) }.getOrNull()
        val global = async { runCatching { emoteRepository.loadGlobalEmotes() }.getOrDefault(emptyList()) }
        val channelEmotes = async {
            if (channel != null) runCatching { emoteRepository.loadChannelEmotes(channel.id, channel.login) }.getOrDefault(emptyList())
            else emptyList()
        }
        buildEmoteIndex(thirdPartyChannel = channelEmotes.await(), thirdPartyGlobal = global.await())
    }

    /** Publishes the due window, tokenizing only the visible comments against the current index. */
    private fun emitDue(sec: Long) {
        _messages.value = buffer.due(sec).takeLast(MAX_VISIBLE).withThirdPartyEmotes(emoteIndex).map { it.message }
    }

    private fun onSecond(sec: Long) {
        val backward = sec < lastSec - 3
        lastSec = sec
        when {
            // Backward seek: chat for that point may differ; reload from scratch.
            backward -> { buffer.reset(); exhausted = false; requestLoad(sec.toInt()) }
            buffer.isEmpty() -> requestLoad(sec.toInt())
            !exhausted && sec.toInt() >= buffer.maxOffsetSeconds - PREFETCH_LEAD_SECONDS ->
                requestLoad(buffer.maxOffsetSeconds)
        }
        emitDue(sec)
    }

    /** Fetch a window; guarded so overlapping ticks don't stack requests. */
    private fun requestLoad(offsetSeconds: Int) {
        if (loading) return
        loading = true
        scope.launch {
            runCatching { vodRepository.videoComments(vodId, offsetSeconds) }
                .onSuccess { batch ->
                    buffer.add(batch.comments)
                    if (!batch.hasNextPage) exhausted = true
                }
            loading = false
            emitDue(lastSec.coerceAtLeast(0))
        }
    }

    companion object {
        private const val PREFETCH_LEAD_SECONDS = 20
        private const val MAX_VISIBLE = 200
    }
}
