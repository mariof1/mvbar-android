package com.mvbar.android.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdateManagerTest {
    @Test
    fun `patch version newer than current is update`() {
        assertTrue(AppUpdateManager.isNewerVersion("1.0.10", "1.0.9"))
    }

    @Test
    fun `minor version newer than current is update`() {
        assertTrue(AppUpdateManager.isNewerVersion("1.1.0", "1.0.99"))
    }

    @Test
    fun `same version is not update`() {
        assertFalse(AppUpdateManager.isNewerVersion("1.0.9", "1.0.9"))
    }

    @Test
    fun `older version is not update`() {
        assertFalse(AppUpdateManager.isNewerVersion("1.0.8", "1.0.9"))
    }

    @Test
    fun `v prefix and suffix are ignored`() {
        assertTrue(AppUpdateManager.isNewerVersion("v1.2.0-beta1", "1.1.9"))
        assertFalse(AppUpdateManager.isNewerVersion("v1.2.0-beta1", "1.2.0"))
    }
}
