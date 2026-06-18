package com.puretv.twitch.desktop.update

import com.puretv.twitch.desktop.update.UpdateSignatureVerifier.VerifyResult
import java.nio.file.Files
import java.security.KeyPairGenerator
import java.security.Signature
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The installer is ~170MB; verifying it from a single ByteArray (readBytes) plus Ed25519's
 * internal buffering OOM'd the 1GB heap. verify(File) streams the file into the signature so the
 * whole installer is never materialized as one array on top of that. This pins that the streamed
 * verify matches the byte-based result for valid, tampered, and multi-chunk inputs.
 */
class UpdateSignatureVerifierStreamTest {
    private fun b64(b: ByteArray) = Base64.getEncoder().encodeToString(b)

    // Bigger than the verifier's read buffer, so the streaming path makes several update() calls.
    private val data = ByteArray(300_000) { (it % 251).toByte() }

    @Test fun verifyFromFileAcceptsAValidSignatureOverMultipleChunks() {
        val kp = KeyPairGenerator.getInstance("Ed25519").generateKeyPair()
        val sig = Signature.getInstance("Ed25519").run { initSign(kp.private); update(data); sign() }
        val file = Files.createTempFile("inst", ".bin").toFile().apply { writeBytes(data) }
        try {
            assertEquals(
                VerifyResult.Valid,
                UpdateSignatureVerifier.verify(file, b64(sig), b64(kp.public.encoded)),
            )
        } finally {
            file.delete()
        }
    }

    @Test fun verifyFromFileRejectsATamperedInstaller() {
        val kp = KeyPairGenerator.getInstance("Ed25519").generateKeyPair()
        val sig = Signature.getInstance("Ed25519").run { initSign(kp.private); update(data); sign() }
        val tampered = data.copyOf().also { it[1234] = (it[1234] + 1).toByte() }
        val file = Files.createTempFile("inst", ".bin").toFile().apply { writeBytes(tampered) }
        try {
            assertTrue(
                UpdateSignatureVerifier.verify(file, b64(sig), b64(kp.public.encoded)) is VerifyResult.Invalid,
                "a tampered installer must verify as Invalid",
            )
        } finally {
            file.delete()
        }
    }
}
