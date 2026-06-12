package com.puretv.twitch.core.api

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.forms.submitForm
import io.ktor.client.statement.bodyAsText
import io.ktor.http.parameters
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.random.Random

/**
 * RFC 7636 PKCE helpers shared by both the Android/TV (Custom Tabs + deep link)
 * and Windows Desktop (localhost callback server) OAuth flows.
 *
 * Flow overview:
 *   1. generateVerifier() / deriveChallenge() -> open TwitchConfig.authorizeUrl(...)
 *   2. Capture the redirect's `code` + `state` query params
 *   3. exchangeCodeForToken(code, verifier, redirectUri)
 *   4. Persist the resulting [TokenResponse] using the platform-appropriate
 *      encrypted store (see SECTION 03.2 of the build spec)
 */
object PkceAuth {

    private const val VERIFIER_CHARS =
        "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~"

    /** A cryptographically-sufficient random verifier string (43-128 chars). */
    fun generateVerifier(length: Int = 64): String =
        buildString(length) {
            repeat(length) { append(VERIFIER_CHARS[Random.nextInt(VERIFIER_CHARS.length)]) }
        }

    fun generateState(): String = generateVerifier(32)

    /**
     * S256 challenge = base64url( sha256(verifier) ), no padding.
     * Implemented per-platform via [Sha256] expect/actual since kotlinx-crypto
     * is not yet stable across all KMP targets at the pinned Kotlin version.
     */
    fun deriveChallenge(verifier: String): String =
        base64UrlNoPad(Sha256.digest(verifier.encodeToByteArray()))

    private fun base64UrlNoPad(bytes: ByteArray): String {
        val table = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
        val sb = StringBuilder()
        var i = 0
        while (i < bytes.size) {
            val b0 = bytes[i].toInt() and 0xFF
            val b1 = if (i + 1 < bytes.size) bytes[i + 1].toInt() and 0xFF else -1
            val b2 = if (i + 2 < bytes.size) bytes[i + 2].toInt() and 0xFF else -1
            sb.append(table[b0 ushr 2])
            sb.append(table[((b0 and 0x03) shl 4) or (if (b1 >= 0) (b1 ushr 4) else 0)])
            if (b1 >= 0) sb.append(table[((b1 and 0x0F) shl 2) or (if (b2 >= 0) (b2 ushr 6) else 0)])
            if (b2 >= 0) sb.append(table[b2 and 0x3F])
            i += 3
        }
        return sb.toString().replace('+', '-').replace('/', '_')
    }

    suspend fun exchangeCodeForToken(
        httpClient: HttpClient,
        code: String,
        codeVerifier: String,
        redirectUri: String,
    ): TokenResponse {
        // Don't call .body() directly — if Twitch returns an error envelope
        // ({"status": 400, "message": "..."}), the kotlinx-serialization failure
        // ("Field 'access_token' is required ... missing at path: $") buries
        // the actual problem. Read the body once, attempt to deserialize, and
        // on failure throw with Twitch's actual response text so the UI can
        // show the real cause (wrong secret, expired code, etc.).
        val response = httpClient.submitForm(
            url = "${TwitchConfig.AUTH_BASE}/token",
            formParameters = parameters {
                append("client_id", TwitchConfig.CLIENT_ID)
                // Twitch quirk: client_secret is required for authorization_code
                // even when sending code_verifier. PKCE doesn't substitute for
                // the secret in Twitch's implementation the way it does for most
                // other OAuth providers. See TwitchConfig kdoc.
                append("client_secret", TwitchConfig.CLIENT_SECRET)
                append("grant_type", "authorization_code")
                append("code", code)
                append("redirect_uri", redirectUri)
                // Sent anyway — Twitch ignores it but if they ever add proper
                // PKCE support, this becomes a useful extra binding to the
                // /authorize call's code_challenge.
                append("code_verifier", codeVerifier)
            },
        )
        val raw = response.bodyAsText()
        val parser = Json { ignoreUnknownKeys = true }
        return runCatching { parser.decodeFromString<TokenResponse>(raw) }.getOrElse { parseError ->
            throw TokenExchangeException(
                "Twitch /oauth2/token returned HTTP ${response.status.value}. Body: $raw",
                parseError,
            )
        }
    }

    suspend fun refreshToken(httpClient: HttpClient, refreshToken: String): TokenResponse =
        httpClient.submitForm(
            url = "${TwitchConfig.AUTH_BASE}/token",
            formParameters = parameters {
                append("client_id", TwitchConfig.CLIENT_ID)
                append("client_secret", TwitchConfig.CLIENT_SECRET)
                append("grant_type", "refresh_token")
                append("refresh_token", refreshToken)
            },
        ).body()
}

class TokenExchangeException(message: String, cause: Throwable? = null) : Exception(message, cause)

@Serializable
data class TokenResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String? = null,
    @SerialName("expires_in") val expiresInSeconds: Long = 0,
    @SerialName("token_type") val tokenType: String = "bearer",
    val scope: List<String> = emptyList(),
)

/**
 * Platform SHA-256. Android/Desktop (JVM) both implement this with
 * java.security.MessageDigest in their respective source sets.
 */
expect object Sha256 {
    fun digest(input: ByteArray): ByteArray
}
