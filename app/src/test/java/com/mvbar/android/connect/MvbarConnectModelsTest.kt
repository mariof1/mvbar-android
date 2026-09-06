package com.mvbar.android.connect

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MvbarConnectModelsTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun deviceSnapshotDecodesAndBecomesRemotePlayerState() {
        val payload = json.decodeFromString<ConnectDevicesPayload>(
            """
            {
              "devices": [{
                "id": "web-1",
                "name": "Edge on Windows",
                "type": "web",
                "platform": "Win32",
                "capabilities": ["music", "remote-control"],
                "state": {
                  "track": {
                    "id": 42,
                    "title": "The Song",
                    "artist": "The Artist",
                    "album": "The Album",
                    "durationMs": 201000
                  },
                  "queueIndex": 3,
                  "queueLength": 12,
                  "isPlaying": true,
                  "positionMs": 5000,
                  "durationMs": 201000,
                  "updatedAt": 1234
                }
              }]
            }
            """.trimIndent()
        )

        val device = payload.devices.single()
        assertEquals("web-1", device.id)
        assertTrue(device.state.isPlaying)
        assertEquals(12, device.state.queueLength)

        val player = device.asRemotePlayerState()
        assertEquals(42, player.currentTrack?.id)
        assertEquals("The Artist", player.currentTrack?.displayArtist)
        assertEquals(5000L, player.position)
        assertTrue(player.isPlaying)
        assertFalse(player.isPodcastMode)
    }
}
