package com.puretv.twitch.core.session

import com.puretv.twitch.core.model.AppSettings
import kotlin.test.Test
import kotlin.test.assertEquals

class SessionStateTest {
    @Test
    fun blankTokenIsLoggedOut() {
        assertEquals(SessionState.LoggedOut, deriveSessionState(AppSettings(accessToken = "")))
    }

    @Test
    fun whitespaceTokenIsLoggedOut() {
        assertEquals(SessionState.LoggedOut, deriveSessionState(AppSettings(accessToken = "   ")))
    }

    @Test
    fun presentTokenIsLoggedInWithIdentity() {
        val state = deriveSessionState(
            AppSettings(accessToken = "abc123", userId = "42", username = "ninja"),
        )
        assertEquals(SessionState.LoggedIn(userId = "42", login = "ninja"), state)
    }
}
