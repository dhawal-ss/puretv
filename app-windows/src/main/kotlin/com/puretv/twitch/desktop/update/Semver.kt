package com.puretv.twitch.desktop.update

/**
 * Minimal semantic-version comparison for update checks. Accepts `vX.Y.Z` or
 * `X.Y.Z`, ignores a trailing pre-release/build suffix (`-beta`, `+build`), and
 * pads missing components with 0 (so `1.2` == `1.2.0`). Anything unparseable
 * compares as "not newer" — we never offer an update we can't reason about.
 */
object Semver {

    fun isNewer(current: String, candidate: String): Boolean {
        val c = parse(current) ?: return false
        val n = parse(candidate) ?: return false
        for (i in 0 until maxOf(c.size, n.size)) {
            val a = c.getOrElse(i) { 0 }
            val b = n.getOrElse(i) { 0 }
            if (b != a) return b > a
        }
        return false
    }

    private fun parse(raw: String): List<Int>? {
        val cleaned = raw.trim()
            .removePrefix("v").removePrefix("V")
            .substringBefore('-')
            .substringBefore('+')
        if (cleaned.isBlank()) return null
        val nums = cleaned.split('.').map { it.toIntOrNull() ?: return null }
        return nums.ifEmpty { null }
    }
}
