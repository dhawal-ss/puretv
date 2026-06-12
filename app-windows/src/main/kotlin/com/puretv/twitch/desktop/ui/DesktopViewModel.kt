package com.puretv.twitch.desktop.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

/**
 * SECTION 11 (desktop adaptation) — `androidx.lifecycle.ViewModel` /
 * `viewModelScope` aren't available on plain Compose Desktop (no Android
 * lifecycle artifact in this module's dependency set). [DesktopViewModel] is
 * the minimal stand-in: it owns a [CoroutineScope] that lives exactly as long
 * as the composable that created it, mirroring `viewModelScope`'s
 * "cancelled when the screen goes away" semantics via [rememberDesktopViewModel]
 * + [DisposableEffect].
 *
 * Subclasses launch their `init`-time collectors against [scope] exactly like
 * the phone/TV ViewModels do against `viewModelScope` — the only difference
 * is who cancels it and when.
 */
abstract class DesktopViewModel {
    protected val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Override to release platform resources (chat sockets, players, etc.) — mirrors `onCleared()`. */
    open fun onCleared() {}

    internal fun dispose() {
        onCleared()
        scope.cancel()
    }
}

/**
 * Creates (and remembers) a [DesktopViewModel], disposing it — and cancelling
 * its [DesktopViewModel.scope] — when [keys] change or the call leaves
 * composition. Use exactly like `koinViewModel()`/`koinViewModel(parameters = ...)`
 * on the other platforms:
 *
 * ```
 * val viewModel = rememberDesktopViewModel { koin.get<HomeViewModel>() }
 * val viewModel = rememberDesktopViewModel(channelLogin) { koin.get<StreamViewModel> { parametersOf(channelLogin) } }
 * ```
 */
@Composable
fun <T : DesktopViewModel> rememberDesktopViewModel(vararg keys: Any?, create: () -> T): T {
    val viewModel = remember(*keys) { create() }
    DisposableEffect(viewModel) {
        onDispose { viewModel.dispose() }
    }
    return viewModel
}
