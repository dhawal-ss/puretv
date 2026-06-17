package com.puretv.twitch.desktop.update

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
 */
object UpdateSignatureVerifier {

    sealed interface VerifyResult {
        /** Signature is valid for the data under the key. */
        data object Valid : VerifyResult
        /** Crypto ran; signature did NOT match — corrupted/truncated/tampered/wrong-key download. */
        data object Invalid : VerifyResult
        /** Verification could not run (Ed25519 provider unavailable, malformed key/signature). */
        data class Errored(val reason: String) : VerifyResult
    }

    /**
     * @param data the installer bytes exactly as downloaded.
     * @param signatureBase64 base64 of the raw 64-byte Ed25519 signature (the `.sig` asset).
     * @param publicKeyBase64 base64 of the X.509/SubjectPublicKeyInfo DER Ed25519 public key.
     */
    fun verify(data: ByteArray, signatureBase64: String, publicKeyBase64: String): VerifyResult {
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
                update(data)
                verify(sigBytes)
            }
        }.getOrElse { return VerifyResult.Errored("Ed25519 verify: ${it::class.simpleName}: ${it.message}") }
        return if (matched) VerifyResult.Valid else VerifyResult.Invalid
    }
}
