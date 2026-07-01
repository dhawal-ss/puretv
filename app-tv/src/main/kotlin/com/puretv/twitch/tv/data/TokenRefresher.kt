package com.puretv.twitch.tv.data

import com.puretv.twitch.core.api.DeviceAuth
import com.puretv.twitch.core.repository.UserRepository
import io.ktor.client.HttpClient
import kotlinx.coroutines.flow.first

/**
 * SECTION 03.2 — best-effort access-token refresh on app launch (TV counterpart
 * of the phone app's `TokenRefresher`).
 *
 * Twitch device-flow access tokens expire; the stored refresh token (rotated on
 * each use) buys a fresh one. Fail-soft: on a
 * [com.puretv.twitch.core.api.TokenRefreshException] (revoked/expired refresh
 * token) or any network error we KEEP the existing session rather than logging
 * the user out. Kept as its own class (not shared with app-android) because it
 * depends on this app's own [AppSettingsStore] (Section 12.2).
 */
class TokenRefresher(
    private val httpClient: HttpClient,
    private val settingsStore: AppSettingsStore,
    private val userRepository: UserRepository,
) {
    suspend fun refreshIfPossible() {
        val refresh = settingsStore.currentRefreshToken()?.takeIf { it.isNotBlank() }
        if (refresh != null) {
            val current = settingsStore.flow.first()
            runCatching { DeviceAuth.refreshToken(httpClient, refresh) }
                .onSuccess { token ->
                    settingsStore.setSession(
                        accessToken = token.accessToken,
                        // Twitch may or may not rotate the refresh token; keep the old
                        // one if the response omits a new one.
                        refreshToken = token.refreshToken ?: refresh,
                        username = current.username,
                        userId = current.userId,
                    )
                }
            // Deliberately no onFailure: keep the current session on any failure.
        }
        backfillIdentityIfMissing()
    }

    /**
     * If the user is signed in but username/userId are blank, the post-login
     * identity lookup failed on a network blip (session was saved token-first).
     * Retry the lookup here on launch, best-effort.
     */
    private suspend fun backfillIdentityIfMissing() {
        val settings = settingsStore.flow.first()
        if (settings.accessToken.isBlank()) return
        if (settings.username.isNotBlank() && settings.userId.isNotBlank()) return
        val me = runCatching { userRepository.getCurrentUser() }.getOrNull() ?: return
        settingsStore.setSession(
            accessToken = settings.accessToken,
            refreshToken = settingsStore.currentRefreshToken(),
            username = me.login,
            userId = me.id,
        )
    }
}
