package com.puretv.twitch.desktop.auth

import java.awt.Desktop
import java.net.URI

/**
 * Opens URLs in the user's system browser. Device Code Grant (see DeviceAuth)
 * replaced the old authorization_code + localhost:3000 redirect server, so this
 * no longer listens for anything — it only launches the browser.
 */
class DesktopOAuthManager {

    /** Opens [url] in the system browser; returns false if no browser could be launched. */
    fun openInBrowser(url: String): Boolean =
        if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            runCatching { Desktop.getDesktop().browse(URI(url)); true }.getOrDefault(false)
        } else {
            println("Open this URL to sign in to Twitch: $url")
            false
        }
}
