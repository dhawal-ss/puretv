package com.puretv.twitch.desktop.update

import java.security.KeyPairGenerator
import java.security.Signature
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The update signature gate is the integrity control that makes auto-update
 * safe. The catastrophic failure mode is accepting a tampered/forged installer,
 * so the negative cases (tamper, wrong key, garbage) are the load-bearing tests.
 */
class UpdateSignatureVerifierTest {

    private val b64 = Base64.getEncoder()

    private fun keyPair() = KeyPairGenerator.getInstance("Ed25519").generateKeyPair()

    private fun sign(data: ByteArray, key: java.security.PrivateKey): String =
        Signature.getInstance("Ed25519").run {
            initSign(key)
            update(data)
            b64.encodeToString(sign())
        }

    @Test
    fun acceptsAGenuineSignature() {
        val kp = keyPair()
        val data = "installer-bytes".toByteArray()
        val sig = sign(data, kp.private)
        val pub = b64.encodeToString(kp.public.encoded)

        assertTrue(UpdateSignatureVerifier.verify(data, sig, pub))
    }

    @Test
    fun rejectsTamperedData() {
        val kp = keyPair()
        val sig = sign("installer-bytes".toByteArray(), kp.private)
        val pub = b64.encodeToString(kp.public.encoded)

        assertFalse(
            UpdateSignatureVerifier.verify("installer-bytes-EVIL".toByteArray(), sig, pub),
            "a signature must not validate against modified bytes",
        )
    }

    @Test
    fun rejectsSignatureFromADifferentKey() {
        val signer = keyPair()
        val attacker = keyPair()
        val data = "installer-bytes".toByteArray()
        val sig = sign(data, attacker.private)
        val pub = b64.encodeToString(signer.public.encoded)

        assertFalse(
            UpdateSignatureVerifier.verify(data, sig, pub),
            "a signature from a key other than the embedded publisher key must be rejected",
        )
    }

    @Test
    fun rejectsMalformedInputsWithoutThrowing() {
        val pub = b64.encodeToString(keyPair().public.encoded)
        val data = "x".toByteArray()
        assertFalse(UpdateSignatureVerifier.verify(data, "not-base64!!", pub))
        assertFalse(UpdateSignatureVerifier.verify(data, "", pub))
        assertFalse(UpdateSignatureVerifier.verify(data, b64.encodeToString(ByteArray(64)), "not-a-key"))
        assertFalse(UpdateSignatureVerifier.verify(data, b64.encodeToString(ByteArray(64)), ""))
    }
}
