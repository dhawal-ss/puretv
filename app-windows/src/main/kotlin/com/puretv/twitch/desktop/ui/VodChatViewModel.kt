package com.puretv.twitch.desktop.ui

import com.puretv.twitch.core.model.ChatMessage
import com.puretv.twitch.core.repository.VodRepository
import com.puretv.twitch.core.stream.ReplayBuffer
import com.puretv.twitch.desktop.player.VlcPlayer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Drives VOD chat replay: buffers comments (deduped by id) and exposes those due
 * at the current playback position, prefetching forward and resetting on a
 * backward seek. Isolated from [VodPlayerViewModel] so chat is independently
 * cuttable.
 */
class VodChatViewModel(
    private val vodId: String,
    private val player: VlcPlayer,
    private val vodRepository: VodRepository,
) : DesktopViewModel() {

    private val buffer = ReplayBuffer()
    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    // Touched from collector + load coroutines on a thread-pool dispatcher → @Volatile.
    @Volatile private var loading = false
    @Volatile private var lastSec = -1L
    @Volatile private var exhausted = false   // no more pages after the loaded window

    init {
        requestLoad(0)
        scope.launch {
            player.status.collect { st ->
                val sec = st.positionMs / 1000
                if (sec != lastSec) onSecond(sec)
            }
        }
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
        _messages.value = buffer.due(sec).takeLast(MAX_VISIBLE).map { it.message }
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
            _messages.value = buffer.due(lastSec.coerceAtLeast(0)).takeLast(MAX_VISIBLE).map { it.message }
        }
    }

    companion object {
        private const val PREFETCH_LEAD_SECONDS = 20
        private const val MAX_VISIBLE = 200
    }
}
