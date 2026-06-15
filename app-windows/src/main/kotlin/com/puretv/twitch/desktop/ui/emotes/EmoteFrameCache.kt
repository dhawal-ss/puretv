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

    /** Decoded frames for [url], or null if static / undecodable. Cached (including null). */
    suspend fun frames(url: String): AnimatedEmoteFrames? {
        mutex.withLock { if (cache.containsKey(url)) return cache[url] }
        val decoded = withContext(Dispatchers.IO) {
            runCatching { decodeAnimatedFrames(httpClient.get(url).body<ByteArray>()) }.getOrNull()
        }
        mutex.withLock { cache[url] = decoded }
        return decoded
    }
}
