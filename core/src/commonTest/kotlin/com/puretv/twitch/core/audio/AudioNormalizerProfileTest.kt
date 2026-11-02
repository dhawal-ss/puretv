package com.puretv.twitch.core.audio

import com.puretv.twitch.core.model.AppSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AudioNormalizerProfileTest {
    @Test
    fun offByDefault() {
        assertFalse(AppSettings().audioNormalizer)
    }

    @Test
    fun vlcArgsLoadTheCompressorWithValuesInsideVlcRanges() = AudioLevelling.entries.forEach { level ->
        val args = AudioNormalizerProfile.vlcArgs(level)
        assertEquals("--audio-filter=compressor", args.first())
        val values = args.drop(1).associate { arg ->
            val (name, value) = arg.removePrefix("--").split("=", limit = 2)
            name to value.toDouble()
        }
        // Ranges from `vlc -H` on VLC 3.0.23.
        val ranges = mapOf(
            "compressor-rms-peak" to 0.0..1.0,
            "compressor-attack" to 1.5..400.0,
            "compressor-release" to 2.0..800.0,
            "compressor-threshold" to -30.0..0.0,
            "compressor-ratio" to 1.0..20.0,
            "compressor-knee" to 1.0..10.0,
            "compressor-makeup-gain" to 0.0..24.0,
        )
        assertEquals(ranges.keys, values.keys)
        ranges.forEach { (name, range) ->
            assertTrue(values.getValue(name) in range, "$level: $name=${values[name]} outside $range")
        }
    }

    @Test
    fun levelsAreStoredByKeyAndUnknownKeysLoadTheDefault() {
        assertEquals(AudioLevelling.BALANCED, AppSettings().audioLevelling)
        AudioLevelling.entries.forEach { assertEquals(it, AudioLevelling.parse(it.key)) }
        assertEquals(AudioLevelling.DEFAULT, AudioLevelling.parse(null))
        assertEquals(AudioLevelling.DEFAULT, AudioLevelling.parse("ludicrous"))
        assertEquals(listOf("light", "balanced", "strong"), AudioLevelling.entries.map { it.key }, "keys are in settings files")
    }

    @Test
    fun strongerLevelsCompressHarderAndLiftMore() {
        val (light, balanced, strong) = AudioLevelling.entries.map { it.curve }
        assertTrue(light.thresholdDb >= balanced.thresholdDb && balanced.thresholdDb >= strong.thresholdDb)
        assertTrue(light.ratio < balanced.ratio && balanced.ratio < strong.ratio)
        assertTrue(light.makeupDb < balanced.makeupDb && balanced.makeupDb < strong.makeupDb)
    }

    @Test
    fun mpvFilterIsCompressorThenLimiterWithoutAutoLevel() = AudioLevelling.entries.forEach { level ->
        val curve = level.curve
        val filter = AudioNormalizerProfile.mpvAudioFilter(level)
        assertTrue(filter.startsWith("lavfi=[acompressor="), filter)
        assertTrue(filter.endsWith("]"), filter)
        val (compressor, limiter) = filter.removePrefix("lavfi=[").removeSuffix("]").split(",")
        val c = options(compressor.removePrefix("acompressor="))
        val l = options(limiter.removePrefix("alimiter="))

        // acompressor wants linear amplitudes and a linear knee ratio (1..8), not dB.
        assertEquals(AudioNormalizerProfile.dbToLinear(curve.thresholdDb), c.getValue("threshold").toDouble(), 1e-4)
        assertEquals(curve.ratio, c.getValue("ratio").toDouble())
        assertTrue(c.getValue("knee").toDouble() in 1.0..8.0)
        assertTrue(c.getValue("makeup").toDouble() in 1.0..64.0)
        assertEquals("peak", c["detection"])
        assertEquals("maximum", c["link"])

        assertTrue(l.getValue("limit").toDouble() in 0.0625..1.0)
        assertEquals("0", l["level"], "auto level would push everything back up to 0 dBFS")
    }

    @Test
    fun numbersUseADotAndNoExponent() {
        assertEquals("-20", AudioNormalizerProfile.number(-20.0))
        assertEquals("1.9953", AudioNormalizerProfile.number(AudioNormalizerProfile.dbToLinear(6.0)))
        assertEquals("0.1", AudioNormalizerProfile.number(0.1))
        val everyNumber = Regex("[0-9]+(\\.[0-9]+)?([eE][-+]?[0-9]+)?")
            .findAll(AudioNormalizerProfile.mpvAudioFilter() + AudioNormalizerProfile.vlcArgs().joinToString(" "))
            .map { it.value }
        assertTrue(everyNumber.none { 'e' in it || 'E' in it })
    }

    private fun options(spec: String): Map<String, String> =
        spec.split(":").associate { it.substringBefore("=") to it.substringAfter("=") }
}
