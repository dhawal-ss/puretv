package com.puretv.twitch.core.audio

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.log10
import kotlin.math.pow

/**
 * The phone and TV audio normalizer: a stereo-linked peak compressor followed by
 * a peak limiter, on the curve of the chosen [level] (see [AudioLevelling]). It runs on ExoPlayer's
 * audio thread for every decoded sample, so it is built to be cheap there:
 *  - nothing is allocated after [configure];
 *  - the envelope follower costs a compare and a multiply-add per frame;
 *  - the curve itself (a log and a power) is worked out once per [BLOCK_FRAMES]
 *    frames, and only when the level is near or above the knee; quieter audio
 *    just gets the makeup gain;
 *  - the gain glides linearly across each block, so stepping it causes no zipper
 *    noise.
 *
 * Nothing looks ahead, so no delay is added. The limiter is what keeps the first
 * few ms of a sudden loud sound (before the attack catches up) from clipping:
 * the gain for a frame is never more than the ceiling divided by its peak.
 *
 * [enabled] and [level] may be changed from any thread; a new level takes over on
 * the audio thread at the next frame, the gain gliding to the new curve over one
 * block. Turning it off fades the effect out
 * over [FADE_MS] instead of jumping, and once the fade is done [isBypassed] says
 * the caller can copy the audio through untouched. Everything else runs on the
 * audio thread only.
 *
 * Samples are full scale at 1.0 (float) or 32768 (16-bit). One frame is one
 * sample per channel, interleaved.
 */
class DynamicRangeCompressor {

    /** Whether the normalizer should be heard. Safe to set from any thread. */
    @Volatile var enabled: Boolean = false

    /** How strongly it works. Safe to set from any thread; applied at the next frame. */
    @Volatile var level: AudioLevelling = AudioLevelling.DEFAULT

    // The level the coefficients below were worked out for (audio thread).
    private var activeLevel: AudioLevelling = AudioLevelling.DEFAULT
    private var curve: AudioCurve = activeLevel.curve
    private var sampleRate = DEFAULT_SAMPLE_RATE

    private var attackCoef = 0f
    private var releaseCoef = 0f
    private var limiterReleaseCoef = 0f
    private var fadeStep = 1f
    private var channelCount = 2

    // Audio-thread state.
    private var envelope = 0f
    private var gain = 1f
    private var gainStep = 0f
    private var blockLeft = 0
    private var limiterGain = 1f
    private var mix = 0f

    private var kneeStart = 0f
    private var makeup = 1f
    private val ceiling = AudioNormalizerProfile.dbToLinear(AudioNormalizerProfile.CEILING_DB).toFloat()

    init {
        configure(DEFAULT_SAMPLE_RATE, 2)
    }

    /** Sets the stream's format and starts from silence. Call before the first sample of a new format. */
    fun configure(sampleRate: Int, channelCount: Int) {
        val rate = sampleRate.coerceAtLeast(1)
        this.sampleRate = rate
        this.channelCount = channelCount.coerceAtLeast(1)
        applyLevel(level)
        limiterReleaseCoef = coefficient(AudioNormalizerProfile.LIMITER_RELEASE_MS, rate)
        fadeStep = (1.0 / (FADE_MS / 1000.0 * rate)).toFloat()
        reset()
    }

    /** Works out the curve's constants for [newLevel] at the current sample rate. */
    private fun applyLevel(newLevel: AudioLevelling) {
        activeLevel = newLevel
        curve = newLevel.curve
        attackCoef = coefficient(curve.attackMs, sampleRate)
        releaseCoef = coefficient(curve.releaseMs, sampleRate)
        kneeStart = AudioNormalizerProfile.dbToLinear(curve.thresholdDb - curve.kneeDb / 2).toFloat()
        makeup = AudioNormalizerProfile.dbToLinear(curve.makeupDb).toFloat()
    }

    /** Forgets the audio so far (a seek, a new stream), keeping the format. */
    fun reset() {
        resetFollowers()
        mix = if (enabled) 1f else 0f
    }

    private fun resetFollowers() {
        envelope = 0f
        gain = makeup
        gainStep = 0f
        blockLeft = 0
        limiterGain = 1f
    }

    /** True when turned off and faded out: the audio can be passed through as is. */
    val isBypassed: Boolean get() = !enabled && mix == 0f

    /**
     * The gain for the next frame, given that frame's [peak] (the largest absolute
     * sample across its channels, full scale 1.0). Multiply every channel of the
     * frame by it; [peak] times the result never exceeds the ceiling.
     */
    fun nextGain(rawPeak: Float): Float {
        val on = enabled
        if (!on && mix == 0f) return 1f
        mix = if (on) minOf(1f, mix + fadeStep) else maxOf(0f, mix - fadeStep)
        if (!on && mix == 0f) {
            // Faded out: forget the followers, so switching back on starts clean
            // instead of from the level of whatever played before.
            resetFollowers()
            return 1f
        }
        // A new strength: swap the curve now, and start a fresh block so the gain glides to it.
        val wanted = level
        if (wanted !== activeLevel) {
            applyLevel(wanted)
            blockLeft = 0
        }
        // A NaN or infinite sample (a broken decoder) must not poison the envelope for good.
        val peak = if (rawPeak.isFinite()) rawPeak else 0f

        val coef = if (peak > envelope) attackCoef else releaseCoef
        envelope = peak + coef * (envelope - peak)

        if (blockLeft == 0) {
            blockLeft = BLOCK_FRAMES
            gainStep = (targetGain(envelope) - gain) / BLOCK_FRAMES
        }
        blockLeft--
        gain += gainStep

        // Fading in or out blends toward unity gain rather than switching.
        val g = 1f + mix * (gain - 1f)

        // Limiter: let go slowly, clamp instantly.
        if (limiterGain < 1f) limiterGain = 1f - limiterReleaseCoef * (1f - limiterGain)
        val out = peak * g * limiterGain
        if (out > ceiling) limiterGain = ceiling / (peak * g)
        // The limiter fades with the rest, so there is no step when it ends.
        return g * (1f + mix * (limiterGain - 1f))
    }

    /** Processes [frameCount] interleaved 16-bit frames of [samples] in place, from [offset]. */
    fun process(samples: ShortArray, offset: Int = 0, frameCount: Int = (samples.size - offset) / channelCount) {
        if (isBypassed) return
        val channels = channelCount
        var i = offset
        repeat(frameCount) {
            var peak = 0
            for (c in 0 until channels) {
                val s = abs(samples[i + c].toInt())
                if (s > peak) peak = s
            }
            val g = nextGain(peak / PCM16_FULL_SCALE)
            for (c in 0 until channels) samples[i + c] = scale16(samples[i + c], g)
            i += channels
        }
    }

    /** Processes [frameCount] interleaved float frames of [samples] in place, from [offset]. */
    fun process(samples: FloatArray, offset: Int = 0, frameCount: Int = (samples.size - offset) / channelCount) {
        if (isBypassed) return
        val channels = channelCount
        var i = offset
        repeat(frameCount) {
            var peak = 0f
            for (c in 0 until channels) {
                val s = abs(samples[i + c])
                if (s > peak) peak = s
            }
            val g = nextGain(peak)
            for (c in 0 until channels) samples[i + c] *= g
            i += channels
        }
    }

    /** Gain for a steady level, makeup included: the static curve with a soft knee. */
    private fun targetGain(level: Float): Float {
        if (level <= kneeStart) return makeup
        val threshold = curve.thresholdDb
        val knee = curve.kneeDb
        val slope = 1.0 / curve.ratio - 1.0
        val x = 20.0 * log10(level.toDouble())
        val over = x - threshold
        val reductionDb = if (over < knee / 2) {
            val k = over + knee / 2
            slope * k * k / (2 * knee)
        } else {
            slope * over
        }
        return 10.0.pow((reductionDb + curve.makeupDb) / 20.0).toFloat()
    }

    companion object {
        /** Frames between two evaluations of the curve: a third of a ms at 48 kHz. */
        const val BLOCK_FRAMES = 16

        /** How long switching the normalizer on or off takes to blend in. */
        const val FADE_MS = 50.0

        private const val DEFAULT_SAMPLE_RATE = 48_000
        private const val PCM16_FULL_SCALE = 32768f

        /** [sample] times [gain], clamped to the 16-bit range instead of wrapping around. */
        fun scale16(sample: Short, gain: Float): Short {
            val scaled = (sample * gain).toInt()
            return when {
                scaled > Short.MAX_VALUE -> Short.MAX_VALUE
                scaled < Short.MIN_VALUE -> Short.MIN_VALUE
                else -> scaled.toShort()
            }
        }

        /** One-pole smoothing coefficient that covers about 63% of a step in [ms]. */
        private fun coefficient(ms: Double, sampleRate: Int): Float =
            exp(-1.0 / (ms / 1000.0 * sampleRate)).toFloat()
    }
}
