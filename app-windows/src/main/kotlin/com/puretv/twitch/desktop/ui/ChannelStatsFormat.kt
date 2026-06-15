package com.puretv.twitch.desktop.ui

import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Locale

/** Compact count for display: 11_301_990 -> "11.3M", 12_400 -> "12.4K", 947 -> "947". */
fun formatCompact(n: Long): String = when {
    n < 0 -> "0"
    n >= 1_000_000 -> oneDecimal(n / 1_000_000.0) + "M"
    n >= 1_000 -> oneDecimal(n / 1_000.0) + "K"
    else -> n.toString()
}

private fun oneDecimal(v: Double): String {
    val s = String.format(Locale.US, "%.1f", v)
    return if (s.endsWith(".0")) s.dropLast(2) else s
}

private fun parseInstant(iso: String?): Instant? {
    if (iso.isNullOrBlank()) return null
    return runCatching { Instant.parse(iso) }.getOrNull()
}

/** Magnitude of account age for a tile value: "13 years", "1 year", "4 months", "New". "" if unparseable. */
fun accountAgeLabel(createdAtIso: String, now: Instant = Instant.now()): String {
    val created = parseInstant(createdAtIso) ?: return ""
    val days = ChronoUnit.DAYS.between(created, now)
    return when {
        days < 0 -> ""
        days < 30 -> "New"
        days < 365 -> { val m = (days / 30).toInt(); "" + m + if (m == 1) " month" else " months" }
        else -> { val y = (days / 365).toInt(); "" + y + if (y == 1) " year" else " years" }
    }
}

/** 4-digit join year ("2012"), or null if unparseable. */
fun joinedYearLabel(createdAtIso: String): String? {
    val created = parseInstant(createdAtIso) ?: return null
    return created.atZone(java.time.ZoneOffset.UTC).year.toString()
}

/** Stream uptime: "2h 30m" / "15m". null if no start time or unparseable. */
fun uptimeLabel(startedAtIso: String?, now: Instant = Instant.now()): String? {
    val started = parseInstant(startedAtIso) ?: return null
    val secs = Duration.between(started, now).seconds
    if (secs < 0) return null
    val h = secs / 3600
    val m = (secs % 3600) / 60
    return if (h > 0) "" + h + "h " + m + "m" else "" + m + "m"
}
