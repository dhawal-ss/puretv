package com.puretv.twitch.core.playback

import kotlinx.coroutines.delay

/** Which way a channel flip steps through the ring: channel up is [NEXT], channel down is [PREVIOUS]. */
enum class FlipDirection { NEXT, PREVIOUS }

/**
 * The maths behind flipping channels with a remote or the keyboard.
 *
 * A flip walks a ring of channel logins and wraps at both ends. Which ring is a
 * product rule: the live follows when the viewer is signed in and the channel they
 * are watching is one of them (surfing what you follow), otherwise the row the
 * stream was opened from (Home "Live now", a category, search results).
 *
 * Logins compare case-insensitively and results keep the casing the ring held.
 */
object ChannelFlip {
    /** The login one step from [current] in [ring], wrapping; null when there is nowhere else to go. */
    fun step(ring: List<String>, current: String, direction: FlipDirection): String? {
        val at = ring.indexOfFirst { it.equals(current, ignoreCase = true) }
        if (at < 0 || ring.size < 2) return null
        val next = when (direction) {
            FlipDirection.NEXT -> (at + 1) % ring.size
            FlipDirection.PREVIOUS -> (at - 1 + ring.size) % ring.size
        }
        return ring[next]
    }

    /** Both neighbours of [current], previous first. Empty when [step] has nowhere to go. */
    fun neighbours(ring: List<String>, current: String): List<String> =
        listOfNotNull(step(ring, current, FlipDirection.PREVIOUS), step(ring, current, FlipDirection.NEXT)).distinct()

    /**
     * The ring to flip through for [current].
     *
     * [liveFollows] are the signed-in viewer's live follows (empty or null when signed
     * out); [opened] is the row the stream was opened from. When [current] is in neither
     * (a channel reached some other way, with an older row still remembered) there is
     * nothing honest to flip through, so the ring is just [current] and a flip says so.
     * The result never repeats a login.
     */
    fun ringFor(current: String, liveFollows: List<String>?, opened: List<String>): List<String> {
        fun holds(row: List<String>?) = row != null && row.any { it.equals(current, ignoreCase = true) }
        val base = when {
            holds(liveFollows) -> liveFollows!!
            holds(opened) -> opened
            else -> return listOf(current)
        }
        return base.distinctBy { it.lowercase() }
    }
}

/**
 * The row of channels a stream was opened from. A screen records it when the viewer
 * picks a channel; the stream screen reads it for the ring when the channel is not
 * one of the viewer's live follows. One per app (a Koin singleton).
 */
class FlipOrigin {
    @Volatile
    var logins: List<String> = emptyList()
        private set

    /** Remembers [logins] (blank entries dropped) as the row the next stream is opened from. */
    fun record(logins: List<String>) {
        this.logins = logins.filter { it.isNotBlank() }
    }
}

/**
 * Turns a burst of flip presses into one channel change. Holding Down or tapping it
 * three times walks the ring locally ([press] returns where it would land, for the
 * banner) and only the settled target is played, so no stream is opened and thrown
 * away on the way. The clock is passed in; the screen asks [takeDue] after [settleMs].
 */
class PendingFlip(private val settleMs: Long = SETTLE_MS) {
    private var target: String? = null
    private var lastPressAtMs = 0L

    /** Where the flip would land right now, or null when nothing is pending. */
    val pending: String? get() = target

    /**
     * Steps one more channel from the pending target (or from [current] when none is
     * pending) and returns the new target; null when the ring has nowhere to go.
     */
    fun press(ring: List<String>, current: String, direction: FlipDirection, nowMs: Long): String? {
        val from = target ?: current
        val next = ChannelFlip.step(ring, from, direction) ?: return null
        target = next
        lastPressAtMs = nowMs
        return next
    }

    /** When [takeDue] will next have an answer, or null when nothing is pending. */
    fun dueAtMs(): Long? = target?.let { lastPressAtMs + settleMs }

    /**
     * The settled target once [settleMs] has passed since the last press, clearing it.
     * Null while still settling, with nothing pending, or when the walk ended back on [current].
     */
    fun takeDue(current: String, nowMs: Long): String? {
        val t = target ?: return null
        if (nowMs - lastPressAtMs < settleMs) return null
        target = null
        return t.takeUnless { it.equals(current, ignoreCase = true) }
    }

    fun cancel() {
        target = null
    }

    /**
     * Waits until the pending flip has settled and returns where it landed (null when the
     * walk ended back on the current channel or nothing is pending). A coroutine delay and
     * the wall clock can disagree by a millisecond, and a flip must not be dropped for
     * that, so a read that is a hair early waits out the rest and asks again.
     */
    suspend fun awaitSettled(current: () -> String, clock: () -> Long): String? {
        while (true) {
            val due = dueAtMs() ?: return null
            val wait = due - clock()
            if (wait > 0) delay(wait)
            takeDue(current(), clock())?.let { return it }
            if (target == null) return null
        }
    }

    companion object {
        /**
         * Long enough to ride out quick repeated presses, short enough to feel
         * instant. Every single flip waits this out before anything loads, so it
         * stays tight: 450 ms read as lag on every channel change.
         */
        const val SETTLE_MS = 300L
    }
}

/**
 * Decides which channels are worth prewarming (`PlayableStreamCache.prewarm`) so a flip
 * is instant without background traffic. Prewarm happens on intent only: the first remote
 * key, and after a flip commits. A cache entry lives 60 seconds, so a channel is prewarmed
 * again at most every [intervalMs] (45 seconds), which keeps the entry fresh and caps the
 * cost at two token mints and two playlist fetches per burst of channel surfing.
 */
class PrewarmGate(private val clock: () -> Long, private val intervalMs: Long = INTERVAL_MS) {
    private val last = LinkedHashMap<String, Long>()

    /** The subset of [logins] to prewarm now; marks them as done. */
    fun take(logins: List<String>): List<String> {
        val now = clock()
        val out = ArrayList<String>(logins.size)
        for (login in logins) {
            val key = login.lowercase()
            val at = last[key]
            if (at != null && now - at < intervalMs) continue
            last.remove(key)
            last[key] = now
            out += login
        }
        while (last.size > MAX_TRACKED) last.remove(last.keys.first())
        return out
    }

    companion object {
        const val INTERVAL_MS = 45_000L
        private const val MAX_TRACKED = 16
    }
}
