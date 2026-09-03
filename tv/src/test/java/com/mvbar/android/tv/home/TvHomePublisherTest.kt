package com.mvbar.android.tv.home

import com.mvbar.android.tv.data.Episode
import com.mvbar.android.tv.data.RecommendationBucket
import com.mvbar.android.tv.data.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TvHomePublisherTest {
    @Test
    fun prioritizesContinueListeningThenRecentAndRecommendationBuckets() {
        val programs = buildHomePrograms(
            recommendations = listOf(
                RecommendationBucket("daily_mix", "Daily Mix", tracks = listOf(Track(id = 3)))
            ),
            recentlyAdded = listOf(Track(id = 2, title = "New Song", artist = "Artist")),
            episodes = listOf(
                Episode(id = 1, title = "Continue Me", podcastTitle = "Show", positionMs = 20_000L)
            ),
            serverUrl = "https://mvbar.example/"
        )

        assertEquals(listOf("episode:1", "track:2", "bucket:daily_mix"), programs.map { it.id })
        assertEquals("mvbar-tv://episode/1", programs.first().intentUri)
        assertEquals("mvbar-tv://bucket/daily_mix", programs.last().intentUri)
        assertEquals("https://mvbar.example/api/podcasts/episodes/1/art", programs.first().posterUrl)
        assertEquals("https://mvbar.example/api/library/tracks/2/art", programs[1].posterUrl)
    }

    @Test
    fun limitsTheTvHomeRowToTwentyPrograms() {
        val recent = (1..30).map { Track(id = it, title = "Track $it") }

        val programs = buildHomePrograms(emptyList(), recent, emptyList())

        assertTrue(programs.size <= 20)
        assertEquals(8, programs.size)
    }
}
