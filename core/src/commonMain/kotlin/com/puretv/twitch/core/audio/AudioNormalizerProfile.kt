package com.puretv.twitch.core.audio

import kotlin.math.pow
import kotlin.math.roundToLong

/**
 * The one curve behind the "Even out loud and quiet audio" setting, written once
 * in dB and ms and handed to each player in the form it understands:
 *  - VLC 3.0.23 (Windows): its `compressor` audio filter, as libVLC instance
 *    arguments ([vlcArgs]). VLC only loads audio filters per instance, so
 *    switching it runs the stream on a second libVLC instance built with them.
 *  - mpv (Windows): ffmpeg's `acompressor` then `alimiter`, as the `af`
 *    property ([mpvAudioFilter]), set live.
 *  - ExoPlayer (phone and TV): [DynamicRangeCompressor], run on the decoded PCM.
 *
 * Every detector is a stereo-linked peak detector: the loudest channel sets one
 * gain for all channels, so the stereo image never shifts. Nothing here looks
 * ahead (the limiter's [LIMITER_ATTACK_MS] on mpv aside), so turning it on adds
 * no delay and leaves low latency alone.
 *
 * Quiet passages (below the knee) only get [MAKEUP_DB]; louder ones are pulled
 * down at [RATIO]:1 above [THRESHOLD_DB]. A voice peaking at -3 dBFS ends up
 * around -11 dBFS, about where it was before, while background music at -36 dBFS
 * comes up to -25 dBFS: a 33 dB gap shrinks to about 14 dB. (1.17.0 retune; the
 * first curve, -20 dB at 3:1 with +4 dB, only shrank it to about 20 dB, issue #36.)
 */
object AudioNormalizerProfile {
    /** Where compression starts, in dBFS of the peak level. VLC accepts nothing lower. */
    const val THRESHOLD_DB = -30.0

    /** Above the threshold, every [RATIO] dB in gives 1 dB out. */
    const val RATIO = 4.0

    /** How fast the gain comes down when the audio gets louder. */
    const val ATTACK_MS = 3.0

    /** How fast the gain recovers when it gets quieter again. Slow enough not to pump. */
    const val RELEASE_MS = 300.0

    /** Width of the soft knee centred on the threshold, so the curve has no corner. */
    const val KNEE_DB = 8.0

    /** Fixed gain on top: lifts quiet audio and keeps a compressed voice about as loud as before. */
    const val MAKEUP_DB = 11.0

    /**
     * Peak ceiling of the limiter after the compressor (mpv and ExoPlayer). It
     * catches the first few ms of a sudden loud sound, before the attack has
     * pulled the gain down, which would otherwise clip with the makeup gain on.
     * VLC 3.0.23 has no limiter module, so VLC runs the compressor alone.
     */
    const val CEILING_DB = -1.0

    /** How fast the limiter lets go once the peak has passed. */
    const val LIMITER_RELEASE_MS = 50.0

    /**
     * ffmpeg's `alimiter` looks this far ahead and delays the audio by as much.
     * 1 ms is its smallest useful value and nothing anyone can hear or see.
     */
    const val LIMITER_ATTACK_MS = 1.0

    /**
     * libVLC instance arguments for VLC's `compressor` filter. Each value sits
     * inside the ranges VLC 3.0.23 accepts (`vlc -H`): threshold -30..0 dB,
     * ratio 1..20, attack 1.5..400 ms, release 2..800 ms, knee 1..10 dB, makeup
     * 0..24 dB, rms-peak 0..1 (1 means peak detection).
     */
    fun vlcArgs(): List<String> = listOf(
        "--audio-filter=compressor",
        "--compressor-rms-peak=1",
        "--compressor-attack=${number(ATTACK_MS)}",
        "--compressor-release=${number(RELEASE_MS)}",
        "--compressor-threshold=${number(THRESHOLD_DB)}",
        "--compressor-ratio=${number(RATIO)}",
        "--compressor-knee=${number(KNEE_DB)}",
        "--compressor-makeup-gain=${number(MAKEUP_DB)}",
    )

    /**
     * The mpv `af` value. `acompressor` takes threshold and makeup as linear
     * amplitudes and the knee as a linear ratio (1..8), not dB. `alimiter`
     * auto-levels its output up to 0 dBFS unless `level` is off, which would
     * undo the whole point, so it is switched off here.
     */
    fun mpvAudioFilter(): String {
        val compressor = listOf(
            "threshold=${number(dbToLinear(THRESHOLD_DB))}",
            "ratio=${number(RATIO)}",
            "attack=${number(ATTACK_MS)}",
            "release=${number(RELEASE_MS)}",
            "knee=${number(dbToLinear(KNEE_DB))}",
            "makeup=${number(dbToLinear(MAKEUP_DB))}",
            "detection=peak",
            "link=maximum",
        ).joinToString(":")
        val limiter = listOf(
            "limit=${number(dbToLinear(CEILING_DB))}",
            "attack=${number(LIMITER_ATTACK_MS)}",
            "release=${number(LIMITER_RELEASE_MS)}",
            "level=0",
        ).joinToString(":")
        return "lavfi=[acompressor=$compressor,alimiter=$limiter]"
    }

    fun dbToLinear(db: Double): Double = 10.0.pow(db / 20.0)

    /**
     * A number for a command line: at most four decimals, a dot as the decimal
     * separator whatever the system locale, and no `.0` on whole numbers.
     */
    internal fun number(value: Double): String {
        val rounded = (value * 10_000).roundToLong() / 10_000.0
        val whole = rounded.roundToLong()
        return if (rounded == whole.toDouble()) whole.toString() else rounded.toString()
    }
}
