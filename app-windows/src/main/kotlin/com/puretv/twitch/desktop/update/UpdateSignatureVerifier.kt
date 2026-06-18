package com.puretv.twitch.desktop.update

import java.io.File
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

/**
 * Verifies the Ed25519 signature of a downloaded update installer against the
 * publisher public key embedded in the app ([UpdateSigning.PUBLIC_KEY_BASE64]).
 *
 * This is the integrity gate that makes auto-update safe: the signing private
 * key exists only in CI (the `UPDATE_SIGNING_KEY` secret — see release.yml), so
 * a tampered, swapped, or man-in-the-middled installer cannot produce a valid
 * signature and is never executed.
 *
 * The result distinguishes a genuine signature MISMATCH ([VerifyResult.Invalid] —
 * the crypto ran and the bytes did not validate, i.e. a corrupted/truncated/
 * tampered/wrong download) from an inability to RUN the check at all
 * ([VerifyResult.Errored] — Ed25519 provider unavailable in the runtime, malformed
 * key/signature). They need different handling: a mismatch is worth a re-download;
 * an Errored is an app/runtime problem a re-download can't fix. The old Boolean
 * collapsed both into "false", which is exactly why "signature check FAILED" was
 * undiagnosable. Never throws — the gate stays fail-closed.
 *
 * MEMORY (the installer is ~170MB). Prefer [verify] (File): it streams the file
 * into the signature in small reads, so the whole installer is never materialized
 * as one heap ByteArray on top of Ed25519's own internal buffering. Reading the
 * whole thing into a ByteArray first (the old path) plus that internal buffer
 * roughly doubled the transient heap and OOM'd the player's 1GB heap mid-update.
 */
object UpdateSignatureVerifier {

    private const val READ_BUFFER_BYTES = 1 shl 16 // 64 KiB

    sealed interface VerifyResult {
        /** Signature is valid for the data under the key. */
        data object Valid : VerifyResult
        /** Crypto ran; signature did NOT match — corrupted/truncated/tampered/wrong-key download. */
        data object Invalid : VerifyResult
        /** Verification could not run (Ed25519 provider unavailable, malformed key/signature). */
        data class Errored(val reason: String) : VerifyResult
    }

    /**
     * Stream [installer] into the verifier (preferred — constant heap beyond Ed25519's
     * own buffer). [signatureBase64] is the `.sig` asset; [publicKeyBase64] is the
     * X.509/SubjectPublicKeyInfo DER Ed25519 public key.
     */
    fun verify(installer: File, signatureBase64: String, publicKeyBase64: String): VerifyResult =
        verifyWith(signatureBase64, publicKeyBase64) { signature ->
            installer.inputStream().buffered().use { input ->
                val buf = ByteArray(READ_BUFFER_BYTES)
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    signature.update(buf, 0, n)
                }
            }
        }

    /** Verify already-in-memory [data]. Retained for tests/callers that hold the bytes. */
    fun verify(data: ByteArray, signatureBase64: String, publicKeyBase64: String): VerifyResult =
        verifyWith(signatureBase64, publicKeyBase64) { it.update(data) }

    /**
     * Shared decode + signature lifecycle; [feed] supplies the message bytes to the
     * initialized [Signature] (either in one [Signature.update] or streamed). [feed] runs
     * inside the verify runCatching, so an IO error while streaming surfaces as Errored,
     * never an uncaught throw.
     */
    private inline fun verifyWith(
        signatureBase64: String,
        publicKeyBase64: String,
        feed: (Signature) -> Unit,
    ): VerifyResult {
        val keyBytes = runCatching { Base64.getDecoder().decode(publicKeyBase64.trim()) }
            .getOrElse { return VerifyResult.Errored("public-key base64 decode: ${it.message}") }
        val sigBytes = runCatching { Base64.getDecoder().decode(signatureBase64.trim()) }
            .getOrElse { return VerifyResult.Errored("signature base64 decode: ${it.message}") }
        val publicKey = runCatching {
            KeyFactory.getInstance("Ed25519").generatePublic(X509EncodedKeySpec(keyBytes))
        }.getOrElse { return VerifyResult.Errored("Ed25519 key/provider: ${it::class.simpleName}: ${it.message}") }
        val matched = runCatching {
            Signature.getInstance("Ed25519").run {
                initVerify(publicKey)
                feed(this)
                verify(sigBytes)
            }
        }.getOrElse { return VerifyResult.Errored("Ed25519 verify: ${it::class.simpleName}: ${it.message}") }
        return if (matched) VerifyResult.Valid else VerifyResult.Invalid
    }
}
