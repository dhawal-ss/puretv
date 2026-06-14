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
 * Uses the JDK's native Ed25519 (JDK 15+; this module targets 17) — no external
 * crypto dependency. Any malformed input (bad base64, wrong key encoding, wrong
 * signature length) returns `false` rather than throwing: the gate is
 * fail-closed by construction.
 */
object UpdateSignatureVerifier {

    /**
     * @param data the installer bytes exactly as downloaded.
     * @param signatureBase64 base64 of the raw 64-byte Ed25519 signature (the `.sig` asset).
     * @param publicKeyBase64 base64 of the X.509/SubjectPublicKeyInfo DER Ed25519 public key.
     * @return `true` only if [signatureBase64] is a valid signature over [data] under the key.
     */
    fun verify(data: ByteArray, signatureBase64: String, publicKeyBase64: String): Boolean = runCatching {
        val keyBytes = Base64.getDecoder().decode(publicKeyBase64.trim())
        val sigBytes = Base64.getDecoder().decode(signatureBase64.trim())
        val publicKey = KeyFactory.getInstance("Ed25519")
            .generatePublic(X509EncodedKeySpec(keyBytes))
        Signature.getInstance("Ed25519").run {
            initVerify(publicKey)
            update(data)
            verify(sigBytes)
        }
    }.getOrDefault(false)
}
