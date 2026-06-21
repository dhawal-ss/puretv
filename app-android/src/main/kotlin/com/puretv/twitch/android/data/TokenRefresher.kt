package com.puretv.twitch.android.data

import com.puretv.twitch.core.api.DeviceAuth
import io.ktor.client.HttpClient
import kotlinx.coroutines.flow.first

/**
 * SECTION 03.2 — best-effort access-token refresh on app launch.
 *
 * Twitch device-flow access tokens expire; the stored refresh token (rotated on
 * each use) buys a fresh one. Before this, the refresh token was persisted but
 * never consumed, so a returning user whose access token had expired was
 * silently logged out with no recovery. We refresh proactively at startup.
 *
 * This is intentionally fail-soft: on a [com.puretv.twitch.core.api.TokenRefreshException]
 * (revoked/expired refresh token) or any network error we KEEP the existing
 * session rather than logging the user out. The existing access token may still
 * be valid, and if it is not, the next authenticated call surfaces the error to
 * the UI. We never sign the user out from here.
 */
class TokenRefresher(
    private val httpClient: HttpClient,
    private val settingsStore: AppSettingsStore,
) {
    suspend fun refreshIfPossible() {
        val refresh = settingsStore.currentRefreshToken()?.takeIf { it.isNotBlank() } ?: return
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
}
