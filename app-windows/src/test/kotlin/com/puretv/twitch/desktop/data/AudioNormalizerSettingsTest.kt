package com.puretv.twitch.desktop.data

import com.puretv.twitch.core.audio.AudioLevelling
import com.puretv.twitch.core.di.TokenHolder
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AudioNormalizerSettingsTest {
    private val tmp: File = Files.createTempDirectory("settings-normalizer-test").toFile()

    @AfterTest fun cleanup() { tmp.deleteRecursively() }

    @Test fun survivesARestart() {
        val store = DesktopSettingsStore(TokenHolder(), tmp)
        store.updateSettings { it.copy(audioNormalizer = true) }
        store.flush()

        assertTrue(DesktopSettingsStore(TokenHolder(), tmp).settings.value.audioNormalizer)
    }

    @Test fun olderSettingsFilesLoadWithItOff() {
        File(tmp, "settings.json").writeText("""{"preferredQuality":"source","lowLatencyMode":false}""")

        val settings = DesktopSettingsStore(TokenHolder(), tmp).settings.value
        assertFalse(settings.audioNormalizer)
        assertEquals(AudioLevelling.BALANCED, settings.audioLevelling)
        assertFalse(settings.lowLatencyMode, "the rest of the file still loads")
    }

    @Test fun theStrengthSurvivesARestartAsItsKey() {
        val store = DesktopSettingsStore(TokenHolder(), tmp)
        store.updateSettings { it.copy(audioNormalizer = true, audioLevelling = AudioLevelling.STRONG) }
        store.flush()

        // Stored by its key, which never changes, not by the enum's name.
        assertTrue(Regex(""""audioLevelling"\s*:\s*"strong"""").containsMatchIn(File(tmp, "settings.json").readText()))
        assertEquals(AudioLevelling.STRONG, DesktopSettingsStore(TokenHolder(), tmp).settings.value.audioLevelling)
    }

    @Test fun anUnknownStrengthLoadsAsBalanced() {
        File(tmp, "settings.json").writeText("""{"audioNormalizer":true,"audioLevelling":"loudest"}""")

        val settings = DesktopSettingsStore(TokenHolder(), tmp).settings.value
        assertTrue(settings.audioNormalizer)
        assertEquals(AudioLevelling.BALANCED, settings.audioLevelling)
    }
}
