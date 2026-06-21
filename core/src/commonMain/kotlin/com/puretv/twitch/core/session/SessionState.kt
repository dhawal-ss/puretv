package com.puretv.twitch.core.session

import com.puretv.twitch.core.model.AppSettings

/**
 * Global, observable authentication state: the single source of truth the UI
 * reacts to, so sign-in becomes a push event (instant Home populate) and
 * sign-out clears identity-scoped state everywhere at once.
 */
sealed interface SessionState {
    data object LoggedOut : SessionState
    data class LoggedIn(val userId: String, val login: String) : SessionState
}

/**
 * Pure mapping from persisted [AppSettings] to a [SessionState]. A blank access
 * token means logged out; anything else is a live session. No IO, so it is
 * unit-testable on every platform target.
 */
fun deriveSessionState(settings: AppSettings): SessionState =
    if (settings.accessToken.isBlank()) {
        SessionState.LoggedOut
    } else {
        SessionState.LoggedIn(userId = settings.userId, login = settings.username)
    }
