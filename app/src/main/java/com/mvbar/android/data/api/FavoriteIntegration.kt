package com.mvbar.android.data.api

import com.mvbar.android.data.model.FavoriteMutationResponse

internal fun shouldRetryFavoriteLastfm(response: FavoriteMutationResponse?): Boolean =
    response?.lastfm?.let { !it.submitted && it.reason == "lastfm_error" } == true

internal fun favoriteLastfmFailureMessage(response: FavoriteMutationResponse?): String? {
    val lastfm = response?.lastfm ?: return null
    if (lastfm.submitted || lastfm.reason == "not_connected") return null
    return when (lastfm.reason) {
        "session_expired" -> "Saved in mvbar. Reconnect Last.fm in web settings."
        "missing_metadata" -> "Saved in mvbar. Last.fm needs a title and artist."
        "server_not_configured" -> "Saved in mvbar. Last.fm is not configured."
        else -> "Saved in mvbar. Last.fm update failed; try again."
    }
}
