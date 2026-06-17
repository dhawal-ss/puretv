package com.puretv.twitch.desktop.ui

import com.puretv.twitch.core.follows.FollowRow
import com.puretv.twitch.core.follows.FollowedChannelsSource
import com.puretv.twitch.core.follows.FollowedRef
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class FollowedRailState(
    val isLoggedIn: Boolean = false,
    val isLoading: Boolean = false,
    val live: List<FollowRow> = emptyList(),
    val offline: List<FollowRow> = emptyList(),
    val offlineExpanded: Boolean = false,
    val errored: Boolean = false,
)

/**
 * Drives the followed rail. Auth + local pins are injected as lambdas (resolved
 * from DesktopSettingsStore/FollowStore in DI) so this ViewModel unit-tests
 * without touching persisted token files. Loading is best-effort: a failed load
 * keeps the last-known list and flips [FollowedRailState.errored].
 */
class FollowedRailViewModel(
    private val source: FollowedChannelsSource,
    private val loggedInUserId: () -> String?,
    private val localPins: () -> List<FollowedRef>,
) : DesktopViewModel() {
    private val _state = MutableStateFlow(FollowedRailState(isLoggedIn = loggedInUserId() != null))
    val state: StateFlow<FollowedRailState> = _state.asStateFlow()

    /** Fire-and-forget reload (used by the composable's focus-aware poll loop). */
    fun refresh() { scope.launch { loadOnce() } }

    fun toggleOffline() = _state.update { it.copy(offlineExpanded = !it.offlineExpanded) }

    /** Suspending single load. Also the test entry point. */
    internal suspend fun loadOnce() {
        val userId = loggedInUserId()
        if (userId.isNullOrBlank()) {
            // Signed out (manually or via token-expiry): drop cached profiles so a
            // different account signing in next within this process never reuses them.
            source.clear()
            _state.update { it.copy(isLoggedIn = false, isLoading = false, live = emptyList(), offline = emptyList()) }
            return
        }
        _state.update { it.copy(isLoggedIn = true, isLoading = it.live.isEmpty() && it.offline.isEmpty()) }
        runCatching {
            source.load(userId, localPins()) { live ->
                // Fast partial: live channels are in (avatars may still be loading). Drop
                // the loading state now so the list appears without waiting on avatars.
                _state.update { it.copy(isLoading = false, errored = false, live = live.live, offline = live.offline) }
            }
        }
            .onSuccess { full -> _state.update { it.copy(isLoading = false, errored = false, live = full.live, offline = full.offline) } }
            .onFailure { _state.update { it.copy(isLoading = false, errored = true) } }
    }
}
