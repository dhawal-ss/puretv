package com.puretv.twitch.desktop.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WindowRegistryTest {
    private class FakeHost : WindowHost {
        val navigated = ArrayList<WindowDestination>()
        var fronted = 0
        var grids = 0
        override fun navigate(destination: WindowDestination) { navigated += destination }
        override fun bringToFront() { fronted++ }
        override fun openMultiGrid() { grids++ }
    }

    private fun opened(r: OpenResult): Int = (r as OpenResult.Opened).windowId

    @Test fun startsWithOnlyTheMainWindow() {
        val r = WindowRegistry()
        val all = r.windows.value
        assertEquals(1, all.size)
        assertEquals(WindowKind.PRIMARY, all.single().kind)
        assertEquals(0, all.single().slot)
        assertEquals(0, r.mostRecentlyFocused())
    }

    @Test fun openAddsASecondaryWindowWithItsDestination() {
        val r = WindowRegistry()
        val id = opened(r.open(WindowDestination.Channel("forsen")))
        val entry = r.windows.value.single { it.id == id }
        assertEquals(WindowKind.SECONDARY, entry.kind)
        assertEquals(WindowDestination.Channel("forsen"), entry.start)
        assertEquals(1, entry.slot)
    }

    @Test fun windowCapRefusesWithAFriendlyMessage() {
        val r = WindowRegistry(maxWindows = 3)
        opened(r.open(WindowDestination.Home))
        opened(r.open(WindowDestination.Home))
        val refused = r.open(WindowDestination.Home)
        assertIs<OpenResult.Refused>(refused)
        assertEquals(3, r.windows.value.size)
        assertEquals(refused.message, r.notice.value)
        assertFalse(refused.message.any { it.code == 0x2013 || it.code == 0x2014 })
    }

    @Test fun slotsAreReusedButIdsAreNot() {
        val r = WindowRegistry()
        val a = opened(r.open(WindowDestination.Home))
        val b = opened(r.open(WindowDestination.Home))
        assertTrue(r.close(a))
        val c = opened(r.open(WindowDestination.Home))
        assertTrue(c != a && c != b)
        assertEquals(1, r.windows.value.single { it.id == c }.slot)
        assertEquals(2, r.windows.value.single { it.id == b }.slot)
    }

    @Test fun theMainWindowCannotBeRemoved() {
        val r = WindowRegistry()
        assertFalse(r.close(WindowRegistry.PRIMARY_ID))
        assertFalse(r.close(99))
        assertEquals(1, r.windows.value.size)
    }

    @Test fun closeRuleOfThePrimaryAndSecondaryWindows() {
        val r = WindowRegistry()
        val s = opened(r.open(WindowDestination.Home))
        // A secondary window only ever closes itself.
        assertEquals(CloseAction.CLOSE_WINDOW, r.closeAction(s))
        // The main window always quits, visible or hidden.
        assertEquals(CloseAction.QUIT, r.closeAction(0))
        assertEquals(CloseAction.CLOSE_WINDOW, r.closeAction(99))
    }

    @Test fun minimizeRuleOfThePrimaryAndSecondaryWindows() {
        val r = WindowRegistry()
        val s = opened(r.open(WindowDestination.Home))
        assertEquals(MinimizeAction.HIDE_ALL, r.minimizeAction(0, visible = true, trayAvailable = true, trayChosen = true))
        assertEquals(MinimizeAction.MINIMIZE_ONLY, r.minimizeAction(0, visible = true, trayAvailable = false, trayChosen = true))
        // An update is downloading: stay in the taskbar.
        assertEquals(MinimizeAction.MINIMIZE_ONLY, r.minimizeAction(0, visible = true, trayAvailable = true, updating = true, trayChosen = true))
        // Already hidden in the tray: nothing more to hide.
        assertEquals(MinimizeAction.MINIMIZE_ONLY, r.minimizeAction(0, visible = false, trayAvailable = true, trayChosen = true))
        // Other windows minimize on their own.
        assertEquals(MinimizeAction.MINIMIZE_ONLY, r.minimizeAction(s, visible = true, trayAvailable = true, trayChosen = true))
        assertEquals(MinimizeAction.MINIMIZE_ONLY, r.minimizeAction(99, visible = true, trayAvailable = true, trayChosen = true))
    }

    @Test fun minimizeStaysInTheTaskbarUnlessTheTrayWasChosen() {
        // Issue #40: minimizing hid PureTV in the tray and dropped the stream. It is opt-in now.
        val r = WindowRegistry()
        assertEquals(MinimizeAction.MINIMIZE_ONLY, r.minimizeAction(0, visible = true, trayAvailable = true))
        assertEquals(MinimizeAction.MINIMIZE_ONLY, r.minimizeAction(0, visible = true, trayAvailable = true, trayChosen = false))
    }

    @Test fun focusTrackingFallsBackWhenTheLastUsedWindowCloses() {
        val r = WindowRegistry()
        val a = opened(r.open(WindowDestination.Home))
        val b = opened(r.open(WindowDestination.Home))
        r.focused(a)
        r.focused(b)
        assertEquals(b, r.mostRecentlyFocused())
        r.focused(0)
        r.focused(b)
        r.close(b)
        // b is gone: the one used before it wins.
        assertEquals(0, r.mostRecentlyFocused())
        r.close(a)
        assertEquals(0, r.mostRecentlyFocused())
    }

    @Test fun focusOfAnUnknownWindowIsIgnored() {
        val r = WindowRegistry()
        r.focused(42)
        assertEquals(0, r.mostRecentlyFocused())
    }

    @Test fun playerCapCountsWindowsAndTiles() {
        var tiles = 3
        val r = WindowRegistry(maxPlayers = 4, tiles = { tiles })
        val a = opened(r.open(WindowDestination.Stream("a")))
        r.setActivity(a, playing = true, watching = true)
        assertEquals(4, r.playersInUse())
        assertFalse(r.canStartPlayer())
        // A stream or video may not open another window; a channel page may.
        assertIs<OpenResult.Refused>(r.open(WindowDestination.Stream("b")))
        assertIs<OpenResult.Refused>(r.open(WindowDestination.Vod("1")))
        assertIs<OpenResult.Opened>(r.open(WindowDestination.Channel("b")))
        tiles = 0
        assertTrue(r.canStartPlayer())
        assertIs<OpenResult.Opened>(r.open(WindowDestination.Stream("b")))
    }

    @Test fun anyWatchingFollowsEveryWindow() {
        val r = WindowRegistry()
        val a = opened(r.open(WindowDestination.Home))
        assertFalse(r.anyWatching())
        r.setActivity(a, playing = true, watching = true)
        assertTrue(r.anyWatching())
        r.setActivity(0, playing = false, watching = false)
        assertTrue(r.anyWatching()) // the main window going idle does not stop the other one
        r.close(a)
        assertFalse(r.anyWatching())
    }

    @Test fun navigateGoesToTheMostRecentlyFocusedWindow() {
        val r = WindowRegistry()
        val a = opened(r.open(WindowDestination.Home))
        val main = FakeHost()
        val second = FakeHost()
        r.attach(0, main)
        r.attach(a, second)
        assertTrue(r.navigate(WindowDestination.Stream("x")))
        assertEquals(listOf<WindowDestination>(WindowDestination.Stream("x")), main.navigated)
        r.focused(a)
        r.navigate(WindowDestination.Stream("y"))
        assertEquals(listOf<WindowDestination>(WindowDestination.Stream("y")), second.navigated)
    }

    @Test fun navigateFallsBackToTheMainWindowWhenTheChosenOneIsNotReady() {
        val r = WindowRegistry()
        val a = opened(r.open(WindowDestination.Home))
        val main = FakeHost()
        r.attach(0, main)
        r.focused(a) // used last, but it has not attached a host yet
        assertTrue(r.navigate(WindowDestination.Stream("x")))
        assertEquals(listOf<WindowDestination>(WindowDestination.Stream("x")), main.navigated)
    }

    @Test fun admitPlayerPostsTheReasonWhenFull() {
        val r = WindowRegistry(maxPlayers = 1, tiles = { 1 })
        assertFalse(r.admitPlayer())
        assertTrue(r.notice.value!!.contains("lot of video"))
        assertTrue(WindowRegistry(maxPlayers = 2, tiles = { 1 }).admitPlayer())
    }

    @Test fun navigateWithoutAHostSaysSo() {
        val r = WindowRegistry()
        assertFalse(r.navigate(WindowDestination.Home))
    }

    @Test fun bringAllToFrontReachesEveryHost() {
        val r = WindowRegistry()
        val a = opened(r.open(WindowDestination.Home))
        val h0 = FakeHost()
        val h1 = FakeHost()
        r.attach(0, h0)
        r.attach(a, h1)
        r.bringAllToFront()
        assertEquals(1, h0.fronted)
        assertEquals(1, h1.fronted)
        r.close(a)
        r.bringAllToFront()
        assertEquals(1, h1.fronted)
    }

    @Test fun onlyOneWindowHoldsMultiStream() {
        val r = WindowRegistry()
        val a = opened(r.open(WindowDestination.Home))
        assertTrue(r.claimMultiGrid(0))
        assertTrue(r.claimMultiGrid(0))
        assertFalse(r.claimMultiGrid(a))
        r.releaseMultiGrid(0)
        assertTrue(r.claimMultiGrid(a))
        // A holder that closed does not keep it.
        r.close(a)
        assertTrue(r.claimMultiGrid(0))
    }

    @Test fun aPoppedTileAsksTheGridHolder() {
        val r = WindowRegistry()
        val a = opened(r.open(WindowDestination.Home))
        val h0 = FakeHost()
        val h1 = FakeHost()
        r.attach(0, h0)
        r.attach(a, h1)
        r.openMultiGrid()
        assertEquals(1, h0.grids)
        r.claimMultiGrid(a)
        r.openMultiGrid()
        assertEquals(1, h1.grids)
        assertTrue(r.showMultiGridHolder(except = 0))
        assertEquals(1, h1.fronted)
        assertFalse(r.showMultiGridHolder(except = a))
    }

    @Test fun titleAndNoticeUpdates() {
        val r = WindowRegistry()
        r.setTitle(0, "forsen - PureTV for Twitch")
        assertEquals("forsen - PureTV for Twitch", r.windows.value.single().title)
        r.postNotice("hello")
        r.clearNotice("other")
        assertEquals("hello", r.notice.value)
        r.clearNotice("hello")
        assertNull(r.notice.value)
    }
}

class WindowDestinationTest {
    @Test fun homeAndSearchMapToTabs() {
        assertEquals(StartScreen(Destination.HOME, Route.Top), WindowDestination.Home.toStartScreen())
        assertEquals(
            StartScreen(Destination.SEARCH, Route.Top, "speedrun"),
            WindowDestination.Search("  speedrun ").toStartScreen(),
        )
        assertNull(WindowDestination.Search("").toStartScreen().searchQuery)
    }

    @Test fun channelAndStreamLoginsAreCleaned() {
        assertEquals(Route.Channel("forsen"), WindowDestination.Channel(" @Forsen ").toStartScreen().route)
        assertEquals(Route.Stream("forsen"), WindowDestination.Stream("FORSEN").toStartScreen().route)
    }

    @Test fun blankNamesFallBackToHome() {
        assertEquals(StartScreen(Destination.HOME, Route.Top), WindowDestination.Channel("  ").toStartScreen())
        assertEquals(StartScreen(Destination.HOME, Route.Top), WindowDestination.Stream("@").toStartScreen())
        assertEquals(StartScreen(Destination.HOME, Route.Top), WindowDestination.Vod("").toStartScreen())
        assertEquals(StartScreen(Destination.BROWSE, Route.Top), WindowDestination.Category("").toStartScreen())
    }

    @Test fun vodCarriesWhatTheResumeEntryNeeds() {
        val screen = WindowDestination.Vod("123", "Forsen", "Big run", "http://t").toStartScreen()
        assertEquals(Route.Vod(VodLaunch("123", "forsen", "Big run", "http://t")), screen.route)
    }

    @Test fun categoryKeepsItsNameWithAFallback() {
        assertEquals(Route.Category("509658", "Just Chatting"), WindowDestination.Category("509658", "Just Chatting").toStartScreen().route)
        assertEquals(Route.Category("1", "Category"), WindowDestination.Category("1").toStartScreen().route)
        assertEquals(Destination.BROWSE, WindowDestination.Category("1").toStartScreen().tab)
    }

    @Test fun onlyStreamsAndVideosStartAPlayer() {
        assertTrue(WindowDestination.Stream("a").startsPlayer())
        assertTrue(WindowDestination.Vod("1").startsPlayer())
        assertFalse(WindowDestination.Channel("a").startsPlayer())
        assertFalse(WindowDestination.Home.startsPlayer())
        assertFalse(WindowDestination.Search("x").startsPlayer())
        assertFalse(WindowDestination.Category("1").startsPlayer())
    }

    @Test fun titlesNameTheScreen() {
        assertEquals("PureTV for Twitch", windowTitle(0, screenName(Route.Top, Destination.HOME)))
        assertEquals("Search - PureTV for Twitch", windowTitle(0, screenName(Route.Top, Destination.SEARCH)))
        assertEquals("forsen - PureTV 2", windowTitle(2, screenName(Route.Channel("forsen"), Destination.HOME)))
        assertEquals("forsen (live) - PureTV 1", windowTitle(1, screenName(Route.Stream("forsen"), Destination.HOME)))
        assertEquals("PureTV 3", windowTitle(3, null))
        assertEquals("Video - PureTV 1", windowTitle(1, screenName(Route.Vod(VodLaunch("1", "a", "", "")), Destination.HOME)))
    }
}

class NewWindowLayoutTest {
    // The owner's desk: primary 2560x1440, one monitor to the left (negative x), one above (negative y).
    private val primary = MiniRect(0, 0, 2560, 1400)
    private val left = MiniRect(-1920, 0, 1920, 1040)
    private val above = MiniRect(0, -1080, 1920, 1040)
    private val desk = listOf(primary, left, above)

    private fun assertWithin(inner: MiniRect, outer: MiniRect) {
        assertTrue(inner.x >= outer.x && inner.y >= outer.y, "$inner starts outside $outer")
        assertTrue(inner.x + inner.width <= outer.x + outer.width && inner.y + inner.height <= outer.y + outer.height, "$inner ends outside $outer")
    }

    @Test fun noMonitorsMeansNoPlacement() {
        assertNull(NewWindowLayout.place(emptyList(), null, emptyList()))
    }

    @Test fun opensOnAnotherMonitorThanTheOneInUse() {
        val placed = NewWindowLayout.place(desk, avoid = 0, occupied = listOf(MiniRect(100, 100, 1280, 720)))!!
        assertTrue(placed.centerX < 0 || placed.centerY < 0, "$placed should not be on the primary monitor")
        assertWithin(placed, if (placed.centerX < 0) left else above)
        assertEquals(NewWindowLayout.DEFAULT_WIDTH, placed.width)
    }

    @Test fun nextWindowPrefersAMonitorWithoutPureTV() {
        val first = NewWindowLayout.place(desk, 0, listOf(MiniRect(100, 100, 1280, 720)))!!
        val second = NewWindowLayout.place(desk, 0, listOf(MiniRect(100, 100, 1280, 720), first))!!
        assertTrue(PopoutLayout.monitorOf(first, desk) != PopoutLayout.monitorOf(second, desk))
        assertTrue(PopoutLayout.monitorOf(second, desk) != 0)
    }

    @Test fun oneMonitorCascades() {
        val only = listOf(primary)
        val a = NewWindowLayout.place(only, 0, listOf(MiniRect(0, 0, 1280, 720)))!!
        val b = NewWindowLayout.place(only, 0, listOf(MiniRect(0, 0, 1280, 720), a))!!
        assertTrue(b.x > a.x && b.y > a.y, "$b should sit below and right of $a")
        assertWithin(a, primary)
        assertWithin(b, primary)
    }

    @Test fun smallMonitorShrinksTheWindowToFit() {
        val tiny = MiniRect(-800, 0, 800, 600)
        val placed = NewWindowLayout.place(listOf(tiny), null, emptyList())!!
        assertWithin(placed, tiny)
        assertTrue(placed.width <= 800 && placed.height <= 600)
    }

    @Test fun rememberedSpotIsKeptWhereverItWasAndRescuedWhenItsMonitorIsGone() {
        val fallback = MiniRect(0, 0, 1280, 720)
        val onLeft = MiniRect(-1500, 100, 1200, 700)
        assertEquals(onLeft, NewWindowLayout.resolve(onLeft, fallback, desk))
        // Monitor on the left unplugged: pulled onto the nearest remaining one.
        val rescued = NewWindowLayout.resolve(onLeft, fallback, listOf(primary))
        assertWithin(rescued, primary)
        assertEquals(fallback, NewWindowLayout.resolve(null, fallback, desk))
    }

    @Test fun savedSizeNeverFallsBelowTheMinimum() {
        val resolved = NewWindowLayout.resolve(MiniRect(10, 10, 100, 100), MiniRect(0, 0, 1280, 720), desk)
        assertTrue(resolved.width >= NewWindowLayout.MIN_WIDTH && resolved.height >= NewWindowLayout.MIN_HEIGHT)
    }
}

class WindowKeyGuardTest {
    private val main = "main"
    private val second = "second"
    private val popped = "popped"
    private val all = listOf(main, second, popped)
    private val owners = mapOf("dialog" to main, "glass" to second)
    private fun handles(active: String?, mine: String) = WindowKeyGuard.handles(active, mine, all) { owners[it] }

    @Test fun onlyTheActiveWindowHandlesKeys() {
        assertTrue(handles(main, main))
        assertFalse(handles(second, main))
        assertFalse(handles(main, second))
        assertFalse(handles(popped, main))
    }

    @Test fun aDialogOrOverlayBelongsToItsOwner() {
        assertTrue(handles("dialog", main))
        assertFalse(handles("dialog", second))
        assertTrue(handles("glass", second))
        assertFalse(handles("glass", main))
    }

    @Test fun nothingActiveOrAForeignWindowIsNotRefused() {
        assertTrue(handles(null, main))
        assertTrue(handles("someone else", main))
    }
}
