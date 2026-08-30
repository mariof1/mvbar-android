package com.mvbar.android.social

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SocialRealtimeManagerTest {
    @Test
    fun `secure websocket request keeps https scheme for OkHttp upgrade`() {
        val url = SocialRealtimeManager.webSocketRequestUrl("https://mvbar.example/")

        assertEquals("https", url?.scheme)
        assertEquals("https://mvbar.example/api/ws", url.toString())
    }

    @Test
    fun `local websocket request keeps http scheme and base path`() {
        val url = SocialRealtimeManager.webSocketRequestUrl("http://192.168.1.5/mvbar/")

        assertEquals("http", url?.scheme)
        assertEquals("http://192.168.1.5/mvbar/api/ws", url.toString())
    }

    @Test
    fun `invalid server URL cannot create websocket request`() {
        assertNull(SocialRealtimeManager.webSocketRequestUrl("not a server"))
    }

    @Test
    fun `refresh request advances social revision`() {
        val previous = SocialRealtimeManager.revision.value

        SocialRealtimeManager.requestRefresh()

        assertEquals(previous + 1, SocialRealtimeManager.revision.value)
    }
}
