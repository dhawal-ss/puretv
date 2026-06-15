package com.puretv.twitch.desktop.update

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import java.io.IOException
import java.net.http.HttpTimeoutException

/**
 * A transient update failure a fresh attempt might fix: a truncated download, a
 * retryable HTTP status, etc. Distinct from fatal failures (bad signature, no
 * signing key) which must never be retried.
 */
class TransientUpdateException(message: String) : Exception(message)

/**
 * Classifies a failure as worth retrying. Network-level faults (connection
 * reset, EOF/GOAWAY from a stale HTTP/2 connection, timeouts) are transient;
 * cancellation and logic/security errors (e.g. a failed signature check, raised
 * as [IllegalStateException]) are not.
 */
internal fun isTransientUpdateError(e: Throwable): Boolean = when (e) {
    is CancellationException -> false
    is TransientUpdateException -> true
    is HttpTimeoutException -> true
    is IOException -> true
    else -> false
}

/**
 * Runs [block], retrying transient failures (per [isTransient]) up to
 * [maxAttempts] total with exponential backoff (baseDelayMs, then doubling).
 * A non-transient failure is re-thrown immediately (fail-closed); once attempts
 * are exhausted the last transient failure is re-thrown. [sleep] is injectable
 * so tests run without real delays.
 */
internal suspend fun <T> withUpdateRetry(
    maxAttempts: Int = 3,
    baseDelayMs: Long = 400,
    isTransient: (Throwable) -> Boolean = ::isTransientUpdateError,
    sleep: suspend (Long) -> Unit = { delay(it) },
    block: suspend () -> T,
): T {
    var attempt = 1
    while (true) {
        try {
            return block()
        } catch (e: Throwable) {
            if (!isTransient(e) || attempt >= maxAttempts) throw e
            sleep(baseDelayMs shl (attempt - 1)) // 400ms, 800ms, 1600ms…
            attempt++
        }
    }
}
