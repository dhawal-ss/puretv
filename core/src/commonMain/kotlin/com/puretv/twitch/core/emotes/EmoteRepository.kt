package com.puretv.twitch.core.emotes

import com.puretv.twitch.core.model.ChannelEmote
import com.puretv.twitch.core.model.EmoteProvider
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import com.puretv.twitch.core.storage.TextStore
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/**
 * SECTION 05.3: fetches and caches third-party emotes from BTTV, FFZ and 7TV.
 *
 * Cache strategy:
 *   - emote lists persist across restarts through [EmoteCache] (a
 *     [PersistentEmoteCache] on every app), so chat can show emotes on its very
 *     first lines; [thirdPartyEmotes] hands out that cached copy first and the
 *     fresh one when it arrives
 *   - a provider that fails keeps its cached emotes instead of dropping them
 *   - emote *images* are cached by Coil's disk cache, not here
 *   - globals refresh once per session; a channel's set refreshes on each join,
 *     except a re-join within [channelRefreshInterval]
 */
class EmoteRepository(
    private val httpClient: HttpClient,
    private val cache: EmoteCache,
    private val json: Json = Json { ignoreUnknownKeys = true },
    private val timeSource: TimeSource = TimeSource.Monotonic,
    private val channelRefreshInterval: Duration = 10.minutes,
    // Provider responses run to hundreds of KB (7TV's channel payload carries
    // every emote's full metadata). Decoding them on the caller's dispatcher,
    // usually Main, stalled the UI when a big channel opened.
    private val workDispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    private var globalsLoadedThisSession = false
    private val channelFetchLock = Mutex()
    private val channelFetchedAt = HashMap<String, TimeMark>()

    /**
     * Everyone's globals plus [channelId]'s own emotes: the copy cached on disk
     * first (when there is one), then the fresh copy once the providers answer.
     * Without a channel (the channel lookup failed) only globals are loaded.
     */
    fun thirdPartyEmotes(channelId: String?, channelLogin: String?): Flow<ThirdPartyEmotes> = flow {
        val cached = ThirdPartyEmotes(
            global = runCatching { cache.globalEmotes() }.getOrNull().orEmpty(),
            channel = channelId?.let { runCatching { cache.channelEmotes(it) }.getOrNull() }.orEmpty(),
        )
        if (cached.global.isNotEmpty() || cached.channel.isNotEmpty()) emit(cached)
        val fresh = coroutineScope {
            val global = async { loadGlobalEmotes() }
            val channel = async {
                if (channelId != null && channelLogin != null) loadChannelEmotes(channelId, channelLogin) else emptyList()
            }
            ThirdPartyEmotes(global.await(), channel.await())
        }
        if (fresh != cached) emit(fresh)
    }

    suspend fun loadGlobalEmotes(): List<ChannelEmote> = withContext(workDispatcher) {
        val cached = runCatching { cache.globalEmotes() }.getOrNull()
        if (globalsLoadedThisSession && cached != null) return@withContext cached
        coroutineScope {
            val bttvResult = async { runCatching { fetchBttvGlobal() } }
            val seventvResult = async { runCatching { fetchSevenTvGlobal() } }
            val bttv = bttvResult.await()
            val seventv = seventvResult.await()
            val all = bttv.orCached(cached, EmoteProvider.BTTV) + seventv.orCached(cached, EmoteProvider.SEVENTV)
            if (bttv.isSuccess && seventv.isSuccess) {
                cache.putGlobalEmotes(all)
                globalsLoadedThisSession = true
            }
            all
        }
    }

    suspend fun loadChannelEmotes(channelId: String, channelLogin: String): List<ChannelEmote> = withContext(workDispatcher) {
        val cached = runCatching { cache.channelEmotes(channelId) }.getOrNull()
        val recentlyFetched = channelFetchLock.withLock {
            channelFetchedAt[channelId]?.let { it.elapsedNow() < channelRefreshInterval } == true
        }
        if (recentlyFetched && cached != null) return@withContext cached
        coroutineScope {
            val bttv = async { runCatching { fetchBttvChannel(channelId) } }
            val ffz = async { runCatching { fetchFfzChannel(channelLogin) } }
            val seventv = async { runCatching { fetchSevenTvChannel(channelId) } }
            val bttvResult = bttv.await()
            val ffzResult = ffz.await()
            val seventvResult = seventv.await()
            val all = bttvResult.orCached(cached, EmoteProvider.BTTV) +
                ffzResult.orCached(cached, EmoteProvider.FFZ) +
                seventvResult.orCached(cached, EmoteProvider.SEVENTV)
            // Only persist when every provider fetch succeeded (audit M3): caching
            // a partial set would clobber a good cached one on a transient outage
            // (offline, provider 5xx). The live set, with the failed providers
            // filled from the cache, is still returned for this session.
            if (bttvResult.isSuccess && ffzResult.isSuccess && seventvResult.isSuccess) {
                cache.putChannelEmotes(channelId, all)
                channelFetchLock.withLock { channelFetchedAt[channelId] = timeSource.markNow() }
            }
            all
        }
    }

    /** This provider's fresh emotes, or its emotes from [cached] when the fetch failed. */
    private fun Result<List<ChannelEmote>>.orCached(cached: List<ChannelEmote>?, provider: EmoteProvider): List<ChannelEmote> =
        getOrNull() ?: cached.orEmpty().filter { it.provider == provider }

    // ---- BTTV ----

    private suspend fun fetchBttvGlobal(): List<ChannelEmote> {
        val raw: JsonArray = httpClient.get("https://api.betterttv.net/3/cached/emotes/global").body()
        // Skip individual malformed entries instead of failing the whole set (audit L6).
        return raw.mapNotNull { runCatching { it.jsonObject.toBttvEmote(channelEmote = false) }.getOrNull() }
    }

    private suspend fun fetchBttvChannel(channelId: String): List<ChannelEmote> {
        val raw: JsonObject = httpClient.get("https://api.betterttv.net/3/cached/users/twitch/$channelId").body()
        val channelEmotes = raw["channelEmotes"]?.jsonArray.orEmpty()
        val sharedEmotes = raw["sharedEmotes"]?.jsonArray.orEmpty()
        return (channelEmotes + sharedEmotes).mapNotNull { runCatching { it.jsonObject.toBttvEmote(channelEmote = true) }.getOrNull() }
    }

    private fun JsonObject.toBttvEmote(channelEmote: Boolean): ChannelEmote {
        val id = this["id"]!!.jsonPrimitive.content
        val code = this["code"]!!.jsonPrimitive.content
        val imageType = this["imageType"]?.jsonPrimitive?.contentOrNull ?: "png"
        return ChannelEmote(
            id = id,
            name = code,
            url = "https://cdn.betterttv.net/emote/$id/3x",
            provider = EmoteProvider.BTTV,
            animated = imageType == "gif",
        )
    }

    // ---- FFZ ----

    private suspend fun fetchFfzChannel(channelLogin: String): List<ChannelEmote> {
        val raw: JsonObject = httpClient.get("https://api.frankerfacez.com/v1/room/$channelLogin").body()
        val sets = raw["sets"]?.jsonObject ?: return emptyList()
        return sets.values.flatMap { set ->
            set.jsonObject["emoticons"]?.jsonArray.orEmpty()
                .mapNotNull { runCatching { it.jsonObject.toFfzEmote() }.getOrNull() } // skip malformed entries (audit L6)
        }
    }

    private fun JsonObject.toFfzEmote(): ChannelEmote {
        val id = this["id"]!!.jsonPrimitive.content
        val name = this["name"]!!.jsonPrimitive.content
        val urls = this["urls"]?.jsonObject
        val bestUrl = urls?.get("4") ?: urls?.get("2") ?: urls?.get("1")
        return ChannelEmote(
            id = id,
            name = name,
            url = bestUrl?.jsonPrimitive?.content?.let { if (it.startsWith("http")) it else "https:$it" } ?: "",
            provider = EmoteProvider.FFZ,
            animated = false,
        )
    }

    // ---- 7TV ----

    private suspend fun fetchSevenTvGlobal(): List<ChannelEmote> {
        val raw: JsonObject = httpClient.get("https://7tv.io/v3/emote-sets/global").body()
        return raw["emotes"]?.jsonArray.orEmpty()
            .mapNotNull { runCatching { it.jsonObject.toSevenTvEmote() }.getOrNull() } // skip malformed entries (audit L6)
    }

    private suspend fun fetchSevenTvChannel(channelId: String): List<ChannelEmote> {
        val raw: JsonObject = httpClient.get("https://7tv.io/v3/users/twitch/$channelId").body()
        val emoteSet = raw["emote_set"]?.jsonObject ?: return emptyList()
        return emoteSet["emotes"]?.jsonArray.orEmpty()
            .mapNotNull { runCatching { it.jsonObject.toSevenTvEmote() }.getOrNull() } // skip malformed entries (audit L6)
    }
}

internal fun JsonObject.toSevenTvEmote(): ChannelEmote {
    val id = this["id"]!!.jsonPrimitive.content
    val name = this["name"]!!.jsonPrimitive.content
    val data = this["data"]?.jsonObject
    val animated = data?.get("animated")?.jsonPrimitive?.boolean ?: false
    // 7TV marks overlays two ways across its API surface: the active-emote
    // `flags` bit 0, or the emote `data.flags` bit 8 (256). Treat either as zero-width.
    val activeFlags = this["flags"]?.jsonPrimitive?.intOrNull ?: 0
    val dataFlags = data?.get("flags")?.jsonPrimitive?.intOrNull ?: 0
    val zeroWidth = (activeFlags and 0x1) != 0 || (dataFlags and 0x100) != 0
    return ChannelEmote(
        id = id,
        name = name,
        url = "https://cdn.7tv.app/emote/$id/4x.${if (animated) "webp" else "png"}",
        provider = EmoteProvider.SEVENTV,
        animated = animated,
        zeroWidth = zeroWidth,
    )
}

/** Everyone's global third-party emotes plus one channel's own. */
data class ThirdPartyEmotes(val global: List<ChannelEmote>, val channel: List<ChannelEmote>)

/**
 * Storage-agnostic cache contract. Every app uses [PersistentEmoteCache];
 * [InMemoryEmoteCache] is for tests.
 */
interface EmoteCache {
    suspend fun globalEmotes(): List<ChannelEmote>?
    suspend fun putGlobalEmotes(emotes: List<ChannelEmote>)
    suspend fun channelEmotes(channelId: String): List<ChannelEmote>?
    suspend fun putChannelEmotes(channelId: String, emotes: List<ChannelEmote>)
}

/**
 * In-memory cache used in tests and as the Desktop default until SQLite-backed
 * cache lands. The single [EmoteRepository] is shared across screens, so rapid
 * channel switches (or PiP + a second stream) can call these overlapping; a
 * [Mutex] keeps the backing map from corrupting (mirrors ChannelRepository).
 */
class InMemoryEmoteCache : EmoteCache {
    private val lock = Mutex()
    private var globals: List<ChannelEmote>? = null
    private val channels = mutableMapOf<String, List<ChannelEmote>>()

    override suspend fun globalEmotes(): List<ChannelEmote>? = lock.withLock { globals }
    override suspend fun putGlobalEmotes(emotes: List<ChannelEmote>) = lock.withLock { globals = emotes }
    override suspend fun channelEmotes(channelId: String): List<ChannelEmote>? = lock.withLock { channels[channelId] }
    override suspend fun putChannelEmotes(channelId: String, emotes: List<ChannelEmote>) =
        lock.withLock { channels[channelId] = emotes }
}

/**
 * [EmoteCache] that survives restarts: each list is kept in memory and written
 * through to [store] as JSON (one entry for the globals, one per channel), so a
 * cold start shows emotes before any provider has answered. A corrupt or
 * unreadable entry is just a miss.
 */
class PersistentEmoteCache(
    private val store: TextStore,
    private val workDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : EmoteCache {
    private val json = Json { ignoreUnknownKeys = true }
    private val lock = Mutex()
    private val memory = HashMap<String, List<ChannelEmote>>()
    private val serializer = ListSerializer(ChannelEmote.serializer())

    override suspend fun globalEmotes(): List<ChannelEmote>? = get(GLOBAL)
    override suspend fun putGlobalEmotes(emotes: List<ChannelEmote>) = put(GLOBAL, emotes)
    override suspend fun channelEmotes(channelId: String): List<ChannelEmote>? = get(CHANNEL_PREFIX + channelId)
    override suspend fun putChannelEmotes(channelId: String, emotes: List<ChannelEmote>) = put(CHANNEL_PREFIX + channelId, emotes)

    private suspend fun get(name: String): List<ChannelEmote>? {
        lock.withLock { memory[name] }?.let { return it }
        val text = store.read(name) ?: return null
        // A big channel's list is hundreds of KB of JSON: decode it off the caller's thread.
        val emotes = withContext(workDispatcher) { runCatching { json.decodeFromString(serializer, text) }.getOrNull() } ?: return null
        lock.withLock { memory[name] = emotes }
        return emotes
    }

    private suspend fun put(name: String, emotes: List<ChannelEmote>) {
        lock.withLock { memory[name] = emotes }
        store.write(name, withContext(workDispatcher) { json.encodeToString(serializer, emotes) })
    }

    private companion object {
        const val GLOBAL = "emotes-global.json"
        const val CHANNEL_PREFIX = "emotes-channel-"
    }
}
