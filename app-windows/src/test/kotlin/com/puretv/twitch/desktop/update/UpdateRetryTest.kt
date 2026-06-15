package com.puretv.twitch.desktop.update

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The retry core behind the reliable in-app updater. The old updater made each
 * GitHub request exactly once, so a single transient blip (stale HTTP/2
 * connection / GOAWAY / truncated read) surfaced as a hard error and the user
 * had to retry by hand. This helper does that retry automatically — while still
 * failing closed on non-transient errors (a failed signature check must NOT be
 * retried into a "success").
 */
class UpdateRetryTest {

    private val noSleep: suspend (Long) -> Unit = {}

    @Test fun returnsImmediatelyOnFirstSuccess() = runBlocking {
        var calls = 0
        val result = withUpdateRetry(maxAttempts = 3, sleep = noSleep) { calls++; "ok" }
        assertEquals("ok", result)
        assertEquals(1, calls)
    }

    @Test fun retriesTransientFailureThenSucceeds() = runBlocking {
        var calls = 0
        val result = withUpdateRetry(maxAttempts = 3, sleep = noSleep) {
            calls++
            if (calls < 3) throw TransientUpdateException("connection blip")
            "recovered"
        }
        assertEquals("recovered", result)
        assertEquals(3, calls)
    }

    @Test fun givesUpAfterMaxAttemptsAndRethrowsLast() = runBlocking {
        var calls = 0
        assertFailsWith<TransientUpdateException> {
            withUpdateRetry(maxAttempts = 3, sleep = noSleep) {
                calls++; throw TransientUpdateException("always down")
            }
        }
        assertEquals(3, calls)
    }

    @Test fun doesNotRetryNonTransientFailure() = runBlocking {
        // A failed signature check / "not signed" is fatal — retrying it would be
        // a security regression. It must surface on the first attempt.
        var calls = 0
        assertFailsWith<IllegalStateException> {
            withUpdateRetry(maxAttempts = 3, sleep = noSleep) {
                calls++; error("signature check FAILED")
            }
        }
        assertEquals(1, calls)
    }

    @Test fun classifiesNetworkBlipsAsTransientButNotLogicErrors() {
        assertTrue(isTransientUpdateError(java.io.IOException("GOAWAY received")))
        assertTrue(isTransientUpdateError(TransientUpdateException("truncated")))
        assertFalse(isTransientUpdateError(IllegalStateException("not signed")))
    }
}
