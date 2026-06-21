package com.puretv.twitch.android.data

import com.puretv.twitch.core.session.SessionState
import com.puretv.twitch.core.session.deriveSessionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * App-wide observable session, derived from the persisted settings stream.
 * ViewModels and the navigation shell collect [state] so a sign-in (the token
 * appearing in [AppSettingsStore.flow]) instantly drives Home to repopulate,
 * and a sign-out clears identity-scoped UI everywhere. distinctUntilChanged
 * keeps unrelated settings edits (quality, theme, and so on) from re-triggering loads.
 */
class SessionManager(settingsStore: AppSettingsStore) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val state: StateFlow<SessionState> = settingsStore.flow
        .map { deriveSessionState(it) }
        .distinctUntilChanged()
        .stateIn(scope, SharingStarted.Eagerly, SessionState.LoggedOut)
}
