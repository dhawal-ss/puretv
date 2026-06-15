package com.puretv.twitch.desktop.player

/** Default startup volume (percent). Streams begin here so users can adjust up or down. */
const val DEFAULT_VOLUME = 50

/** Pure volume/mute state, decoupled from VLCJ for testability. */
data class VolumeState(val volume: Int, val isMuted: Boolean, val preMute: Int)

fun applyMuteToggle(s: VolumeState): VolumeState =
    if (s.isMuted) {
        s.copy(volume = s.preMute.coerceIn(1, 100), isMuted = false)
    } else {
        val remember = if (s.volume > 0) s.volume else DEFAULT_VOLUME
        s.copy(volume = 0, isMuted = true, preMute = remember)
    }

fun applyVolumeChange(s: VolumeState, requested: Int): VolumeState {
    val v = requested.coerceIn(0, 100)
    return s.copy(volume = v, isMuted = if (v > 0) false else s.isMuted)
}
