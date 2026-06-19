package com.puretv.twitch.desktop.auth

/**
 * Opens URLs in the user's system browser. Device Code Grant (see DeviceAuth)
 * replaced the old authorization_code + localhost:3000 redirect server, so this
 * no longer listens for anything -- it only launches the browser.
 */
class DesktopOAuthManager {

    /** Opens [url] in the system browser; returns false if no browser could be launched. */
    fun openInBrowser(url: String): Boolean =
        com.puretv.twitch.desktop.platform.openInBrowser(url).also { launched ->
            if (!launched) println("Open this URL to sign in to Twitch: $url")
        }
}
