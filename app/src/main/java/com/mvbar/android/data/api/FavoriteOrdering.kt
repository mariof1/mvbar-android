package com.mvbar.android.data.api

import com.mvbar.android.data.model.FavoritesResponse
import com.mvbar.android.data.model.Track
import kotlinx.serialization.Serializable

@Serializable
data class FavoriteMoveRequest(val trackId: Int, val beforeTrackId: Int?)

/** Fetch the complete server order, including favorites beyond the first page. */
suspend fun MvbarApi.getAllFavorites(): FavoritesResponse {
    val tracks = mutableListOf<Track>()
    var offset = 0
    while (true) {
        val page = getFavorites(limit = 200, offset = offset)
        check(page.ok) { "Could not load favorites" }
        tracks.addAll(page.tracks)
        if (page.tracks.size < 200) break
        offset += page.tracks.size
    }
    return FavoritesResponse(ok = true, tracks = tracks.distinctBy { it.id })
}
