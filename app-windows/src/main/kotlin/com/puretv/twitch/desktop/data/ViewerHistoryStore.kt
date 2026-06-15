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
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true; encodeDefaults = true }

    private val _histories = MutableStateFlow(loadFromDisk())
    val histories: StateFlow<Map<String, ChannelHistory>> = _histories.asStateFlow()

    init { runCatching { file.parentFile?.mkdirs() } }

    fun get(login: String): ChannelHistory? = _histories.value[login.lowercase()]

    /** Append a sample and persist; returns the updated history. */
    fun record(login: String, sample: ViewerSample): ChannelHistory {
        val key = login.lowercase()
        val updated = ViewerHistoryAggregator.record(_histories.value[key], login, sample)
        persist(_histories.value + (key to updated))
        return updated
    }

    private fun persist(map: Map<String, ChannelHistory>) {
        _histories.value = map
        runCatching {
            file.parentFile?.mkdirs()
            file.writeText(json.encodeToString(map))
        }
    }

    private fun loadFromDisk(): Map<String, ChannelHistory> = runCatching {
        if (file.exists()) json.decodeFromString<Map<String, ChannelHistory>>(file.readText()) else emptyMap()
    }.getOrElse { emptyMap() }

    companion object {
        private fun defaultDir(): File =
            File(System.getenv("APPDATA") ?: System.getProperty("user.home"), "PureTwitch")
    }
}
