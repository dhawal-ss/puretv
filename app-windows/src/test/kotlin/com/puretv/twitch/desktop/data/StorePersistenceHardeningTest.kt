package com.puretv.twitch.desktop.data

import java.io.File
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Audit P0-2 / F2 / F3 regression tests: atomic writes, concurrent-write
 * serialization (no lost updates), bounded growth, and no leftover temp files.
 */
class StorePersistenceHardeningTest {
    private val tmp: File = Files.createTempDirectory("store-hardening-test").toFile()

    @AfterTest fun cleanup() { tmp.deleteRecursively() }

    @Test fun concurrentFollowsDoNotLoseUpdates() {
        val store = FollowStore(tmp)
        val n = 200
        val pool = Executors.newFixedThreadPool(16)
        val start = CountDownLatch(1)
        repeat(n) { i ->
            pool.submit {
                start.await()
                store.follow(FollowedChannel(id = "id$i", login = "chan$i", displayName = "Chan$i"))
            }
        }
        start.countDown()
        pool.shutdown()
        assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS), "follow workers did not finish")
        // Without serialization, racing read-derive-write would drop updates.
        assertEquals(n, store.followed.value.size, "every concurrent follow must be retained")
        // And it must survive a reload from disk (atomic write produced a valid file).
        assertEquals(n, FollowStore(tmp).followed.value.size)
    }

    @Test fun watchProgressIsBoundedAndKeepsNewest() {
        val store = WatchProgressStore(tmp)
        val total = 360 // > MAX_ENTRIES (300)
        repeat(total) { i ->
            store.save(WatchProgress(vodId = "v$i", positionMs = 60_000, durationMs = 1_200_000, updatedAt = i.toLong()))
        }
        val kept = store.progress.value
        assertEquals(300, kept.size, "store must be capped to MAX_ENTRIES")
        // The newest (highest updatedAt) entries are the ones retained.
        assertTrue(kept.containsKey("v359"), "newest entry must be kept")
        assertTrue(!kept.containsKey("v0"), "oldest entry must be evicted")
    }

    @Test fun atomicWriteLeavesNoTempFilesAndValidContent() {
        val store = FollowStore(tmp)
        repeat(20) { store.follow(FollowedChannel(id = "id$it", login = "c$it", displayName = "C$it")) }
        val leftovers = tmp.listFiles()?.filter { it.name.endsWith(".tmp") }.orEmpty()
        assertTrue(leftovers.isEmpty(), "atomic write must not leave temp files: $leftovers")
        assertTrue(File(tmp, "following.json").exists())
        assertEquals(20, FollowStore(tmp).followed.value.size)
    }
}
