package com.puretv.twitch.core.stream

import com.puretv.twitch.core.emotes.ResolvedEmote
import com.puretv.twitch.core.emotes.applyThirdPartyEmotes
import com.puretv.twitch.core.model.Badge
import com.puretv.twitch.core.model.ChatMessage
import com.puretv.twitch.core.model.MessagePart
import kotlin.concurrent.Volatile
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

/**
 * Accumulates replay comments (deduped by id) and yields those due at a position.
 *
 * COPY-ON-WRITE for concurrency safety: VodChatViewModel touches a single buffer
 * from three coroutines on a thread-pool dispatcher (the load coroutine → [add],
 * the emote-index coroutine → [due], and the player.status collector →
 * [reset]/[isEmpty]/[maxOffsetSeconds]/[due]). A bare mutable [LinkedHashMap]
 * would throw ConcurrentModificationException (crashing the VOD screen) the moment
 * a reader iterated its values while a writer mutated it. Here the entries live in
 * an IMMUTABLE map behind a single [Volatile] reference: every reader captures the
 * reference once and only ever iterates that frozen snapshot, while writers publish
 * a brand-new map with one atomic volatile store. No lock is taken, so the public
 * API stays non-suspend and pure-commonMain (usable on every target).
 */
class ReplayBuffer {
    @Volatile private var byId: Map<String, ReplayComment> = emptyMap()

    fun add(items: List<ReplayComment>) {
        if (items.isEmpty()) return
        // Build a fresh map off the current snapshot (preserving insertion order +
        // dedupe-by-id), then publish it atomically — never mutate the live snapshot.
        byId = LinkedHashMap(byId).apply { items.forEach { put(it.message.id, it) } }
    }

    fun reset() { byId = emptyMap() }

    fun isEmpty(): Boolean = byId.isEmpty()

    val maxOffsetSeconds: Int get() = byId.values.maxOfOrNull { it.offsetSeconds } ?: 0

    /** Comments at or before [positionSeconds], in offset order. */
    fun due(positionSeconds: Long): List<ReplayComment> =
        byId.values.filter { it.offsetSeconds <= positionSeconds }.sortedBy { it.offsetSeconds }
}
