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
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true; encodeDefaults = true }

    private val _followed = MutableStateFlow(loadFromDisk())
    val followed: StateFlow<List<FollowedChannel>> = _followed.asStateFlow()

    init {
        runCatching { file.parentFile?.mkdirs() }
    }

    fun isFollowed(login: String): Boolean =
        _followed.value.any { it.login.equals(login, ignoreCase = true) }

    fun follow(channel: FollowedChannel) {
        if (isFollowed(channel.login)) return
        persist(_followed.value + channel)
    }

    fun unfollow(login: String) {
        persist(_followed.value.filterNot { it.login.equals(login, ignoreCase = true) })
    }

    fun toggle(channel: FollowedChannel) {
        if (isFollowed(channel.login)) unfollow(channel.login) else follow(channel)
    }

    private fun persist(list: List<FollowedChannel>) {
        _followed.value = list
        runCatching {
            file.parentFile?.mkdirs()
            file.writeText(json.encodeToString(list))
        }
    }

    private fun loadFromDisk(): List<FollowedChannel> = runCatching {
        if (file.exists()) json.decodeFromString<List<FollowedChannel>>(file.readText()) else emptyList()
    }.getOrElse { emptyList() }

    companion object {
        private fun defaultDir(): File =
            File(System.getenv("APPDATA") ?: System.getProperty("user.home"), "PureTwitch")
    }
}
