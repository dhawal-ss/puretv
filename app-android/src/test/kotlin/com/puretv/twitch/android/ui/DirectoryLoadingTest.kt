package com.puretv.twitch.android.ui

import com.puretv.twitch.core.api.ChannelSearchResult
import com.puretv.twitch.core.model.GameInfo
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Browse paging and Search's channels-plus-categories lookup (issue #41). */
class DirectoryLoadingTest {
    private fun game(id: String) = GameInfo(id = id, name = "Game $id")
    private fun channel(login: String) =
        ChannelSearchResult(id = login, broadcaster_login = login, display_name = login, is_live = false)

    @Test
    fun aNextPageIsAppendedAfterTheGamesAlreadyShown() {
        val shown = listOf(game("1"), game("2"))
        assertEquals(listOf("1", "2", "3", "4"), shown.appendingPage(listOf(game("3"), game("4"))).map { it.id })
    }

    @Test
    fun gamesThatMovedIntoTheNextPageAreNotShownTwice() {
        // The ranking shifts between requests, so page two can repeat a game from page one.
        val shown = listOf(game("1"), game("2"))
        val merged = shown.appendingPage(listOf(game("2"), game("3"), game("3")))
        assertEquals(listOf("1", "2", "3"), merged.map { it.id })
    }

    @Test
    fun aPageWithNothingNewKeepsTheSameList() {
        val shown = listOf(game("1"))
        assertTrue(shown.appendingPage(listOf(game("1"))) === shown)
    }

    @Test
    fun channelsAndCategoriesComeBackTogether() = runBlocking {
        val hits = searchChannelsAndCategories(
            channels = { listOf(channel("xqc")) },
            categories = { listOf(game("509658")) },
        ).getOrThrow()
        assertEquals(listOf("xqc"), hits.channels.map { it.broadcaster_login })
        assertEquals(listOf("509658"), hits.categories.map { it.id })
    }

    @Test
    fun aCategoryFailureStillShowsTheChannels() = runBlocking {
        val result = searchChannelsAndCategories(
            channels = { listOf(channel("xqc")) },
            categories = { error("categories are down") },
        )
        assertTrue(result.isSuccess)
        assertEquals(listOf("xqc"), result.getOrThrow().channels.map { it.broadcaster_login })
        assertTrue(result.getOrThrow().categories.isEmpty())
    }

    @Test
    fun aChannelFailureFailsTheSearch() = runBlocking {
        val result = searchChannelsAndCategories(
            channels = { error("channels are down") },
            categories = { listOf(game("1")) },
        )
        assertTrue(result.isFailure)
        assertEquals("channels are down", result.exceptionOrNull()?.message)
    }

    @Test
    fun aChannelFailureDoesNotWaitForTheCategories() = runBlocking {
        // A slow category lookup is cancelled rather than holding up the error.
        val result = searchChannelsAndCategories(
            channels = { error("channels are down") },
            categories = { awaitCancellation() },
        )
        assertTrue(result.isFailure)
    }

    @Test
    fun bothLookupsRunAtTheSameTime() = runBlocking {
        // The channel lookup only finishes once the category lookup has started,
        // which can only happen if they overlap.
        val categoriesStarted = CompletableDeferred<Unit>()
        val hits = searchChannelsAndCategories(
            channels = {
                categoriesStarted.await()
                listOf(channel("a"))
            },
            categories = {
                categoriesStarted.complete(Unit)
                listOf(game("1"))
            },
        ).getOrThrow()
        assertEquals(1, hits.channels.size)
        assertEquals(1, hits.categories.size)
    }

    @Test
    fun aCancelledSearchIsNotReportedAsAFailure() = runBlocking {
        // A newer keystroke cancels the old search: it must end, not paint an error.
        var finishedWith: Result<SearchHits>? = null
        val search = async(start = CoroutineStart.UNDISPATCHED) {
            finishedWith = searchChannelsAndCategories(
                channels = { awaitCancellation() },
                categories = { emptyList() },
            )
        }
        yield()
        search.cancel()
        search.join()
        assertTrue(search.isCancelled)
        assertEquals(null, finishedWith)
    }
}
