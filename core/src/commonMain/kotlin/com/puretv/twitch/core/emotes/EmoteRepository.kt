package com.puretv.twitch.core.emotes

import com.puretv.twitch.core.model.ChannelEmote
import com.puretv.twitch.core.model.EmoteProvider
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * SECTION 05.3 — fetches & caches third-party emotes from BTTV, FFZ, and 7TV.
 *
 * Cache strategy (per the spec):
 *   - persist emote metadata in Room (Android/TV) / SQLite (Desktop) — see
 *     [EmoteCache] for the storage-agnostic interface this repo writes through
 *   - emote *images* are cached by Coil's disk cache, not here
 *   - channel emotes refresh on every channel join; globals refresh once/session
 */
class EmoteRepository(
    private val httpClient: HttpClient,
    private val cache: EmoteCache,
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    private var globalsLoadedThisSession = false

    suspend fun loadGlobalEmotes(): List<ChannelEmote> {
        if (globalsLoadedThisSession) cache.globalEmotes()?.let { return it }
        return coroutineScope {
            val bttv = async { runCatching { fetchBttvGlobal() }.getOrDefault(emptyList()) }
            val seventv = async { runCatching { fetchSevenTvGlobal() }.getOrDefault(emptyList()) }
            val all = bttv.await() + seventv.await()
            cache.putGlobalEmotes(all)
            globalsLoadedThisSession = true
            all
        }
    }

    suspend fun loadChannelEmotes(channelId: String, channelLogin: String): List<ChannelEmote> = coroutineScope {
        val bttv = async { runCatching { fetchBttvChannel(channelId) }.getOrDefault(emptyList()) }
        val ffz = async { runCatching { fetchFfzChannel(channelLogin) }.getOrDefault(emptyList()) }
        val seventv = async { runCatching { fetchSevenTvChannel(channelId) }.getOrDefault(emptyList()) }
        val all = bttv.await() + ffz.await() + seventv.await()
        cache.putChannelEmotes(channelId, all)
        all
    }

    // ---- BTTV ----

    private suspend fun fetchBttvGlobal(): List<ChannelEmote> {
        val raw: JsonArray = httpClient.get("https://api.betterttv.net/3/cached/emotes/global").body()
        return raw.map { it.jsonObject.toBttvEmote(channelEmote = false) }
    }

    private suspend fun fetchBttvChannel(channelId: String): List<ChannelEmote> {
        val raw: JsonObject = httpClient.get("https://api.betterttv.net/3/cached/users/twitch/$channelId").body()
        val channelEmotes = raw["channelEmotes"]?.jsonArray.orEmpty()
        val sharedEmotes = raw["sharedEmotes"]?.jsonArray.orEmpty()
        return (channelEmotes + sharedEmotes).map { it.jsonObject.toBttvEmote(channelEmote = true) }
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
            set.jsonObject["emoticons"]?.jsonArray.orEmpty().map { it.jsonObject.toFfzEmote() }
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
        return raw["emotes"]?.jsonArray.orEmpty().map { it.jsonObject.toSevenTvEmote() }
    }

    private suspend fun fetchSevenTvChannel(channelId: String): List<ChannelEmote> {
        val raw: JsonObject = httpClient.get("https://7tv.io/v3/users/twitch/$channelId").body()
        val emoteSet = raw["emote_set"]?.jsonObject ?: return emptyList()
        return emoteSet["emotes"]?.jsonArray.orEmpty().map { it.jsonObject.toSevenTvEmote() }
    }

    private fun JsonObject.toSevenTvEmote(): ChannelEmote {
        val id = this["id"]!!.jsonPrimitive.content
        val name = this["name"]!!.jsonPrimitive.content
        val animated = this["data"]?.jsonObject?.get("animated")?.jsonPrimitive?.boolean ?: false
        return ChannelEmote(
            id = id,
            name = name,
            url = "https://cdn.7tv.app/emote/$id/4x.${if (animated) "webp" else "png"}",
            provider = EmoteProvider.SEVENTV,
            animated = animated,
        )
    }
}

/**
 * Storage-agnostic cache contract. Implemented by `RoomEmoteCache` on
 * Android/TV and `SqliteEmoteCache` on Desktop (see androidMain/desktopMain).
 */
interface EmoteCache {
    suspend fun globalEmotes(): List<ChannelEmote>?
    suspend fun putGlobalEmotes(emotes: List<ChannelEmote>)
    suspend fun channelEmotes(channelId: String): List<ChannelEmote>?
    suspend fun putChannelEmotes(channelId: String, emotes: List<ChannelEmote>)
}

/** In-memory cache used in tests and as the Desktop default until SQLite-backed cache lands. */
class InMemoryEmoteCache : EmoteCache {
    private var globals: List<ChannelEmote>? = null
    private val channels = mutableMapOf<String, List<ChannelEmote>>()

    override suspend fun globalEmotes(): List<ChannelEmote>? = globals
    override suspend fun putGlobalEmotes(emotes: List<ChannelEmote>) { globals = emotes }
    override suspend fun channelEmotes(channelId: String): List<ChannelEmote>? = channels[channelId]
    override suspend fun putChannelEmotes(channelId: String, emotes: List<ChannelEmote>) { channels[channelId] = emotes }
}
