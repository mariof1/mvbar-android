package com.mvbar.android.tv.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class TvRealtimeClientTest {
    @Test
    fun buildsAuthenticatedWebSocketEndpointFromServerRoot() {
        val url = tvWebSocketUrl("https://mvbar.example.test/")

        assertNotNull(url)
        assertEquals("https://mvbar.example.test/api/ws", url.toString())
    }

    @Test
    fun classifiesEventsByRequiredRefreshScope() {
        assertEquals(TvRealtimeRefresh.ALL_CONTENT, realtimeRefreshForEvent("library:update"))
        assertEquals(TvRealtimeRefresh.ALL_CONTENT, realtimeRefreshForEvent("playlist:item_added"))
        assertEquals(TvRealtimeRefresh.ALL_CONTENT, realtimeRefreshForEvent("favorite:added"))
        assertEquals(TvRealtimeRefresh.RECOMMENDATIONS, realtimeRefreshForEvent("history:added"))
        assertEquals(TvRealtimeRefresh.LONG_FORM, realtimeRefreshForEvent("podcast:progress"))
        assertEquals(TvRealtimeRefresh.NONE, realtimeRefreshForEvent("social:friend_request"))
    }
}
