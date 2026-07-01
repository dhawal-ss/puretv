package com.puretv.twitch.core.chat

import com.puretv.twitch.core.api.ChatBadgeSetDto
import com.puretv.twitch.core.api.TwitchApiClient

/** A resolved chat badge image: a CDN URL plus its human title (tooltip/alt text). */
data class ChatBadgeImage(val url: String, val title: String)

/**
 * Immutable lookup from (badge set id, version) -> [ChatBadgeImage], built by
 * merging a channel's badge sets over the global sets. Used to render real badge
 * icons (Twitch/sub/mod/bits) in chat instead of text chips.
 */
class BadgeIndex(private val bySet: Map<String, Map<String, ChatBadgeImage>>) {
    /** Resolve a badge to its image, or null if the set/version is unknown. */
    fun resolve(setId: String, version: String): ChatBadgeImage? = bySet[setId]?.get(version)

    companion object {
        val EMPTY = BadgeIndex(emptyMap())

        /**
         * Build an index from global + channel badge sets. Channel sets win on a
         * set-id collision (a channel's "subscriber" badge overrides the global one),
         * so they are applied last.
         */
        fun from(global: List<ChatBadgeSetDto>, channel: List<ChatBadgeSetDto>): BadgeIndex {
            val out = LinkedHashMap<String, Map<String, ChatBadgeImage>>()
            for (set in global + channel) {
                if (set.setId.isBlank()) continue
                val versions = LinkedHashMap<String, ChatBadgeImage>()
                for (v in set.versions) {
                    if (v.id.isBlank()) continue
                    // Prefer the 2x art for chat (crisp at ~18px on HiDPI); fall back down the ladder.
                    val url = v.imageUrl2x.ifBlank { v.imageUrl4x.ifBlank { v.imageUrl1x } }
                    if (url.isNotBlank()) versions[v.id] = ChatBadgeImage(url, v.title)
                }
                if (versions.isNotEmpty()) out[set.setId] = versions
            }
            return BadgeIndex(out)
        }
    }
}

/**
 * Loads Twitch chat badge sets for a channel and assembles a [BadgeIndex].
 * Best-effort: a failed fetch yields an empty set so chat still renders (just
 * without that tier of badge art).
 */
class BadgeRepository(private val api: TwitchApiClient) {
    suspend fun loadForChannel(broadcasterId: String): BadgeIndex {
        val global = api.getGlobalChatBadges()
        val channel = api.getChannelChatBadges(broadcasterId)
        return BadgeIndex.from(global, channel)
    }
}
