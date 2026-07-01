package com.puretv.twitch.android.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.io.IOException
import java.security.GeneralSecurityException

/**
 * SECTION 09.3 — OAuth session data is sensitive and must never land in
 * plain DataStore or Room. [EncryptedSharedPreferences] wraps a
 * hardware-backed [MasterKey] (AES256-GCM) per the spec's security notes.
 *
 * RECOVER-ON-CORRUPTION: an Android Keystore keyset can be invalidated out
 * from under us (OS upgrade, biometric/lock-screen reset, backup restore onto
 * a different device, partial keystore wipe). When that happens both
 * [MasterKey.Builder.build] and [EncryptedSharedPreferences.create] throw
 * [GeneralSecurityException]/[IOException]. With no recovery that is a
 * permanent crash loop with no escape, since the store is forced eagerly at
 * DI construction. We catch once, delete the unreadable backing prefs plus the
 * orphaned keyset, and rebuild fresh. The cost is that the prior session is
 * lost, so the user simply signs in again (acceptable and fully local, no
 * servers involved).
 */
class SecureTokenStore(context: Context) {
    private val prefs: SharedPreferences by lazy { openOrRecover(context) }

    /**
     * Builds the encrypted prefs. On a corrupt/invalidated keyset this throws,
     * so we recover exactly once (delete the backing file + keyset, rebuild)
     * to avoid infinite recursion: a second failure is genuinely unrecoverable
     * and is allowed to propagate.
     */
    private fun openOrRecover(context: Context): SharedPreferences = try {
        createEncryptedPrefs(context)
    } catch (e: GeneralSecurityException) {
        recoverAndRecreate(context, e)
    } catch (e: IOException) {
        recoverAndRecreate(context, e)
    }

    private fun createEncryptedPrefs(context: Context): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        return EncryptedSharedPreferences.create(
            context,
            PREFS_FILE_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    /**
     * Clears the unreadable encrypted prefs and the androidx-security keyset
     * that can no longer decrypt them, then rebuilds once. If the rebuild also
     * fails we rethrow: that is a device-level keystore fault we cannot mask.
     */
    private fun recoverAndRecreate(context: Context, cause: Exception): SharedPreferences {
        // (a) drop the encrypted prefs file whose contents we can no longer read.
        context.deleteSharedPreferences(PREFS_FILE_NAME)
        // (b) drop the orphaned keyset so create() mints a fresh one.
        context.getSharedPreferences(KEYSET_PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        // (c) rebuild fresh (empty: the user re-authenticates, which is fine).
        return createEncryptedPrefs(context)
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
        const val PREFS_FILE_NAME = "puretv_secure_tokens"

        // Default keyset prefs name used by androidx.security.crypto when no
        // explicit keyset name is supplied to EncryptedSharedPreferences.create.
        // Holds the AES key that decrypts PREFS_FILE_NAME; orphaned if the
        // Keystore master key is invalidated.
        const val KEYSET_PREFS_NAME = "__androidx_security_crypto_encrypted_prefs_key_keyset__"

        const val KEY_ACCESS = "access_token"
        const val KEY_REFRESH = "refresh_token"
        const val KEY_USERNAME = "username"
        const val KEY_USER_ID = "user_id"
    }
}
