package com.puretv.twitch.desktop.data

import kotlinx.serialization.Serializable

/** One sampled viewer reading. */
@Serializable
data class ViewerSample(val epochSec: Long, val viewers: Int)

/** Locally-accrued viewer history for one channel (sampled only while watched). */
@Serializable
data class ChannelHistory(
    val login: String,
    val samples: List<ViewerSample> = emptyList(),
    val peakViewers: Int = 0,
    val sessionsTracked: Int = 0,
    val lastSampleEpochSec: Long = 0,
)

/**
 * Pure aggregation over [ChannelHistory]. A "session" boundary is a sampling
 * gap longer than [SESSION_GAP_SEC] (the user closed the channel and came back
 * later). Samples are capped to [MAX_SAMPLES], keeping the newest.
 */
object ViewerHistoryAggregator {
    const val SESSION_GAP_SEC = 30L * 60
    const val MAX_SAMPLES = 500

    fun record(history: ChannelHistory?, login: String, sample: ViewerSample): ChannelHistory {
        val prev = history ?: ChannelHistory(login = login)
        val isNewSession = prev.samples.isEmpty() ||
            (sample.epochSec - prev.lastSampleEpochSec) > SESSION_GAP_SEC
        val samples = (prev.samples + sample).takeLast(MAX_SAMPLES)
        return prev.copy(
            login = login,
            samples = samples,
            peakViewers = maxOf(prev.peakViewers, sample.viewers),
            sessionsTracked = prev.sessionsTracked + if (isNewSession) 1 else 0,
            lastSampleEpochSec = sample.epochSec,
        )
    }

    fun averageViewers(history: ChannelHistory): Int =
        if (history.samples.isEmpty()) 0
        else (history.samples.sumOf { it.viewers.toLong() } / history.samples.size).toInt()
}
