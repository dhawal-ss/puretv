package com.puretv.twitch.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Core data models shared across PureTV for Twitch (Android, Android TV, Windows Desktop).
 */

@Serializable
data class StreamInfo(
    val id: String,
    @SerialName("user_id") val userId: String,
    @SerialName("user_login") val userLogin: String,
    @SerialName("user_name") val userName: String,
    @SerialName("game_id") val gameId: String = "",
    @SerialName("game_name") val gameName: String = "",
    val title: String = "",
    @SerialName("viewer_count") val viewerCount: Int = 0,
    @SerialName("started_at") val startedAt: String = "",
    val language: String = "en",
    @SerialName("thumbnail_url") val thumbnailUrl: String = "",
    val tags: List<String> = emptyList(),
)

@Serializable
data class ChannelInfo(
    val id: String,
    val login: String,
    @SerialName("display_name") val displayName: String,
    val description: String = "",
    @SerialName("profile_image_url") val profileImageUrl: String = "",
    @SerialName("offline_image_url") val offlineImageUrl: String = "",
    @SerialName("view_count") val viewCount: Int = 0,
    @SerialName("broadcaster_type") val broadcasterType: String = "",
    @SerialName("created_at") val createdAt: String = "",
)

@Serializable
data class GameInfo(
    val id: String,
    val name: String,
    @SerialName("box_art_url") val boxArtUrl: String = "",
)

/** Quality variants parsed out of the Twitch HLS master playlist. */
enum class StreamQuality(val label: String, val sortOrder: Int) {
    SOURCE("Source", 0),
    P1080P60("1080p60", 1),
    P720P60("720p60", 2),
    P480P("480p", 3),
    P360P("360p", 4),
    AUDIO_ONLY("Audio Only", 5),
    AUTO("Auto", -1);

    companion object {
        fun fromVariantName(name: String): StreamQuality = when {
            name.contains("chunked", ignoreCase = true) -> SOURCE
            name.contains("1080") -> P1080P60
            name.contains("720") -> P720P60
            name.contains("480") -> P480P
            name.contains("360") -> P360P
            name.contains("audio", ignoreCase = true) -> AUDIO_ONLY
            else -> AUTO
        }
    }
}

data class PlaylistVariant(
    val quality: StreamQuality,
    val resolution: String,
    val frameRate: Double,
    val bandwidth: Long,
    val url: String,
)

data class StreamToken(
    val value: String,
    val signature: String,
)

/** Result returned by [com.puretv.twitch.core.adblock.AdBlockEngine.resolveCleanStream]. */
sealed class CleanStreamResult {
    data class Success(
        val playlistContent: String,
        val variants: List<PlaylistVariant>,
        val strategyUsed: AdBlockStrategyResult,
    ) : CleanStreamResult()

    data class Failure(val reason: String) : CleanStreamResult()
}

enum class AdBlockStrategyResult { PROXY, MANIFEST_REWRITE, PASSTHROUGH_BLOCKED, DISABLED }

data class FilteredPlaylist(
    val content: String,
    val adSegmentsRemoved: Int,
    val containedAds: Boolean,
)

// ---- Chat models ----

data class ChatMessage(
    val id: String,
    val channel: String,
    val username: String,
    val displayName: String,
    val color: String,
    val message: String,
    val parsedParts: List<MessagePart>,
    val badges: List<Badge>,
    val timestamp: Long,
    val isSubscriber: Boolean,
    val isModerator: Boolean,
    val isBroadcaster: Boolean,
)

sealed class MessagePart {
    data class Text(val content: String) : MessagePart()
    data class TwitchEmote(val id: String, val name: String) : MessagePart()
    data class ThirdPartyEmote(val url: String, val name: String, val provider: EmoteProvider) : MessagePart()
}

data class Badge(val setId: String, val version: String)

enum class EmoteProvider { BTTV, FFZ, SEVENTV }

sealed class ChatEvent {
    data class Message(val message: ChatMessage) : ChatEvent()
    data class UserNotice(val systemMessage: String, val raw: ChatMessage?) : ChatEvent()
    data class ClearChat(val targetUser: String?, val durationSeconds: Int?) : ChatEvent()
    data class ClearMessage(val targetMessageId: String) : ChatEvent()
    data class RoomState(val slowModeSeconds: Int?, val emoteOnly: Boolean) : ChatEvent()
    data class SelfState(val displayName: String, val color: String, val badges: List<Badge>) : ChatEvent()
    data class ConnectionState(val connected: Boolean, val reason: String? = null) : ChatEvent()
}

data class ChannelEmote(
    val id: String,
    val name: String,
    val url: String,
    val provider: EmoteProvider,
    val animated: Boolean,
)

// ---- Settings ----

data class AppSettings(
    // Playback
    val preferredQuality: String = "auto",
    val lowLatencyMode: Boolean = true,

    // Ad Block
    val adBlockEnabled: Boolean = true,
    val adBlockStrategy: String = "proxy", // proxy, rewrite, disabled
    val customProxyUrl: String = "",

    // Chat
    val chatEnabled: Boolean = true,
    val chatFontSize: Float = 14f,
    val showBadges: Boolean = true,
    val showBttvEmotes: Boolean = true,
    val show7tvEmotes: Boolean = true,
    val showFfzEmotes: Boolean = true,
    val chatTimestamps: Boolean = false,

    // UI
    val theme: String = "dark", // dark, darker, purple
    val compactMode: Boolean = false,

    // Account
    val accessToken: String = "",
    val username: String = "",
    val userId: String = "",
)
