package com.puretv.twitch.tv.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.puretv.twitch.core.di.TokenHolder
import com.puretv.twitch.core.model.AppSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first

private val Context.dataStore by preferencesDataStore(name = "puretv_tv_settings")

/**
 * SECTION 09.2 — TV counterpart of the phone app's `AppSettingsStore`.
 * Same split — ordinary prefs in DataStore, OAuth triad mirrored from
 * [SecureTokenStore] via [tokenState] — kept as its own class (rather than
 * shared) because `Context.dataStore` is a top-level per-package delegate and
 * Section 12.2 specifies app-tv shares no UI/platform-store code with
 * app-android, only `core`.
 */
class AppSettingsStore(
    private val context: Context,
    private val secureTokenStore: SecureTokenStore,
    private val tokenHolder: TokenHolder,
) {
    private object Keys {
        val PREFERRED_QUALITY = stringPreferencesKey("preferred_quality")
        val LOW_LATENCY = booleanPreferencesKey("low_latency_mode")
        val AD_BLOCK_ENABLED = booleanPreferencesKey("ad_block_enabled")
        val AD_BLOCK_STRATEGY = stringPreferencesKey("ad_block_strategy")
        val CUSTOM_PROXY_URL = stringPreferencesKey("custom_proxy_url")
        val CHAT_ENABLED = booleanPreferencesKey("chat_enabled")
        val CHAT_FONT_SIZE = floatPreferencesKey("chat_font_size")
        val SHOW_BADGES = booleanPreferencesKey("show_badges")
        val SHOW_BTTV = booleanPreferencesKey("show_bttv_emotes")
        val SHOW_7TV = booleanPreferencesKey("show_7tv_emotes")
        val SHOW_FFZ = booleanPreferencesKey("show_ffz_emotes")
        val CHAT_TIMESTAMPS = booleanPreferencesKey("chat_timestamps")
        val THEME = stringPreferencesKey("theme")
        val COMPACT_MODE = booleanPreferencesKey("compact_mode")
    }

    private data class TokenSnapshot(val accessToken: String, val username: String, val userId: String)

    private val tokenState = MutableStateFlow(loadTokenSnapshot())

    init {
        tokenHolder.update(secureTokenStore.accessToken())
    }

    private fun loadTokenSnapshot() = TokenSnapshot(
        accessToken = secureTokenStore.accessToken().orEmpty(),
        username = secureTokenStore.username().orEmpty(),
        userId = secureTokenStore.userId().orEmpty(),
    )

    val flow: Flow<AppSettings> = combine(context.dataStore.data, tokenState) { prefs, token ->
        AppSettings(
            preferredQuality = prefs[Keys.PREFERRED_QUALITY] ?: "auto",
            lowLatencyMode = prefs[Keys.LOW_LATENCY] ?: true,
            adBlockEnabled = prefs[Keys.AD_BLOCK_ENABLED] ?: true,
            adBlockStrategy = prefs[Keys.AD_BLOCK_STRATEGY] ?: "proxy",
            customProxyUrl = prefs[Keys.CUSTOM_PROXY_URL] ?: "",
            chatEnabled = prefs[Keys.CHAT_ENABLED] ?: true,
            chatFontSize = prefs[Keys.CHAT_FONT_SIZE] ?: 14f,
            showBadges = prefs[Keys.SHOW_BADGES] ?: true,
            showBttvEmotes = prefs[Keys.SHOW_BTTV] ?: true,
            show7tvEmotes = prefs[Keys.SHOW_7TV] ?: true,
            showFfzEmotes = prefs[Keys.SHOW_FFZ] ?: true,
            chatTimestamps = prefs[Keys.CHAT_TIMESTAMPS] ?: false,
            theme = prefs[Keys.THEME] ?: "dark",
            compactMode = prefs[Keys.COMPACT_MODE] ?: false,
            accessToken = token.accessToken,
            username = token.username,
            userId = token.userId,
        )
    }

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        val current = flow.first()
        val next = transform(current)

        context.dataStore.edit { prefs ->
            prefs[Keys.PREFERRED_QUALITY] = next.preferredQuality
            prefs[Keys.LOW_LATENCY] = next.lowLatencyMode
            prefs[Keys.AD_BLOCK_ENABLED] = next.adBlockEnabled
            prefs[Keys.AD_BLOCK_STRATEGY] = next.adBlockStrategy
            prefs[Keys.CUSTOM_PROXY_URL] = next.customProxyUrl
            prefs[Keys.CHAT_ENABLED] = next.chatEnabled
            prefs[Keys.CHAT_FONT_SIZE] = next.chatFontSize
            prefs[Keys.SHOW_BADGES] = next.showBadges
            prefs[Keys.SHOW_BTTV] = next.showBttvEmotes
            prefs[Keys.SHOW_7TV] = next.show7tvEmotes
            prefs[Keys.SHOW_FFZ] = next.showFfzEmotes
            prefs[Keys.CHAT_TIMESTAMPS] = next.chatTimestamps
            prefs[Keys.THEME] = next.theme
            prefs[Keys.COMPACT_MODE] = next.compactMode
        }
    }

    fun setSession(accessToken: String, refreshToken: String?, username: String = "", userId: String = "") {
        secureTokenStore.save(accessToken, refreshToken, username, userId)
        tokenHolder.update(accessToken)
        tokenState.value = TokenSnapshot(accessToken, username, userId)
    }

    fun clearSession() {
        secureTokenStore.clear()
        tokenHolder.update(null)
        tokenState.value = TokenSnapshot("", "", "")
    }

    fun currentRefreshToken(): String? = secureTokenStore.refreshToken()
}
