package com.puretv.twitch.desktop.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/** Saved playback position for one VOD. Metadata lets Home render a card
 *  without re-fetching. positionMs/durationMs mirror PlayerStatus. */
@Serializable
data class WatchProgress(
    val vodId: String,
    val positionMs: Long,
    val durationMs: Long,
    val updatedAt: Long,
    val title: String = "",
    val channelLogin: String = "",
    val thumbnailUrl: String = "",
)

/** Decides whether a saved progress entry is worth resuming. */
object ResumePolicy {
    private const val MIN_RESUME_MS = 5_000L
    private const val FINISHED_PERCENT = 95

    /** The position to resume from, or null if too early, finished, or unknown length. */
    fun resumePositionMs(p: WatchProgress): Long? {
        if (p.durationMs <= 0) return null
        if (p.positionMs < MIN_RESUME_MS) return null
        if (p.positionMs >= p.durationMs * FINISHED_PERCENT / 100) return null
        return p.positionMs
    }
}

/**
 * Local per-VOD playback positions (the "continue watching" backing store).
 * Plaintext JSON next to the other desktop stores; nothing sensitive. The data
 * dir is injectable so tests point at a temp dir — mirrors [FollowStore].
 */
class WatchProgressStore(appDataDir: File = defaultDir()) {
    private val file = File(appDataDir, "watch-progress.json")
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true; encodeDefaults = true }

    private val _progress = MutableStateFlow(loadFromDisk())
    val progress: StateFlow<Map<String, WatchProgress>> = _progress.asStateFlow()

    init { runCatching { file.parentFile?.mkdirs() } }

    fun get(vodId: String): WatchProgress? = _progress.value[vodId]

    fun save(p: WatchProgress) = persist(_progress.value + (p.vodId to p))

    fun remove(vodId: String) = persist(_progress.value - vodId)

    /** Resumable entries (per [ResumePolicy]), newest first. */
    fun continueWatching(): List<WatchProgress> =
        _progress.value.values
            .filter { ResumePolicy.resumePositionMs(it) != null }
            .sortedByDescending { it.updatedAt }

    private fun persist(map: Map<String, WatchProgress>) {
        _progress.value = map
        runCatching {
            file.parentFile?.mkdirs()
            file.writeText(json.encodeToString(map))
        }
    }

    private fun loadFromDisk(): Map<String, WatchProgress> = runCatching {
        if (file.exists()) json.decodeFromString<Map<String, WatchProgress>>(file.readText()) else emptyMap()
    }.getOrElse { emptyMap() }

    companion object {
        private fun defaultDir(): File =
            File(System.getenv("APPDATA") ?: System.getProperty("user.home"), "PureTwitch")
    }
}
