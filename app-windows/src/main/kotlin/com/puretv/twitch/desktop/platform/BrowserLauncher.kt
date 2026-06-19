package com.puretv.twitch.desktop.platform

import java.awt.Desktop
import java.net.URI

/**
 * Opens [url] in the user's default system browser. Returns false if no browser
 * could be launched (headless / unsupported environment). Never throws -- a failed
 * launch must not crash the caller.
 */
fun openInBrowser(url: String): Boolean =
    if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
        runCatching { Desktop.getDesktop().browse(URI(url)); true }.getOrDefault(false)
    } else {
        false
    }
