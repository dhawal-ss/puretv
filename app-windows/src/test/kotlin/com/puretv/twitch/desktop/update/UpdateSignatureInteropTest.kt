package com.puretv.twitch.desktop.update

import com.puretv.twitch.desktop.update.UpdateSignatureVerifier.VerifyResult
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Proves the openssl -> JDK interop the release pipeline depends on. CI signs
 * the installer with `openssl pkeyutl -sign -rawin` (raw Ed25519) using an
 * `openssl pkey -pubout -outform DER` public key; the app verifies with the
 * JDK's Signature("Ed25519") + X509EncodedKeySpec.
 *
 * These vectors were produced by openssl 3.2 over the bytes of
 * "puretv-interop-test-vector" with a throwaway key. If the two implementations'
 * formats ever diverge, this fails in CI instead of silently at release time.
 */
class UpdateSignatureInteropTest {

    private val data = "puretv-interop-test-vector".toByteArray()
    private val opensslPublicKey = "MCowBQYDK2VwAyEAaX2Nr5azoLx/alHYtqiajHejbTn5LAh4vRq0UJhWrhM="
    private val opensslSignature = "1LxewGF6MW+M2BaKgIdxE414npLZPARDC86bvPK5A8Iw5eGAzJsHRk0PVEXoZ5n4KKVmF1Fxi1pzvPYFVvXtCg=="

    @Test
    fun jdk_verifier_accepts_an_openssl_ed25519_signature() {
        assertEquals(VerifyResult.Valid, UpdateSignatureVerifier.verify(data, opensslSignature, opensslPublicKey))
    }

    @Test
    fun jdk_verifier_rejects_tampered_data_against_an_openssl_signature() {
        assertEquals(
            VerifyResult.Invalid,
            UpdateSignatureVerifier.verify("puretv-interop-test-vectorX".toByteArray(), opensslSignature, opensslPublicKey),
        )
    }
}
