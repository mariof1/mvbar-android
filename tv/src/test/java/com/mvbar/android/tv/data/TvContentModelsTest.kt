package com.mvbar.android.tv.data

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class TvContentModelsTest {
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
    }

    @Test
    fun decodesServerStringIdsForPodcastsAndAudiobooks() {
        val podcasts = json.decodeFromString<PodcastsResponse>(
            """{"ok":true,"podcasts":[{"id":"30","title":"Busem Przez Świat","unplayed_count":5}]}"""
        )
        val books = json.decodeFromString<List<Audiobook>>(
            """[{"id":"2023","title":"12 Rules for Life","duration_ms":"56405171","chapter_count":18}]"""
        )

        assertEquals(30, podcasts.podcasts.single().id)
        assertEquals(2023, books.single().id)
        assertEquals(56_405_171L, books.single().durationMs)
    }

    @Test
    fun convertsFlatPlaylistItemsIntoPlayableTracks() {
        val response = json.decodeFromString<PlaylistItemsResponse>(
            """{"ok":true,"items":[{"id":9,"track_id":42,"position":0,"title":"Song","artist":"Artist","album":"Album","duration_ms":123000}]}"""
        )

        val track = response.items.single().toTrack()
        assertNotNull(track)
        assertEquals(42, track?.id)
        assertEquals("Song", track?.displayTitle)
        assertEquals("Artist", track?.displayArtist)
    }

    @Test
    fun decodesAllSearchMediaFamilies() {
        val response = json.decodeFromString<SearchResponse>(
            """{"ok":true,"hits":[{"id":1,"title":"Song"}],"playlists":[{"id":2,"name":"Mix"}],"podcasts":[{"id":"3","title":"Show"}],"podcastEpisodes":[{"id":"4","podcast_id":"3","title":"Episode"}]}"""
        )

        assertEquals(1, response.hits.size)
        assertEquals("Mix", response.playlists.single().name)
        assertEquals("Show", response.podcasts.single().title)
        assertEquals("Episode", response.podcastEpisodes.single().title)
    }

    @Test
    fun decodesRecommendationArtworkCollageMetadata() {
        val response = json.decodeFromString<RecommendationsResponse>(
            """{"ok":true,"buckets":[{"key":"daily_mix","name":"Daily Mix","count":4,"tracks":[{"id":1}],"art_paths":["one.jpg","two.jpg","three.jpg","four.jpg"],"art_hashes":["a","b","c","d"]}]}"""
        )

        val bucket = response.buckets.single()
        assertEquals(4, bucket.artPaths.size)
        assertEquals(listOf("a", "b", "c", "d"), bucket.artHashes)
        assertEquals(4, bucket.count)
    }
}
