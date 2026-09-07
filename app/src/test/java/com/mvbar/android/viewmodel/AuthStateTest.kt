package com.mvbar.android.viewmodel

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthStateTest {
    private val enabled = AuthState(isLoading = false, googleEnabled = true,
        googleClientId = "server-a-client", googleServerUrl = "https://server-a.test")

    @Test fun editingServerImmediatelyInvalidatesOldGoogleConfiguration() {
        assertTrue(enabled.canUseGoogleAuth("https://server-a.test"))
        assertFalse(enabled.canUseGoogleAuth("https://server-b.test"))
        assertFalse(enabled.canUseGoogleAuth(""))
        assertFalse(enabled.canUseGoogleAuth("https://server-a.test/other"))
    }

    @Test fun whitespaceAndTrailingSlashKeepTheSameServer() {
        assertTrue(enabled.canUseGoogleAuth("  https://server-a.test/  "))
    }

    @Test fun pendingOrUnattributedDiscoveryCannotBeUsed() {
        assertFalse(enabled.copy(checkingGoogle = true).canUseGoogleAuth("https://server-a.test"))
        assertFalse(enabled.copy(googleServerUrl = null).canUseGoogleAuth("https://server-a.test"))
        assertFalse(enabled.copy(googleEnabled = false).canUseGoogleAuth("https://server-a.test"))
    }
}
