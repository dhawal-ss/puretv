package com.puretv.twitch.desktop.ui

import com.puretv.twitch.core.audio.AudioLevelling
import com.puretv.twitch.core.di.TokenHolder
import com.puretv.twitch.desktop.data.DesktopSettingsStore
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AudioNormalizerSwitchTest {
    private val tmp: File = Files.createTempDirectory("normalizer-switch-test").toFile()

    @AfterTest fun cleanup() { tmp.deleteRecursively() }

    @Test fun savesTheSettingAndTellsTheRunningPlayer() {
        val store = DesktopSettingsStore(TokenHolder(), tmp)
        val told = mutableListOf<AudioLevelling?>()
        val viewModel = SettingsViewModel(store, onAudioLevellingChange = { told += it })

        viewModel.setAudioNormalizer(true)
        assertTrue(store.settings.value.audioNormalizer)
        viewModel.setAudioNormalizer(false)

        // On means the saved strength (Balanced until one is picked); off means no levelling.
        assertEquals(listOf(AudioLevelling.BALANCED, null), told)
        viewModel.dispose()
        store.flush()
    }

    @Test fun aNewStrengthIsSavedAndReachesThePlayerOnlyWhileItIsOn() {
        val store = DesktopSettingsStore(TokenHolder(), tmp)
        val told = mutableListOf<AudioLevelling?>()
        val viewModel = SettingsViewModel(store, onAudioLevellingChange = { told += it })

        // Picked while off: remembered for later, and the player stays without levelling.
        viewModel.setAudioLevelling(AudioLevelling.STRONG)
        assertEquals(AudioLevelling.STRONG, store.settings.value.audioLevelling)
        viewModel.setAudioNormalizer(true)
        viewModel.setAudioLevelling(AudioLevelling.LIGHT)

        assertEquals(listOf(null, AudioLevelling.STRONG, AudioLevelling.LIGHT), told)
        viewModel.dispose()
        store.flush()
    }
}
