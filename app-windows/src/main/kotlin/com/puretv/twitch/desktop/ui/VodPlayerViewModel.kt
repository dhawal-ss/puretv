package com.puretv.twitch.desktop.ui

import com.puretv.twitch.desktop.player.audioLevellingInUse
import com.puretv.twitch.core.audio.AudioLevelling
import com.puretv.twitch.core.model.PlaybackBackend
import com.puretv.twitch.core.model.StreamQuality
import com.puretv.twitch.core.model.UpscalingMode
import com.puretv.twitch.core.repository.VodRepository
import com.puretv.twitch.core.stream.Storyboard
import com.puretv.twitch.core.stream.VodResolver
import com.puretv.twitch.core.vod.ResumePolicy
import com.puretv.twitch.core.vod.VodFailures
import com.puretv.twitch.core.vod.WatchProgress
import com.puretv.twitch.desktop.data.WatchProgressStore
import com.puretv.twitch.desktop.data.DesktopSettingsStore
import com.puretv.twitch.desktop.player.DesktopPlayer
import com.puretv.twitch.desktop.player.PlayTicket
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
    val player: DesktopPlayer,
    private val vodRepository: VodRepository,
    private val store: WatchProgressStore,
    private val settingsStore: DesktopSettingsStore,
    private val playTicket: PlayTicket,
) : DesktopViewModel() {

    // Taken when built, before a previous stream's view model is cleaned up, so its
    // stop cannot reach this VOD (see PlayTicket).
    private val ticket = playTicket.issue()

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
                // Audit U2: a single IO error (locked file, disk full) must not kill
                // the save loop for the rest of the VOD — guard each save.
                runCatching { persistProgress() }
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
                _state.value = _state.value.copy(loading = false, error = VodFailures.message(e))
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

    /** Live-apply the scaler to the running player AND persist it (mirrors StreamViewModel). */
    /** NVIDIA RTX Video switches from the player menu: saved, then applied to this video. */
    fun setRtxSuperResolution(on: Boolean) {
        settingsStore.updateSettings { it.copy(rtxSuperResolution = on) }
        player.applyRtxVideo()
    }

    fun setRtxHdr(on: Boolean) {
        settingsStore.updateSettings { it.copy(rtxHdr = on) }
        player.applyRtxVideo()
    }

    /**
     * "Even out loud and quiet audio" from the player menu or the A key: saved, then
     * applied to this video (mirrors StreamViewModel). On VLC the video starts again on
     * an instance with (or without) it and carries on from where it was.
     */
    fun setAudioNormalizer(on: Boolean) {
        settingsStore.updateSettings { it.copy(audioNormalizer = on) }
        player.setAudioLevelling(settingsStore.settings.value.audioLevellingInUse())
    }

    /** The normalizer's strength from the player menu: saved, then applied (when it is on). */
    fun setAudioLevelling(level: AudioLevelling) {
        settingsStore.updateSettings { it.copy(audioLevelling = level) }
        player.setAudioLevelling(settingsStore.settings.value.audioLevellingInUse())
    }

    /** The A key: flips the setting. */
    fun toggleAudioNormalizer() = setAudioNormalizer(!settingsStore.settings.value.audioNormalizer)

    fun setUpscaling(mode: UpscalingMode) {
        player.setUpscaling(mode)
        settingsStore.updateSettings { it.copy(upscalingMode = mode) }
    }

    /** Persist the backend choice; restart-gated. */
    fun setPlaybackBackend(backend: PlaybackBackend) =
        settingsStore.updateSettings { it.copy(playbackBackend = backend) }

    fun seekTo(ms: Long) = player.seekTo(ms)

    /**
     * Relative seek, backing the arrow-key shortcuts. Clamped to 0..duration, the
     * same range the scrub slider is bound to, so holding an arrow at either end
     * settles on the boundary instead of asking the backend to seek past it.
     *
     * Reports whether it acted: a VOD that is still resolving has no duration and
     * cannot seek yet, and the caller uses a false here to leave the key
     * unconsumed rather than swallowing it into a silent no-op.
     */
    fun seekBy(deltaMs: Long): Boolean {
        val s = player.status.value
        if (!s.isSeekable || s.durationMs <= 0L) return false
        player.seekTo((s.positionMs + deltaMs).coerceIn(0L, s.durationMs))
        return true
    }

    fun togglePlayPause() = player.togglePlayPause()
    fun setVolume(v: Int) = player.setVolume(v)
    fun toggleMute() = player.toggleMute()

    // Save before tearing down, then stop the SHARED player singleton (safe because
    // the Stream and Vod routes are mutually exclusive). Mirrors StreamViewModel.
    override fun onCleared() {
        runCatching { persistProgress() }
        playTicket.stopIfCurrent(ticket) { player.stop() }
    }
}
