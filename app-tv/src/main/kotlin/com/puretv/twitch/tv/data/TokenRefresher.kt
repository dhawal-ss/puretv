package com.puretv.twitch.tv.data

import com.puretv.twitch.core.api.DeviceAuth
import com.puretv.twitch.core.repository.UserRepository
import io.ktor.client.HttpClient
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * SECTION 03.2, best-effort access-token refresh on app launch (TV counterpart
 * of the phone app's `TokenRefresher`).
 *
 * Twitch device-flow access tokens expire; the stored refresh token (rotated on
 * each use) buys a fresh one. Fail-soft: on a
 * [com.puretv.twitch.core.api.TokenRefreshException] (revoked/expired refresh
 * token) or any network error we KEEP the existing session rather than logging
 * the user out. Kept as its own class (not shared with app-android) because it
 * depends on this app's own [AppSettingsStore] (Section 12.2).
 *
 * Beyond the launch refresh, [refreshIfPossible] is now also called on-demand by
 * the Home/Browse ViewModels when a Helix discovery call fails (top streams/games
 * 401 on an expired token). A [Mutex] + a short recency guard COALESCE those
 * concurrent callers so the launch refresh and a ViewModel retry can't both fire
 * simultaneously and rotate each other's refresh token into an invalid state.
 */
class TokenRefresher(
    private val httpClient: HttpClient,
    private val settingsStore: AppSettingsStore,
    private val userRepository: UserRepository,
) {
    private val mutex = Mutex()
    @Volatile private var lastSuccessfulRefreshAtMs = 0L

    /**
     * Refreshes the access token if a refresh token is present. [force] bypasses
     * the recency guard (the caller knows the current token is definitely dead);
     * otherwise a refresh that succeeded within [MIN_REFRESH_INTERVAL_MS] is
     * skipped so a burst of resume/retry calls doesn't hammer Twitch's token
     * endpoint. Serialized so overlapping callers coalesce onto one network round.
     */
    suspend fun refreshIfPossible(force: Boolean = false): Unit = mutex.withLock {
        val refresh = settingsStore.currentRefreshToken()?.takeIf { it.isNotBlank() }
        val recentlyRefreshed =
            lastSuccessfulRefreshAtMs != 0L &&
                System.currentTimeMillis() - lastSuccessfulRefreshAtMs < MIN_REFRESH_INTERVAL_MS
        if (refresh != null && (force || !recentlyRefreshed)) {
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
                    lastSuccessfulRefreshAtMs = System.currentTimeMillis()
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

    private companion object {
        // A successful refresh within this window short-circuits a redundant one,
        // so a flurry of resume/retry calls coalesces onto a single token round.
        const val MIN_REFRESH_INTERVAL_MS = 60_000L
    }
}
