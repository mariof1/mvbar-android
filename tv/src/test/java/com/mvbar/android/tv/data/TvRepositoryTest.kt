package com.mvbar.android.tv.data

import org.junit.Assert.assertEquals
import org.junit.Test

class TvRepositoryTest {
    @Test
    fun normalizesServerUrl() {
        assertEquals("https://mvbar.example/", normalizeServerUrl("mvbar.example"))
        assertEquals("http://192.168.1.2:3000/", normalizeServerUrl(" http://192.168.1.2:3000/ "))
    }
}
