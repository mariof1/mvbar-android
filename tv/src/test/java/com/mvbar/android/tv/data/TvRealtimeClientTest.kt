package com.mvbar.android.tv.data

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
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

    @Test
    fun decodesAConnectDeviceSnapshotPublishedByAnotherPlayer() {
        val device = Json { ignoreUnknownKeys = true }.decodeFromString<TvConnectDevice>(
            """
            {
              "id": "phone-1",
              "name": "Pixel phone",
              "type": "android",
              "capabilities": ["music", "remote-control"],
              "state": {
                "track": { "id": 42, "title": "The Song", "artist": "The Artist" },
                "queueIndex": 4,
                "queueLength": 18,
                "isPlaying": true,
                "positionMs": 32000,
                "durationMs": 201000
              }
            }
            """.trimIndent()
        )

        assertEquals("phone-1", device.id)
        assertEquals(42, device.state.track?.id)
        assertEquals(18, device.state.queueLength)
        assertTrue(device.state.isPlaying)
    }
}
