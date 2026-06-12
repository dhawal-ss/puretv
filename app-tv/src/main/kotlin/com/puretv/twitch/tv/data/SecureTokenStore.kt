package com.puretv.twitch.tv.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * SECTION 09.3 — TV counterpart of the phone app's `SecureTokenStore`.
 * Identical AES256-GCM `EncryptedSharedPreferences`/`MasterKey` scheme; uses
 * its own preferences file name (`puretv_tv_secure_tokens`) so the phone and
 * TV apps — separate `applicationId`s, but possibly the same Twitch account —
 * keep independent sessions when sideloaded onto the same device/profile.
 */
class SecureTokenStore(context: Context) {
    private val prefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        EncryptedSharedPreferences.create(
            context,
            "puretv_tv_secure_tokens",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    fun accessToken(): String? = prefs.getString(KEY_ACCESS, null)
    fun refreshToken(): String? = prefs.getString(KEY_REFRESH, null)
    fun username(): String? = prefs.getString(KEY_USERNAME, null)
    fun userId(): String? = prefs.getString(KEY_USER_ID, null)

    fun save(accessToken: String?, refreshToken: String?, username: String = "", userId: String = "") {
        prefs.edit()
            .putString(KEY_ACCESS, accessToken)
            .putString(KEY_REFRESH, refreshToken)
            .putString(KEY_USERNAME, username)
            .putString(KEY_USER_ID, userId)
            .apply()
    }

    fun clear() = prefs.edit().clear().apply()

    private companion object {
        const val KEY_ACCESS = "access_token"
        const val KEY_REFRESH = "refresh_token"
        const val KEY_USERNAME = "username"
        const val KEY_USER_ID = "user_id"
    }
}
