package com.puretv.twitch.core.api

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DeviceAuthTest {

    @Test
    fun parseRefreshResponse_success_returns_token() {
        val body = """{"access_token":"AT2","refresh_token":"RT2","expires_in":3600}"""
        val token = DeviceAuth.parseRefreshResponse(body)
        assertEquals("AT2", token.accessToken)
        assertEquals("RT2", token.refreshToken)
    }

    @Test
    fun parseRefreshResponse_error_envelope_throws_typed_not_missingfield() {
        // A revoked/invalid refresh token returns {status,message} with no
        // access_token — must surface a clean TokenRefreshException (audit F4),
        // not an opaque kotlinx MissingFieldException.
        assertFailsWith<TokenRefreshException> {
            DeviceAuth.parseRefreshResponse("""{"status":400,"message":"Invalid refresh token"}""")
        }
    }

    @Test
    fun deviceCodeForm_has_client_id_and_scopes_and_no_secret() {
        val form = DeviceAuth.deviceCodeForm("CID", "scope:a scope:b").toMap()
        assertEquals("CID", form["client_id"])
        assertEquals("scope:a scope:b", form["scopes"])
        assertFalse(form.containsKey("client_secret"), "device request must not send a client_secret")
    }

    @Test
    fun pollForm_has_device_grant_type_and_no_secret() {
        val form = DeviceAuth.pollForm("CID", "DEV123", "scope:a").toMap()
        assertEquals("CID", form["client_id"])
        assertEquals("DEV123", form["device_code"])
        assertEquals("urn:ietf:params:oauth:grant-type:device_code", form["grant_type"])
        assertFalse(form.containsKey("client_secret"))
    }

    @Test
    fun refreshForm_includes_client_secret_for_confidential_client() {
        // Twitch's /oauth2/token refresh endpoint requires client_secret for a
        // confidential client (which this app is — it ships and uses a secret for
        // the PKCE/auth-code flow). Omitting it made every device-flow refresh fail
        // and the desktop store then cleared the session, forcing a re-login on
        // essentially every launch. Twitch ignores the secret for a public client,
        // so sending it is safe regardless of client type.
        val form = DeviceAuth.refreshForm("CID", "RT", "SECRET").toMap()
        assertEquals("refresh_token", form["grant_type"])
        assertEquals("RT", form["refresh_token"])
        assertEquals("CID", form["client_id"])
        assertEquals("SECRET", form["client_secret"])
    }

    @Test
    fun parseDeviceCode_reads_all_fields() {
        val body = """
            {"device_code":"DC","user_code":"ABCD-1234",
             "verification_uri":"https://www.twitch.tv/activate",
             "expires_in":1800,"interval":5}
        """.trimIndent()
        val r = DeviceAuth.parseDeviceCode(body)
        assertEquals("DC", r.deviceCode)
        assertEquals("ABCD-1234", r.userCode)
        assertEquals("https://www.twitch.tv/activate", r.verificationUri)
        assertEquals(1800L, r.expiresInSeconds)
        assertEquals(5L, r.intervalSeconds)
    }

    @Test
    fun parsePollResult_pending() {
        val r = DeviceAuth.parsePollResult("""{"status":400,"message":"authorization_pending"}""")
        assertTrue(r is DevicePollResult.Pending, "got $r")
    }

    @Test
    fun parsePollResult_success_returns_token() {
        val body = """{"access_token":"AT","refresh_token":"RT","expires_in":3600,"token_type":"bearer","scope":["chat:read"]}"""
        val r = DeviceAuth.parsePollResult(body)
        assertTrue(r is DevicePollResult.Success, "got $r")
        assertEquals("AT", (r as DevicePollResult.Success).token.accessToken)
        assertEquals("RT", r.token.refreshToken)
    }

    @Test
    fun parsePollResult_invalid_device_code_is_expired() {
        val r = DeviceAuth.parsePollResult("""{"status":400,"message":"invalid device code"}""")
        assertTrue(r is DevicePollResult.Expired, "got $r")
    }
}
