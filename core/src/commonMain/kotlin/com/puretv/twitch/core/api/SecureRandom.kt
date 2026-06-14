package com.puretv.twitch.core.api

/**
 * Cryptographically secure random bytes, backed by `java.security.SecureRandom`
 * on Android and Desktop (JVM).
 *
 * Used for OAuth/PKCE secrets — the `code_verifier` and CSRF `state` — where a
 * predictable, non-cryptographic PRNG (e.g. `kotlin.random.Random`) would weaken
 * the flow. RFC 7636 §7.1 requires the verifier to be generated with a CSPRNG.
 */
expect fun secureRandomBytes(size: Int): ByteArray
