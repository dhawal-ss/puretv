package com.puretv.twitch.core

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * A request counter that is safe to bump from inside a Ktor `MockEngine`
 * handler.
 *
 * Several services under test fan their requests out with `async`
 * (`FollowedChannelsService` chunks /streams and /users, `EmoteRepository`
 * loads its providers side by side), so the handler runs on more than one
 * thread and a plain `var n = 0` loses increments.
 *
 * That is not hypothetical. CI failed on
 * `paginatesFollowsChunksLiveAndProfilesThenMerges` with
 * `120 ids must be chunked into 2 /users calls expected:<2> but was:<1>`,
 * while every data assertion in the same test passed and the identically
 * chunked /streams counter read 2. Both requests were made; one increment was
 * simply lost. The same test had passed twice on the same commit minutes
 * earlier, which is the tell: a logic error would fail every time, a lost
 * update fails on whichever machine happens to interleave.
 *
 * [incrementAndGet] returns the value it just stored, so a handler that
 * branches on "is this the first call?" reads its own increment rather than
 * re-reading a field another thread may have moved.
 */
class CallCounter {
    private val lock = Mutex()
    private var value = 0

    suspend fun incrementAndGet(): Int = lock.withLock { ++value }

    /** Read under the same lock, so the assertion cannot race the last write. */
    suspend fun get(): Int = lock.withLock { value }
}
