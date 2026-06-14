package com.puretv.twitch.core.api

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests for the CSPRNG primitive and the PKCE verifier/state it backs.
 *
 * Note: "is this cryptographically secure?" is not observable from output, so
 * these assert the testable contract — correct size, non-constant output, and
 * the verifier/state length+charset invariants the OAuth flow depends on. The
 * security improvement (CSPRNG vs `kotlin.random.Random`) is enforced by the
 * implementation using [secureRandomBytes].
 */
class PkceAuthTest {

    @Test
    fun secureRandomReturnsRequestedNumberOfBytes() {
        assertEquals(32, secureRandomBytes(32).size)
        assertEquals(1, secureRandomBytes(1).size)
    }

    @Test
    fun secureRandomIsNotConstant() {
        // Two 32-byte draws colliding has negligible probability (2^-256).
        assertFalse(
            secureRandomBytes(32).contentEquals(secureRandomBytes(32)),
            "two CSPRNG draws must differ",
        )
    }

    @Test
    fun verifierHasRequestedLengthAndAllowedCharset() {
        val verifier = PkceAuth.generateVerifier(64)
        assertEquals(64, verifier.length)
        val allowed = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~".toSet()
        assertTrue(verifier.all { it in allowed }, "verifier has out-of-charset characters: $verifier")
    }

    @Test
    fun successiveVerifiersDiffer() {
        assertFalse(PkceAuth.generateVerifier() == PkceAuth.generateVerifier(), "verifiers must not repeat")
    }

    @Test
    fun stateIsThirtyTwoChars() {
        assertEquals(32, PkceAuth.generateState().length)
    }
}
