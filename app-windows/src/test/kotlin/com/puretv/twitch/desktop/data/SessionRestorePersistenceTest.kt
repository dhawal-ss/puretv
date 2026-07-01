package com.puretv.twitch.desktop.data

import com.puretv.twitch.core.di.TokenHolder
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Regression: a saved session must survive a "restart" (a fresh
 * [DesktopSettingsStore] over the same data dir).
 *
 * The bug: `machineKey` was a `by lazy` property declared AFTER `init`, so its
 * delegate was still null when `init` -> loadTokens -> decrypt first touched it.
 * decrypt threw NullPointerException, loadTokens swallowed it to null, and the
 * restored session was dropped — the desktop app asked the user to sign in again
 * on every launch even though tokens.enc was on disk and perfectly decryptable.
 */
class SessionRestorePersistenceTest {

    @Test
    fun savedSessionRestoresInAFreshStore() {
        val dir = Files.createTempDirectory("puretv-session-test").toFile()
        try {
            // First run: sign in.
            DesktopSettingsStore(TokenHolder(), dir).saveTokens(
                accessToken = "access-123",
                refreshToken = "refresh-456",
                expiresAtEpochSeconds = 9_999_999_999L,
                userId = "u-789",
                login = "streamer",
            )

            // Second run (simulated restart): a brand-new store over the same dir
            // must restore the session in its init, not report logged-out.
            val restored = DesktopSettingsStore(TokenHolder(), dir)

            assertTrue(restored.isLoggedIn, "session did not restore on restart (init-time decrypt failed)")
            assertEquals("streamer", restored.sessionLogin)
            assertEquals("u-789", restored.sessionUserId)
            assertEquals("access-123", restored.loadTokens()?.accessToken)
        } finally {
            dir.deleteRecursively()
        }
    }
}
