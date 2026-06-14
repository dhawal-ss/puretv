package com.puretv.twitch.core.api

/**
 * Twitch Device Code Grant flow (public client — no client_secret). Used by the
 * desktop app instead of the authorization_code + loopback flow. Pure helpers
 * (form builders + parsers) are unit-tested; the suspend wrappers are thin.
 */
object DeviceAuth {

    private const val DEVICE_GRANT_TYPE = "urn:ietf:params:oauth:grant-type:device_code"

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
