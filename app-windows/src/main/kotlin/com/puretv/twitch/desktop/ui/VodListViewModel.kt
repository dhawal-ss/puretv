package com.puretv.twitch.desktop.ui

import com.puretv.twitch.core.model.VideoInfo
import com.puretv.twitch.core.model.VideoType
import com.puretv.twitch.core.repository.VodRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class VodListState(
    val videos: List<VideoInfo> = emptyList(),
    val filter: VideoType? = null,      // null = All
    val loading: Boolean = false,
    val cursor: String? = null,
    val error: String? = null,
)

class VodListViewModel(
    private val userId: String,
    private val vodRepository: VodRepository,
) : DesktopViewModel() {

    private val _state = MutableStateFlow(VodListState())
    val state: StateFlow<VodListState> = _state.asStateFlow()

    init { load(reset = true) }

    fun setFilter(type: VideoType?) {
        if (_state.value.filter == type) return
        _state.value = _state.value.copy(filter = type)
        load(reset = true)
    }

    fun loadMore() {
        val s = _state.value
        if (s.loading || s.cursor == null) return
        load(reset = false)
    }

    private fun load(reset: Boolean) {
        val s = _state.value
        _state.value = s.copy(loading = true, error = null)
        scope.launch {
            runCatching {
                vodRepository.videosFor(
                    userId = userId,
                    type = s.filter,
                    after = if (reset) null else s.cursor,
                )
            }.onSuccess { page ->
                _state.value = _state.value.copy(
                    videos = if (reset) page.videos else _state.value.videos + page.videos,
                    cursor = page.cursor,
                    loading = false,
                )
            }.onFailure { e ->
                _state.value = _state.value.copy(loading = false, error = e.message ?: "Failed to load videos")
            }
        }
    }
}
