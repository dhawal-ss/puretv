package com.puretv.twitch.core.api

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

    fun refreshForm(clientId: String, refreshToken: String): List<Pair<String, String>> =
        listOf(
            "client_id" to clientId,
            "grant_type" to "refresh_token",
            "refresh_token" to refreshToken,
        )
}
