package com.puretv.twitch.desktop.player

import com.puretv.twitch.core.model.UpscalingMode
import java.awt.Component
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ChannelClaimsTest {
    @Test fun onlyTheLastWatcherReleasesAChannel() {
        val claims = ChannelClaims()
        claims.acquire("Shroud") // a window
        claims.acquire("shroud") // a tile
        assertFalse(claims.release("SHROUD"))
        assertTrue(claims.release("shroud"))
    }

    @Test fun channelsAreIndependent() {
        val claims = ChannelClaims()
        claims.acquire("a")
        claims.acquire("b")
        assertTrue(claims.release("a"))
        assertTrue(claims.release("b"))
    }

    @Test fun releasingWhatWasNeverClaimedIsSafe() {
        val claims = ChannelClaims()
        assertTrue(claims.release("x"))
        claims.acquire("x")
        assertTrue(claims.release("x"))
    }
}

class ReleasablePlayerTest {
    private class FakePlayer : DesktopPlayer {
        val calls = ArrayList<String>()
        override val status: StateFlow<PlayerStatus> = MutableStateFlow(PlayerStatus())
        override val isAvailable = true
        override fun play(streamUrl: String, lowLatency: Boolean) { calls += "play" }
        override fun pause() { calls += "pause" }
        override fun resume() { calls += "resume" }
        override fun togglePlayPause() { calls += "toggle" }
        override fun stop() { calls += "stop" }
        override fun seekTo(positionMs: Long) { calls += "seek" }
        override fun setVolume(volume: Int) { calls += "volume" }
        override fun toggleMute() { calls += "mute" }
        override fun attachToPanel(panel: Component) { calls += "attach" }
        override fun detachFromPanel() { calls += "detach" }
        override fun release() { calls += "release" }
        override fun setUpscaling(mode: UpscalingMode) { calls += "scale" }
    }

    @Test fun releaseStopsThenFreesExactlyOnce() {
        val inner = FakePlayer()
        val player = ReleasablePlayer(inner)
        player.release()
        player.release()
        assertEquals(listOf("stop", "release"), inner.calls)
    }

    @Test fun nothingReachesTheFreedPlayerAfterRelease() {
        val inner = FakePlayer()
        val player = ReleasablePlayer(inner)
        player.play("u")
        player.release()
        inner.calls.clear()
        player.stop() // a screen leaving composition stops its player late
        player.play("u")
        player.setVolume(50)
        player.seekTo(1)
        player.toggleMute()
        player.setUpscaling(UpscalingMode.OFF)
        player.detachFromPanel()
        assertEquals(emptyList(), inner.calls)
    }
}

class SecondaryPlaybackTest {
    @Test fun releasingBeforePreparingBuildsNothing() {
        // The engine is never touched: a window that closed before its player was made makes none.
        val secondary = SecondaryPlayback(VlcEngines(levelling = { null }), ChannelClaims())
        secondary.release()
        secondary.release()
        assertFalse(secondary.prepare())
    }
}
