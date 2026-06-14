package com.puretv.twitch.core.api

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DeviceAuthTest {

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
    fun refreshForm_is_public_client_refresh_with_no_secret() {
        val form = DeviceAuth.refreshForm("CID", "RT").toMap()
        assertEquals("refresh_token", form["grant_type"])
        assertEquals("RT", form["refresh_token"])
        assertFalse(form.containsKey("client_secret"))
    }
}
