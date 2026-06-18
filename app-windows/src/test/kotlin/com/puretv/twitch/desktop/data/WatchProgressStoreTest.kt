package com.puretv.twitch.desktop.data

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WatchProgressStoreTest {
    private val tmp: File = Files.createTempDirectory("watchprogress-test").toFile()

    @AfterTest fun cleanup() { tmp.deleteRecursively() }

    private fun progress(id: String, pos: Long, dur: Long, updated: Long) =
        WatchProgress(vodId = id, positionMs = pos, durationMs = dur, updatedAt = updated, title = "T$id")

    @Test fun saveAndGet() {
        val store = WatchProgressStore(tmp)
        store.save(progress("a", 60_000, 1_200_000, 100))
        assertEquals(60_000, store.get("a")?.positionMs)
        assertNull(store.get("missing"))
    }

    @Test fun saveOverwritesSameVod() {
        val store = WatchProgressStore(tmp)
        store.save(progress("a", 60_000, 1_200_000, 100))
        store.save(progress("a", 90_000, 1_200_000, 200))
        assertEquals(90_000, store.get("a")?.positionMs)
        assertEquals(1, store.progress.value.size)
    }

    @Test fun removeDeletes() {
        val store = WatchProgressStore(tmp)
        store.save(progress("a", 60_000, 1_200_000, 100))
        store.remove("a")
        assertNull(store.get("a"))
    }

    @Test fun continueWatchingExcludesFinishedAndSortsNewestFirst() {
        val store = WatchProgressStore(tmp)
        store.save(progress("old", 60_000, 1_200_000, 100))
        store.save(progress("new", 60_000, 1_200_000, 300))
        store.save(progress("done", 1_190_000, 1_200_000, 400)) // finished -> excluded
        val list = store.continueWatching()
        assertEquals(listOf("new", "old"), list.map { it.vodId })
    }

    @Test fun persistsAcrossInstances() {
        val first = WatchProgressStore(tmp)
        first.save(progress("a", 60_000, 1_200_000, 100))
        first.flush()
        assertTrue(WatchProgressStore(tmp).get("a") != null)
    }
}
