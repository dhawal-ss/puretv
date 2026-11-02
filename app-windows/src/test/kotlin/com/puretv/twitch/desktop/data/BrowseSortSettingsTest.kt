package com.puretv.twitch.desktop.data

import com.puretv.twitch.core.di.TokenHolder
import com.puretv.twitch.core.model.CategorySort
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The Browse order is remembered (1.17.0), and older settings files load most viewers first. */
class BrowseSortSettingsTest {
    private val tmp: File = Files.createTempDirectory("settings-browse-sort-test").toFile()

    @AfterTest fun cleanup() { tmp.deleteRecursively() }

    @Test fun theSortSurvivesARestartAsItsKey() {
        val store = DesktopSettingsStore(TokenHolder(), tmp)
        store.updateSettings { it.copy(browseSort = CategorySort.FEWEST) }
        store.flush()

        assertTrue(Regex(""""browseSort"\s*:\s*"fewest"""").containsMatchIn(File(tmp, "settings.json").readText()))
        assertEquals(CategorySort.FEWEST, DesktopSettingsStore(TokenHolder(), tmp).settings.value.browseSort)
    }

    @Test fun olderAndUnknownValuesLoadMostViewersFirst() {
        File(tmp, "settings.json").writeText("""{"preferredQuality":"source","lowLatencyMode":false}""")
        val older = DesktopSettingsStore(TokenHolder(), tmp).settings.value
        assertEquals(CategorySort.VIEWERS, older.browseSort)
        assertFalse(older.lowLatencyMode, "the rest of the file still loads")

        File(tmp, "settings.json").writeText("""{"browseSort":"by-vibes"}""")
        assertEquals(CategorySort.VIEWERS, DesktopSettingsStore(TokenHolder(), tmp).settings.value.browseSort)
    }
}
