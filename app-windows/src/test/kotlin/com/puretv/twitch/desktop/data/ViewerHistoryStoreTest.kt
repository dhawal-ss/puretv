package com.puretv.twitch.desktop.data

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class ViewerHistoryStoreTest {
    private val tmp: File = Files.createTempDirectory("viewerhistory-test").toFile()

    @AfterTest fun cleanup() { tmp.deleteRecursively() }

    @Test fun recordIsCaseInsensitive() {
        val store = ViewerHistoryStore(tmp)
        store.record("Shroud", ViewerSample(1000, 100))
        store.record("Shroud", ViewerSample(1060, 80))
        val h = store.get("shroud")
        assertNotNull(h)
        assertEquals(2, h.samples.size)
    }

    @Test fun persistsAcrossInstances() {
        ViewerHistoryStore(tmp).apply {
            record("Shroud", ViewerSample(1000, 100))
            record("Shroud", ViewerSample(1060, 80))
        }
        val reloaded = ViewerHistoryStore(tmp).get("shroud")
        assertNotNull(reloaded)
        assertEquals(2, reloaded.samples.size)
        assertEquals(100, reloaded.peakViewers)
    }
}
