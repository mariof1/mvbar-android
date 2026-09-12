package com.mvbar.android.data.api

import com.mvbar.android.data.model.FavoriteMutationResponse
import com.mvbar.android.data.model.LastfmFavoriteResult
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FavoriteIntegrationTest {
    @Test
    fun `connected Lastfm failures are visible and transient failures retry`() {
        val transient = FavoriteMutationResponse(
            ok = true,
            lastfm = LastfmFavoriteResult(submitted = false, reason = "lastfm_error")
        )
        val expired = FavoriteMutationResponse(
            ok = true,
            lastfm = LastfmFavoriteResult(submitted = false, reason = "session_expired")
        )

        assertTrue(shouldRetryFavoriteLastfm(transient))
        assertTrue(favoriteLastfmFailureMessage(transient)?.contains("update failed") == true)
        assertFalse(shouldRetryFavoriteLastfm(expired))
        assertTrue(favoriteLastfmFailureMessage(expired)?.contains("Reconnect") == true)
    }

    @Test
    fun `disconnected or successful Lastfm state needs no warning`() {
        val disconnected = FavoriteMutationResponse(
            ok = true,
            lastfm = LastfmFavoriteResult(submitted = false, reason = "not_connected")
        )
        val submitted = FavoriteMutationResponse(
            ok = true,
            lastfm = LastfmFavoriteResult(submitted = true)
        )

        assertNull(favoriteLastfmFailureMessage(null))
        assertNull(favoriteLastfmFailureMessage(disconnected))
        assertNull(favoriteLastfmFailureMessage(submitted))
        assertFalse(shouldRetryFavoriteLastfm(disconnected))
        assertFalse(shouldRetryFavoriteLastfm(submitted))
    }
}
