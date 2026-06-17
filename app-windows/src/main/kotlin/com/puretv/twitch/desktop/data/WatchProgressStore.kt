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

    // Serializes the read-derive-write sections (the 10s autosave loop, the
    // onCleared save, and explicit removes can otherwise interleave — audit F2).
    private val lock = Any()

    private val _progress = MutableStateFlow(loadFromDisk())
    val progress: StateFlow<Map<String, WatchProgress>> = _progress.asStateFlow()

    init { runCatching { file.parentFile?.mkdirs() } }

    fun get(vodId: String): WatchProgress? = _progress.value[vodId]

    fun save(p: WatchProgress) = synchronized(lock) { persistLocked(capped(_progress.value + (p.vodId to p))) }

    fun remove(vodId: String) = synchronized(lock) { persistLocked(_progress.value - vodId) }

    /** Resumable entries (per [ResumePolicy]), newest first. */
    fun continueWatching(): List<WatchProgress> =
        _progress.value.values
            .filter { ResumePolicy.resumePositionMs(it) != null }
            .sortedByDescending { it.updatedAt }

    /**
     * Bound the store (audit F3): an unbounded map is loaded into memory on
     * startup and rewritten in full on every 10s autosave, so cost grows with
     * watch history forever. Keep only the [MAX_ENTRIES] most-recently-updated
     * VODs — far more than any "continue watching" surface shows.
     */
    private fun capped(map: Map<String, WatchProgress>): Map<String, WatchProgress> =
        if (map.size <= MAX_ENTRIES) {
            map
        } else {
            map.entries.sortedByDescending { it.value.updatedAt }
                .take(MAX_ENTRIES)
                .associate { it.key to it.value }
        }

    private fun persistLocked(map: Map<String, WatchProgress>) {
        _progress.value = map
        runCatching { AtomicFile.writeTextAtomically(file, json.encodeToString(map)) }
            .onFailure { System.err.println("WatchProgressStore: failed to persist watch progress: ${it.message}") }
    }

    private fun loadFromDisk(): Map<String, WatchProgress> = runCatching {
        if (file.exists()) json.decodeFromString<Map<String, WatchProgress>>(file.readText()) else emptyMap()
    }.getOrElse { emptyMap() }

    companion object {
        private const val MAX_ENTRIES = 300

        private fun defaultDir(): File =
            File(System.getenv("APPDATA") ?: System.getProperty("user.home"), "PureTwitch")
    }
}
