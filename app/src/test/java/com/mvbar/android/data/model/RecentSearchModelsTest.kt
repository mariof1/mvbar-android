package com.mvbar.android.data.model

import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RecentSearchModelsTest {
    @Test
    fun audiobookRecentsFromWebCanBeOpenedAndNativeSelectionsRoundTrip() {
        val recent = Json { ignoreUnknownKeys = true }.decodeFromString<RecentSearchItem>(
            """{"itemType":"audiobook","itemKey":"42","title":"1984","payload":{"id":42}}"""
        )
        assertEquals(42, recent.asAudiobook()?.id)
        assertNull(recent.copy(payload = RecentSearchPayload(id = -1)).asAudiobook())
        val request = RecentSearchSelection.audiobook(Audiobook(id = 42, title = "1984", author = "George Orwell"))
        assertEquals("audiobook", request.itemType)
        assertEquals("/api/audiobook-art/42", request.imageUrl)
        assertEquals(42, request.optimisticItem().asAudiobook()?.id)
    }

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun artistSelectionUsesTrackArtworkFallbackAndCanBeReopened() {
        val request = RecentSearchSelection.artist(
            SearchArtist(id = 42, name = "Metallica", artTrackId = 9001)
        )

        assertEquals("artist", request.itemType)
        assertEquals("42", request.itemKey)
        assertEquals("/api/library/tracks/9001/art", request.imageUrl)
        assertEquals(
            SearchArtist(id = 42, name = "Metallica"),
            request.optimisticItem().asArtist()
        )
    }

    @Test
    fun albumKeyMatchesTheWebAppFormat() {
        val request = RecentSearchSelection.album(
            SearchAlbum(
                album = "Master of Puppets",
                displayArtist = "Metallica",
                artistId = 42,
                artTrackId = 9001
            )
        )

        assertEquals("[42,\"Master of Puppets\"]", request.itemKey)
        assertEquals("Metallica · Album", request.subtitle)
        assertEquals("/api/library/tracks/9001/art", request.imageUrl)
        assertEquals("Master of Puppets", request.optimisticItem().asAlbum()?.album)
    }

    @Test
    fun serverEpisodePayloadDecodesAndCanBePlayedAgain() {
        val response = json.decodeFromString<RecentSearchItem>(
            """
            {
              "ok": true,
              "itemType": "podcast_episode",
              "itemKey": "77",
              "title": "The episode",
              "subtitle": "The show · Podcast episode",
              "imageUrl": "/api/podcasts/episodes/77/art",
              "payload": {
                "id": 77,
                "podcast_id": 12,
                "title": "The episode",
                "audio_url": "https://media.example/77.mp3",
                "duration_ms": 123000,
                "position_ms": 4000,
                "played": false,
                "podcast_title": "The show"
              },
              "accessedAt": "2026-09-05T12:00:00.000Z"
            }
            """.trimIndent()
        )

        val episode = response.asEpisode()
        assertNotNull(episode)
        episode!!
        assertEquals(77, episode.id)
        assertEquals(12, episode.podcastId)
        assertEquals("https://media.example/77.mp3", episode.audioUrl)
        assertEquals(4000L, episode.positionMs)
        assertNull(response.asTrack())
    }

    @Test
    fun episodeSelectionSerializesWithTheServerFieldNames() {
        val request = RecentSearchSelection.episode(
            Episode(
                id = 77,
                podcastId = 12,
                title = "The episode",
                audioUrl = "https://media.example/77.mp3",
                durationMs = 123000
            )
        )

        val encoded = json.encodeToString(request)

        assertTrue("\"itemType\":\"podcast_episode\"" in encoded)
        assertTrue("\"podcast_id\":12" in encoded)
        assertTrue("\"audio_url\":\"https://media.example/77.mp3\"" in encoded)
        assertTrue("episodePodcastId" !in encoded)
    }

    @Test
    fun trackSelectionStoresOnlyThePlayableIdentity() {
        val request = RecentSearchSelection.track(
            Track(id = 7, title = "Take It Easy", artist = "Eagles")
        )
        val restored = request.optimisticItem().asTrack()

        assertEquals("7", request.itemKey)
        assertEquals("Eagles · Song", request.subtitle)
        assertEquals(7, restored?.id)
        assertEquals("Take It Easy", restored?.title)
        assertEquals("Eagles", restored?.artist)
    }
}
