package com.puretv.twitch.desktop.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Local per-channel viewer history (the "audience over time" backing store).
 * Plaintext JSON next to the other desktop stores; nothing sensitive. The data
 * dir is injectable so tests point at a temp dir — mirrors WatchProgressStore.
 */
class ViewerHistoryStore(appDataDir: File = defaultDir()) {
    private val file = File(appDataDir, "viewer-history.json")
    // Compact JSON: machine-only file. prettyPrint was the worst offender here — the
    // whole 200-channel x 500-sample map was pretty-printed and fsync'd every ~45s.
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val writer = AtomicJsonWriter(file)

    // Serializes concurrent sampling of multiple channels into the one file
    // (audit F2): without it two records read the same snapshot and the second
    // write clobbers the first channel's update.
    private val lock = Any()

    private val _histories = MutableStateFlow(loadFromDisk())
    val histories: StateFlow<Map<String, ChannelHistory>> = _histories.asStateFlow()

    init { runCatching { file.parentFile?.mkdirs() } }

    fun get(login: String): ChannelHistory? = _histories.value[login.lowercase()]

    /** Append a sample and persist; returns the updated history. */
    fun record(login: String, sample: ViewerSample): ChannelHistory = synchronized(lock) {
        val key = login.lowercase()
        val updated = ViewerHistoryAggregator.record(_histories.value[key], login, sample)
        persistLocked(capped(_histories.value + (key to updated)))
        updated
    }

    /**
     * Bound the channel map (audit F2 follow-up): each channel holds up to 500
     * samples and the WHOLE map is re-serialized on every ~45s sample and loaded
     * at startup. Unlike [WatchProgressStore] this had no size cap, so it grew one
     * entry per channel ever opened, forever. Keep the [MAX_CHANNELS]
     * most-recently-sampled channels (the one just recorded is always newest, so
     * never evicted).
     */
    private fun capped(map: Map<String, ChannelHistory>): Map<String, ChannelHistory> =
        if (map.size <= MAX_CHANNELS) {
            map
        } else {
            map.entries.sortedByDescending { it.value.lastSampleEpochSec }
                .take(MAX_CHANNELS)
                .associate { it.key to it.value }
        }

    private fun persistLocked(map: Map<String, ChannelHistory>) {
        // In-memory update is synchronous; the (large) serialize + fsync run off-thread
        // and are conflated, so the 45s sampler no longer blocks the CPU pool on IO.
        _histories.value = map
        writer.enqueue { json.encodeToString(map) }
    }

    /** Block until pending writes are durable. Tests + shutdown only — never the UI thread. */
    fun flush() = writer.flush()

    private fun loadFromDisk(): Map<String, ChannelHistory> {
        if (!file.exists()) return emptyMap()
        val text = runCatching { file.readText() }.getOrNull() ?: return emptyMap()
        return runCatching { json.decodeFromString<Map<String, ChannelHistory>>(text) }
            .getOrElse {
                // Preserve the unparseable bytes before falling back (audit F3).
                AtomicFile.quarantineCorrupt(file)
                emptyMap()
            }
    }

    companion object {
        private const val MAX_CHANNELS = 200

        private fun defaultDir(): File =
            File(System.getenv("APPDATA") ?: System.getProperty("user.home"), "PureTwitch")
    }
}
