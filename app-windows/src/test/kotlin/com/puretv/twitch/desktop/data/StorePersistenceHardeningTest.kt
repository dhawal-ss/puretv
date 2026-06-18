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
        // flush() awaits the conflated async writer so the latest snapshot is durable.
        store.flush()
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

    @Test fun corruptFileIsQuarantinedNotSilentlyDestroyed() {
        val followingJson = File(tmp, "following.json")
        val garbage = "{ this is not valid json at all "
        followingJson.writeText(garbage)

        // A corrupt file still loads as empty (existing lenient behavior) ...
        val store = FollowStore(tmp)
        assertEquals(0, store.followed.value.size, "corrupt file loads as empty")

        // ... but the unparseable bytes must be moved to a `.corrupt-*` sidecar so a
        // subsequent write can't silently overwrite and permanently destroy them.
        val quarantined = tmp.listFiles()?.filter { it.name.startsWith("following.json.corrupt-") }.orEmpty()
        assertEquals(1, quarantined.size, "corrupt file must be quarantined for recovery, not left to be overwritten")
        assertEquals(garbage, quarantined.first().readText(), "quarantine must preserve the original bytes verbatim")

        // A subsequent legitimate write persists cleanly, and the quarantine survives it.
        store.follow(FollowedChannel(id = "id1", login = "c1", displayName = "C1"))
        store.flush()
        assertEquals(1, FollowStore(tmp).followed.value.size, "the store recovers and persists new data")
        assertTrue(quarantined.first().exists(), "quarantine file must remain for the user to recover")
    }

    @Test fun flushIsBoundedAndDoesNotHangOnAWedgedWrite() {
        val writer = AtomicJsonWriter(File(tmp, "wedge.json"))
        val gate = CountDownLatch(1)
        // This producer jams the single writer thread, so the flush ordering barrier can
        // never run. flush() (called from the JVM shutdown hook) must time out and return,
        // not block exit indefinitely on a wedged disk / AV-locked file.
        writer.enqueue { gate.await(); "{}" }
        val flusher = kotlin.concurrent.thread { writer.flush() }
        flusher.join(6_000)
        val stillHung = flusher.isAlive
        gate.countDown() // release the jammed writer so nothing leaks past the test
        assertTrue(!stillHung, "flush() must be bounded, not block forever on a wedged write")
    }

    @Test fun atomicWriteLeavesNoTempFilesAndValidContent() {
        val store = FollowStore(tmp)
        repeat(20) { store.follow(FollowedChannel(id = "id$it", login = "c$it", displayName = "C$it")) }
        store.flush()
        val leftovers = tmp.listFiles()?.filter { it.name.endsWith(".tmp") }.orEmpty()
        assertTrue(leftovers.isEmpty(), "atomic write must not leave temp files: $leftovers")
        assertTrue(File(tmp, "following.json").exists())
        assertEquals(20, FollowStore(tmp).followed.value.size)
    }
}
