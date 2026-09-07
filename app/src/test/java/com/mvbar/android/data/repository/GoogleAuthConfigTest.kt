package com.mvbar.android.data.repository

import org.junit.Assert.*
import org.junit.Test

class GoogleAuthConfigTest {
    @Test fun formattedJsonEnablesGoogleSignIn() {
        val config = parseGoogleAuthConfig("""{
            "enabled": true,
            "clientId": "test.apps.googleusercontent.com"
        }""")
        assertTrue(config.enabled)
        assertEquals("test.apps.googleusercontent.com", config.clientId)
    }

    @Test fun nestedConfigurationCannotEnableSignIn() {
        assertFalse(parseGoogleAuthConfig("""{"enabled":false,"other":{"enabled":true}}""").enabled)
    }

    @Test fun escapedClientIdIsDecoded() {
        assertEquals("test.apps.googleusercontent.com",
            parseGoogleAuthConfig("""{"enabled":true,"clientId":"test\u002eapps.googleusercontent.com"}""").clientId)
    }

    @Test fun unexpectedTypesAreNotAccepted() {
        assertFalse(parseGoogleAuthConfig("""{"enabled":"true","clientId":null}""").enabled)
        assertNull(parseGoogleAuthConfig("""{"enabled":true,"clientId":123}""").clientId)
        assertNull(parseGoogleAuthConfig("""{"enabled":true,"clientId":" "}""").clientId)
        assertFalse(parseGoogleAuthConfig("[]").enabled)
    }
}
