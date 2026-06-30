package com.puretv.twitch.core.api

import io.ktor.client.HttpClient
import io.ktor.client.request.forms.submitForm
import io.ktor.client.statement.bodyAsText
import io.ktor.http.parameters
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Response from POST /oauth2/device. */
data class DeviceCodeResponse(
    val deviceCode: String,
    val userCode: String,
    val verificationUri: String,
    val expiresInSeconds: Long,
    val intervalSeconds: Long,
)

/** Outcome of a single poll of POST /oauth2/token. */
sealed interface DevicePollResult {
    data class Success(val token: TokenResponse) : DevicePollResult
    data object Pending : DevicePollResult            // authorization_pending — keep polling
    data object SlowDown : DevicePollResult           // defensive: widen the interval
    data class Expired(val reason: String) : DevicePollResult  // invalid/expired — restart
}

/**
 * Twitch Device Code Grant flow (public client — no client_secret). Used by the
 * desktop app instead of the authorization_code + loopback flow. Pure helpers
 * (form builders + parsers) are unit-tested; the suspend wrappers are thin.
 */
object DeviceAuth {

    private const val DEVICE_GRANT_TYPE = "urn:ietf:params:oauth:grant-type:device_code"

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    @Serializable
    private data class DeviceCodeDto(
        @SerialName("device_code") val deviceCode: String,
        @SerialName("user_code") val userCode: String,
        @SerialName("verification_uri") val verificationUri: String,
        @SerialName("expires_in") val expiresIn: Long = 0,
        val interval: Long = 5,
    )

    fun parseDeviceCode(body: String): DeviceCodeResponse {
        val dto = json.decodeFromString(DeviceCodeDto.serializer(), body)
        return DeviceCodeResponse(dto.deviceCode, dto.userCode, dto.verificationUri, dto.expiresIn, dto.interval)
    }

    fun parsePollResult(body: String): DevicePollResult {
        // Success bodies carry a token (access_token is required on TokenResponse,
        // so decoding a {status,message} envelope throws and falls through).
        runCatching { json.decodeFromString(TokenResponse.serializer(), body) }
            .getOrNull()
            ?.takeIf { it.accessToken.isNotBlank() }
            ?.let { return DevicePollResult.Success(it) }

        val message = runCatching {
            json.parseToJsonElement(body).jsonObject["message"]?.jsonPrimitive?.content
        }.getOrNull().orEmpty().lowercase()

        return when {
            message.contains("authorization_pending") -> DevicePollResult.Pending
            message.contains("slow") -> DevicePollResult.SlowDown
            else -> DevicePollResult.Expired(message.ifBlank { "device authorization expired" })
        }
    }

    fun deviceCodeForm(clientId: String, scopes: String): List<Pair<String, String>> =
        listOf("client_id" to clientId, "scopes" to scopes)

    fun pollForm(clientId: String, deviceCode: String, scopes: String): List<Pair<String, String>> =
        listOf(
            "client_id" to clientId,
            "device_code" to deviceCode,
            "grant_type" to DEVICE_GRANT_TYPE,
            "scopes" to scopes,
        )

    /**
     * Refresh form. Unlike the device-code REQUEST and POLL (which are genuine
     * public-client calls and must NOT carry a secret), Twitch's /oauth2/token
     * refresh endpoint requires `client_secret` for a *confidential* client —
     * which this app is (it ships and uses a secret for the PKCE/auth-code flow,
     * see [TwitchConfig.CLIENT_SECRET] and [PkceAuth.refreshToken]). Omitting it
     * here made every refresh fail with a `{status,message}` envelope ->
     * [TokenRefreshException] -> the desktop store cleared the session, forcing a
     * re-login on (essentially) every launch once the ~4h access token went stale.
     * Twitch ignores the secret for a public client, so sending it is safe either
     * way.
     */
    fun refreshForm(clientId: String, refreshToken: String, clientSecret: String): List<Pair<String, String>> =
        listOf(
            "client_id" to clientId,
            "client_secret" to clientSecret,
            "grant_type" to "refresh_token",
            "refresh_token" to refreshToken,
        )

    suspend fun requestDeviceCode(
        http: HttpClient,
        clientId: String = TwitchConfig.CLIENT_ID,
        scopes: String = TwitchConfig.SCOPES,
    ): DeviceCodeResponse {
        val response = http.submitForm(
            url = "${TwitchConfig.AUTH_BASE}/device",
            formParameters = parameters { deviceCodeForm(clientId, scopes).forEach { (k, v) -> append(k, v) } },
        )
        return parseDeviceCode(response.bodyAsText())
    }

    suspend fun pollOnce(
        http: HttpClient,
        deviceCode: String,
        clientId: String = TwitchConfig.CLIENT_ID,
        scopes: String = TwitchConfig.SCOPES,
    ): DevicePollResult {
        val response = http.submitForm(
            url = "${TwitchConfig.AUTH_BASE}/token",
            formParameters = parameters { pollForm(clientId, deviceCode, scopes).forEach { (k, v) -> append(k, v) } },
        )
        return parsePollResult(response.bodyAsText())
    }

    suspend fun refreshToken(
        http: HttpClient,
        refreshToken: String,
        clientId: String = TwitchConfig.CLIENT_ID,
        clientSecret: String = TwitchConfig.CLIENT_SECRET,
    ): TokenResponse {
        val response = http.submitForm(
            url = "${TwitchConfig.AUTH_BASE}/token",
            formParameters = parameters { refreshForm(clientId, refreshToken, clientSecret).forEach { (k, v) -> append(k, v) } },
        )
        return parseRefreshResponse(response.bodyAsText())
    }

    /**
     * Decode a /oauth2/token refresh response. Twitch returns a `{status,message}`
     * envelope (no `access_token`) when the refresh token is invalid/expired/
     * revoked; decoding that as a [TokenResponse] throws an opaque
     * `MissingFieldException`. Read the body once and surface a clean, typed
     * [TokenRefreshException] so callers can route to a re-login prompt
     * (audit F4).
     */
    internal fun parseRefreshResponse(body: String): TokenResponse {
        runCatching { json.decodeFromString(TokenResponse.serializer(), body) }
            .getOrNull()
            ?.takeIf { it.accessToken.isNotBlank() }
            ?.let { return it }

        val message = runCatching {
            json.parseToJsonElement(body).jsonObject["message"]?.jsonPrimitive?.content
        }.getOrNull().orEmpty()
        throw TokenRefreshException(message.ifBlank { "refresh token rejected" })
    }
}

/** Thrown when a token refresh fails (invalid/expired/revoked refresh token). */
class TokenRefreshException(message: String) : Exception(message)
