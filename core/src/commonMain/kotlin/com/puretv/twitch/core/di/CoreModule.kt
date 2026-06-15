package com.puretv.twitch.core.di

import com.puretv.twitch.core.adblock.AdBlockConfig
import com.puretv.twitch.core.adblock.AdBlockEngine
import com.puretv.twitch.core.api.TwitchApiClient
import com.puretv.twitch.core.chat.TwitchChatClient
import com.puretv.twitch.core.emotes.EmoteRepository
import com.puretv.twitch.core.emotes.InMemoryEmoteCache
import com.puretv.twitch.core.repository.ChannelRepository
import com.puretv.twitch.core.repository.StreamRepository
import com.puretv.twitch.core.repository.UserRepository
import com.puretv.twitch.core.stream.GqlHashProvider
import com.puretv.twitch.core.stream.StreamResolver
import com.puretv.twitch.core.stream.TwitchGqlClient
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.plugins.websocket.WebSockets
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
    single { GqlHashProvider() }
    single { TwitchGqlClient(get(), get()) }
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

    // Emotes
    single<com.puretv.twitch.core.emotes.EmoteCache> { InMemoryEmoteCache() }
    single { EmoteRepository(get(), get()) }

    // Repositories
    single { StreamRepository(get(), get()) }
    single { ChannelRepository(get()) }
    single { UserRepository(get()) }
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
    }
    install(Logging) { level = LogLevel.INFO }
}
