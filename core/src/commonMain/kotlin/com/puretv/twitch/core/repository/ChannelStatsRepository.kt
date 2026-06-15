package com.puretv.twitch.core.repository

import com.puretv.twitch.core.model.ChannelStatsSnapshot
import com.puretv.twitch.core.stream.TwitchGqlClient

/**
 * Assembles a [ChannelStatsSnapshot] from existing Helix calls plus the GQL
 * follower count. Each source degrades independently — a failed sub-call yields
 * a null field rather than failing the whole snapshot.
 */
class ChannelStatsRepository(
    private val channelRepository: ChannelRepository,
    private val streamRepository: StreamRepository,
    private val gqlClient: TwitchGqlClient,
) {
    suspend fun snapshot(login: String): ChannelStatsSnapshot? {
        val channel = runCatching { channelRepository.getChannel(login) }.getOrNull() ?: return null
        val live = runCatching { streamRepository.streamsForChannels(listOf(login)).firstOrNull() }.getOrNull()
        val followers = runCatching { gqlClient.fetchFollowerCount(login) }.getOrNull()
        return ChannelStatsSnapshot.from(channel, live, followers)
    }

    /** Cheap per-tick read for the viewer-history sampler. Null when offline/failed. */
    suspend fun liveViewers(login: String): Int? =
        runCatching { streamRepository.streamsForChannels(listOf(login)).firstOrNull()?.viewerCount }.getOrNull()
}
