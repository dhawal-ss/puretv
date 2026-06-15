package com.puretv.twitch.core.model

/**
 * Immutable "right now" snapshot of a channel's public stats, assembled from
 * Helix /users + /streams + the GQL follower count. Nullable fields are simply
 * unavailable (offline, or Twitch withheld them) — never fabricated.
 */
data class ChannelStatsSnapshot(
    val login: String,
    val displayName: String,
    val profileImageUrl: String,
    val broadcasterType: String,
    val createdAtIso: String,
    val followerCount: Long?,
    val isLive: Boolean,
    val viewerCount: Int?,
    val gameName: String?,
    val streamTitle: String?,
    val startedAtIso: String?,
    val language: String?,
) {
    companion object {
        fun from(channel: ChannelInfo, live: StreamInfo?, followerCount: Long?): ChannelStatsSnapshot =
            ChannelStatsSnapshot(
                login = channel.login,
                displayName = channel.displayName,
                profileImageUrl = channel.profileImageUrl,
                broadcasterType = channel.broadcasterType,
                createdAtIso = channel.createdAt,
                followerCount = followerCount,
                isLive = live != null,
                viewerCount = live?.viewerCount,
                gameName = live?.gameName?.takeIf { it.isNotBlank() },
                streamTitle = live?.title?.takeIf { it.isNotBlank() },
                startedAtIso = live?.startedAt?.takeIf { it.isNotBlank() },
                language = live?.language?.takeIf { it.isNotBlank() },
            )
    }
}
