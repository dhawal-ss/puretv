package com.puretv.twitch.desktop.ui

import com.puretv.twitch.core.follows.FollowRow
import com.puretv.twitch.core.follows.FollowedChannelsSource
import com.puretv.twitch.core.follows.FollowedList
import com.puretv.twitch.core.follows.FollowedRef
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private class FakeSource(var result: FollowedList, var fail: Boolean = false) : FollowedChannelsSource {
    var calls = 0
    override suspend fun load(userId: String, localPins: List<FollowedRef>): FollowedList {
        calls++
        if (fail) throw RuntimeException("boom")
        return result
    }
}

private fun row(login: String, live: Boolean) = FollowRow(login, login, null, live, if (live) 5 else 0, "")

class FollowedRailViewModelTest {
    private val sample = FollowedList(live = listOf(row("alice", true)), offline = listOf(row("bob", false)))

    @Test fun loadPopulatesLiveAndOffline() = runTest {
        val src = FakeSource(sample)
        val vm = FollowedRailViewModel(src, loggedInUserId = { "u1" }, localPins = { emptyList() })
        vm.loadOnce()
        assertEquals(listOf("alice"), vm.state.value.live.map { it.login })
        assertEquals(listOf("bob"), vm.state.value.offline.map { it.login })
        assertTrue(vm.state.value.isLoggedIn)
        assertFalse(vm.state.value.errored)
    }

    @Test fun loggedOutClearsAndDoesNotCallSource() = runTest {
        val src = FakeSource(sample)
        val vm = FollowedRailViewModel(src, loggedInUserId = { null }, localPins = { emptyList() })
        vm.loadOnce()
        assertFalse(vm.state.value.isLoggedIn)
        assertTrue(vm.state.value.live.isEmpty())
        assertEquals(0, src.calls)
    }

    @Test fun errorKeepsLastKnownListAndFlagsErrored() = runTest {
        val src = FakeSource(sample)
        val vm = FollowedRailViewModel(src, loggedInUserId = { "u1" }, localPins = { emptyList() })
        vm.loadOnce()                 // succeeds, populates
        src.fail = true
        vm.loadOnce()                 // fails
        assertEquals(listOf("alice"), vm.state.value.live.map { it.login }, "keeps last-known live list")
        assertTrue(vm.state.value.errored)
    }

    @Test fun toggleOfflineFlips() = runTest {
        val vm = FollowedRailViewModel(FakeSource(sample), loggedInUserId = { "u1" }, localPins = { emptyList() })
        assertFalse(vm.state.value.offlineExpanded)
        vm.toggleOffline()
        assertTrue(vm.state.value.offlineExpanded)
    }
}
