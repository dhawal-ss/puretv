package com.puretv.twitch.desktop.auth

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AuthMigrationTest {

    @Test
    fun clears_when_old_schema_and_a_session_exists() {
        assertTrue(needsAuthReset(storedSchema = 0, currentSchema = 1, hasSession = true))
    }

    @Test
    fun does_not_clear_when_no_session() {
        assertFalse(needsAuthReset(storedSchema = 0, currentSchema = 1, hasSession = false))
    }

    @Test
    fun does_not_clear_when_already_on_current_schema() {
        assertFalse(needsAuthReset(storedSchema = 1, currentSchema = 1, hasSession = true))
    }
}
