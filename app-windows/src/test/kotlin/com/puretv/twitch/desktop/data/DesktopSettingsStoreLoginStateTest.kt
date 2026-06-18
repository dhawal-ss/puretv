package com.puretv.twitch.desktop.data

import com.puretv.twitch.core.di.TokenHolder
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The followed rail needs to reload the instant the user signs in, so the shell
 * observes loggedInState and refreshes on the false->true transition. This pins the
 * flow's behavior at the three points login state changes (init / saveTokens / clearTokens).
 */
class DesktopSettingsStoreLoginStateTest {
    private val tmp: File = Files.createTempDirectory("settings-login-test").toFile()

    @AfterTest fun cleanup() { tmp.deleteRecursively() }

    @Test fun loggedInStateTracksSignInAndSignOut() {
        val store = DesktopSettingsStore(TokenHolder(), tmp)
        assertFalse(store.loggedInState.value, "a fresh store with no token is signed out")

        store.saveTokens("access-tok", "refresh-tok", expiresAtEpochSeconds = 9_999_999_999L, userId = "u1", login = "me")
        assertTrue(store.loggedInState.value, "saveTokens must flip loggedInState to true")

        store.clearTokens()
        assertFalse(store.loggedInState.value, "clearTokens must flip loggedInState back to false")
    }
}
