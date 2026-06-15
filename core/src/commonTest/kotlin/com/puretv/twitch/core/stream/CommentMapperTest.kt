package com.puretv.twitch.core.stream

import com.puretv.twitch.core.model.MessagePart
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CommentMapperTest {
    private val json = Json { ignoreUnknownKeys = true }

    private fun fixture(): String =
        this::class.java.classLoader.getResourceAsStream("vod/sample-video-comments.json")!!
            .bufferedReader().readText()

    @Test fun mapsFixture() {
        val data = json.decodeFromString<GqlEnvelope<VideoCommentsData>>(fixture())
        val out = CommentMapper.toReplayComments(data.data!!.video!!.comments!!)
        assertEquals(8, out.size)
        val first = out.first()
        assertEquals(31, first.offsetSeconds)
        assertEquals("dinamita2005", first.message.displayName)
        assertEquals("#008000", first.message.color)
        assertEquals(listOf(MessagePart.Text("A")), first.message.parsedParts)
        assertTrue(!first.message.isModerator && !first.message.isSubscriber)
    }

    @Test fun mapsBadgesAndEmotes() {
        val conn = VideoCommentConnection(
            edges = listOf(
                VideoCommentEdge(
                    VideoCommentNode(
                        id = "x", contentOffsetSeconds = 10,
                        commenter = CommentCommenter("Mod"),
                        message = CommentMsg(
                            userColor = "#FFFFFF",
                            userBadges = listOf(CommentBadge("subscriber", "3"), CommentBadge("moderator", "1")),
                            fragments = listOf(
                                CommentFragment("hi "),
                                CommentFragment("Kappa", CommentEmoteRef("25")),
                            ),
                        ),
                    ),
                ),
            ),
        )
        val m = CommentMapper.toReplayComments(conn).single().message
        assertTrue(m.isSubscriber && m.isModerator)
        assertEquals(
            listOf(MessagePart.Text("hi "), MessagePart.TwitchEmote("25", "Kappa")),
            m.parsedParts,
        )
    }
}
