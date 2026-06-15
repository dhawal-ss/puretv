package com.puretv.twitch.desktop.ui

import com.puretv.twitch.core.model.ChannelStatsSnapshot
import com.puretv.twitch.core.repository.ChannelStatsRepository
import com.puretv.twitch.desktop.data.ChannelHistory
import com.puretv.twitch.desktop.data.ViewerHistoryStore
import com.puretv.twitch.desktop.data.ViewerSample
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Backs the channel stats panel: loads a one-shot [ChannelStatsSnapshot], then
 * samples the live viewer count every [SAMPLE_INTERVAL_MS] while the screen is
 * open, appending to the locally-persisted [ViewerHistoryStore]. Twitch exposes
 * no viewer history, so this is the only way we build a trend — and only for
 * moments the user is actually watching.
 */
data class ChannelStatsUiState(
    val snapshot: ChannelStatsSnapshot? = null,
    val history: ChannelHistory? = null,
    val isLoading: Boolean = true,
)

class ChannelStatsViewModel(
    private val channelLogin: String,
    private val statsRepository: ChannelStatsRepository,
    private val historyStore: ViewerHistoryStore,
) : DesktopViewModel() {
    private val _state = MutableStateFlow(ChannelStatsUiState(history = historyStore.get(channelLogin)))
    val state: StateFlow<ChannelStatsUiState> = _state.asStateFlow()

    init {
        scope.launch {
            val snap = statsRepository.snapshot(channelLogin)
            _state.update {
                it.copy(snapshot = snap, history = historyStore.get(channelLogin), isLoading = false)
            }
            val initialViewers = snap?.viewerCount
            if (snap?.isLive == true && initialViewers != null) {
                recordSample(initialViewers)
            }
            sampleLoop()
        }
    }

    private suspend fun sampleLoop() {
        while (scope.isActive) {
            delay(SAMPLE_INTERVAL_MS)
            val viewers = statsRepository.liveViewers(channelLogin)
            if (viewers != null) {
                recordSample(viewers)
                _state.update { st -> st.copy(snapshot = st.snapshot?.copy(isLive = true, viewerCount = viewers)) }
            } else {
                _state.update { st -> st.copy(snapshot = st.snapshot?.copy(isLive = false, viewerCount = null)) }
            }
        }
    }

    private fun recordSample(viewers: Int) {
        val updated = historyStore.record(channelLogin, ViewerSample(epochSec = System.currentTimeMillis() / 1000, viewers = viewers))
        _state.update { it.copy(history = updated) }
    }

    companion object {
        const val SAMPLE_INTERVAL_MS = 45_000L
    }
}
