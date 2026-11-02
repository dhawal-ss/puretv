package com.puretv.twitch.desktop.player

import com.puretv.twitch.core.audio.AudioLevelling
import com.puretv.twitch.core.audio.AudioNormalizerProfile
import com.puretv.twitch.core.model.AppSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame

// Nothing here starts libVLC: a VlcEngine only loads it on first use, and CI has no VLC.
class VlcEnginesTest {
    private val sr = RtxVideoChoice(superResolution = true, hdr = false)

    @Test fun theLevelInUseIsTheStrengthOnlyWhileTheSwitchIsOn() {
        assertNull(AppSettings(audioNormalizer = false, audioLevelling = AudioLevelling.STRONG).audioLevellingInUse())
        assertEquals(AudioLevelling.STRONG, AppSettings(audioNormalizer = true, audioLevelling = AudioLevelling.STRONG).audioLevellingInUse())
        assertEquals(AudioLevelling.DEFAULT, AppSettings(audioNormalizer = true).audioLevellingInUse())
    }

    @Test fun theNormalizerIsAnInstanceArgumentAndRtxVideoAnOptionalOne() {
        assertEquals(emptyList(), VlcEngineKey.standard(null).extraArgs())
        assertEquals(emptyList(), VlcEngineKey.standard(null).optionalArgs())
        for (level in AudioLevelling.entries) {
            assertEquals(AudioNormalizerProfile.vlcArgs(level), VlcEngineKey.standard(level).extraArgs())
        }
        // Each strength is its own set of arguments, so its own instance.
        assertNotEquals(VlcEngineKey.standard(AudioLevelling.LIGHT).extraArgs(), VlcEngineKey.standard(AudioLevelling.STRONG).extraArgs())
        // RTX Video stays optional (libVLC may refuse it); the normalizer never moves there.
        val both = VlcEngineKey(sr, AudioLevelling.BALANCED)
        assertEquals(AudioNormalizerProfile.vlcArgs(AudioLevelling.BALANCED), both.extraArgs())
        assertEquals(sr.vlcArgs(), both.optionalArgs())
        assertEquals(emptyList(), VlcEngineKey(sr, null).extraArgs())
    }

    @Test fun eachKeyGetsOneInstanceMadeOnlyWhenAsked() {
        val made = mutableListOf<VlcEngineKey>()
        val engines = VlcEngines(levelling = { null }, make = { made += it; VlcEngine(it::extraArgs, it::optionalArgs) })
        assertEquals(emptyList(), made)

        val off = engines.engineFor(VlcEngineKey.standard(null))
        assertSame(off, engines.engineFor(VlcEngineKey.standard(null)))
        val balanced = engines.engineFor(VlcEngineKey.standard(AudioLevelling.BALANCED))
        assertNotSame(off, balanced)
        // Another strength is another instance, never the same one with stale arguments.
        assertNotSame(balanced, engines.engineFor(VlcEngineKey.standard(AudioLevelling.STRONG)))
        // RTX Video with and without the normalizer are two more, never the plain ones.
        val rtxOn = engines.engineFor(VlcEngineKey(sr, AudioLevelling.BALANCED))
        assertNotSame(balanced, rtxOn)
        assertNotSame(rtxOn, engines.engineFor(VlcEngineKey(sr, null)))
        assertEquals(5, made.size)
        assertEquals(made.toSet().size, made.size)
    }

    @Test fun nothingCountsAsBuiltAndReleasingUnbuiltInstancesDoesNothing() {
        val engines = VlcEngines(levelling = { AudioLevelling.BALANCED })
        val key = VlcEngineKey.standard(AudioLevelling.BALANCED)
        assertFalse(engines.isBuilt(key))
        engines.engineFor(key)
        assertFalse(engines.isBuilt(key))
        // Shutdown with instances that never started must not load libVLC to free them.
        engines.releaseIfBuilt()
        engines.releaseIfBuilt()
    }

    @Test fun theSettingIsReadEachTimeNotBakedIn() {
        var setting: AudioLevelling? = null
        val engines = VlcEngines(levelling = { setting })
        assertNull(engines.levellingWanted())
        setting = AudioLevelling.LIGHT
        assertEquals(AudioLevelling.LIGHT, engines.levellingWanted())
    }

    @Test fun aChangeReachesEveryFollowingPlayerUntilItStopsFollowing() {
        val engines = VlcEngines(levelling = { null })
        val first = mutableListOf<AudioLevelling?>()
        val second = mutableListOf<AudioLevelling?>()
        val followFirst: (AudioLevelling?) -> Unit = { first += it }
        val followSecond: (AudioLevelling?) -> Unit = { second += it }
        engines.follow(followFirst)
        engines.follow(followSecond)

        engines.levellingChanged(AudioLevelling.BALANCED)
        engines.unfollow(followFirst)
        engines.levellingChanged(AudioLevelling.STRONG)
        engines.levellingChanged(null)

        assertEquals(listOf<AudioLevelling?>(AudioLevelling.BALANCED), first)
        assertEquals(listOf(AudioLevelling.BALANCED, AudioLevelling.STRONG, null), second)
    }

    @Test fun oneFailingFollowerDoesNotStopTheOthers() {
        val engines = VlcEngines(levelling = { null })
        val told = mutableListOf<AudioLevelling?>()
        engines.follow { error("a released player") }
        engines.follow { told += it }
        engines.levellingChanged(AudioLevelling.LIGHT)
        assertEquals(listOf<AudioLevelling?>(AudioLevelling.LIGHT), told)
    }
}
