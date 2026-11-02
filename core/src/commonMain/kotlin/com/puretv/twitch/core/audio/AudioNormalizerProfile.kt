package com.puretv.twitch.core.audio

import kotlin.math.pow
import kotlin.math.roundToLong

/**
 * One compressor curve, in dB and ms. Every detector is a stereo-linked peak
 * detector, so the loudest channel sets one gain for all and the stereo image
 * never shifts. Quiet passages (below the knee) only get [makeupDb]; louder ones
 * are pulled down at [ratio]:1 above [thresholdDb].
 */
data class AudioCurve(
    /** Where compression starts, in dBFS of the peak level. VLC accepts -30..0. */
    val thresholdDb: Double,
    /** Above the threshold, every [ratio] dB in gives 1 dB out. */
    val ratio: Double,
    /** How fast the gain comes down when the audio gets louder. */
    val attackMs: Double,
    /** How fast the gain recovers when it gets quieter again. Slow enough not to pump. */
    val releaseMs: Double,
    /** Width of the soft knee centred on the threshold, so the curve has no corner. */
    val kneeDb: Double,
    /** Fixed gain on top, which is what lifts the quiet parts. */
    val makeupDb: Double,
)

/**
 * How strongly "Even out loud and quiet audio" works. Each level is one curve,
 * handed to every player by [AudioNormalizerProfile], so VLC, mpv, the phone and
 * the TV all sound the same at the same level. The numbers in each description
 * are for a voice peaking at -3 dBFS over background music at -36 dBFS (33 dB
 * apart with it off), checked in DynamicRangeCompressorTest.
 */
enum class AudioLevelling(
    /** Stored in settings files; never rename. */
    val key: String,
    val label: String,
    val description: String,
    val curve: AudioCurve,
) {
    /** The 1.13 curve: takes the edge off sudden loud moments, leaves the rest alone. About 20 dB apart. */
    LIGHT(
        key = "light",
        label = "Light",
        description = "Takes the edge off sudden loud moments.",
        curve = AudioCurve(thresholdDb = -20.0, ratio = 3.0, attackMs = 5.0, releaseMs = 250.0, kneeDb = 6.0, makeupDb = 4.0),
    ),

    /** Brings quiet voices and music much closer to loud ones; a voice stays about as loud. About 14 dB apart. */
    BALANCED(
        key = "balanced",
        label = "Balanced",
        description = "Brings quiet voices and music closer to loud ones.",
        curve = AudioCurve(thresholdDb = -30.0, ratio = 4.0, attackMs = 3.0, releaseMs = 300.0, kneeDb = 8.0, makeupDb = 11.0),
    ),

    /** Close to one level throughout, for quiet listening. Lifts background noise too. About 10 dB apart. */
    STRONG(
        key = "strong",
        label = "Strong",
        description = "Keeps everything at about one level, good for quiet listening.",
        curve = AudioCurve(thresholdDb = -30.0, ratio = 8.0, attackMs = 3.0, releaseMs = 400.0, kneeDb = 10.0, makeupDb = 16.0),
    ),
    ;

    companion object {
        val DEFAULT = BALANCED

        /** The level stored as [key]; anything unknown (or an older file without one) is [DEFAULT]. */
        fun parse(key: String?): AudioLevelling = entries.firstOrNull { it.key.equals(key, ignoreCase = true) } ?: DEFAULT
    }
}

/**
 * The curves behind the "Even out loud and quiet audio" setting, handed to each
 * player in the form it understands:
 *  - VLC 3.0.24 (Windows): its `compressor` audio filter, as libVLC instance
 *    arguments ([vlcArgs]). VLC only loads audio filters per instance, so
 *    switching it runs the stream on another libVLC instance built with them.
 *  - mpv (Windows): ffmpeg's `acompressor` then `alimiter`, as the `af`
 *    property ([mpvAudioFilter]), set live.
 *  - ExoPlayer (phone and TV): [DynamicRangeCompressor], run on the decoded PCM.
 *
 * Nothing here looks ahead (the limiter's [LIMITER_ATTACK_MS] on mpv aside), so
 * turning it on adds no delay and leaves low latency alone.
 */
object AudioNormalizerProfile {
    /**
     * Peak ceiling of the limiter after the compressor (mpv and ExoPlayer). It
     * catches the first few ms of a sudden loud sound, before the attack has
     * pulled the gain down, which would otherwise clip with the makeup gain on.
     * VLC 3.0 has no limiter module, so VLC runs the compressor alone.
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
     * libVLC instance arguments for VLC's `compressor` filter at [level]. Each
     * value sits inside the ranges VLC 3.0 accepts (`vlc -H`): threshold -30..0 dB,
     * ratio 1..20, attack 1.5..400 ms, release 2..800 ms, knee 1..10 dB, makeup
     * 0..24 dB, rms-peak 0..1 (1 means peak detection).
     */
    fun vlcArgs(level: AudioLevelling = AudioLevelling.DEFAULT): List<String> {
        val c = level.curve
        return listOf(
            "--audio-filter=compressor",
            "--compressor-rms-peak=1",
            "--compressor-attack=${number(c.attackMs)}",
            "--compressor-release=${number(c.releaseMs)}",
            "--compressor-threshold=${number(c.thresholdDb)}",
            "--compressor-ratio=${number(c.ratio)}",
            "--compressor-knee=${number(c.kneeDb)}",
            "--compressor-makeup-gain=${number(c.makeupDb)}",
        )
    }

    /**
     * The mpv `af` value at [level]. `acompressor` takes threshold and makeup as
     * linear amplitudes and the knee as a linear ratio (1..8), not dB. `alimiter`
     * auto-levels its output up to 0 dBFS unless `level` is off, which would undo
     * the whole point, so it is switched off here.
     */
    fun mpvAudioFilter(level: AudioLevelling = AudioLevelling.DEFAULT): String {
        val c = level.curve
        val compressor = listOf(
            "threshold=${number(dbToLinear(c.thresholdDb))}",
            "ratio=${number(c.ratio)}",
            "attack=${number(c.attackMs)}",
            "release=${number(c.releaseMs)}",
            "knee=${number(dbToLinear(c.kneeDb).coerceAtMost(8.0))}",
            "makeup=${number(dbToLinear(c.makeupDb))}",
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
