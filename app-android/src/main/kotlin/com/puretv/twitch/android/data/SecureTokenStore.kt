package com.puretv.twitch.android.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import android.util.Log
import androidx.security.crypto.MasterKey
import java.security.KeyStore
import java.util.Collections

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
 * DI construction. We catch once, delete the unreadable backing prefs, the
 * orphaned keyset and the master key itself, and rebuild fresh. If even that
 * fails the session is held in memory for the process instead, because a
 * signed-out app the viewer can use beats an app that refuses to launch. The
 * cost of every branch is the same and small: the prior session is lost and the
 * user signs in again (fully local, no servers involved).
 */
class SecureTokenStore(context: Context) {
    private val appContext = context.applicationContext

    /** Null only when the device's keystore is broken past recovery. */
    private val prefs: SharedPreferences? by lazy { openOrRecover(appContext) }

    /**
     * Last-resort store for the case above. Process-lifetime only, so the viewer
     * signs in once per launch rather than not being able to launch at all.
     * Tokens held here never touch disk. Synchronised because it stands in for
     * SharedPreferences, which callers already treat as thread-safe, and a
     * ConcurrentHashMap cannot hold the null a signed-out token is.
     */
    private val memory: MutableMap<String, String?> = Collections.synchronizedMap(HashMap())

    /**
     * Builds the encrypted prefs. On a corrupt/invalidated keyset this throws,
     * so we recover exactly once (delete the backing file, keyset and master
     * key, then rebuild) to avoid infinite recursion.
     *
     * Deliberately every Exception, not just the GeneralSecurityException and
     * IOException the API documents. OEM keystore and Tink paths also throw
     * ProviderException and IllegalStateException, and those are unchecked: a
     * narrower catch would let exactly the crash this exists to prevent walk
     * straight past it. Nothing here is worth failing to launch.
     */
    private fun openOrRecover(context: Context): SharedPreferences? = try {
        createEncryptedPrefs(context)
    } catch (e: Exception) {
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
     * Clears the unreadable encrypted prefs and the key material that can no
     * longer decrypt them, then rebuilds once. A second failure means a
     * device-level keystore fault, and there the app runs on [memory] rather
     * than refusing to start.
     */
    private fun recoverAndRecreate(context: Context, cause: Exception): SharedPreferences? {
        Log.w(TAG, "Encrypted token store unreadable, rebuilding it", cause)
        return runCatching {
            // (a) drop the encrypted prefs file whose contents we can no longer
            //     read. It holds both Tink keysets, so they go with it.
            context.deleteSharedPreferences(PREFS_FILE_NAME)
            // (b) older androidx.security builds kept the key keyset in a file of
            //     its own. Harmless when it was never there.
            context.getSharedPreferences(KEYSET_PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .clear()
                .commit()
            // (c) drop the Keystore entry itself. This is the step that matters
            //     when the master key, rather than the file, is what went bad:
            //     without it the rebuild below throws exactly as the first
            //     attempt did, and the app is bricked all over again.
            runCatching {
                KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }.deleteEntry(MASTER_KEY_ALIAS)
            }
            // (d) rebuild fresh (empty: the user re-authenticates, which is fine).
            createEncryptedPrefs(context)
        }.getOrElse { second ->
            Log.e(TAG, "Keystore unusable on this device, session will not persist", second)
            null
        }
    }

    fun accessToken(): String? = read(KEY_ACCESS)
    fun refreshToken(): String? = read(KEY_REFRESH)
    fun username(): String? = read(KEY_USERNAME)
    fun userId(): String? = read(KEY_USER_ID)

    fun save(accessToken: String?, refreshToken: String?, username: String = "", userId: String = "") {
        val store = prefs
        if (store == null) {
            memory[KEY_ACCESS] = accessToken
            memory[KEY_REFRESH] = refreshToken
            memory[KEY_USERNAME] = username
            memory[KEY_USER_ID] = userId
            return
        }
        store.edit()
            .putString(KEY_ACCESS, accessToken)
            .putString(KEY_REFRESH, refreshToken)
            .putString(KEY_USERNAME, username)
            .putString(KEY_USER_ID, userId)
            .apply()
    }

    fun clear() {
        memory.clear()
        prefs?.edit()?.clear()?.apply()
    }

    private fun read(key: String): String? = prefs?.getString(key, null) ?: memory[key]

    private companion object {
        const val TAG = "SecureTokenStore"

        // The Keystore alias MasterKey.Builder mints by default.
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val MASTER_KEY_ALIAS = MasterKey.DEFAULT_MASTER_KEY_ALIAS

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
