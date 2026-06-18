package com.puretv.twitch.desktop.ui.emotes

import io.ktor.client.HttpClient
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Verifies the native-memory safety contract of [EmoteFrameCache] (audit P0-5): an evicted
 * entry's off-heap skia frames are freed via [AnimatedEmoteFrames.close], but ONLY once no
 * composable is still rendering them — otherwise closing mid-draw would be a use-after-free.
 */
class EmoteFrameCacheTest {
    private val http = HttpClient() // never used: these tests seed entries via putForTest()
    @AfterTest fun cleanup() { http.close() }

    private fun frames() = AnimatedEmoteFrames(emptyList(), emptyList(), emptyList())

    @Test fun evictsAndClosesUnreferencedEntry() {
        val cache = EmoteFrameCache(http, maxEntries = 2)
        val a = frames(); val b = frames(); val c = frames()
        cache.putForTest("a", a)
        cache.putForTest("b", b)
        cache.putForTest("c", c) // size 3 > 2 -> eldest "a" evicted
        assertTrue(a.isClosed, "an evicted entry with no live renderers must be freed")
        assertFalse(b.isClosed)
        assertFalse(c.isClosed)
    }

    @Test fun defersCloseOfReferencedEntryUntilReleased() {
        val cache = EmoteFrameCache(http, maxEntries = 2)
        val a = frames(); val b = frames(); val c = frames()
        cache.retain("a") // a composable is rendering "a"
        cache.putForTest("a", a)
        cache.putForTest("b", b)
        cache.putForTest("c", c) // evicts "a", but it is still on screen
        assertFalse(a.isClosed, "must NOT free native frames while still rendered (use-after-free)")
        cache.release("a")
        assertTrue(a.isClosed, "freed once the last renderer releases")
    }

    @Test fun closesOnlyAfterLastReferenceReleased() {
        val cache = EmoteFrameCache(http, maxEntries = 1)
        val a = frames(); val b = frames()
        cache.retain("a"); cache.retain("a") // two renderers of the same emote
        cache.putForTest("a", a)
        cache.putForTest("b", b) // evicts "a"; referenced x2 -> deferred
        cache.release("a")
        assertFalse(a.isClosed, "still one renderer left")
        cache.release("a")
        assertTrue(a.isClosed, "closed after the final release")
    }
}
