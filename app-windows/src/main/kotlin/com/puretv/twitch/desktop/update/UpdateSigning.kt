package com.puretv.twitch.desktop.update

/**
 * Embedded publisher key for verifying auto-update installers.
 *
 * [PUBLIC_KEY_BASE64] is the base64 of the X.509/SubjectPublicKeyInfo DER
 * Ed25519 PUBLIC key. The matching PRIVATE key exists ONLY as the CI secret
 * `UPDATE_SIGNING_KEY` (see .github/workflows/release.yml) and must never be
 * committed.
 *
 * ── [ACTION REQUIRED] one-time setup ─────────────────────────────────────────
 * Generate the keypair locally (needs openssl ≥ 1.1.1):
 *
 *   openssl genpkey -algorithm ed25519 -out update_private.pem
 *   openssl pkey -in update_private.pem -pubout -outform DER | base64 -w0
 *
 * 1. Paste the printed base64 line between the quotes in [PUBLIC_KEY_BASE64].
 * 2. Put the FULL contents of update_private.pem into the repo secret
 *    `UPDATE_SIGNING_KEY` (Settings → Secrets and variables → Actions).
 * 3. Delete update_private.pem from disk; keep an offline backup somewhere safe.
 *
 * Until [PUBLIC_KEY_BASE64] is filled in, the updater is fail-closed: it refuses
 * to install anything (safer than installing an unverified binary). Set it
 * before cutting the first signed release.
 */
object UpdateSigning {

    const val PUBLIC_KEY_BASE64: String = "MCowBQYDK2VwAyEAgz+vFKXZfaDYzI8KpIc4OChDqCAegU8a4vIzTAlOVOc="

    /** True once a publisher key is embedded; gates whether updates can install. */
    val isConfigured: Boolean get() = PUBLIC_KEY_BASE64.isNotBlank()
}
