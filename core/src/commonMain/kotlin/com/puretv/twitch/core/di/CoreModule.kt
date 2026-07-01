package com.puretv.twitch.core.di

import com.puretv.twitch.core.adblock.AdBlockConfig
import com.puretv.twitch.core.adblock.AdBlockEngine
import com.puretv.twitch.core.api.TwitchApiClient
import com.puretv.twitch.core.chat.TwitchChatClient
import com.puretv.twitch.core.emotes.EmoteRepository
import com.puretv.twitch.core.emotes.InMemoryEmoteCache
import com.puretv.twitch.core.repository.ChannelRepository
import com.puretv.twitch.core.repository.ChannelStatsRepository
import com.puretv.twitch.core.repository.StreamRepository
import com.puretv.twitch.core.repository.UserRepository
import com.puretv.twitch.core.stream.StreamResolver
import com.puretv.twitch.core.stream.TwitchGqlClient
import com.puretv.twitch.core.api.RateLimitedException
import com.puretv.twitch.core.api.HelixApiException
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpResponseValidator
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import org.koin.dsl.module

/**
 * SECTION 11 — shared Koin modules. `coreModule` is platform-agnostic;
 * each app supplies an HttpClient engine (OkHttp on both Android and Desktop)
 * and a settings-backed [AdBlockConfig] before this module's `single { }`
 * definitions are resolved.
 */
val coreModule = module {
    // Networking — engine supplied by platform module via `single<HttpClientEngine>`-style override,
    // or simply call buildKtorClient(engineFactory) from each platform's DI setup.
    single { buildKtorClient(get()) }

    // Twitch
    single { val holder = get<TokenHolder>(); TwitchApiClient(get()) { holder.current() } }
    single { TwitchGqlClient(get()) }
    single { StreamResolver(get(), get()) }

    // Ad Block — AdBlockConfig is normally provided per-platform from DataStore/settings;
    // this default keeps the graph resolvable out of the box (proxy strategy, Section 4).
    single { AdBlockConfig() }
    single { AdBlockEngine(get(), get()) }
    // Backup player-type swap (Section 4.1) — the primary ad remover. Needs the
    // shared HttpClient + StreamResolver to mint alternate-player-type tokens.
    single { com.puretv.twitch.core.adblock.BackupStreamResolver(get(), get()) }

    // Chat
    factory { TwitchChatClient(get()) }
    single { com.puretv.twitch.core.chat.BadgeRepository(get()) }

    // Emotes
    single<com.puretv.twitch.core.emotes.EmoteCache> { InMemoryEmoteCache() }
    single { EmoteRepository(get(), get()) }
    // 7TV live emote updates (EventAPI websocket) — one per stream session, like the chat client.
    factory { com.puretv.twitch.core.emotes.SevenTvEventClient(get()) }

    // Repositories
    single { StreamRepository(get(), get()) }
    single { ChannelRepository(get()) }
    single { UserRepository(get()) }
    single { ChannelStatsRepository(get(), get(), get()) }
    single { com.puretv.twitch.core.stream.VodResolver(get(), get()) }
    single { com.puretv.twitch.core.repository.VodRepository(get(), get()) }

    // Token holder — wraps whatever encrypted store the platform uses
    // (EncryptedSharedPreferences on Android/TV, AES-encrypted file on Desktop).
    single { TokenHolder() }
}

/**
 * Bridges the platform's encrypted token store to [TwitchApiClient]'s
 * `tokenProvider` lambda without creating a circular Koin dependency.
 * Platform DI modules call `tokenHolder.update(token)` after OAuth/refresh.
 */
class TokenHolder {
    @Volatile private var token: String? = null
    fun current(): String? = token
    fun update(newToken: String?) { token = newToken }
}

/**
 * Builds the shared Ktor [HttpClient]. [engine] is the platform OkHttp engine
 * factory supplied via each app's `single { OkHttp.create { ... } }`.
 */
fun buildKtorClient(engine: io.ktor.client.engine.HttpClientEngine): HttpClient = HttpClient(engine) {
    install(ContentNegotiation) {
        json(Json { ignoreUnknownKeys = true; isLenient = true })
    }
    install(WebSockets)
    install(HttpTimeout) {
        requestTimeoutMillis = 15_000
        connectTimeoutMillis = 10_000
        // A stalled server that sends headers then trickles/stops the body would
        // otherwise hang a coroutine indefinitely between bytes (audit F9).
        socketTimeoutMillis = 15_000
    }
    // Audit F5: this single client carries the OAuth token endpoint
    // (client_secret/refresh_token), Bearer/OAuth Authorization headers, AND the
    // usher URL whose query string contains the SIGNED playback token+sig. Even
    // at INFO, Ktor logs the request line — leaking the usher token URL into any
    // captured log / bug report. For a privacy-first product, default to NONE;
    // a maintainer can temporarily raise this locally while debugging.
    install(Logging) { level = LogLevel.NONE }
    // Audit F3: normalize Helix 429s into RateLimitedException so
    // TwitchApiClient.withRateLimitRetry actually engages. Without this a 429
    // body deserializes to an empty HelixEnvelope (data defaults to []), so
    // rate-limited calls silently returned no results and never backed off.
    HttpResponseValidator {
        validateResponse { response ->
            if (response.status == HttpStatusCode.TooManyRequests) {
                throw RateLimitedException(response.headers["Ratelimit-Reset"]?.toLongOrNull())
            }
            // Audit H1: a non-2xx Helix error body ({"error","status","message"})
            // otherwise deserializes CLEANLY into an empty HelixEnvelope (data
            // defaults to [], ignoreUnknownKeys=true), so 401/403/500 silently
            // returned "no results" with NO exception — and, worse, truncated the
            // cursor-paginated loops in TwitchApiClient (getAllFollowedChannels,
            // getVideos) mid-stream as if the list were complete. Fail loudly so
            // partial pages are never mistaken for the full set.
            //
            // Scope: Helix ONLY (api.twitch.tv/helix). The OAuth flows on this same
            // shared client deliberately read their own non-2xx bodies — DeviceAuth
            // polling expects 400 "authorization_pending"; PkceAuth surfaces the raw
            // error body; refresh parses {status,message} envelopes — so they MUST
            // NOT be intercepted here. GQL/usher (stream resolution) likewise handle
            // their own error responses and are left untouched.
            val url = response.call.request.url
            val isHelix = url.host == "api.twitch.tv" && url.encodedPath.startsWith("/helix")
            if (isHelix && !response.status.isSuccess()) {
                throw HelixApiException(response.status.value, response.status.description)
            }
        }
    }
}
