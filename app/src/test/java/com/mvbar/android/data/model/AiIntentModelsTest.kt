package com.mvbar.android.data.model

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AiIntentModelsTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun requestMatchesServerContract() {
        val encoded = json.encodeToString(AiIntentRequest("quiet evening jazz"))

        assertEquals("{\"query\":\"quiet evening jazz\"}", encoded)
    }

    @Test
    fun responseDecodesCamelCaseTracksAndConvertsThemForPlayback() {
        val response = json.decodeFromString<AiIntentResponse>(
            """
            {
              "ok": true,
              "model": "openrouter/free",
              "requestedModel": "openrouter/free",
              "usedFreeFallback": false,
              "originalQuery": "play calm rock",
              "action": "play",
              "requestedTrackCount": 20,
              "searchQuery": "calm rock",
              "explanation": "A calmer rock mix",
              "interpretation": { "moods": ["calm"] },
              "tracks": [{
                "id": 42,
                "title": "The Song",
                "artist": "The Artist",
                "albumArtist": "Album Artist",
                "displayArtist": "The Artist & Guest",
                "album": "The Album",
                "path": "/music/the-song.flac",
                "ext": "flac",
                "durationMs": 201000
              }]
            }
            """.trimIndent()
        )

        assertEquals("play", response.action)
        assertEquals(20, response.requestedTrackCount)
        assertEquals(1, response.playableTracks.size)

        val track = response.playableTracks.single()
        assertEquals(42, track.id)
        assertEquals("The Song", track.title)
        assertEquals("The Artist & Guest", track.displayArtist)
        assertEquals("Album Artist", track.albumArtist)
        assertEquals(201, track.durationSeconds)
        assertTrue(track.path?.endsWith("the-song.flac") == true)
    }
}
