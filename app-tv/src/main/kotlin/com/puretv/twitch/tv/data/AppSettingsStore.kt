package com.puretv.twitch.tv.data

import android.content.Context
import android.util.Log
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.puretv.twitch.core.di.TokenHolder
import com.puretv.twitch.core.audio.AudioLevelling
import com.puretv.twitch.core.model.AppSettings
import com.puretv.twitch.core.model.CategorySort
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

// A half-written preferences file (a process killed mid-write, and the in-app
// updater kills this one by definition) otherwise makes DataStore throw on
// every read for the rest of the install's life. That read happens during
// startup, so the app stops launching and stays that way, since reinstalling
// over the top leaves app data alone. Falling back to defaults is safe here:
// none of this is sensitive, and the viewer just gets factory settings back.
// Mirrors the phone app's delegate.
private val Context.dataStore by preferencesDataStore(
    name = "puretv_tv_settings",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
)

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
        // "low_latency_mode" was written as true while the setting did nothing and
        // had no switch. Low latency is opt-in on TV (Fire TV Wi-Fi rebuffers on a
        // thin buffer), so the working setting starts fresh under its own key.
        val LOW_LATENCY = booleanPreferencesKey("low_latency_live")
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
        // Go-live alerts.
        val LIVE_NOTIFICATIONS = booleanPreferencesKey("live_notifications")
        val NOTIFY_MUTED_LOGINS = stringSetPreferencesKey("notify_muted_logins")

        // TV chat placement
        val TV_CHAT_MODE = stringPreferencesKey("tv_chat_mode")
        val TV_CHAT_OPACITY = floatPreferencesKey("tv_chat_opacity")

        // Audio normalizer (Wave 4).
        val AUDIO_NORMALIZER = booleanPreferencesKey("audio_normalizer")
        val AUDIO_LEVELLING = stringPreferencesKey("audio_levelling")

        // Browse order (issue #41).
        val BROWSE_SORT = stringPreferencesKey("browse_sort")

        // Remote control (Wave 4).
        val TV_REMOTE_FLIP = booleanPreferencesKey("tv_remote_flip")

        // Per-channel go-live alerts: the channels the viewer turned the bell on for.
        val NOTIFY_CHANNELS = stringSetPreferencesKey("notify_channels")
        val PER_CHANNEL_NOTE_DISMISSED = booleanPreferencesKey("per_channel_note_dismissed")
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
            lowLatencyMode = prefs[Keys.LOW_LATENCY] ?: false,
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
            liveNotifications = prefs[Keys.LIVE_NOTIFICATIONS] ?: AppSettings().liveNotifications,
            notifyMutedLogins = prefs[Keys.NOTIFY_MUTED_LOGINS] ?: emptySet(),
            accessToken = token.accessToken,
            username = token.username,
            userId = token.userId,
            // TV chat placement
            tvChatMode = prefs[Keys.TV_CHAT_MODE] ?: AppSettings().tvChatMode,
            tvChatOpacity = prefs[Keys.TV_CHAT_OPACITY] ?: AppSettings().tvChatOpacity,
            // Audio normalizer (Wave 4).
            audioNormalizer = prefs[Keys.AUDIO_NORMALIZER] ?: AppSettings().audioNormalizer,
            audioLevelling = AudioLevelling.parse(prefs[Keys.AUDIO_LEVELLING]),
            browseSort = CategorySort.parse(prefs[Keys.BROWSE_SORT]),
            // Remote control (Wave 4).
            tvRemoteFlip = prefs[Keys.TV_REMOTE_FLIP] ?: AppSettings().tvRemoteFlip,
            // Per-channel go-live alerts.
            notifyChannels = prefs[Keys.NOTIFY_CHANNELS] ?: emptySet(),
            perChannelNoteDismissed = prefs[Keys.PER_CHANNEL_NOTE_DISMISSED] ?: false,
        )
    }
        // The corruption handler above covers a file DataStore can parse but not
        // trust. It does not cover the file being unreadable outright, and this
        // flow is collected from composition, where a thrown read takes the
        // Activity with it. Settings are not worth that: fall back to factory
        // values, keeping the session, which lives in memory and is still good.
        .catch { e ->
            Log.w(TAG, "Settings unreadable, falling back to defaults", e)
            val token = tokenState.value
            emit(
                AppSettings(
                    accessToken = token.accessToken,
                    username = token.username,
                    userId = token.userId,
                ),
            )
        }

    // One update at a time: each reads the current settings, changes them and writes every
    // key back, so two overlapping updates (rapid Menu presses on Following) would otherwise
    // both start from the same snapshot and the later write would undo the earlier one.
    private val updateLock = Mutex()

    suspend fun update(transform: (AppSettings) -> AppSettings): Unit = updateLock.withLock {
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
            prefs[Keys.LIVE_NOTIFICATIONS] = next.liveNotifications
            prefs[Keys.NOTIFY_MUTED_LOGINS] = next.notifyMutedLogins.mapTo(HashSet()) { it.lowercase() }
            // TV chat placement
            prefs[Keys.TV_CHAT_MODE] = next.tvChatMode
            prefs[Keys.TV_CHAT_OPACITY] = next.tvChatOpacity
            // Audio normalizer (Wave 4).
            prefs[Keys.AUDIO_NORMALIZER] = next.audioNormalizer
            prefs[Keys.AUDIO_LEVELLING] = next.audioLevelling.key
            prefs[Keys.BROWSE_SORT] = next.browseSort.key
            // Remote control (Wave 4).
            prefs[Keys.TV_REMOTE_FLIP] = next.tvRemoteFlip
            // Per-channel go-live alerts.
            prefs[Keys.NOTIFY_CHANNELS] = next.notifyChannels.mapTo(HashSet()) { it.lowercase() }
            prefs[Keys.PER_CHANNEL_NOTE_DISMISSED] = next.perChannelNoteDismissed
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

    private companion object {
        const val TAG = "AppSettingsStore"
    }
}
