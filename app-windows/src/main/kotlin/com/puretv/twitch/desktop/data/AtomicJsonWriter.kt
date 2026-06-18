package com.puretv.twitch.desktop.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

/**
 * Conflating, off-thread durable writer for the desktop JSON stores.
 *
 * PERFORMANCE (audit: fsync-on-EDT freeze). The stores used to serialize their
 * whole snapshot and run [AtomicFile.writeTextAtomically] (createTempFile + write +
 * fsync + atomic rename) inline, inside `synchronized(lock)`, on whatever thread
 * called the mutator. The hot mutators (follow/unfollow, settings + in-player gear
 * toggles, continue-watching remove) are bound straight to Compose `onClick`, so
 * that fsync ran on the AWT EDT and froze the UI on every such click — worst on an
 * antivirus-scanned %APPDATA%. The 10s/45s autosave loops did the same blocking IO
 * on Dispatchers.Default (the CPU pool).
 *
 * This writer takes the mutation off the calling thread entirely:
 *  - the store updates its in-memory StateFlow synchronously (cheap, EDT-safe) and
 *    hands a snapshot *producer* (`() -> String`) here,
 *  - the producer is invoked AND the file written on a private single-thread IO
 *    dispatcher,
 *  - writes are CONFLATED: only the latest pending snapshot is kept, so a burst of
 *    mutations (200 follows, the autosave loops) collapses to one write of the
 *    newest state — also eliminating the previous whole-file write amplification.
 *
 * The producer is invoked off-thread, so the snapshot it captures MUST be immutable
 * (the stores capture `_state.value`, which holds immutable lists/maps of immutable
 * data classes — safe).
 *
 * [flush] blocks until every snapshot enqueued so far is durably written; it is for
 * tests (which assert reload-from-disk) and the shutdown hook ONLY. Never call it
 * from the UI thread.
 */
internal class AtomicJsonWriter(private val file: java.io.File) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))
    private val lock = Any()
    private var latest: (() -> String)? = null
    private var draining = false

    /** Queue [produce]'s result to be written atomically, off-thread. Non-blocking. */
    fun enqueue(produce: () -> String) {
        synchronized(lock) {
            latest = produce
            if (!draining) {
                draining = true
                scope.launch { drain() }
            }
        }
    }

    private fun drain() {
        while (true) {
            val produce = synchronized(lock) {
                val p = latest
                if (p == null) {
                    // Exit decision is made under the same lock that enqueue() uses to
                    // (re)start draining, so a concurrent enqueue can never be lost: it
                    // either sets `latest` before we read null here (we keep looping) or
                    // sees `draining == false` after we clear it (it relaunches).
                    draining = false
                    return
                }
                latest = null
                p
            }
            runCatching { AtomicFile.writeTextAtomically(file, produce()) }
                .onFailure { System.err.println("AtomicJsonWriter: failed to persist ${file.name}: ${it.message}") }
        }
    }

    /**
     * Block until all snapshots enqueued so far are durable. Works by submitting an
     * empty ordering barrier to the single-thread write dispatcher and joining it:
     * because that dispatcher runs tasks strictly FIFO, once the barrier runs every
     * prior write (including the in-flight drain) has completed.
     *
     * BOUNDED (audit F1): this is called from the JVM shutdown hook. If a write is wedged
     * (a locked file, an AV scanner holding %APPDATA%, a stalled disk), the barrier can
     * never run and an unbounded join would hang process exit forever. We cap the wait;
     * on timeout we give up and let exit proceed. The atomic-rename guarantees the
     * *previous* state is never corrupted, so a dropped last write is tolerable.
     */
    fun flush() {
        runCatching { runBlocking { withTimeout(FLUSH_TIMEOUT_MS) { scope.launch { /* barrier */ }.join() } } }
    }

    private companion object {
        const val FLUSH_TIMEOUT_MS = 2_000L
    }
}
