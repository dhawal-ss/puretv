package com.puretv.twitch.core.audio

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DynamicRangeCompressorTest {
    private val rate = 48_000
    private val makeup = AudioNormalizerProfile.dbToLinear(AudioLevelling.DEFAULT.curve.makeupDb).toFloat()
    private val ceiling = AudioNormalizerProfile.dbToLinear(AudioNormalizerProfile.CEILING_DB).toFloat()

    private fun compressor(on: Boolean = true) = DynamicRangeCompressor().apply {
        enabled = on
        configure(rate, 2)
    }

    /** Interleaved stereo sine, the same on both channels unless [right] says otherwise. */
    private fun sine(amplitude: Float, seconds: Double, right: Float = amplitude): FloatArray {
        val frames = (rate * seconds).toInt()
        return FloatArray(frames * 2) { i ->
            val s = sin(2 * PI * 440 * (i / 2) / rate).toFloat()
            if (i % 2 == 0) amplitude * s else right * s
        }
    }

    private fun peak(samples: FloatArray, from: Int = 0): Float {
        var p = 0f
        for (i in from until samples.size) p = maxOf(p, abs(samples[i]))
        return p
    }

    @Test
    fun loudAudioIsTurnedDown() {
        val audio = sine(0.9f, seconds = 1.0)
        compressor().process(audio)
        // Settled second half: -0.9 dBFS in comes out near -11.7 dBFS on the 4:1 curve.
        val settled = peak(audio, from = audio.size / 2)
        assertTrue(settled < 0.9f * 0.5f, "loud peak only came down to $settled")
        assertTrue(settled > 0.25f, "loud peak squashed too far, to $settled")
    }

    /** Settled peak in dBFS of a 1 s sine at [amplitude] through a fresh compressor at [level]. */
    private fun settledDb(amplitude: Float, level: AudioLevelling): Double {
        val audio = sine(amplitude, seconds = 1.0)
        compressor().apply { this.level = level }.process(audio)
        return 20 * kotlin.math.log10(peak(audio, from = audio.size / 2).toDouble())
    }

    @Test
    fun voiceAndBackgroundMusicEndUpMuchCloser() {
        // Issue #36: a streamer's voice peaking at -3 dBFS over music at -36 dBFS is
        // a 33 dB gap with it off. Each level closes more of it, and none of them
        // makes the voice much quieter than it was.
        val gaps = AudioLevelling.entries.associateWith { settledDb(0.708f, it) - settledDb(0.0158f, it) }
        assertTrue(gaps.getValue(AudioLevelling.LIGHT) < 22.0, "light gap ${gaps[AudioLevelling.LIGHT]}")
        assertTrue(gaps.getValue(AudioLevelling.BALANCED) < 16.0, "balanced gap ${gaps[AudioLevelling.BALANCED]}")
        assertTrue(gaps.getValue(AudioLevelling.STRONG) < 11.0, "strong gap ${gaps[AudioLevelling.STRONG]}")
        assertTrue(gaps.getValue(AudioLevelling.STRONG) < gaps.getValue(AudioLevelling.BALANCED))
        assertTrue(gaps.getValue(AudioLevelling.BALANCED) < gaps.getValue(AudioLevelling.LIGHT))
        AudioLevelling.entries.forEach { level ->
            val voice = settledDb(0.708f, level)
            assertTrue(voice in -14.0..-8.0, "$level voice came out at $voice dBFS")
        }
    }

    @Test
    fun theLevelCanChangeWhilePlaying() {
        val c = compressor()
        val first = sine(0.0158f, seconds = 0.5)
        c.process(first)
        c.level = AudioLevelling.STRONG
        val second = sine(0.0158f, seconds = 0.5)
        c.process(second)
        // Quiet music sits below every knee, so it gets exactly the new makeup gain.
        val strongMakeup = AudioNormalizerProfile.dbToLinear(AudioLevelling.STRONG.curve.makeupDb).toFloat()
        assertEquals(0.0158f * strongMakeup, peak(second, from = second.size / 2), 1e-3f)
    }

    @Test
    fun quietAudioOnlyGetsTheMakeupGain() {
        val input = sine(0.01f, seconds = 0.5)
        val audio = input.copyOf()
        compressor().process(audio)
        // -40 dBFS sits well below the knee: no compression, just the fixed makeup.
        for (i in audio.indices) {
            assertEquals(input[i] * makeup, audio[i], 1e-4f, "sample $i")
        }
    }

    @Test
    fun aSuddenFullScaleSoundNeverClips() {
        val silence = ShortArray(rate) // half a second of stereo silence
        val loud = ShortArray(rate) { if ((it / 2 / 24) % 2 == 0) Short.MAX_VALUE else Short.MIN_VALUE }
        val c = compressor()
        c.process(silence)
        c.process(loud)
        val limit = (ceiling * 32768).toInt() + 1
        loud.forEachIndexed { i, s ->
            assertTrue(abs(s.toInt()) <= limit, "sample $i is $s, over the ceiling")
        }
        // Signs survive: clamping, never wrapping around.
        assertTrue(loud[0] > 0 && loud[48] < 0)

        val floatAudio = FloatArray(rate) { if ((it / 2 / 24) % 2 == 0) 1f else -1f }
        compressor().process(floatAudio)
        assertTrue(peak(floatAudio) <= ceiling + 1e-5f, "float peak ${peak(floatAudio)}")
    }

    @Test
    fun bothChannelsGetTheSameGain() {
        val input = sine(0.9f, seconds = 0.5, right = 0.05f)
        val audio = input.copyOf()
        compressor().process(audio)
        var frame = 0
        while (frame * 2 < audio.size) {
            val l = frame * 2
            if (abs(input[l + 1]) > 0.01f) {
                assertEquals(audio[l] / input[l], audio[l + 1] / input[l + 1], 1e-3f, "frame $frame")
            }
            frame++
        }
        // Once settled, the quiet right channel is turned down with the loud left
        // one, not boosted as it would be on its own.
        val settledRight = FloatArray(audio.size / 4) { audio[audio.size / 2 + it * 2 + 1] }
        assertTrue(peak(settledRight) < 0.05f * 0.5f, "right channel peak ${peak(settledRight)}")
    }

    @Test
    fun offPassesAudioThroughUntouched() {
        val input = ShortArray(4_800) { (it * 37 % 20_000 - 10_000).toShort() }
        val audio = input.copyOf()
        val c = compressor(on = false)
        assertTrue(c.isBypassed)
        c.process(audio)
        assertContentEquals(input, audio)
    }

    @Test
    fun turningItOffFadesOutThenBypasses() {
        val c = compressor()
        c.process(sine(0.9f, seconds = 0.5))
        c.enabled = false
        assertFalse(c.isBypassed, "fades out first")
        val fade = sine(0.9f, seconds = DynamicRangeCompressor.FADE_MS / 1000.0 + 0.01)
        val before = fade.copyOf()
        c.process(fade)
        assertTrue(c.isBypassed)
        // The last frames are already at unity gain.
        assertEquals(before.last(), fade.last(), 1e-6f)
        // No jump: neighbouring gains move a little at a time.
        var lastGain = Float.NaN
        for (i in 0 until fade.size step 2) {
            if (abs(before[i]) < 0.2f) continue
            val g = fade[i] / before[i]
            if (!lastGain.isNaN()) assertTrue(abs(g - lastGain) < 0.05f, "gain jumped at $i")
            lastGain = g
        }
    }

    @Test
    fun stateCarriesAcrossBuffers() {
        val whole = sine(0.9f, seconds = 0.3)
        val split = whole.copyOf()
        compressor().process(whole)

        val c = compressor()
        var offset = 0
        while (offset < split.size) {
            val frames = minOf(7, (split.size - offset) / 2)
            c.process(split, offset, frames)
            offset += frames * 2
        }
        assertContentEquals(whole, split)
    }

    @Test
    fun nonFinitePeaksDoNotPoisonTheEnvelope() {
        val c = compressor()
        c.nextGain(Float.NaN)
        c.nextGain(Float.POSITIVE_INFINITY)
        val audio = sine(0.01f, seconds = 0.2)
        val input = audio.copyOf()
        c.process(audio)
        for (i in audio.indices) {
            assertEquals(input[i] * makeup, audio[i], 1e-4f, "sample $i")
        }
    }

    @Test
    fun noStepWhenALimitedSignalFadesOut() {
        val c = compressor()
        c.process(sine(0.9f, seconds = 0.3))
        c.process(FloatArray(2 * 4_800) { 1f }) // pins the limiter well below unity
        c.enabled = false
        var last = Float.NaN
        var steps = 0
        while (!c.isBypassed && steps < rate) {
            val g = c.nextGain(1f)
            if (!last.isNaN()) assertTrue(abs(g - last) < 0.01f, "gain stepped from $last to $g")
            last = g
            steps++
        }
        assertTrue(c.isBypassed)
        assertTrue(abs(c.nextGain(1f) - last) < 0.01f, "step at the end of the fade")
    }

    @Test
    fun switchingBackOnStartsCleanInsteadOfFromStaleLevels() {
        val c = compressor()
        c.process(sine(0.9f, seconds = 0.5))
        c.enabled = false
        c.process(sine(0.9f, seconds = 0.2))
        assertTrue(c.isBypassed)
        c.enabled = true
        val audio = sine(0.01f, seconds = 0.3)
        val input = audio.copyOf()
        c.process(audio)
        // Once faded back in, quiet audio gets the plain makeup, not a dip left over from the loud audio.
        val from = (rate * 0.1).toInt() * 2
        for (i in from until audio.size) {
            assertEquals(input[i] * makeup, audio[i], 1e-4f, "sample $i")
        }
    }

    @Test
    fun clampsInsteadOfWrapping() {
        assertEquals(Short.MAX_VALUE, DynamicRangeCompressor.scale16(30_000, 2f))
        assertEquals(Short.MIN_VALUE, DynamicRangeCompressor.scale16(-30_000, 2f))
        assertEquals(1_500.toShort(), DynamicRangeCompressor.scale16(1_000, 1.5f))
    }
}
