package com.puretv.twitch.core.stream

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FollowerCountParseTest {
    @Test fun parsesTotalCount() {
        val body = """{"data":{"user":{"followers":{"totalCount":11301990}}}}"""
        assertEquals(11301990L, parseFollowerCount(body))
    }

    @Test fun nullUserReturnsNull() {
        assertNull(parseFollowerCount("""{"data":{"user":null}}"""))
    }

    @Test fun errorBodyReturnsNull() {
        assertNull(parseFollowerCount("""{"errors":[{"message":"service error"}]}"""))
    }

    @Test fun malformedReturnsNull() {
        assertNull(parseFollowerCount("not json at all"))
    }
}
