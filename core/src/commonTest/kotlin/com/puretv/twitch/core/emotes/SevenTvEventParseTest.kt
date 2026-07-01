package com.puretv.twitch.core.emotes

import com.puretv.twitch.core.model.EmoteProvider
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SevenTvEventParseTest {

    // parseDispatch is pure; the client only needs an HttpClient to construct.
    private val client = SevenTvEventClient(HttpClient(MockEngine { respond("") }))

    @Test fun parsesPushedEmoteAsAddition() {
        val frame = """
            {"op":0,"d":{"type":"emote_set.update","body":{"id":"set1","pushed":[
              {"key":"emotes","index":0,"value":{"id":"60aeab8df6a2c3b332d809c0","name":"catJAM","flags":0,
               "data":{"id":"60aeab8df6a2c3b332d809c0","name":"catJAM","animated":true,"flags":0}}}
            ]}}}
        """.trimIndent()
        val delta = client.parseDispatch(frame)
        assertEquals(1, delta?.added?.size)
        assertTrue(delta!!.removed.isEmpty())
        val e = delta.added.first()
        assertEquals("catJAM", e.name)
        assertEquals(EmoteProvider.SEVENTV, e.provider)
        assertTrue(e.animated)
        assertEquals("https://cdn.7tv.app/emote/60aeab8df6a2c3b332d809c0/4x.webp", e.url)
    }

    @Test fun parsesPulledEmoteAsRemoval() {
        val frame = """
            {"op":0,"d":{"type":"emote_set.update","body":{"pulled":[
              {"key":"emotes","old_value":{"id":"x1","name":"OldOne","flags":0,
               "data":{"id":"x1","name":"OldOne","animated":false,"flags":0}}}
            ]}}}
        """.trimIndent()
        val delta = client.parseDispatch(frame)
        assertTrue(delta?.added?.isEmpty() == true)
        assertEquals(listOf("OldOne"), delta?.removed?.map { it.name })
    }

    @Test fun renameArrivesAsRemoveOldPlusAddNew() {
        val frame = """
            {"op":0,"d":{"type":"emote_set.update","body":{"updated":[
              {"key":"emotes",
               "old_value":{"id":"id1","name":"OldName","data":{"id":"id1","name":"OldName","animated":false}},
               "value":{"id":"id1","name":"NewName","data":{"id":"id1","name":"NewName","animated":false}}}
            ]}}}
        """.trimIndent()
        val delta = client.parseDispatch(frame)
        assertEquals(listOf("OldName"), delta?.removed?.map { it.name })
        assertEquals(listOf("NewName"), delta?.added?.map { it.name })
    }

    @Test fun ignoresNonEmoteKeyChanges() {
        // A change to some other set field (not "emotes") must yield nothing.
        val frame = """
            {"op":0,"d":{"type":"emote_set.update","body":{"pushed":[
              {"key":"name","value":{"id":"z","name":"whatever"}}
            ]}}}
        """.trimIndent()
        val delta = client.parseDispatch(frame)
        assertTrue(delta?.added?.isEmpty() == true && delta.removed.isEmpty())
    }

    @Test fun helloAndAckAndHeartbeatYieldNull() {
        assertNull(client.parseDispatch("""{"op":1,"d":{"heartbeat_interval":25000,"session_id":"s"}}"""))
        assertNull(client.parseDispatch("""{"op":5,"d":{"type":"emote_set.update"}}"""))
        assertNull(client.parseDispatch("""{"op":2,"d":{"count":1}}"""))
    }

    @Test fun otherDispatchTypesYieldNull() {
        assertNull(client.parseDispatch("""{"op":0,"d":{"type":"user.update","body":{}}}"""))
    }

    @Test fun malformedFrameYieldsNull() {
        assertNull(client.parseDispatch("not json"))
        assertNull(client.parseDispatch("""{"op":0}"""))
    }
}
