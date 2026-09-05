package com.mvbar.android.data.model

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecommendationModelsTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun recommendationResponseDecodesRefreshAndProfileMetadata() {
        val response = json.decodeFromString<RecommendationsResponse>(
            """
            {
              "ok": true,
              "generatedAt": "2026-09-05T12:00:00.000Z",
              "slateId": "slate-123",
              "_cached": true,
              "_stale": true,
              "_refreshing": true,
              "hiddenMixCount": 2,
              "recommendationProfile": "personalized",
              "buckets": []
            }
            """.trimIndent()
        )

        assertEquals("slate-123", response.slateId)
        assertTrue(response.cached)
        assertTrue(response.stale)
        assertTrue(response.refreshing)
        assertEquals(2, response.hiddenMixCount)
        assertEquals(RecommendationProfile.PERSONALIZED, response.recommendationProfile)
    }

    @Test
    fun bucketAddsPlaybackAttributionToEveryTrack() {
        val bucket = RecBucket(
            key = "discover_weekly",
            name = "Discover Weekly",
            tracks = listOf(Track(id = 7), Track(id = 8))
        ).withPlaybackContext("slate-123")

        assertEquals("slate-123", bucket.tracks[0].recommendationSlateId)
        assertEquals("discover_weekly", bucket.tracks[0].recommendationBucketKey)
        assertEquals(0, bucket.tracks[0].recommendationPosition)
        assertEquals(1, bucket.tracks[1].recommendationPosition)
    }

    @Test
    fun feedbackRequestMatchesServerContract() {
        val encoded = json.encodeToString(
            RecommendationFeedbackRequest(
                action = RecommendationFeedbackAction.LESS_LIKE_ARTIST,
                trackId = 7,
                artist = "Metallica",
                bucketKey = "daily_mix_metal"
            )
        )

        assertTrue("\"action\":\"less_like_artist\"" in encoded)
        assertTrue("\"trackId\":7" in encoded)
        assertTrue("\"bucketKey\":\"daily_mix_metal\"" in encoded)
    }

    @Test
    fun playbackSignalIncludesRecommendationContext() {
        val encoded = json.encodeToString(
            PlaybackSignalRequest(
                currentMs = 80_000,
                durationMs = 100_000,
                listenedMs = 80_000,
                completionPct = 0.8,
                slateId = "slate-123",
                bucketKey = "top_picks"
            )
        )

        assertTrue("\"completionPct\":0.8" in encoded)
        assertTrue("\"slateId\":\"slate-123\"" in encoded)
        assertTrue("\"bucketKey\":\"top_picks\"" in encoded)
        assertFalse("recommendationSlateId" in encoded)
    }
}
