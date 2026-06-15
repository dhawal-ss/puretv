package com.puretv.twitch.desktop.ui

import com.puretv.twitch.core.model.StreamQuality
import com.puretv.twitch.core.repository.VodRepository
import com.puretv.twitch.core.stream.VodResolver
import com.puretv.twitch.desktop.player.VlcPlayer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class VodPlayerState(
    val quality: StreamQuality = StreamQuality.AUTO,
    val loading: Boolean = true,
    val error: String? = null,
)

/**
 * Plays a VOD directly: resolve the signed source manifest, pick the URL for the
 * chosen quality, and hand it to the shared [VlcPlayer]. VOD source manifests are
 * ad-free by construction (the spike confirmed this), so there is no proxy/strip
 * step — unlike the live path.
 */
class VodPlayerViewModel(
    private val launch: VodLaunch,
    val player: VlcPlayer,
    private val vodRepository: VodRepository,
) : DesktopViewModel() {

    private val vodId: String get() = launch.vodId

    private val _state = MutableStateFlow(VodPlayerState())
    val state: StateFlow<VodPlayerState> = _state.asStateFlow()
    val status get() = player.status

    init { play(StreamQuality.AUTO) }

    fun setQuality(quality: StreamQuality) {
        if (_state.value.quality == quality) return
        play(quality)
    }

    private fun play(quality: StreamQuality) {
        _state.value = _state.value.copy(quality = quality, loading = true, error = null)
        scope.launch {
            runCatching {
                val master = vodRepository.resolvePlayableVod(vodId)
                VodResolver.playableUrlFor(master, quality)
            }.onSuccess { url ->
                _state.value = _state.value.copy(loading = false)
                player.play(url)
            }.onFailure { e ->
                _state.value = _state.value.copy(loading = false, error = e.message ?: "Couldn't load this video")
            }
        }
    }

    fun seekTo(ms: Long) = player.seekTo(ms)
    fun togglePlayPause() = player.togglePlayPause()
    fun setVolume(v: Int) = player.setVolume(v)

    // Stops the SHARED VlcPlayer singleton when this screen leaves composition —
    // safe today because the Stream and Vod routes are mutually exclusive (never
    // composed side-by-side). If routing ever allows a co-existing mini-player,
    // this singleton + stop() would need rethinking. Mirrors StreamViewModel.
    override fun onCleared() { player.stop() }
}
