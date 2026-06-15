package com.puretv.twitch.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ChannelStatsSnapshotTest {
    @Test fun liveSnapshotCarriesStreamFields() {
        val ch = ChannelInfo(
            id = "1", login = "shroud", displayName = "shroud",
            profileImageUrl = "http://img", broadcasterType = "partner",
            createdAt = "2012-11-03T15:50:32Z",
        )
        val live = StreamInfo(
            id = "s1", userId = "1", userLogin = "shroud", userName = "shroud",
            gameName = "VALORANT", title = "ranked grind", viewerCount = 12000,
            startedAt = "2026-06-15T10:00:00Z", language = "en",
        )
        val snap = ChannelStatsSnapshot.from(ch, live, 11301990L)
        assertTrue(snap.isLive)
        assertEquals(12000, snap.viewerCount)
        assertEquals("VALORANT", snap.gameName)
        assertEquals("en", snap.language)
        assertEquals(11301990L, snap.followerCount)
        assertEquals("2012-11-03T15:50:32Z", snap.createdAtIso)
    }

    @Test fun offlineSnapshotHasNullLiveFields() {
        val ch = ChannelInfo(id = "1", login = "x", displayName = "X")
        val snap = ChannelStatsSnapshot.from(ch, null, null)
        assertFalse(snap.isLive)
        assertNull(snap.viewerCount)
        assertNull(snap.gameName)
        assertNull(snap.followerCount)
        assertNull(snap.startedAtIso)
    }

    @Test fun blankStreamStringsBecomeNull() {
        val ch = ChannelInfo(id = "1", login = "x", displayName = "X")
        val live = StreamInfo(id = "s", userId = "1", userLogin = "x", userName = "X", gameName = "", title = "", startedAt = "", language = "")
        val snap = ChannelStatsSnapshot.from(ch, live, null)
        assertTrue(snap.isLive)
        assertNull(snap.gameName)
        assertNull(snap.streamTitle)
    }
}
