package com.puretv.twitch.desktop.data

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FollowStoreTest {

    private val tmp: File = Files.createTempDirectory("followstore-test").toFile()

    @AfterTest
    fun cleanup() {
        tmp.deleteRecursively()
    }

    private fun channel(login: String) = FollowedChannel(
        id = "id-$login",
        login = login,
        displayName = login.replaceFirstChar { it.uppercase() },
    )

    @Test
    fun follow_adds_channel() {
        val store = FollowStore(tmp)
        store.follow(channel("ninja"))
        assertTrue(store.isFollowed("ninja"))
        assertEquals(1, store.followed.value.size)
    }

    @Test
    fun follow_is_idempotent_by_login_case_insensitive() {
        val store = FollowStore(tmp)
        store.follow(channel("ninja"))
        store.follow(channel("NINJA"))
        assertEquals(1, store.followed.value.size)
    }

    @Test
    fun unfollow_removes_channel() {
        val store = FollowStore(tmp)
        store.follow(channel("ninja"))
        store.unfollow("ninja")
        assertFalse(store.isFollowed("ninja"))
        assertTrue(store.followed.value.isEmpty())
    }

    @Test
    fun toggle_flips_state() {
        val store = FollowStore(tmp)
        store.toggle(channel("pokimane"))
        assertTrue(store.isFollowed("pokimane"))
        store.toggle(channel("pokimane"))
        assertFalse(store.isFollowed("pokimane"))
    }

    @Test
    fun persists_across_instances() {
        val first = FollowStore(tmp)
        first.follow(channel("shroud"))
        first.flush()
        val reopened = FollowStore(tmp)
        assertTrue(reopened.isFollowed("shroud"))
        assertEquals("Shroud", reopened.followed.value.first().displayName)
    }
}
