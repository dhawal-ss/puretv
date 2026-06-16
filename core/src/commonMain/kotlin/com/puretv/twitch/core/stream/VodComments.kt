package com.puretv.twitch.core.stream

import com.puretv.twitch.core.emotes.ResolvedEmote
import com.puretv.twitch.core.emotes.applyThirdPartyEmotes
import com.puretv.twitch.core.model.Badge
import com.puretv.twitch.core.model.ChatMessage
import com.puretv.twitch.core.model.MessagePart
import kotlinx.serialization.Serializable

// ---- GQL wire types for video(id).comments ----
@Serializable data class VideoCommentsData(val video: VideoCommentsVideo? = null)
@Serializable data class VideoCommentsVideo(val comments: VideoCommentConnection? = null)
@Serializable data class VideoCommentConnection(
    val edges: List<VideoCommentEdge> = emptyList(),
    val pageInfo: VideoCommentPageInfo = VideoCommentPageInfo(),
)
@Serializable data class VideoCommentPageInfo(val hasNextPage: Boolean = false)
@Serializable data class VideoCommentEdge(val node: VideoCommentNode)
@Serializable data class VideoCommentNode(
    val id: String = "",
    val contentOffsetSeconds: Int = 0,
    val commenter: CommentCommenter? = null,
    val message: CommentMsg? = null,
)
@Serializable data class CommentCommenter(val displayName: String = "")
@Serializable data class CommentMsg(
    val userColor: String? = null,
    val userBadges: List<CommentBadge> = emptyList(),
    val fragments: List<CommentFragment> = emptyList(),
)
@Serializable data class CommentBadge(val setID: String = "", val version: String = "")
@Serializable data class CommentFragment(val text: String = "", val emote: CommentEmoteRef? = null)
@Serializable data class CommentEmoteRef(val emoteID: String = "")

/** A historical chat comment placed at [offsetSeconds] into the VOD. */
data class ReplayComment(val offsetSeconds: Int, val message: ChatMessage)

/** A fetched window of replay comments plus whether more pages exist after it. */
data class CommentBatch(val comments: List<ReplayComment>, val hasNextPage: Boolean)

/**
 * Tokenizes 7TV/BTTV/FFZ emotes inside each replay comment's parts (Twitch emotes already
 * arrive tagged from the comments API). Returns the list unchanged when [index] is empty.
 */
fun List<ReplayComment>.withThirdPartyEmotes(index: Map<String, ResolvedEmote>): List<ReplayComment> =
    if (index.isEmpty()) this
    else map { it.copy(message = it.message.copy(parsedParts = applyThirdPartyEmotes(it.message.parsedParts, index))) }

/** Pure mapping from GQL comment nodes to the shared [ChatMessage] render model. */
object CommentMapper {
    fun toReplayComments(connection: VideoCommentConnection): List<ReplayComment> =
        connection.edges.mapNotNull { edge ->
            val n = edge.node
            val msg = n.message ?: return@mapNotNull null
            val name = n.commenter?.displayName.orEmpty()
            val sets = msg.userBadges.map { it.setID }
            ReplayComment(
                offsetSeconds = n.contentOffsetSeconds,
                message = ChatMessage(
                    id = n.id,
                    channel = "",
                    username = name,
                    displayName = name,
                    color = msg.userColor.orEmpty(),
                    message = msg.fragments.joinToString("") { it.text },
                    parsedParts = msg.fragments.map { f ->
                        val emoteId = f.emote?.emoteID
                        if (!emoteId.isNullOrBlank()) MessagePart.TwitchEmote(id = emoteId, name = f.text)
                        else MessagePart.Text(f.text)
                    },
                    badges = msg.userBadges.map { Badge(it.setID, it.version) },
                    timestamp = 0L,
                    isSubscriber = "subscriber" in sets,
                    isModerator = "moderator" in sets,
                    isBroadcaster = "broadcaster" in sets,
                ),
            )
        }
}

/** Accumulates replay comments (deduped by id) and yields those due at a position. */
class ReplayBuffer {
    private val byId = LinkedHashMap<String, ReplayComment>()

    fun add(items: List<ReplayComment>) {
        items.forEach { byId[it.message.id] = it }
    }

    fun reset() = byId.clear()

    fun isEmpty(): Boolean = byId.isEmpty()

    val maxOffsetSeconds: Int get() = byId.values.maxOfOrNull { it.offsetSeconds } ?: 0

    /** Comments at or before [positionSeconds], in offset order. */
    fun due(positionSeconds: Long): List<ReplayComment> =
        byId.values.filter { it.offsetSeconds <= positionSeconds }.sortedBy { it.offsetSeconds }
}
