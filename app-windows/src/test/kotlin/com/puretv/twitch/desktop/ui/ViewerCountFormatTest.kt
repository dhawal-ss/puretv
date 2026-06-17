package com.puretv.twitch.desktop.ui

import com.puretv.twitch.desktop.ui.components.formatViewerCount
import kotlin.test.Test
import kotlin.test.assertEquals

/** Audit U7: formatViewerCount must handle millions, not render "1200.0K". */
class ViewerCountFormatTest {
    @Test fun formatsSmallCountsVerbatim() {
        assertEquals("0", formatViewerCount(0))
        assertEquals("999", formatViewerCount(999))
    }

    @Test fun formatsThousands() {
        assertEquals("1.2K", formatViewerCount(1_234))
        assertEquals("12.3K", formatViewerCount(12_345))
    }

    @Test fun formatsMillions() {
        assertEquals("1.2M", formatViewerCount(1_200_000))
        assertEquals("3.0M", formatViewerCount(3_000_000))
    }
}
