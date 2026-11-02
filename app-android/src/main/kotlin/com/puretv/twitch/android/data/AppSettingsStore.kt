package com.puretv.twitch.android.data

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
import com.puretv.twitch.core.session.SessionState
import com.puretv.twitch.core.session.deriveSessionState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

// A truncated or otherwise corrupt settings file (killed mid-write, bad
// restore) would make DataStore throw CorruptionException on first read,
// crashing every collector. The handler resets to empty defaults instead,
// which is safe here: these are all non-sensitive preferences and the user
// just gets factory values back.
private val Context.dataStore by preferencesDataStore(
    name = "puretv_settings",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
)

/**
 * SECTION 09.2 — DataStore-backed [AppSettings] persistence.
 *
 * [AppSettings] mixes ordinary preferences (quality, ad-block mode, theme…)
 * with the OAuth `accessToken`/`username`/`userId` triad. The former live in
 * Jetpack DataStore; the latter are sensitive and are mirrored from
 * [SecureTokenStore] (EncryptedSharedPreferences) through [tokenState] so a
 * single [flow] can still expose one coherent [AppSettings] snapshot to the UI.
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
        val ANIMATE_EMOTES = booleanPreferencesKey("animate_emotes")
        val CHAT_TIMESTAMPS = booleanPreferencesKey("chat_timestamps")
        val CHAT_FRACTION = floatPreferencesKey("chat_fraction")
        val THEME = stringPreferencesKey("theme")
        val SHAPE_INTENSITY = stringPreferencesKey("shape_intensity")
        val COMPACT_MODE = booleanPreferencesKey("compact_mode")
        val BACKGROUND_PLAY = booleanPreferencesKey("background_play")
        val PIP_ON_LEAVE = booleanPreferencesKey("pip_on_leave")
        val AUDIO_ONLY_IN_BACKGROUND = booleanPreferencesKey("audio_only_in_background")

        // Go-live alerts.
        val LIVE_NOTIFICATIONS = booleanPreferencesKey("live_notifications")
        val NOTIFY_MUTED_LOGINS = stringSetPreferencesKey("notify_muted_logins")

        // Audio normalizer (Wave 4).
        val AUDIO_NORMALIZER = booleanPreferencesKey("audio_normalizer")
        val AUDIO_LEVELLING = stringPreferencesKey("audio_levelling")

        // Browse order (issue #41).
        val BROWSE_SORT = stringPreferencesKey("browse_sort")

        // Per-channel go-live alerts: the channels the viewer turned the bell on for.
        val NOTIFY_CHANNELS = stringSetPreferencesKey("notify_channels")
        val PER_CHANNEL_NOTE_DISMISSED = booleanPreferencesKey("per_channel_note_dismissed")
    }

    /** Mirrors [SecureTokenStore]; updated explicitly by [setSession]/[clearSession]. */
    private data class TokenSnapshot(val accessToken: String, val username: String, val userId: String)

    private val tokenState = MutableStateFlow(loadTokenSnapshot())

    init {
        // Prime the shared `core` TokenHolder from whatever was persisted last session
        // so TwitchApiClient/TwitchGqlClient can authenticate immediately on cold start.
        tokenHolder.update(secureTokenStore.accessToken())
    }

    private fun loadTokenSnapshot() = TokenSnapshot(
        accessToken = secureTokenStore.accessToken().orEmpty(),
        username = secureTokenStore.username().orEmpty(),
        userId = secureTokenStore.userId().orEmpty(),
    )

    /**
     * The session derivable SYNCHRONOUSLY at construction, from the token side
     * only. [deriveSessionState] reads only the access token / identity, all of
     * which [tokenState] already holds, so the auth gate can start from the REAL
     * session instead of flashing the logged-out Welcome screen while the async
     * DataStore performs its first disk read. (The non-token prefs that DataStore
     * supplies do not influence the session.)
     */
    val initialSession: SessionState = with(tokenState.value) {
        deriveSessionState(AppSettings(accessToken = accessToken, username = username, userId = userId))
    }

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
            animateEmotes = prefs[Keys.ANIMATE_EMOTES] ?: AppSettings().animateEmotes,
            chatTimestamps = prefs[Keys.CHAT_TIMESTAMPS] ?: false,
            chatFraction = prefs[Keys.CHAT_FRACTION] ?: 0.5f,
            theme = prefs[Keys.THEME] ?: "dark",
            shapeIntensity = prefs[Keys.SHAPE_INTENSITY] ?: AppSettings().shapeIntensity,
            compactMode = prefs[Keys.COMPACT_MODE] ?: false,
            backgroundPlay = prefs[Keys.BACKGROUND_PLAY] ?: AppSettings().backgroundPlay,
            pipOnLeave = prefs[Keys.PIP_ON_LEAVE] ?: AppSettings().pipOnLeave,
            audioOnlyInBackground = prefs[Keys.AUDIO_ONLY_IN_BACKGROUND] ?: AppSettings().audioOnlyInBackground,
            liveNotifications = prefs[Keys.LIVE_NOTIFICATIONS] ?: AppSettings().liveNotifications,
            notifyMutedLogins = prefs[Keys.NOTIFY_MUTED_LOGINS] ?: emptySet(),
            accessToken = token.accessToken,
            username = token.username,
            userId = token.userId,
            // Audio normalizer (Wave 4).
            audioNormalizer = prefs[Keys.AUDIO_NORMALIZER] ?: AppSettings().audioNormalizer,
            audioLevelling = AudioLevelling.parse(prefs[Keys.AUDIO_LEVELLING]),
            browseSort = CategorySort.parse(prefs[Keys.BROWSE_SORT]),
            // Per-channel go-live alerts.
            notifyChannels = prefs[Keys.NOTIFY_CHANNELS] ?: emptySet(),
            perChannelNoteDismissed = prefs[Keys.PER_CHANNEL_NOTE_DISMISSED] ?: false,
        )
    }
        // The corruption handler on the delegate covers a file DataStore can
        // parse but not trust. It does not cover the file being unreadable
        // outright, and this flow is collected from the Activity, where a thrown
        // read takes it down. Settings are not worth that: fall back to factory
        // values and keep the session, which is held in memory and still good.
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

    /** Applies a transform to the *non-token* preference fields only. */
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
            prefs[Keys.ANIMATE_EMOTES] = next.animateEmotes
            prefs[Keys.CHAT_TIMESTAMPS] = next.chatTimestamps
            prefs[Keys.CHAT_FRACTION] = next.chatFraction
            prefs[Keys.THEME] = next.theme
            prefs[Keys.SHAPE_INTENSITY] = next.shapeIntensity
            prefs[Keys.COMPACT_MODE] = next.compactMode
            prefs[Keys.BACKGROUND_PLAY] = next.backgroundPlay
            prefs[Keys.PIP_ON_LEAVE] = next.pipOnLeave
            prefs[Keys.AUDIO_ONLY_IN_BACKGROUND] = next.audioOnlyInBackground
            prefs[Keys.LIVE_NOTIFICATIONS] = next.liveNotifications
            prefs[Keys.NOTIFY_MUTED_LOGINS] = next.notifyMutedLogins.mapTo(HashSet()) { it.lowercase() }
            // Audio normalizer (Wave 4).
            prefs[Keys.AUDIO_NORMALIZER] = next.audioNormalizer
            prefs[Keys.AUDIO_LEVELLING] = next.audioLevelling.key
            prefs[Keys.BROWSE_SORT] = next.browseSort.key
            // Per-channel go-live alerts.
            prefs[Keys.NOTIFY_CHANNELS] = next.notifyChannels.mapTo(HashSet()) { it.lowercase() }
            prefs[Keys.PER_CHANNEL_NOTE_DISMISSED] = next.perChannelNoteDismissed
        }
    }

    /**
     * Persists a fresh OAuth session (post token-exchange) to encrypted storage.
     * [expiresAtEpochSeconds] null keeps the stored expiry, for writes that only
     * fill in the identity.
     */
    fun setSession(
        accessToken: String,
        refreshToken: String?,
        username: String = "",
        userId: String = "",
        expiresAtEpochSeconds: Long? = null,
    ) {
        val expiry = expiresAtEpochSeconds ?: secureTokenStore.expiresAtEpochSeconds()
        secureTokenStore.save(accessToken, refreshToken, username, userId, expiry)
        tokenHolder.update(accessToken)
        tokenState.value = TokenSnapshot(accessToken, username, userId)
        _signedOutByTwitch.value = false
    }

    /**
     * Signs out. [revokedByTwitch] marks a sign-out the viewer did not ask for
     * (Twitch rejected the saved sign-in, for example after a password change or
     * "disconnect" on twitch.tv), so the Welcome screen can say why instead of
     * the app just looking logged out for no reason.
     */
    fun clearSession(revokedByTwitch: Boolean = false) {
        secureTokenStore.clear()
        tokenHolder.update(null)
        tokenState.value = TokenSnapshot("", "", "")
        _signedOutByTwitch.value = revokedByTwitch
    }

    private val _signedOutByTwitch = MutableStateFlow(false)
    val signedOutByTwitch: StateFlow<Boolean> = _signedOutByTwitch.asStateFlow()

    fun currentRefreshToken(): String? = secureTokenStore.refreshToken()

    fun tokenExpiresAtEpochSeconds(): Long = secureTokenStore.expiresAtEpochSeconds()

    private companion object {
        const val TAG = "AppSettingsStore"
    }
}
