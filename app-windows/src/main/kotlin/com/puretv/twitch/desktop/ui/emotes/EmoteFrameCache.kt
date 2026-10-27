package com.puretv.twitch.desktop.ui.emotes

import coil3.disk.DiskCache
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext

/**
 * URL-keyed cache of decoded animated emote frames. Decodes each emote ONCE and shares the
 * immutable result across every on-screen instance. Returns null for static/failed images so
 * the caller falls back to the static (Coil) path. LRU-bounded to cap memory.
 *
 * NATIVE-MEMORY SAFETY (audit P0-5). Each cached entry holds off-heap skia Images. Simply
 * dropping the Java reference on LRU eviction leaks that native memory until a heap GC runs
 * (GC is heap-, not native-, pressure driven). But an on-screen [AnimatedEmote] only fetches
 * via [frames] ONCE (in a LaunchedEffect), so a still-rendered entry can become the LRU eldest
 * and be evicted WHILE in use — closing it then would be a use-after-free. So callers
 * [retain]/[release] around their render lifetime, and an evicted entry's native memory is
 * freed only once its live-render count reaches zero.
 */
class EmoteFrameCache(
    private val httpClient: HttpClient,
    /**
     * Coil's disk cache, shared with the app's image loader. The static fallback
     * shown while frames decode is loaded through Coil, which stores the file
     * there; reading it back means an emote already on disk (from this session
     * or an earlier one) is never downloaded a second time.
     */
    private val diskCache: DiskCache? = null,
    private val maxEntries: Int = 64,
) {
    private val lock = Any()

    // How many composables are currently rendering each url (retain/release). Guards an
    // evicted entry from being closed while still on screen.
    private val refs = HashMap<String, Int>()

    // Entries evicted from [cache] while still referenced; closed when refs hit zero.
    private val pendingClose = HashMap<String, AnimatedEmoteFrames>()

    private val cache = object : LinkedHashMap<String, AnimatedEmoteFrames?>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, AnimatedEmoteFrames?>): Boolean {
            if (size <= maxEntries) return false
            val victim = eldest.value
            if (victim != null) {
                // Safe to free now only if nobody is rendering it; otherwise defer the close
                // to the last release() so we never free pixels mid-draw.
                if ((refs[eldest.key] ?: 0) <= 0) victim.close()
                else pendingClose[eldest.key] = victim
            }
            return true
        }
    }

    /** Mark that a composable will render [url]'s frames. Pairs with [release]. */
    fun retain(url: String) = synchronized(lock) { refs[url] = (refs[url] ?: 0) + 1 }

    /** A composable stopped rendering [url]. Frees an already-evicted entry when the last one leaves. */
    fun release(url: String) = synchronized(lock) {
        val n = (refs[url] ?: 0) - 1
        if (n <= 0) {
            refs.remove(url)
            pendingClose.remove(url)?.close()
        } else {
            refs[url] = n
        }
    }

    // Loads in progress, by url. An emote spammed in chat appears in many rows at once, and
    // each row asked for it: every one downloaded and decoded it again. Now they share one
    // load, which runs on the cache's own scope so a row leaving the screen cannot cancel
    // it for the others.
    private val inFlight = HashMap<String, Deferred<AnimatedEmoteFrames?>>()
    private val loadScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Decoded frames for [url], or null if static / undecodable. Caches success + static. */
    suspend fun frames(url: String): AnimatedEmoteFrames? {
        var started: Deferred<AnimatedEmoteFrames?>? = null
        val load = synchronized(lock) {
            if (cache.containsKey(url)) return cache[url]
            inFlight.getOrPut(url) { loadScope.async { load(url) }.also { started = it } }
        }
        // Registered once the load is in the map, so a load that is already finished still
        // takes itself out (the handler then runs right here).
        started?.let { mine -> mine.invokeOnCompletion { synchronized(lock) { if (inFlight[url] === mine) inFlight.remove(url) } } }
        return load.await()
    }

    private suspend fun load(url: String): AnimatedEmoteFrames? {
        // Decode off the lock (and off the EDT). A put for the same url can still race
        // this one (putForTest); the loser closes its duplicate below rather than leaking it.
        //
        // Audit F12: a TRANSIENT fetch failure (timeout/503) must NOT be cached as a permanent
        // null — only cache a successful fetch, so the next request retries after connectivity
        // returns. (A genuine static image decodes to null and IS cached so we don't re-decode.)
        val bytes = withContext(Dispatchers.IO) {
            cachedBytes(url) ?: runCatching { httpClient.get(url).body<ByteArray>() }.getOrNull()
        } ?: return null
        // The Skia decode is CPU-bound and MUST run off the EDT. frames() is called from
        // AnimatedEmote's LaunchedEffect, whose dispatcher on Compose Desktop is the main
        // (EDT) dispatcher, so decoding inline here ran on the UI thread. Combined with the
        // 2-arg readPixels (which recursively re-decodes each frame's required-frame chain,
        // O(n^2)), a large multi-frame emote burned tens of seconds of EDT CPU and froze the
        // whole window. Default = the CPU thread pool.
        val decoded = withContext(Dispatchers.Default) { decodeAnimatedFrames(bytes) }
        synchronized(lock) {
            if (cache.containsKey(url)) {
                // Another coroutine won the race and cached an equivalent result; drop ours.
                decoded?.close()
                return cache[url]
            }
            cache[url] = decoded
            return decoded
        }
    }

    /** The file Coil already holds for [url] (its disk-cache key is the URL), or null. */
    private fun cachedBytes(url: String): ByteArray? {
        val cache = diskCache ?: return null
        return runCatching {
            val snapshot = cache.openSnapshot(url) ?: return null
            try {
                cache.fileSystem.read(snapshot.data) { readByteArray() }
            } finally {
                snapshot.close()
            }
        }.getOrNull()
    }

    /** Visible for tests: seed an entry without network IO. */
    internal fun putForTest(url: String, value: AnimatedEmoteFrames?) = synchronized(lock) { cache[url] = value }
}
