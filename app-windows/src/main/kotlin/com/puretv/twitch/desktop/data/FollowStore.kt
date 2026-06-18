package com.puretv.twitch.desktop.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * One saved channel in the local "Following" list. The display fields are
 * captured at follow-time (from the [com.puretv.twitch.core.model.ChannelInfo]
 * already on screen) so an *offline* channel can still be rendered — name +
 * avatar — without an extra API round-trip. Twitch's `getStreams` only returns
 * channels that are currently live.
 */
@Serializable
data class FollowedChannel(
    val id: String,
    val login: String,
    val displayName: String,
    val profileImageUrl: String = "",
)

/**
 * SECTION 08.5 — local "Following" list (the in-app library).
 *
 * Twitch removed app-initiated follow/unfollow from its Helix API in 2021–2022,
 * so this list is intentionally LOCAL: a curated set of channels the user keeps
 * on this device, independent of their real Twitch follow graph. Persisted as
 * plaintext JSON next to [DesktopSettingsStore]'s files (nothing sensitive).
 *
 * The data directory is injectable so tests can point at a temp dir; production
 * uses `%APPDATA%/PureTwitch/following.json`.
 */
class FollowStore(
    appDataDir: File = defaultDir(),
) {
    private val file = File(appDataDir, "following.json")
    // Compact JSON: these files are machine-only, and prettyPrint roughly doubles
    // bytes + serialize CPU on every write (loaders are whitespace-insensitive).
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val writer = AtomicJsonWriter(file)

    // Serializes the read-derive-write critical sections so concurrent
    // follow/unfollow from different coroutines (Dispatchers.Default) can't lose
    // an update or interleave writes (audit F2).
    private val lock = Any()

    private val _followed = MutableStateFlow(loadFromDisk())
    val followed: StateFlow<List<FollowedChannel>> = _followed.asStateFlow()

    init {
        runCatching { file.parentFile?.mkdirs() }
    }

    fun isFollowed(login: String): Boolean =
        _followed.value.any { it.login.equals(login, ignoreCase = true) }

    fun follow(channel: FollowedChannel) = synchronized(lock) {
        if (_followed.value.any { it.login.equals(channel.login, ignoreCase = true) }) return
        persistLocked(_followed.value + channel)
    }

    fun unfollow(login: String) = synchronized(lock) {
        persistLocked(_followed.value.filterNot { it.login.equals(login, ignoreCase = true) })
    }

    fun toggle(channel: FollowedChannel) = synchronized(lock) {
        if (_followed.value.any { it.login.equals(channel.login, ignoreCase = true) }) {
            persistLocked(_followed.value.filterNot { it.login.equals(channel.login, ignoreCase = true) })
        } else {
            persistLocked(_followed.value + channel)
        }
    }

    private fun persistLocked(list: List<FollowedChannel>) {
        // Update the in-memory list synchronously (cheap, UI-thread safe); serialize +
        // fsync happen off-thread so a follow/unfollow click never blocks the EDT.
        _followed.value = list
        writer.enqueue { json.encodeToString(list) }
    }

    /** Block until pending writes are durable. Tests + shutdown only — never the UI thread. */
    fun flush() = writer.flush()

    private fun loadFromDisk(): List<FollowedChannel> {
        if (!file.exists()) return emptyList()
        val text = runCatching { file.readText() }.getOrNull() ?: return emptyList()
        return runCatching { json.decodeFromString<List<FollowedChannel>>(text) }
            .getOrElse {
                // Preserve the unparseable bytes before falling back, so the next write
                // can't silently destroy recoverable data (audit F3).
                AtomicFile.quarantineCorrupt(file)
                emptyList()
            }
    }

    companion object {
        private fun defaultDir(): File =
            File(System.getenv("APPDATA") ?: System.getProperty("user.home"), "PureTwitch")
    }
}
