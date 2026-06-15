package com.puretv.twitch.desktop.ui

import com.puretv.twitch.core.model.StreamQuality
import com.puretv.twitch.core.repository.VodRepository
import com.puretv.twitch.core.stream.Storyboard
import com.puretv.twitch.core.stream.VodResolver
import com.puretv.twitch.desktop.data.ResumePolicy
import com.puretv.twitch.desktop.data.WatchProgress
import com.puretv.twitch.desktop.data.WatchProgressStore
import com.puretv.twitch.desktop.player.VlcPlayer
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class VodPlayerState(
    val quality: StreamQuality = StreamQuality.AUTO,
    val loading: Boolean = true,
    val error: String? = null,
    val resumeOfferMs: Long? = null,
    val storyboard: Storyboard? = null,
)

/**
 * Plays a VOD directly (source manifest is ad-free by construction — see the VOD
 * core work). Remembers position via [WatchProgressStore]: offers a resume jump
 * on open and saves progress every ~10s and on close.
 */
class VodPlayerViewModel(
    private val launch: VodLaunch,
    val player: VlcPlayer,
    private val vodRepository: VodRepository,
    private val store: WatchProgressStore,
) : DesktopViewModel() {

    private val vodId: String get() = launch.vodId

    private val _state = MutableStateFlow(VodPlayerState())
    val state: StateFlow<VodPlayerState> = _state.asStateFlow()
    val status get() = player.status

    init {
        val resumeAt = store.get(vodId)?.let { ResumePolicy.resumePositionMs(it) }
        _state.value = _state.value.copy(resumeOfferMs = resumeAt)
        play(StreamQuality.AUTO)
        scope.launch {
            runCatching { vodRepository.loadStoryboard(vodId) }
                .onSuccess { sb -> _state.value = _state.value.copy(storyboard = sb) }
        }
        // Periodic progress save. Cancelled when the screen leaves composition.
        scope.launch {
            while (true) {
                delay(10_000)
                persistProgress()
            }
        }
    }

    /** Jump to the saved position once the media reports seekable. */
    fun resume() {
        val at = _state.value.resumeOfferMs ?: return
        _state.value = _state.value.copy(resumeOfferMs = null)
        scope.launch {
            player.status.first { it.isSeekable }
            player.seekTo(at)
        }
    }

    fun startOver() { _state.value = _state.value.copy(resumeOfferMs = null) }

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

    private fun persistProgress() {
        val s = player.status.value
        if (s.durationMs > 0 && s.positionMs > 0) {
            store.save(
                WatchProgress(
                    vodId = vodId,
                    positionMs = s.positionMs,
                    durationMs = s.durationMs,
                    updatedAt = System.currentTimeMillis(),
                    title = launch.title,
                    channelLogin = launch.channelLogin,
                    thumbnailUrl = launch.thumbnailUrl,
                ),
            )
        }
    }

    fun seekTo(ms: Long) = player.seekTo(ms)
    fun togglePlayPause() = player.togglePlayPause()
    fun setVolume(v: Int) = player.setVolume(v)

    // Save before tearing down, then stop the SHARED player singleton (safe because
    // the Stream and Vod routes are mutually exclusive). Mirrors StreamViewModel.
    override fun onCleared() {
        persistProgress()
        player.stop()
    }
}
