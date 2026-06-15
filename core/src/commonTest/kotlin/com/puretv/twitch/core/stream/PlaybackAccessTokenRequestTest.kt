package com.puretv.twitch.core.stream

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Locks in the GOTCHA #1 fix: the PlaybackAccessToken request must send the
 * FULL inline GraphQL query, never a persisted-query hash. Twitch rotated the
 * pinned `sha256Hash`, which made every persisted request come back
 * `PersistedQueryNotFound` → no token → no stream. An inline query carries the
 * operation itself, so there is no hash for Twitch to invalidate.
 */
class PlaybackAccessTokenRequestTest {

    @Test fun sendsInlineQueryNotAPersistedHash() {
        val body = buildPlaybackAccessTokenBody(
            PlaybackAccessTokenVariables(login = "twitch", playerType = "popout"),
        )

        // The operation itself must be in the body...
        assertTrue("streamPlaybackAccessToken" in body, "must inline the live-token query")
        assertTrue("videoPlaybackAccessToken" in body, "must inline the VOD-token query")
        // ...and there must be NO rotatable persisted-query hash.
        assertFalse("persistedQuery" in body, "must not depend on a persisted-query hash")
        assertFalse("sha256Hash" in body, "must not pin a rotatable hash")
    }

    @Test fun encodesDefaultValuedVariablesSoTwitchDoesNotSeeNulls() {
        // encodeDefaults is load-bearing: Twitch's schema types isLive/isVod/
        // vodID/playerType as non-null, so stripping the defaults makes Twitch
        // reject the query with "Expected type X!, found null".
        val body = buildPlaybackAccessTokenBody(
            PlaybackAccessTokenVariables(login = "twitch", playerType = "popout"),
        )
        assertTrue("\"isLive\":true" in body, "isLive default must be serialized")
        assertTrue("\"isVod\":false" in body, "isVod default must be serialized")
        assertTrue("\"login\":\"twitch\"" in body)
        assertTrue("\"playerType\":\"popout\"" in body)
    }
}
