package com.puretv.twitch.desktop.platform

import com.puretv.twitch.core.perf.TimingLog
import java.util.concurrent.TimeUnit

/** One display adapter as Windows lists it. */
data class GpuAdapter(val name: String, val nvidia: Boolean, val rtx: Boolean)

/**
 * Whether this PC has an NVIDIA RTX card, so the "NVIDIA RTX Video" settings are only
 * shown to people who can use them. Having the card only decides whether the settings
 * appear; whether RTX Video actually runs is judged from VLC's log.
 *
 * The adapters come from the display adapter class key in the registry, read with
 * `reg query` (no admin rights needed). Each adapter's subkey holds its name
 * (`DriverDesc`) and hardware id (`MatchingDeviceId`, which carries the PCI vendor).
 * Anything that goes wrong reads as "no RTX". Off Windows there is nothing to look up.
 */
object RtxGpu {
    private const val DISPLAY_CLASS_KEY =
        "HKLM\\SYSTEM\\CurrentControlSet\\Control\\Class\\{4d36e968-e325-11ce-bfc1-08002be10318}"

    /** NVIDIA's PCI vendor id. */
    private const val NVIDIA_VENDOR = "VEN_10DE"

    private val rtxWord = Regex("\\bRTX\\b", RegexOption.IGNORE_CASE)
    private val valueLine = Regex("^\\s+(DriverDesc|MatchingDeviceId)\\s+REG_\\w+\\s+(.*)$", RegexOption.IGNORE_CASE)

    /** The display adapters Windows lists, read once (blocking; call off the UI thread first). */
    val adapters: List<GpuAdapter> by lazy {
        if (HostOs.current != HostOs.WINDOWS) emptyList()
        else runCatching {
            val started = System.nanoTime()
            // Two small queries, merged by key in parse(). Either can fail on its own.
            val text = listOf("DriverDesc", "MatchingDeviceId").joinToString("\n") { value ->
                regQuery(DISPLAY_CLASS_KEY, "/s", "/v", value).orEmpty()
            }
            parse(text).also { TimingLog.log("app.gpu_lookup", (System.nanoTime() - started) / 1_000_000, "adapters=${it.size}") }
        }.getOrDefault(emptyList())
    }

    /** True when at least one adapter is an NVIDIA RTX card. Windows only. */
    val present: Boolean get() = adapters.any { it.rtx }

    /** The RTX card's name for the UI, e.g. "NVIDIA GeForce RTX 4070", or null. */
    val name: String? get() = adapters.firstOrNull { it.rtx }?.name

    /** Starts the lookup on a background daemon thread so later reads do not block. */
    fun warmUp() {
        Thread({ runCatching { adapters } }, "puretv-gpu-lookup").apply { isDaemon = true }.start()
    }

    private fun regQuery(vararg args: String): String? = runCatching {
        val p = ProcessBuilder("reg", "query", *args).redirectErrorStream(true).start()
        // Read on another thread so a hung `reg` cannot hold us past the timeout.
        var output: String? = null
        val reader = Thread({ output = runCatching { p.inputStream.bufferedReader().readText() }.getOrNull() }, "puretv-gpu-reg")
            .apply { isDaemon = true; start() }
        if (!p.waitFor(5, TimeUnit.SECONDS)) {
            p.destroyForcibly()
            return@runCatching null
        }
        reader.join(1000)
        output
    }.getOrNull()

    /**
     * Turns `reg query /s` output into adapters. A line starting with `HKEY_` opens a key and
     * the indented `Name    REG_SZ    value` lines under it belong to that key. The output of
     * several queries can be joined: values for the same key are merged. Keys without a
     * DriverDesc (the `Configuration` and `Properties` subkeys) are not adapters.
     */
    internal fun parse(regOutput: String): List<GpuAdapter> {
        val names = LinkedHashMap<String, String>()
        val ids = HashMap<String, String>()
        var key: String? = null
        for (raw in regOutput.lineSequence()) {
            val line = raw.trimEnd('\r', ' ', '\t')
            if (line.startsWith("HKEY_", ignoreCase = true) || line.startsWith("HKLM\\", ignoreCase = true)) {
                key = line.lowercase()
                continue
            }
            val k = key ?: continue
            val m = valueLine.find(line) ?: continue
            val value = m.groupValues[2].trim()
            if (value.isEmpty()) continue
            if (m.groupValues[1].equals("DriverDesc", ignoreCase = true)) names[k] = value else ids[k] = value
        }
        return names.map { (k, name) ->
            val id = ids[k]
            GpuAdapter(name = name, nvidia = isNvidia(name, id), rtx = isRtx(name, id))
        }
    }

    /** NVIDIA by vendor id, or by name when the id is missing, and "RTX" as a word in the name. */
    internal fun isRtx(name: String, matchingDeviceId: String?): Boolean =
        isNvidia(name, matchingDeviceId) && rtxWord.containsMatchIn(name)

    private fun isNvidia(name: String, matchingDeviceId: String?): Boolean =
        if (matchingDeviceId.isNullOrBlank()) name.trim().startsWith("NVIDIA", ignoreCase = true)
        else matchingDeviceId.contains(NVIDIA_VENDOR, ignoreCase = true)
}
