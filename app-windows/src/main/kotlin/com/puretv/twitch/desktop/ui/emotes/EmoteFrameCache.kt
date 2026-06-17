package com.puretv.twitch.desktop.ui.emotes

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * URL-keyed cache of decoded animated emote frames. Decodes each emote ONCE and shares the
 * immutable result across every on-screen instance. Returns null for static/failed images so
 * the caller falls back to the static (Coil) path. LRU-bounded to cap memory.
 */
class EmoteFrameCache(
    private val httpClient: HttpClient,
    private val maxEntries: Int = 64,
) {
    private val mutex = Mutex()
    private val cache = object : LinkedHashMap<String, AnimatedEmoteFrames?>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, AnimatedEmoteFrames?>) =
            size > maxEntries
    }

    /** Decoded frames for [url], or null if static / undecodable. Caches success + static. */
    suspend fun frames(url: String): AnimatedEmoteFrames? {
        mutex.withLock { if (cache.containsKey(url)) return cache[url] }
        // No in-flight de-dup: two coroutines racing the same uncached url both decode and
        // the second put overwrites with an identical result. Intentional — emotes are
        // small/idempotent, and holding the lock across the decode would serialize ALL
        // emote decoding. Do not "optimize" by locking around the IO below.
        //
        // Audit F12: a TRANSIENT fetch failure (timeout/503) must NOT be cached as a
        // permanent null — that would render the emote static forever even after
        // connectivity returns. Only cache a successful fetch: a genuine static image
        // (decode -> null) is cached so we don't re-decode it; a fetch error returns
        // null WITHOUT caching, so the next request retries.
        val bytes = withContext(Dispatchers.IO) {
            runCatching { httpClient.get(url).body<ByteArray>() }.getOrNull()
        } ?: return null
        val decoded = decodeAnimatedFrames(bytes)
        mutex.withLock { cache[url] = decoded }
        return decoded
    }
}
