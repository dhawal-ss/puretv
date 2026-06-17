package com.puretv.twitch.desktop.update

import com.puretv.twitch.desktop.update.UpdateSignatureVerifier.VerifyResult
import java.security.KeyPairGenerator
import java.security.Signature
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * The update signature gate is the integrity control that makes auto-update
 * safe. The catastrophic failure mode is accepting a tampered/forged installer,
 * so the negative cases (tamper, wrong key, garbage) are the load-bearing tests.
 *
 * The result now distinguishes Invalid (crypto ran, didn't match) from Errored
 * (couldn't run) — see [UpdateSignatureVerifier].
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

        assertEquals(VerifyResult.Valid, UpdateSignatureVerifier.verify(data, sig, pub))
    }

    @Test
    fun tamperedDataIsInvalidNotErrored() {
        val kp = keyPair()
        val sig = sign("installer-bytes".toByteArray(), kp.private)
        val pub = b64.encodeToString(kp.public.encoded)

        // Crypto ran and rejected the bytes → Invalid (a corrupted/tampered download), NOT Errored.
        assertEquals(VerifyResult.Invalid, UpdateSignatureVerifier.verify("installer-bytes-EVIL".toByteArray(), sig, pub))
    }

    @Test
    fun signatureFromADifferentKeyIsInvalid() {
        val signer = keyPair()
        val attacker = keyPair()
        val data = "installer-bytes".toByteArray()
        val sig = sign(data, attacker.private)
        val pub = b64.encodeToString(signer.public.encoded)

        assertEquals(VerifyResult.Invalid, UpdateSignatureVerifier.verify(data, sig, pub))
    }

    @Test
    fun malformedInputsAreErroredNotInvalid() {
        val pub = b64.encodeToString(keyPair().public.encoded)
        val data = "x".toByteArray()
        // Bad base64 / unparseable key can't even run the check → Errored (an app/runtime
        // problem), which must be distinguishable from a genuine mismatch.
        assertIs<VerifyResult.Errored>(UpdateSignatureVerifier.verify(data, "not-base64!!", pub))
        assertIs<VerifyResult.Errored>(UpdateSignatureVerifier.verify(data, b64.encodeToString(ByteArray(64)), "not-a-key"))
        assertIs<VerifyResult.Errored>(UpdateSignatureVerifier.verify(data, b64.encodeToString(ByteArray(64)), ""))
    }
}
