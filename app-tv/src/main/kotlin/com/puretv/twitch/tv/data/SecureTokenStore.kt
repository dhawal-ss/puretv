package com.puretv.twitch.tv.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.KeyStore
import java.util.Collections

/**
 * SECTION 09.3 — TV counterpart of the phone app's `SecureTokenStore`.
 * Identical AES256-GCM `EncryptedSharedPreferences`/`MasterKey` scheme; uses
 * its own preferences file name (`puretv_tv_secure_tokens`) so the phone and
 * TV apps, separate `applicationId`s but possibly the same Twitch account,
 * keep independent sessions when sideloaded onto the same device/profile.
 *
 * RECOVER-ON-CORRUPTION: an Android Keystore keyset can be invalidated out
 * from under us (OS upgrade, backup restore onto a different device, a partial
 * keystore wipe, a process killed mid-write while the app updates itself).
 * When that happens both [MasterKey.Builder.build] and
 * [EncryptedSharedPreferences.create] throw, checked and unchecked
 * alike. This store is forced open eagerly
 * at DI construction, so with no recovery that is a permanent crash loop with
 * no escape: the app dies before it draws, and reinstalling over the top does
 * not help, because the unreadable file lives in app data and survives. On a
 * television, where "clear app data" is several menus deep and the app is
 * sideloaded, that is indistinguishable from a dead app. The phone app has
 * carried this recovery since the first release; the TV app had not, which is
 * what left updaters with an app that would no longer start.
 *
 * Recovery runs at most once, then degrades rather than throwing:
 *
 *  1. delete the unreadable prefs file (both Tink keysets live inside it),
 *  2. delete the orphaned Keystore master key so a fresh one is minted,
 *  3. rebuild empty, and if even that fails, keep the session in memory only.
 *
 * The cost of every branch is the same and it is small: the previous session is
 * gone and the viewer signs in again. Nothing leaves the device either way.
 */
class SecureTokenStore(context: Context) {

    private val appContext = context.applicationContext

    /** Null only when the device's keystore is broken past recovery. */
    private val prefs: SharedPreferences? by lazy { openOrRecover(appContext) }

    /**
     * Last-resort store for the case above. Process-lifetime only, so the
     * viewer signs in once per launch rather than not being able to launch at
     * all. Tokens held here never touch disk. Synchronised because it stands in
     * for SharedPreferences, which callers already treat as thread-safe, and a
     * ConcurrentHashMap cannot hold the null a signed-out token is.
     */
    private val memory: MutableMap<String, String?> = Collections.synchronizedMap(HashMap())

    // Deliberately every Exception, not just the documented
    // GeneralSecurityException/IOException pair. OEM keystore and Tink paths
    // also throw ProviderException and IllegalStateException, and those are
    // unchecked: a narrower catch would let exactly the crash this exists to
    // prevent walk straight past it. Nothing here is worth failing to launch.
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
     * Clears the unreadable encrypted prefs and the master key that can no
     * longer decrypt them, then rebuilds once. A second failure means a
     * device-level keystore fault, and there the app runs on [memory] rather
     * than refusing to start.
     */
    private fun recoverAndRecreate(context: Context, cause: Exception): SharedPreferences? {
        Log.w(TAG, "Encrypted token store unreadable, rebuilding it", cause)
        return runCatching {
            // (a) The prefs file holds the ciphertext AND both Tink keysets, so
            //     dropping it takes the orphaned keysets with it.
            context.deleteSharedPreferences(PREFS_FILE_NAME)
            // (b) Older androidx.security builds kept the key keyset in a file
            //     of its own. Harmless when it was never there.
            context.getSharedPreferences(KEYSET_PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .clear()
                .commit()
            // (c) Drop the Keystore entry itself. This is the step that matters
            //     when the master key, rather than the file, is what went bad:
            //     without it the rebuild below throws exactly as the first
            //     attempt did, and the app is bricked all over again.
            runCatching {
                KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }.deleteEntry(MASTER_KEY_ALIAS)
            }
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

    /**
     * Branches on which store exists, exactly as [save] does. Writing this as
     * `prefs?.getString(...) ?: memory[key]` would read the wrong thing: elvis
     * fires on a null *result*, so a signed-out viewer with a perfectly healthy
     * encrypted store would fall through to [memory], and the two would stop
     * being alternatives to each other.
     */
    private fun read(key: String): String? {
        val store = prefs ?: return memory[key]
        return store.getString(key, null)
    }

    private companion object {
        const val TAG = "SecureTokenStore"
        const val PREFS_FILE_NAME = "puretv_tv_secure_tokens"

        // Default keyset prefs name used by androidx.security.crypto when no
        // explicit keyset name is supplied to EncryptedSharedPreferences.create.
        const val KEYSET_PREFS_NAME = "__androidx_security_crypto_encrypted_prefs_key_keyset__"

        // The Keystore alias MasterKey.Builder mints by default.
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val MASTER_KEY_ALIAS = MasterKey.DEFAULT_MASTER_KEY_ALIAS

        const val KEY_ACCESS = "access_token"
        const val KEY_REFRESH = "refresh_token"
        const val KEY_USERNAME = "username"
        const val KEY_USER_ID = "user_id"
    }
}
