package com.puretv.twitch.desktop.player

/** Formats a millisecond position as `M:SS` (or `H:MM:SS` past an hour). */
fun formatTimecode(ms: Long): String {
    val totalSeconds = (ms.coerceAtLeast(0)) / 1000
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
